import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Scan,
  Search,
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  CheckCircle2,
  Printer,
  Banknote,
  X,
  ShieldAlert,
  Package,
  ChevronDown,
  CheckCircle,
  XCircle
} from 'lucide-react';
import { getProductsApi } from '../services/productsApi';
import { getCustomersApi } from '../services/customersApi';
import { createSaleApi } from '../services/salesApi';
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
  // Los precios de venta ya incluyen IVA y el backend es quien calcula impuestos (por producto, ver tax_rate):
  // el total a cobrar es la suma de los precios, con o sin factura electrónica. Antes se sumaba un 19% fijo
  // si se marcaba "Transmitir a DIAN", y el cajero cobraba un valor distinto al que registraba el backend.
  const total = subtotal;

  const cashNumber = parseFloat(cashTendered) || 0;
  const change = cashNumber - total;

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
      paymentMethod: selectedPaymentMethod,
      sendToFactus: issueDian
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

    const completed = {
      id: result?.id ? `FAC-${result.id}` : `FAC-${Math.floor(1000 + Math.random() * 9000)}`,
      date: new Date().toLocaleString('es-CO'),
      customer: selectedCustomer?.fullName || selectedCustomer?.name || 'Cliente General',
      items: [...cart],
      total,
      cashTendered: cashNumber,
      change,
      // La factura se emite de forma asíncrona tras confirmar la venta; el estado real (VALIDATED / REJECTED / ERROR)
      // está en el backend. Nunca se muestra un CUFE: solo existe cuando la DIAN valida el documento.
      dianStatus: issueDian ? 'PENDIENTE_DIAN' : 'NO_REQUERIDA',
      cufe: null
    };

    setSaleCompleted(completed);
    setShowCheckoutModal(false);
    setShowMobileCart(false);
    setCart([]);
    setCashTendered('');
    setSelectedPaymentMethod('CASH');
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
              toast.type === 'success' ? 'bg-[#0A0A0A]' : 'bg-red-600'
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
            <Search className="w-4 h-4 text-gray-400 dark:text-gray-500 absolute left-4 top-1/2 -translate-y-1/2" />
            <input
              ref={barcodeInputRef}
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={handleBarcodeInputKeyDown}
              placeholder="Buscar productos por nombre, SKU o código de barra..."
              className="w-full h-12 pl-11 pr-16 bg-white dark:bg-[#17171B] rounded-2xl border border-[#EAEAEA] dark:border-[#272730] text-xs font-semibold focus:outline-none focus:border-[#0A0A0A] dark:focus:border-white text-[#0A0A0A] dark:text-white placeholder-gray-400 dark:placeholder-gray-500 shadow-[0_2px_8px_rgba(0,0,0,0.02)] transition-all"
            />
            {searchQuery && (
              <button onClick={() => setSearchQuery('')} className="absolute right-4 top-1/2 -translate-y-1/2 text-[10px] font-bold text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 px-2 py-0.5 rounded-full bg-gray-100 dark:bg-[#272730] cursor-pointer">
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
                    ? 'bg-[#0A0A0A] text-white border-[#0A0A0A] shadow-sm'
                    : 'bg-white dark:bg-[#17171B] text-gray-600 dark:text-gray-300 border-[#EAEAEA] dark:border-[#272730] hover:border-[#D0D0CE] hover:text-[#0A0A0A] dark:hover:text-white'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          <div className="flex-1 lg:overflow-y-auto lg:pr-1 lg:pb-8">
          {loading ? (
            <div className="py-12 text-center text-xs font-bold text-gray-400 dark:text-gray-500">
              Cargando catálogo real de productos...
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-3xl border border-dashed border-gray-200 dark:border-gray-700">
              <Package className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay productos en el catálogo</p>
              <p className="text-[11px] text-gray-400 dark:text-gray-500">Ve al módulo de productos para registrar artículos</p>
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
                    className={`group relative flex flex-col justify-between text-left p-3 rounded-2xl bg-white dark:bg-[#17171B] border border-[#EAEAEA] dark:border-[#272730] hover:border-[#0A0A0A] dark:hover:border-white transition-all duration-150 shadow-[0_2px_12px_rgba(0,0,0,0.02)] hover:shadow-[0_8px_24px_rgba(0,0,0,0.05)] active:scale-[0.98] ${
                      isOut ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'
                    }`}
                  >
                    {/* Product Image */}
                    <div className="w-full aspect-square rounded-xl overflow-hidden bg-[#F7F7F5] dark:bg-[#0E0E12] mb-2 relative">
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
                          <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-lg bg-white/90 dark:bg-black/60 backdrop-blur-sm text-[#0A0A0A] dark:text-white shadow-xs">
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
                        <h4 className="text-sm font-bold text-[#0A0A0A] dark:text-white line-clamp-2 leading-snug">
                          {product.name}
                        </h4>
                        <span className="text-[11px] font-mono text-gray-400 dark:text-gray-500">{product.internalCode}</span>
                      </div>

                      <div className="mt-2 pt-2 border-t border-[#F5F5F3] dark:border-[#272730] flex items-center justify-between">
                        <span className="text-lg font-extrabold text-[#0A0A0A] dark:text-white font-mono tracking-tight">
                          ${priceVal.toLocaleString('es-CO')}
                        </span>
                        <div className="w-8 h-8 rounded-xl bg-[#F7F7F5] dark:bg-[#0E0E12] group-hover:bg-[#0A0A0A] group-hover:text-white flex items-center justify-center transition-colors shrink-0">
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
          <div className="w-full lg:w-[380px] xl:w-[440px] shrink-0 bg-white dark:bg-[#17171B] lg:border-l border-[#EAEAEA] dark:border-[#272730] flex flex-col lg:h-full max-h-[85vh] lg:max-h-none rounded-t-3xl lg:rounded-none shadow-xl lg:shadow-none overflow-hidden">

            <div className="shrink-0 flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-800">
              <div className="flex items-center gap-2.5">
                <div className="p-2.5 bg-[#0A0A0A] text-[#FF5520] rounded-2xl shadow-xs">
                  <ShoppingCart className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="font-extrabold text-sm text-[#0A0A0A] dark:text-white">Ticket de Venta</h2>
                  <p className="text-[11px] text-gray-400 dark:text-gray-500 font-semibold">{cart.length} productos agregados</p>
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
                  className="lg:hidden p-1.5 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            <div className="shrink-0 bg-gray-50 dark:bg-white/5 p-4 border-b border-gray-200 dark:border-gray-700 space-y-2">
              <div className="flex items-center justify-between text-xs font-bold text-gray-600 dark:text-gray-300">
                <span>Cliente Asignado:</span>
                <span className="text-[10px] text-[#FF5520] dark:text-[#FF5520] font-extrabold">SELECCIONAR</span>
              </div>
              <select
                value={selectedCustomer?.id || ''}
                onChange={(e) => {
                  const cust = customersList.find(c => c.id === parseInt(e.target.value));
                  setSelectedCustomer(cust);
                }}
                className="w-full bg-white dark:bg-[#0E0E12] border border-gray-300 dark:border-gray-700 rounded-xl p-2 text-xs font-bold text-[#0A0A0A] dark:text-white focus:outline-none focus:border-[#0A0A0A]"
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

              <label className="flex items-center gap-2 pt-1 cursor-pointer">
                <input
                  type="checkbox"
                  checked={issueDian}
                  onChange={(e) => setIssueDian(e.target.checked)}
                  className="w-4 h-4 rounded text-[#FF5520] accent-[#FF5520] focus:ring-[#FF5520] cursor-pointer"
                />
                <span className="text-xs font-bold text-[#FF5520] dark:text-[#FF5520]">
                  Transmitir Factura Electrónica a DIAN (Factus API)
                </span>
              </label>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto p-4 space-y-2">
              {cart.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-center space-y-3">
                  <div className="w-14 h-14 rounded-3xl bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] flex items-center justify-center text-gray-300 dark:text-gray-600">
                    <ShoppingCart className="w-7 h-7" />
                  </div>
                  <div>
                    <p className="text-xs font-bold text-[#0A0A0A] dark:text-white">El carrito está vacío</p>
                    <p className="text-[11px] text-gray-400 dark:text-gray-500 mt-1 max-w-[200px] mx-auto">Haz clic en los productos para agregar al ticket</p>
                  </div>
                </div>
              ) : (
                cart.map(item => (
                  <div key={item.id} className="p-3 bg-gray-50 dark:bg-white/5 rounded-2xl flex items-center justify-between border border-gray-200 dark:border-gray-700">
                    <div className="space-y-0.5">
                      <h4 className="font-bold text-xs text-[#0A0A0A] dark:text-white">{item.name}</h4>
                      <p className="text-[10px] text-gray-500 dark:text-gray-400 font-mono">
                        ${item.price.toLocaleString('es-CO')} c/u
                      </p>
                    </div>

                    <div className="flex items-center gap-3">
                      <div className="flex items-center gap-1.5 bg-white dark:bg-[#0E0E12] px-2 py-1 rounded-xl border border-gray-300 dark:border-gray-700">
                        <button
                          onClick={() => updateQuantity(item.id, -1)}
                          className="text-gray-500 dark:text-gray-400 hover:text-[#FF5520] dark:hover:text-[#FF5520] font-bold text-xs cursor-pointer"
                        >
                          <Minus className="w-3.5 h-3.5" />
                        </button>
                        <span className="text-xs font-black text-[#0A0A0A] dark:text-white px-1 tabular-nums">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() => updateQuantity(item.id, 1)}
                          className="text-gray-500 dark:text-gray-400 hover:text-[#FF5520] dark:hover:text-[#FF5520] font-bold text-xs cursor-pointer"
                        >
                          <Plus className="w-3.5 h-3.5" />
                        </button>
                      </div>

                      <span className="text-xs font-black text-[#0A0A0A] dark:text-white tabular-nums min-w-[70px] text-right">
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
                <span className="font-bold text-[#0A0A0A] dark:text-white tabular-nums">${subtotal.toLocaleString('es-CO')} COP</span>
              </div>
              {issueDian && (
                <div className="flex justify-between text-gray-500 dark:text-gray-400 text-xs">
                  <span>Factura electrónica:</span>
                  <span>Se emitirá a la DIAN al confirmar (precios con IVA incluido)</span>
                </div>
              )}
              <div className="flex justify-between text-base font-black text-[#0A0A0A] dark:text-white pt-2 border-t border-gray-200 dark:border-gray-700">
                <span>Total a Cobrar:</span>
                <span className="text-xl text-[#FF5520] dark:text-[#FF5520] tabular-nums">${total.toLocaleString('es-CO')} COP</span>
              </div>
            </div>

            <button
              disabled={cart.length === 0}
              onClick={() => setShowCheckoutModal(true)}
              className={`w-full py-3.5 rounded-2xl font-black text-sm flex items-center justify-center gap-2 shadow-lg transition-all ${
                cart.length === 0
                  ? 'bg-gray-200 dark:bg-gray-800 text-gray-400 dark:text-gray-500 cursor-not-allowed'
                  : 'bg-[#0A0A0A] hover:bg-[#202020] text-white shadow-[#0A0A0A]/10 active:scale-98 cursor-pointer'
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
          className="w-full py-3.5 px-5 bg-[#0A0A0A] text-white rounded-2xl font-black text-xs flex items-center justify-between shadow-2xl shadow-[#0A0A0A]/10 active:scale-95 transition-all cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <ShoppingCart className="w-4.5 h-4.5" />
            <span>Ver Ticket ({cart.length} ítems)</span>
          </div>
          <span className="text-sm font-extrabold tabular-nums">${total.toLocaleString('es-CO')} COP</span>
        </button>
      </div>

      {/* Checkout Bento Modal */}
      {showCheckoutModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-md w-full bg-white dark:bg-[#17171B] p-5 sm:p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto animate-in fade-in zoom-in duration-150 border border-gray-200 dark:border-[#272730]">
            <div className="flex items-center justify-between border-b border-gray-100 dark:border-gray-800 pb-3">
              <div className="flex items-center gap-2">
                <div className="p-2 bg-[#0A0A0A] text-white rounded-2xl shadow-xs">
                  <Banknote className="w-5 h-5" />
                </div>
                <h3 className="font-extrabold text-base text-[#0A0A0A] dark:text-white">Cobrar Venta</h3>
              </div>
              <button onClick={() => setShowCheckoutModal(false)} className="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-4 bg-[#FF5520]/5 dark:bg-[#FF5520]/10 rounded-2xl border border-[#FF5520]/20 text-center">
              <span className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Monto Total de la Venta</span>
              <h2 className="text-2xl sm:text-3xl font-black text-[#FF5520] dark:text-[#FF5520] tabular-nums mt-0.5">
                ${total.toLocaleString('es-CO')} <span className="text-sm font-bold text-gray-500 dark:text-gray-400">COP</span>
              </h2>
            </div>

            {paymentMethods.length > 0 && (
              <div className="space-y-2">
                <label className="text-xs font-extrabold text-gray-700 dark:text-gray-300 block">
                  Método de Pago:
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {paymentMethods.map(m => (
                    <button
                      key={m.code}
                      type="button"
                      onClick={() => {
                        setSelectedPaymentMethod(m.code);
                        setCashTendered(m.code === 'CASH' ? '' : total.toString());
                      }}
                      className={`py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer ${
                        selectedPaymentMethod === m.code
                          ? 'bg-[#0A0A0A] border-[#D0D0CE] text-white shadow-sm'
                          : 'bg-gray-100 dark:bg-[#0E0E12] border-transparent text-[#0A0A0A] dark:text-white hover:border-[#D0D0CE]'
                      }`}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {selectedPaymentMethod === 'CASH' ? (
              <div className="space-y-2">
                <label className="text-xs font-extrabold text-gray-700 dark:text-gray-300 block">
                  Efectivo Recibido del Cliente:
                </label>
                <div className="relative">
                  <span className="absolute left-3.5 top-2.5 text-gray-400 dark:text-gray-500 font-bold">$</span>
                  <input
                    type="number"
                    value={cashTendered}
                    onChange={(e) => setCashTendered(e.target.value)}
                    placeholder="Ingrese monto pagado..."
                    className="w-full pl-8 pr-4 py-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-gray-300 dark:border-gray-700 rounded-2xl text-base font-black text-[#0A0A0A] dark:text-white focus:outline-none focus:border-[#0A0A0A]"
                    autoFocus
                  />
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
                  {[total, 20000, 50000, 100000].map(val => (
                    <button
                      key={val}
                      onClick={() => setCashTendered(val.toString())}
                      className="py-1.5 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-[#2C2C36] text-[#0A0A0A] dark:text-white rounded-xl text-xs font-bold cursor-pointer"
                    >
                      ${(val / 1000).toFixed(0)}k
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <p className="text-[11px] text-gray-500 dark:text-gray-400 font-semibold text-center">
                Pago electrónico/digital — se registra por el monto exacto de la venta, sin cambio.
              </p>
            )}

            {selectedPaymentMethod === 'CASH' && (
            <div className={`p-4 rounded-2xl border ${
              change >= 0 ? 'bg-emerald-50 dark:bg-emerald-950/20 border-emerald-200 dark:border-emerald-800/40' : 'bg-red-50 dark:bg-red-950/30 border-red-200 dark:border-red-800/40'
            }`}>
              <div className="flex justify-between items-center">
                <span className="text-xs font-extrabold text-gray-600 dark:text-gray-300 uppercase">
                  Devuelta / Cambio:
                </span>
                <span className={`text-xl sm:text-2xl font-black tabular-nums ${
                  change >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'
                }`}>
                  ${change >= 0 ? change.toLocaleString('es-CO') : '0'} COP
                </span>
              </div>
              {change < 0 && (
                <p className="text-[11px] font-bold text-red-600 dark:text-red-400 mt-1 flex items-center gap-1">
                  <ShieldAlert className="w-3.5 h-3.5" /> El dinero ingresado es menor al total.
                </p>
              )}
            </div>
            )}

            <div className="flex items-center gap-3 pt-2">
              <button
                onClick={() => setShowCheckoutModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-[#2C2C36] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                disabled={change < 0 || cashNumber <= 0 || isSubmittingSale}
                onClick={handleCompleteSale}
                className={`w-1/2 py-3 rounded-2xl font-extrabold text-xs flex items-center justify-center gap-2 text-white shadow-md cursor-pointer ${
                  change < 0 || cashNumber <= 0 || isSubmittingSale
                    ? 'bg-gray-300 dark:bg-gray-700 cursor-not-allowed'
                    : 'bg-[#0A0A0A] hover:bg-[#202020] shadow-[#0A0A0A]/10'
                }`}
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>{isSubmittingSale ? 'Procesando…' : 'Confirmar Venta'}</span>
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Sale Completed Success Receipt Modal */}
      {saleCompleted && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-md w-full bg-white dark:bg-[#17171B] p-6 rounded-3xl shadow-2xl space-y-4 text-center border border-gray-200 dark:border-[#272730]">
            <div className="w-12 h-12 bg-[#FF5520]/15 text-[#FF5520] rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-7 h-7 text-[#FF5520]" />
            </div>
            <h3 className="text-xl font-extrabold text-[#0A0A0A] dark:text-white">¡Venta Registrada Exitosamente!</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Comprobante #{saleCompleted.id}</p>

            <div className="p-4 bg-gray-50 dark:bg-white/5 rounded-2xl border border-gray-200 dark:border-gray-700 text-left text-xs space-y-1.5">
              <div className="flex justify-between font-bold text-gray-700 dark:text-gray-300">
                <span>Cliente:</span>
                <span>{saleCompleted.customer}</span>
              </div>
              <div className="flex justify-between font-bold text-gray-700 dark:text-gray-300">
                <span>Monto Cobrado:</span>
                <span>${saleCompleted.total.toLocaleString('es-CO')} COP</span>
              </div>
              <div className="flex justify-between font-bold text-emerald-600 dark:text-emerald-400">
                <span>Devuelta / Cambio:</span>
                <span>${saleCompleted.change.toLocaleString('es-CO')} COP</span>
              </div>
              {saleCompleted.cufe && (
                <div className="pt-2 border-t border-gray-200 dark:border-gray-700 text-[10px] text-gray-500 dark:text-gray-400 font-mono">
                  <span className="font-bold text-[#FF5520] dark:text-[#FF5520] block">Factura DIAN Transmitida:</span>
                  <span>{saleCompleted.cufe}</span>
                </div>
              )}
            </div>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => notify(`Imprimiendo tiquete térmico para venta ${saleCompleted.id}...`, { title: 'Imprimir tiquete', danger: false })}
                className="w-1/2 py-2.5 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-[#2C2C36] text-[#0A0A0A] dark:text-white rounded-2xl font-bold text-xs flex items-center justify-center gap-1.5 cursor-pointer"
              >
                <Printer className="w-4 h-4" /> Imprimir Tiquete
              </button>
              <button
                onClick={() => { setSaleCompleted(null); focusBarcodeInput(); }}
                className="w-1/2 py-2.5 bg-[#0A0A0A] hover:bg-[#202020] text-white rounded-2xl font-bold text-xs cursor-pointer shadow-md shadow-[#0A0A0A]/10"
              >
                Siguiente Venta
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
