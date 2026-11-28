import React, { useState, useEffect, useMemo, useRef, useCallback, lazy, Suspense } from 'react';
import { ShoppingCart } from 'lucide-react';
import { getAllProductsApi } from '../services/productsApi';
import { getServicesApi } from '../services/servicesApi';
import { getCustomersApi } from '../services/customersApi';
import { createSaleApi } from '../services/salesApi';
import { getBillingSummaryApi, getSaleInvoiceApi } from '../services/billingApi';
import CheckoutModal, { SaleResultModal } from '../components/pos/CheckoutModal';
import ScanToasts from '../components/pos/ScanToasts';
import ProductCatalog from '../components/pos/ProductCatalog';
import VoiceAssistantBar from '../components/pos/VoiceAssistantBar';
import AgentConfirmation from '../components/pos/AgentConfirmation';
import CartPanel from '../components/pos/CartPanel';
// zxing pesa ~480 kB: solo se descarga al abrir la cámara.
const LazyBarcodeScanner = lazy(() => import('../components/scanner/BarcodeScanner'));
function BarcodeScanner(props) {
  return <Suspense fallback={null}><LazyBarcodeScanner {...props} /></Suspense>;
}
import { getCategoriesApi } from '../services/categoriesApi';
import { getParameterValuesApi } from '../services/parametersApi';
import { newIdempotencyKey } from '../services/http';
import useModal from '../hooks/useModal';
import useLoad from '../hooks/useLoad';
import useVoiceAgent from '../hooks/useVoiceAgent';
import asList from '../utils/asList';
import { applyAgentTicket, ticketSignature } from '../utils/agentTicket';
import { ITEM_PRODUCT, ITEM_SERVICE, cartKey, isService, toSaleLine } from '../utils/cartItem';
import { defaultCreditDueDate } from '../utils/credit';

export default function POSBento() {
  const { notify } = useModal();
  // Stock y precios se refrescan solos sin tocar el carrito ni el cliente elegido.
  // El catálogo del POS junta productos y servicios activos; un fallo al listar servicios no frena la venta de productos.
  const { data: productsList, loading: productsLoading, reload: reloadProducts } = useLoad(
    async () => {
      const [products, services] = await Promise.all([
        asList(getAllProductsApi()),
        asList(getServicesApi()).catch(() => []),
      ]);
      return [
        ...products.map((p) => ({ ...p, itemType: ITEM_PRODUCT })),
        ...services.map((s) => ({ ...s, itemType: ITEM_SERVICE })),
      ];
    },
    [],
    { initial: [], autoRefresh: true },
  );
  const { data: customersList, loading: customersLoading, reload: reloadCustomers, setData: setCustomersList } = useLoad(
    () => asList(getCustomersApi()),
    [],
    { initial: [] },
  );
  const { data: catalog, loading: catalogLoading } = useLoad(
    async () => {
      const [cats, methods] = await Promise.all([
        asList(getCategoriesApi()).catch(() => []),
        asList(getParameterValuesApi('PAYMENT_METHODS')).catch(() => []),
      ]);
      return { categories: ['Todos', ...cats.map((c) => c.name)], paymentMethods: methods.filter((m) => m.active) };
    },
    [],
    { initial: { categories: ['Todos'], paymentMethods: [] } },
  );
  const { categories: categoriesList, paymentMethods } = catalog;
  // Si no se puede saber, se asume que el local no factura: mejor no ofrecer una factura que no saldría.
  const { data: billing } = useLoad(
    () => getBillingSummaryApi().catch(() => null),
    [],
    { initial: null },
  );
  const dianAvailable = Boolean(billing?.active);
  const loading = productsLoading || customersLoading || catalogLoading;
  const [selectedCategory, setSelectedCategory] = useState('Todos');
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState('CASH');
  const [searchQuery, setSearchQuery] = useState('');
  const [cart, setCart] = useState([]);
  // Sin elección explícita se usa el primer cliente (consumidor final); tras cada venta se vuelve a ese valor.
  const [customerChoice, setSelectedCustomer] = useState(null);
  const selectedCustomer = customerChoice ?? customersList[0] ?? null;

  const [issueDian, setIssueDian] = useState(false);
  const [creditDueDate, setCreditDueDate] = useState(defaultCreditDueDate);
  const [showCheckoutModal, setShowCheckoutModal] = useState(false);
  const [cashTendered, setCashTendered] = useState('');
  const [saleCompleted, setSaleCompleted] = useState(null);
  const [showMobileCart, setShowMobileCart] = useState(false);
  const [isSubmittingSale, setIsSubmittingSale] = useState(false);
  const [showScanner, setShowScanner] = useState(false);
  // Huella del ticket cuando el asistente pidió confirmar la venta. Solo vale mientras el ticket siga igual.
  const [agentConfirmSignature, setAgentConfirmSignature] = useState(null);

  const barcodeInputRef = useRef(null);
  // Clave de idempotencia estable para el intento de cobro en curso: se reutiliza en
  // reintentos para que un doble clic o un reintento de red no genere ventas duplicadas.
  const saleIdempotencyKeyRef = useRef(null);

  // Feedback no bloqueante de escaneo (a diferencia de useModal().notify, que exige click
  // en "Entendido" y frenaría escaneos sucesivos rápidos del lector físico).
  const [scanToasts, setScanToasts] = useState([]);
  const scanToastIdRef = useRef(0);

  const pushScanToast = useCallback((type, message) => {
    const id = ++scanToastIdRef.current;
    setScanToasts(prev => [...prev, { id, type, message }]);
    setTimeout(() => {
      setScanToasts(prev => prev.filter(t => t.id !== id));
    }, 1500);
  }, []);

  // Beep de éxito/error generado con Web Audio API (sin assets/dependencias nuevas).
  const playScanTone = useCallback((success) => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const oscillator = ctx.createOscillator();
      const gain = ctx.createGain();
      oscillator.connect(gain);
      gain.connect(ctx.destination);
      oscillator.type = 'sine';
      oscillator.frequency.value = success ? 1046.5 : 220;
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + (success ? 0.12 : 0.25));
      oscillator.start();
      oscillator.stop(ctx.currentTime + (success ? 0.12 : 0.25));
      oscillator.onended = () => ctx.close();
    } catch {
      // Entorno sin soporte de Web Audio (o autoplay bloqueado): el feedback visual basta.
    }
  }, []);

  const focusBarcodeInput = useCallback(() => {
    barcodeInputRef.current?.focus();
  }, []);

  // El lector de código de barras funciona como teclado: mantiene el foco en este campo
  // para que escanear (sin hacer click en nada) funcione en cualquier momento del flujo.
  useEffect(() => {
    focusBarcodeInput();
  }, [focusBarcodeInput]);

  // Atajo [F4]: abre el cobro cuando hay algo en el ticket y no hay otro modal abierto.
  const canOpenCheckout = cart.length > 0 && !showCheckoutModal && !saleCompleted;
  useEffect(() => {
    if (!canOpenCheckout) return undefined;
    const onKeyDown = (e) => {
      if (e.key !== 'F4') return;
      e.preventDefault();
      setShowCheckoutModal(true);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [canOpenCheckout]);

  const filteredProducts = productsList.filter(p => {
    const matchesCategory = selectedCategory === 'Todos' || p.categoryName === selectedCategory;
    const matchesSearch = (p.name && p.name.toLowerCase().includes(searchQuery.toLowerCase())) ||
                          (p.barcode && p.barcode.includes(searchQuery)) ||
                          ((p.internalCode || p.code) && (p.internalCode || p.code).toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesCategory && matchesSearch;
  });

  // Un servicio no tiene existencias: no se limita su cantidad ni se bloquea por stock.
  const addToCart = (product) => {
    const service = isService(product);
    const stockVal = service ? Infinity : (product.quantityAvailable ?? product.stock ?? 0);
    if (stockVal <= 0) return;

    setCart(prev => {
      const key = cartKey(product);
      const existing = prev.find(item => cartKey(item) === key);
      if (existing) {
        if (existing.quantity >= stockVal) return prev;
        return prev.map(item => cartKey(item) === key ? { ...item, quantity: item.quantity + 1 } : item);
      }
      const priceVal = product.salePrice || product.price || 0;
      return [...prev, { ...product, price: priceVal, stock: stockVal, quantity: 1 }];
    });
  };

  // Escaneo dinámico: el lector envía el código y un Enter muy rápido. Si hace match exacto
  // contra un producto cargado, se agrega directo al carrito sin que el usuario tenga que
  // hacer click en nada.
  const handleBarcodeScan = useCallback((rawCode) => {
    const code = rawCode.trim();
    if (!code) return { ok: false, message: '' };

    // Los servicios no tienen código de barras: se escanean por su código (SRV-…).
    const product = productsList.find(p => !isService(p) && p.barcode === code) ||
      productsList.find(p => (p.internalCode || p.code) && (p.internalCode || p.code).toLowerCase() === code.toLowerCase());

    let result;
    if (!product) {
      result = { ok: false, message: `Código no encontrado: ${code}` };
    } else {
      const stockVal = isService(product) ? Infinity : (product.quantityAvailable ?? product.stock ?? 0);
      const inCart = cart.find(item => cartKey(item) === cartKey(product))?.quantity ?? 0;
      if (stockVal <= 0) {
        result = { ok: false, message: `${product.name}: sin stock disponible` };
      } else if (inCart >= stockVal) {
        result = { ok: false, message: `${product.name}: ya tienes todo el stock en el carrito` };
      } else {
        addToCart(product);
        result = { ok: true, message: `${product.name} agregado al carrito` };
      }
    }

    playScanTone(result.ok);
    pushScanToast(result.ok ? 'success' : 'error', result.message);
    setSearchQuery('');
    return result;
  }, [productsList, cart, playScanTone, pushScanToast]);

  const handleBarcodeInputKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleBarcodeScan(searchQuery);
    }
  };

  const updateQuantity = (key, delta) => {
    setCart(prev => prev.map(item => {
      if (cartKey(item) === key) {
        const newQty = item.quantity + delta;
        return newQty > 0 ? { ...item, quantity: newQty } : null;
      }
      return item;
    }).filter(Boolean));
  };

  const clearCart = () => {
    setCart([]);
  };

  // Asistente de caja: cada turno devuelve el ticket completo y se pinta directo en el panel (carrito, cliente, pago y
  // efectivo). No crea ninguna venta: si pidió confirmar, se guarda la huella del ticket y la confirmación aparece en el
  // panel mientras ese ticket siga idéntico.
  const ticketState = useMemo(
    () => ({ cart, customer: selectedCustomer, paymentMethod: selectedPaymentMethod, cashTendered }),
    [cart, selectedCustomer, selectedPaymentMethod, cashTendered],
  );
  const handleAgentTurn = useCallback(({ ticket }) => {
    const applied = applyAgentTicket({
      ticket, productsList, customersList, currentPaymentMethod: selectedPaymentMethod,
    });
    const customer = applied.customer ?? selectedCustomer;
    setCart(applied.cart);
    if (applied.customer) setSelectedCustomer(applied.customer);
    setSelectedPaymentMethod(applied.paymentMethod);
    setCashTendered(applied.cashTendered);
    setShowMobileCart(true);
    setAgentConfirmSignature(applied.confirmationRequested && applied.cart.length > 0
      ? ticketSignature({ cart: applied.cart, customer, paymentMethod: applied.paymentMethod, cashTendered: applied.cashTendered })
      : null);
  }, [productsList, customersList, selectedPaymentMethod, selectedCustomer]);
  const agent = useVoiceAgent({ ticketState, onTurn: handleAgentTurn });

  const subtotal = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
  const total = subtotal;

  const cashNumber = parseFloat(cashTendered) || 0;
  const change = cashNumber - total;
  const paymentIsCash = selectedPaymentMethod === 'CASH';
  const paymentIsCredit = selectedPaymentMethod === 'CREDIT';

  // `electronic` decide si se pide factura electrónica: el cobro normal usa lo que eligió el cajero en el cobro;
  // la confirmación del asistente pasa `false` (una factura por voz es una venta normal, sin DIAN).
  const handleCompleteSale = async ({ electronic = issueDian && dianAvailable } = {}) => {
    if (cashNumber < total) return;
    if (isSubmittingSale) return; // Evita reenvíos por doble clic mientras hay uno en curso.

    const saleCommand = {
      customerId: selectedCustomer?.id || null,
      items: cart.map(toSaleLine),
      cashReceived: paymentIsCredit ? 0 : cashNumber,
      totalAmount: total,
      sendElectronicInvoice: electronic,
      paymentMethod: selectedPaymentMethod,
      ...(paymentIsCredit && { creditDueDate })
    };

    // Reutiliza la clave del intento actual si existe (reintento); si no, genera una nueva.
    if (!saleIdempotencyKeyRef.current) {
      saleIdempotencyKeyRef.current = newIdempotencyKey();
    }

    let result = null;
    setIsSubmittingSale(true);
    try {
      result = await createSaleApi(saleCommand, saleIdempotencyKeyRef.current);
    } catch (e) {
      notify('Error registrando la venta: ' + e.message);
      return;
    } finally {
      setIsSubmittingSale(false);
    }
    // Venta confirmada: descarta la clave para que el próximo cobro use una nueva.
    saleIdempotencyKeyRef.current = null;

    // La emisión a la DIAN es asíncrona: si la factura aún no existe, el resultado lo indica
    // y el cajero puede actualizar el estado sin bloquear la venta.
    let invoice = null;
    if (electronic && result?.id) {
      try {
        invoice = await getSaleInvoiceApi(result.id);
      } catch {
        invoice = null;
      }
    }

    setSaleCompleted({
      saleId: result?.id,
      number: result?.invoiceNumber || (result?.id ? `#${result.id}` : ''),
      total,
      items: cart.map(i => ({ name: i.name, quantity: i.quantity, unitPrice: i.price })),
      change: paymentIsCash ? Math.max(0, change) : 0,
      onCredit: paymentIsCredit,
      customerName: selectedCustomer?.fullName || selectedCustomer?.name || 'Consumidor final',
      customerEmail: selectedCustomer?.email || '',
      dianRequested: electronic,
      invoice
    });
    setAgentConfirmSignature(null);
    agent.reset();
    setShowCheckoutModal(false);
    setShowMobileCart(false);
    setCart([]);
    setCashTendered('');
    setSelectedPaymentMethod('CASH');
    setCreditDueDate(defaultCreditDueDate());
    setIssueDian(false);
    setSelectedCustomer(null);
    reloadProducts();
    reloadCustomers();
    focusBarcodeInput();
  };

  // La confirmación del asistente solo se muestra mientras el ticket sea idéntico al que confirmó: cualquier cambio
  // (del cajero o del asistente) la retira sin efectos ni temporizadores.
  const agentConfirmationActive = agentConfirmSignature !== null && !saleCompleted
    && agentConfirmSignature === ticketSignature(ticketState);
  const agentSummary = {
    itemCount: cart.reduce((sum, item) => sum + item.quantity, 0),
    total,
    customerName: selectedCustomer?.fullName || selectedCustomer?.name || 'Consumidor final',
    paymentLabel: paymentMethods.find((m) => m.code === selectedPaymentMethod)?.label ?? selectedPaymentMethod,
    onCredit: paymentIsCredit,
    cashReceived: paymentIsCash ? cashNumber : null,
    change: Math.max(0, change),
    cashShort: cashNumber < total,
  };

  return (
    <div className="relative">
      <ScanToasts toasts={scanToasts} />

      {/* Escáner incrustado arriba: queda fijo mientras se desplaza el catálogo y se cierra con la X.
          Con el ticket abierto pasa a mostrarse dentro del ticket (una sola cámara a la vez). */}
      {showScanner && !showMobileCart && (
        <div className="lg:hidden pb-3">
          <BarcodeScanner
            inline
            onDetect={handleBarcodeScan}
            onClose={() => { setShowScanner(false); focusBarcodeInput(); }}
          />
        </div>
      )}

      {/* En desktop, este bloque cancela el padding de <main> (lg:-m-8) y se ancla a la
          altura exacta bajo el header (lg:h-[calc(100vh-5rem)]) para que la columna
          izquierda scrollee sola y el panel del ticket, a la derecha, quede fijo a pantalla
          completa en vez de ser una tarjeta que se corta. En mobile se mantiene el flujo
          normal de la página con el drawer inferior de siempre. */}
      <div className="pb-20 lg:pb-0 lg:-m-8 lg:h-[calc(100vh-5rem)] lg:overflow-hidden flex flex-col lg:flex-row">
        <ProductCatalog
          inputRef={barcodeInputRef}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onSearchKeyDown={handleBarcodeInputKeyDown}
          categories={categoriesList}
          selectedCategory={selectedCategory}
          onSelectCategory={setSelectedCategory}
          products={filteredProducts}
          loading={loading}
          onAdd={addToCart}
          onOpenScanner={() => setShowScanner(true)}
        />

        <CartPanel
          cart={cart}
          customers={customersList}
          selectedCustomer={selectedCustomer}
          onSelectCustomer={setSelectedCustomer}
          subtotal={subtotal}
          total={total}
          mobileOpen={showMobileCart}
          scanner={showScanner && (
            <BarcodeScanner
              inline
              onDetect={handleBarcodeScan}
              onClose={() => { setShowScanner(false); focusBarcodeInput(); }}
            />
          )}
          assistant={<VoiceAssistantBar agent={agent} />}
          confirmation={agentConfirmationActive ? (
            <AgentConfirmation
              summary={agentSummary}
              submitting={isSubmittingSale}
              onConfirm={() => handleCompleteSale({ electronic: false })}
              onCancel={() => setAgentConfirmSignature(null)}
            />
          ) : null}
          onCloseMobile={() => setShowMobileCart(false)}
          onClear={clearCart}
          onQuantity={updateQuantity}
          onCheckout={() => setShowCheckoutModal(true)}
        />
      </div>

      {/* Botón flotante circular (mobile) para abrir el ticket; el contador muestra cuántos ítems hay */}
      <button
        onClick={() => setShowMobileCart(true)}
        aria-label={`Ver ticket, ${cart.length} ítems, total $${total.toLocaleString('es-CO')}`}
        className="fixed bottom-5 right-5 z-30 lg:hidden w-16 h-16 rounded-full bg-brand text-white flex items-center justify-center shadow-xl shadow-brand/40 ring-4 ring-white dark:ring-page active:scale-90 transition-all cursor-pointer"
      >
        <ShoppingCart className="w-7 h-7" />
        {cart.length > 0 && (
          <span
            key={cart.length}
            className="absolute -top-1 -right-1 min-w-6 h-6 px-1.5 rounded-full bg-solid text-solid-ink text-xs font-black tabular-nums flex items-center justify-center ring-2 ring-white dark:ring-page animate-bounce-once"
          >
            {cart.length}
          </span>
        )}
      </button>

      {showCheckoutModal && (
        <CheckoutModal
          total={total}
          itemCount={cart.length}
          customers={customersList}
          selectedCustomer={selectedCustomer}
          onSelectCustomer={setSelectedCustomer}
          onCustomerCreated={(created) => {
            setCustomersList(prev => [created, ...prev]);
            setSelectedCustomer(created);
          }}
          paymentMethods={paymentMethods}
          selectedPaymentMethod={selectedPaymentMethod}
          onSelectPaymentMethod={(code) => {
            setSelectedPaymentMethod(code);
            setCashTendered(code === 'CASH' ? '' : total.toString());
          }}
          billing={billing}
          issueDian={issueDian && dianAvailable}
          onToggleDian={setIssueDian}
          cashTendered={cashTendered}
          onCashTenderedChange={setCashTendered}
          creditDueDate={creditDueDate}
          onCreditDueDateChange={setCreditDueDate}
          isSubmitting={isSubmittingSale}
          onConfirm={handleCompleteSale}
          onClose={() => setShowCheckoutModal(false)}
        />
      )}

      {saleCompleted && (
        <SaleResultModal
          sale={saleCompleted}
          onClose={() => { setSaleCompleted(null); focusBarcodeInput(); }}
        />
      )}

    </div>
  );
}
