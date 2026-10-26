import React, { useState, useEffect, useRef, useCallback } from 'react';
import { ShoppingCart } from 'lucide-react';
import { getProductsApi } from '../services/productsApi';
import { getCustomersApi } from '../services/customersApi';
import { createSaleApi } from '../services/salesApi';
import { getSaleInvoiceApi } from '../services/billingApi';
import CheckoutModal, { SaleResultModal } from '../components/pos/CheckoutModal';
import ScanToasts from '../components/pos/ScanToasts';
import ProductCatalog from '../components/pos/ProductCatalog';
import CartPanel from '../components/pos/CartPanel';
import { BarcodeScanner } from '../components/scanner';
import { getCategoriesApi } from '../services/categoriesApi';
import { getParameterValuesApi } from '../services/parametersApi';
import { newIdempotencyKey } from '../services/http';
import useModal from '../hooks/useModal';
import useLoad from '../hooks/useLoad';
import asList from '../utils/asList';

export default function POSBento() {
  const { notify } = useModal();
  // Stock y precios se refrescan solos sin tocar el carrito ni el cliente elegido.
  const { data: productsList, loading: productsLoading, reload: reloadProducts } = useLoad(
    () => asList(getProductsApi()),
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
  const loading = productsLoading || customersLoading || catalogLoading;
  const [selectedCategory, setSelectedCategory] = useState('Todos');
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState('CASH');
  const [searchQuery, setSearchQuery] = useState('');
  const [cart, setCart] = useState([]);
  // Sin elección explícita se usa el primer cliente (consumidor final); tras cada venta se vuelve a ese valor.
  const [customerChoice, setSelectedCustomer] = useState(null);
  const selectedCustomer = customerChoice ?? customersList[0] ?? null;

  const [issueDian, setIssueDian] = useState(false);
  const [showCheckoutModal, setShowCheckoutModal] = useState(false);
  const [cashTendered, setCashTendered] = useState('');
  const [saleCompleted, setSaleCompleted] = useState(null);
  const [showMobileCart, setShowMobileCart] = useState(false);
  const [isSubmittingSale, setIsSubmittingSale] = useState(false);
  const [showScanner, setShowScanner] = useState(false);

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

  const filteredProducts = productsList.filter(p => {
    const matchesCategory = selectedCategory === 'Todos' || p.categoryName === selectedCategory;
    const matchesSearch = (p.name && p.name.toLowerCase().includes(searchQuery.toLowerCase())) ||
                          (p.barcode && p.barcode.includes(searchQuery)) ||
                          (p.internalCode && p.internalCode.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesCategory && matchesSearch;
  });

  const addToCart = (product) => {
    const stockVal = product.quantityAvailable ?? product.stock ?? 0;
    if (stockVal <= 0) return;

    setCart(prev => {
      const existing = prev.find(item => item.id === product.id);
      if (existing) {
        if (existing.quantity >= stockVal) return prev;
        return prev.map(item => item.id === product.id ? { ...item, quantity: item.quantity + 1 } : item);
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

    const product = productsList.find(p => p.barcode === code) ||
      productsList.find(p => p.internalCode && p.internalCode.toLowerCase() === code.toLowerCase());

    let result;
    if (!product) {
      result = { ok: false, message: `Código no encontrado: ${code}` };
    } else {
      const stockVal = product.quantityAvailable ?? product.stock ?? 0;
      const inCart = cart.find(item => item.id === product.id)?.quantity ?? 0;
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

  const updateQuantity = (id, delta) => {
    setCart(prev => prev.map(item => {
      if (item.id === id) {
        const newQty = item.quantity + delta;
        return newQty > 0 ? { ...item, quantity: newQty } : null;
      }
      return item;
    }).filter(Boolean));
  };

  const clearCart = () => {
    setCart([]);
  };

  const subtotal = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
  const total = subtotal;

  const cashNumber = parseFloat(cashTendered) || 0;
  const change = cashNumber - total;
  const paymentIsCash = selectedPaymentMethod === 'CASH';

  const handleCompleteSale = async () => {
    if (cashNumber < total) return;
    if (isSubmittingSale) return; // Evita reenvíos por doble clic mientras hay uno en curso.

    const saleCommand = {
      customerId: selectedCustomer?.id || null,
      items: cart.map(item => ({
        productId: item.id,
        quantity: item.quantity,
        unitPrice: item.price
      })),
      cashReceived: cashNumber,
      totalAmount: total,
      sendToFactus: issueDian,
      paymentMethod: selectedPaymentMethod
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
    if (issueDian && result?.id) {
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
      change: paymentIsCash ? Math.max(0, change) : 0,
      customerName: selectedCustomer?.fullName || selectedCustomer?.name || 'Consumidor final',
      customerEmail: selectedCustomer?.email || '',
      dianRequested: issueDian,
      invoice
    });
    setShowCheckoutModal(false);
    setShowMobileCart(false);
    setCart([]);
    setCashTendered('');
    setSelectedPaymentMethod('CASH');
    setIssueDian(false);
    setSelectedCustomer(null);
    reloadProducts();
    reloadCustomers();
    focusBarcodeInput();
  };

  return (
    <div className="relative">
      <ScanToasts toasts={scanToasts} />

      {/* Escáner incrustado arriba: queda fijo mientras se desplaza el catálogo y se cierra con la X. */}
      {showScanner && (
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
            className="absolute -top-1 -right-1 min-w-6 h-6 px-1.5 rounded-full bg-solid text-white text-xs font-black tabular-nums flex items-center justify-center ring-2 ring-white dark:ring-page animate-bounce-once"
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
          issueDian={issueDian}
          onToggleDian={setIssueDian}
          cashTendered={cashTendered}
          onCashTenderedChange={setCashTendered}
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
