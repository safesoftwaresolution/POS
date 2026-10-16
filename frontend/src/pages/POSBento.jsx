import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Search,
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  Banknote,
  X,
  Package,
  CheckCircle,
  XCircle
} from 'lucide-react';
import { getProductsApi } from '../services/productsApi';
import { getCustomersApi } from '../services/customersApi';
import { createSaleApi } from '../services/salesApi';
import { getSaleInvoiceApi } from '../services/billingApi';
import CheckoutModal, { SaleResultModal } from '../components/pos/CheckoutModal';
import { getCategoriesApi } from '../services/categoriesApi';
import { getParameterValuesApi } from '../services/parametersApi';
import { newIdempotencyKey } from '../services/http';
import { useModal } from '../context/ModalContext';

export default function POSBento() {
  const { notify } = useModal();
  const [productsList, setProductsList] = useState([]);
  const [customersList, setCustomersList] = useState([]);
  const [categoriesList, setCategoriesList] = useState(['Todos']);
  const [selectedCategory, setSelectedCategory] = useState('Todos');
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState('CASH');
  const [searchQuery, setSearchQuery] = useState('');
  const [cart, setCart] = useState([]);
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [loading, setLoading] = useState(true);

  const [issueDian, setIssueDian] = useState(false);
  const [showCheckoutModal, setShowCheckoutModal] = useState(false);
  const [cashTendered, setCashTendered] = useState('');
  const [saleCompleted, setSaleCompleted] = useState(null);
  const [showMobileCart, setShowMobileCart] = useState(false);
  const [isSubmittingSale, setIsSubmittingSale] = useState(false);

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

  const loadData = async () => {
    setLoading(true);
    try {
      const prods = await getProductsApi();
      if (Array.isArray(prods)) setProductsList(prods);

      const custs = await getCustomersApi();
      if (Array.isArray(custs)) {
        setCustomersList(custs);
        if (custs.length > 0) setSelectedCustomer(custs[0]);
      }

      const cats = await getCategoriesApi();
      if (Array.isArray(cats)) {
        setCategoriesList(['Todos', ...cats.map(c => c.name)]);
      }

      const methods = await getParameterValuesApi('PAYMENT_METHODS');
      if (Array.isArray(methods)) {
        setPaymentMethods(methods.filter(m => m.active));
      }
    } catch (e) {
      console.warn('Error cargando catálogo POS:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
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
    if (!code) return;

    const product = productsList.find(p => p.barcode === code) ||
      productsList.find(p => p.internalCode && p.internalCode.toLowerCase() === code.toLowerCase());

    if (!product) {
      playScanTone(false);
      pushScanToast('error', `Código no encontrado: ${code}`);
      setSearchQuery('');
      return;
    }

    const stockVal = product.quantityAvailable ?? product.stock ?? 0;
    if (stockVal <= 0) {
      playScanTone(false);
      pushScanToast('error', `${product.name}: sin stock disponible`);
      setSearchQuery('');
      return;
    }

    addToCart(product);
    playScanTone(true);
    pushScanToast('success', `${product.name} agregado al carrito`);
    setSearchQuery('');
  }, [productsList, playScanTone, pushScanToast]);

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
    await loadData();
    focusBarcodeInput();
  };

  return (
    <div className="relative">
      {/* Toasts de escaneo: no bloqueantes, se autodescartan, se apilan si llegan varios escaneos seguidos */}
      <div className="fixed top-4 right-4 z-[60] flex flex-col gap-2 items-end pointer-events-none">
        {scanToasts.map(toast => (
          <div
            key={toast.id}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-2xl shadow-xl text-xs font-bold text-white animate-in fade-in slide-in-from-top-2 duration-150 ${
              toast.type === 'success' ? 'bg-solid' : 'bg-red-600'
            }`}
          >
            {toast.type === 'success' ? <CheckCircle className="w-4 h-4 shrink-0" /> : <XCircle className="w-4 h-4 shrink-0" />}
            <span>{toast.message}</span>
          </div>
        ))}
      </div>

      {/* En desktop, este bloque cancela el padding de <main> (lg:-m-8) y se ancla a la
          altura exacta bajo el header (lg:h-[calc(100vh-5rem)]) para que la columna
          izquierda scrollee sola y el panel del ticket, a la derecha, quede fijo a pantalla
          completa en vez de ser una tarjeta que se corta. En mobile se mantiene el flujo
          normal de la página con el drawer inferior de siempre. */}
      <div className="pb-20 lg:pb-0 lg:-m-8 lg:h-[calc(100vh-5rem)] lg:overflow-hidden flex flex-col lg:flex-row">

        {/* Products Grid (Left Column) */}
        <div className="flex flex-col gap-4 lg:h-full lg:overflow-hidden lg:pt-8 lg:pl-8 lg:pr-4">
          <div className="relative shrink-0">
            <Search className="w-4 h-4 text-ink-muted absolute left-4 top-1/2 -translate-y-1/2" />
            <input
              ref={barcodeInputRef}
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={handleBarcodeInputKeyDown}
              placeholder="Buscar productos por nombre, SKU o código de barra..."
              className="w-full h-12 pl-11 pr-16 bg-card rounded-2xl border border-line text-xs font-semibold focus:outline-none focus:border-ink dark:focus:border-white text-ink placeholder-gray-400 dark:placeholder-gray-500 shadow-[0_2px_8px_rgba(0,0,0,0.02)] transition-all"
            />
            {searchQuery && (
              <button onClick={() => setSearchQuery('')} className="absolute right-4 top-1/2 -translate-y-1/2 text-[10px] font-bold text-ink-muted hover:text-gray-600 dark:hover:text-gray-300 px-2 py-0.5 rounded-full bg-gray-100 dark:bg-[#272730] cursor-pointer">
                Limpiar
              </button>
            )}
          </div>

          {/* Categories */}
          <div className="shrink-0 flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
            {categoriesList.map(cat => (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                className={`px-4 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all duration-150 border cursor-pointer ${
                  selectedCategory === cat
                    ? 'bg-solid text-white border-[#0A0A0A] shadow-sm'
                    : 'bg-card text-gray-600 dark:text-gray-300 border-line hover:border-line-strong hover:text-[#0A0A0A] dark:hover:text-white'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          <div className="flex-1 lg:overflow-y-auto lg:pr-1 lg:pb-8">
          {loading ? (
            <div className="py-12 text-center text-xs font-bold text-ink-muted">
              Cargando catálogo real de productos...
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-3xl border border-dashed border-gray-200 dark:border-gray-700">
              <Package className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay productos en el catálogo</p>
              <p className="text-[11px] text-ink-muted">Ve al módulo de productos para registrar artículos</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3">
              {filteredProducts.map(product => {
                const stockVal = product.quantityAvailable ?? product.stock ?? 0;
                const isLowStock = stockVal <= (product.minStock || 5);
                const isOut = stockVal <= 0;
                const priceVal = product.salePrice || product.price || 0;

                return (
                  <button
                    key={product.id}
                    onClick={() => !isOut && addToCart(product)}
                    disabled={isOut}
                    className={`group relative flex flex-col justify-between text-left p-3 rounded-2xl bg-card border border-line hover:border-[#0A0A0A] dark:hover:border-white transition-all duration-150 shadow-[0_2px_12px_rgba(0,0,0,0.02)] hover:shadow-[0_8px_24px_rgba(0,0,0,0.05)] active:scale-[0.98] ${
                      isOut ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'
                    }`}
                  >
                    {/* Product Image */}
                    <div className="w-full aspect-square rounded-xl overflow-hidden bg-page mb-2 relative">
                      {product.imageUrl ? (
                        <img
                          src={product.imageUrl}
                          alt={product.name}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                        />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center">
                          <Package className="w-8 h-8 text-gray-300 dark:text-gray-600" />
                        </div>
                      )}

                      {/* Stock Badge */}
                      <div className="absolute top-1.5 right-1.5">
                        {isOut ? (
                          <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-lg bg-rose-500 text-white shadow-sm">
                            Agotado
                          </span>
                        ) : isLowStock ? (
                          <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-lg bg-amber-500 text-white shadow-sm">
                            Stock: {stockVal}
                          </span>
                        ) : (
                          <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-lg bg-white/90 dark:bg-black/60 backdrop-blur-sm text-ink shadow-xs">
                            Stock: {stockVal}
                          </span>
                        )}
                      </div>

                      {/* Category tag */}
                      {product.categoryName && (
                        <div className="absolute bottom-1.5 left-1.5">
                          <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-lg bg-[#0A0A0A]/80 text-white backdrop-blur-sm">
                            {product.categoryName}
                          </span>
                        </div>
                      )}
                    </div>

                    {/* Info */}
                    <div className="flex-1 flex flex-col justify-between">
                      <div>
                        <h4 className="text-sm font-bold text-ink line-clamp-2 leading-snug">
                          {product.name}
                        </h4>
                        <span className="text-[11px] font-mono text-ink-muted">{product.internalCode}</span>
                      </div>

                      <div className="mt-2 pt-2 border-t border-[#F5F5F3] dark:border-line flex items-center justify-between">
                        <span className="text-lg font-extrabold text-ink font-mono tracking-tight">
                          ${priceVal.toLocaleString('es-CO')}
                        </span>
                        <div className="w-8 h-8 rounded-xl bg-page group-hover:bg-solid group-hover:text-white flex items-center justify-center transition-colors shrink-0">
                          <Plus className="w-3.5 h-3.5" />
                        </div>
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
          </div>
        </div>

        {/* Shopping Cart (Right Column Desktop / Mobile Drawer Container) */}
        <div className={
          showMobileCart
            ? 'fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex flex-col justify-end lg:static lg:bg-transparent lg:z-auto'
            : 'hidden lg:flex lg:h-full'
        }>
          <div className="w-full lg:w-[380px] xl:w-[440px] shrink-0 bg-card lg:border-l border-line flex flex-col lg:h-full max-h-[85vh] lg:max-h-none rounded-t-3xl lg:rounded-none shadow-xl lg:shadow-none overflow-hidden">

            <div className="shrink-0 flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-800">
              <div className="flex items-center gap-2.5">
                <div className="p-2.5 bg-solid text-brand rounded-2xl shadow-xs">
                  <ShoppingCart className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="font-extrabold text-sm text-ink">Ticket de Venta</h2>
                  <p className="text-[11px] text-ink-muted font-semibold">{cart.length} productos agregados</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {cart.length > 0 && (
                  <button
                    onClick={clearCart}
                    className="text-xs font-extrabold text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 flex items-center gap-1 bg-red-50 dark:bg-red-950/30 px-2.5 py-1 rounded-xl border border-red-100 dark:border-red-800/40 cursor-pointer"
                  >
                    <Trash2 className="w-3.5 h-3.5" /> Vaciar
                  </button>
                )}
                {/* Mobile Close Cart Drawer */}
                <button
                  onClick={() => setShowMobileCart(false)}
                  className="lg:hidden p-1.5 text-ink-muted hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            <div className="shrink-0 bg-gray-50 dark:bg-white/5 p-4 border-b border-gray-200 dark:border-gray-700 space-y-2">
              <div className="flex items-center justify-between text-xs font-bold text-gray-600 dark:text-gray-300">
                <span>Cliente Asignado:</span>
                <span className="text-[10px] text-brand dark:text-brand font-extrabold">SELECCIONAR</span>
              </div>
              <select
                value={selectedCustomer?.id || ''}
                onChange={(e) => {
                  const cust = customersList.find(c => c.id === parseInt(e.target.value));
                  setSelectedCustomer(cust);
                }}
                className="w-full bg-white dark:bg-[#0E0E12] border border-gray-300 dark:border-gray-700 rounded-xl p-2 text-xs font-bold text-ink focus:outline-none focus:border-ink"
              >
                {customersList.length === 0 ? (
                  <option value="">Cliente General (Mostrador)</option>
                ) : (
                  customersList.map(c => (
                    <option key={c.id} value={c.id}>
                      {c.fullName || c.name} ({c.identification || 'Consumidor Final'})
                    </option>
                  ))
                )}
              </select>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto p-4 space-y-2">
              {cart.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-center space-y-3">
                  <div className="w-14 h-14 rounded-3xl bg-page border border-line flex items-center justify-center text-gray-300 dark:text-gray-600">
                    <ShoppingCart className="w-7 h-7" />
                  </div>
                  <div>
                    <p className="text-xs font-bold text-ink">El carrito está vacío</p>
                    <p className="text-[11px] text-ink-muted mt-1 max-w-[200px] mx-auto">Haz clic en los productos para agregar al ticket</p>
                  </div>
                </div>
              ) : (
                cart.map(item => (
                  <div key={item.id} className="p-3 bg-gray-50 dark:bg-white/5 rounded-2xl flex items-center justify-between border border-gray-200 dark:border-gray-700">
                    <div className="space-y-0.5">
                      <h4 className="font-bold text-xs text-ink">{item.name}</h4>
                      <p className="text-[10px] text-ink-soft font-mono">
                        ${item.price.toLocaleString('es-CO')} c/u
                      </p>
                    </div>

                    <div className="flex items-center gap-3">
                      <div className="flex items-center gap-1.5 bg-white dark:bg-[#0E0E12] px-2 py-1 rounded-xl border border-gray-300 dark:border-gray-700">
                        <button
                          onClick={() => updateQuantity(item.id, -1)}
                          className="text-ink-soft hover:text-brand dark:hover:text-brand font-bold text-xs cursor-pointer"
                        >
                          <Minus className="w-3.5 h-3.5" />
                        </button>
                        <span className="text-xs font-black text-ink px-1 tabular-nums">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() => updateQuantity(item.id, 1)}
                          className="text-ink-soft hover:text-brand dark:hover:text-brand font-bold text-xs cursor-pointer"
                        >
                          <Plus className="w-3.5 h-3.5" />
                        </button>
                      </div>

                      <span className="text-xs font-black text-ink tabular-nums min-w-[70px] text-right">
                        ${(item.price * item.quantity).toLocaleString('es-CO')}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="shrink-0 p-5 border-t border-gray-200 dark:border-gray-700 space-y-4">
            <div className="space-y-2 text-xs font-semibold text-gray-600 dark:text-gray-300">
              <div className="flex justify-between">
                <span>Subtotal Neto:</span>
                <span className="font-bold text-ink tabular-nums">${subtotal.toLocaleString('es-CO')} COP</span>
              </div>
              <div className="flex justify-between text-base font-black text-ink pt-2 border-t border-gray-200 dark:border-gray-700">
                <span>Total a Cobrar:</span>
                <span className="text-xl text-brand dark:text-brand tabular-nums">${total.toLocaleString('es-CO')} COP</span>
              </div>
            </div>

            <button
              disabled={cart.length === 0}
              onClick={() => setShowCheckoutModal(true)}
              className={`w-full py-3.5 rounded-2xl font-black text-sm flex items-center justify-center gap-2 shadow-lg transition-all ${
                cart.length === 0
                  ? 'bg-gray-200 dark:bg-gray-800 text-ink-muted cursor-not-allowed'
                  : 'bg-solid hover:bg-solid-hover text-white shadow-[#0A0A0A]/10 active:scale-98 cursor-pointer'
              }`}
            >
              <Banknote className="w-5 h-5" />
              <span>COBRAR EN EFECTIVO [F4]</span>
            </button>
            </div>

          </div>
        </div>

      </div>

      {/* Floating Action Button on Mobile to Toggle Cart Drawer */}
      <div className="fixed bottom-4 left-4 right-4 z-30 lg:hidden">
        <button
          onClick={() => setShowMobileCart(true)}
          className="w-full py-3.5 px-5 bg-solid text-white rounded-2xl font-black text-xs flex items-center justify-between shadow-2xl shadow-[#0A0A0A]/10 active:scale-95 transition-all cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <ShoppingCart className="w-4.5 h-4.5" />
            <span>Ver Ticket ({cart.length} ítems)</span>
          </div>
          <span className="text-sm font-extrabold tabular-nums">${total.toLocaleString('es-CO')} COP</span>
        </button>
      </div>

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
