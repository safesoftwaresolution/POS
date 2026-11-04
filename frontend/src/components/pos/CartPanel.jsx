import React from 'react';
import { ShoppingCart, Trash2, Plus, Minus, Banknote, X } from 'lucide-react';
import { cartKey, isService } from '../../utils/cartItem';

const money = (n) => n.toLocaleString('es-CO');

/**
 * Ticket de venta (columna derecha en desktop, drawer inferior en mobile): cliente asignado,
 * líneas con stepper de cantidad, totales y botón de cobro. `scanner` es el escáner de cámara
 * que se muestra arriba del ticket en mobile.
 */
export default function CartPanel({
  cart, customers, selectedCustomer, onSelectCustomer,
  subtotal, total, mobileOpen, scanner, onCloseMobile, onClear, onQuantity, onCheckout,
}) {
  return (
    <div className={
      mobileOpen
        ? 'fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex flex-col justify-end lg:static lg:bg-transparent lg:z-auto'
        : 'hidden lg:flex lg:h-full'
    }>
      <div className="w-full lg:w-[380px] xl:w-[440px] shrink-0 bg-card lg:border-l border-line flex flex-col lg:h-full max-h-[85vh] lg:max-h-none rounded-t-3xl lg:rounded-none shadow-xl lg:shadow-none overflow-hidden">

        <div className="shrink-0 flex items-center justify-between p-5 border-b border-line">
          <div className="flex items-center gap-2.5">
            <div className="p-2.5 bg-solid text-brand rounded-2xl shadow-xs">
              <ShoppingCart className="w-5 h-5" />
            </div>
            <div>
              <h2 className="font-extrabold text-sm text-ink">Ticket de Venta</h2>
              <p className="text-[11px] text-ink-muted font-semibold">{cart.length} {cart.length === 1 ? 'ítem agregado' : 'ítems agregados'}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {cart.length > 0 && (
              <button
                onClick={onClear}
                className="text-xs font-extrabold text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 flex items-center gap-1 bg-red-50 dark:bg-red-950/30 px-2.5 py-1 rounded-xl border border-red-100 dark:border-red-800/40 cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5" /> Vaciar
              </button>
            )}
            <button onClick={onCloseMobile} className="lg:hidden p-1.5 text-ink-muted hover:text-ink-soft cursor-pointer">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Escáner (solo mobile): sigue activo dentro del ticket para agregar productos sin cerrarlo. */}
        {scanner && mobileOpen && <div className="shrink-0 lg:hidden p-3 border-b border-line">{scanner}</div>}

        <div className="shrink-0 bg-gray-50 dark:bg-white/5 p-4 border-b border-line space-y-2">
          <div className="flex items-center justify-between text-xs font-bold text-ink-soft">
            <span>Cliente Asignado:</span>
            <span className="text-[10px] text-brand font-extrabold">SELECCIONAR</span>
          </div>
          <select
            value={selectedCustomer?.id || ''}
            onChange={(e) => onSelectCustomer(customers.find(c => c.id === parseInt(e.target.value)))}
            className="w-full bg-card border border-line rounded-xl p-2 text-xs font-bold text-ink focus:outline-none focus:border-ink"
          >
            {customers.length === 0 ? (
              <option value="">Cliente General (Mostrador)</option>
            ) : (
              customers.map(c => (
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
              <div key={cartKey(item)} className="p-3 bg-gray-50 dark:bg-white/5 rounded-2xl flex items-center justify-between border border-line">
                <div className="space-y-0.5">
                  <h4 className="font-bold text-xs text-ink">
                    {item.name}
                    {isService(item) && <span className="ml-1.5 text-[9px] font-extrabold uppercase text-brand">Servicio</span>}
                  </h4>
                  <p className="text-[10px] text-ink-soft font-mono">${money(item.price)} c/u</p>
                </div>

                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-1.5 bg-card px-2 py-1 rounded-xl border border-line">
                    <button onClick={() => onQuantity(cartKey(item), -1)} className="text-ink-soft hover:text-brand font-bold text-xs cursor-pointer">
                      <Minus className="w-3.5 h-3.5" />
                    </button>
                    <span className="text-xs font-black text-ink px-1 tabular-nums">{item.quantity}</span>
                    <button onClick={() => onQuantity(cartKey(item), 1)} className="text-ink-soft hover:text-brand font-bold text-xs cursor-pointer">
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <span className="text-xs font-black text-ink tabular-nums min-w-[70px] text-right">
                    ${money(item.price * item.quantity)}
                  </span>
                </div>
              </div>
            ))
          )}
        </div>

        <div className="shrink-0 p-5 border-t border-line space-y-4">
          <div className="space-y-2 text-xs font-semibold text-ink-soft">
            <div className="flex justify-between">
              <span>Subtotal Neto:</span>
              <span className="font-bold text-ink tabular-nums">${money(subtotal)} COP</span>
            </div>
            <div className="flex justify-between text-base font-black text-ink pt-2 border-t border-line">
              <span>Total a Cobrar:</span>
              <span className="text-xl text-brand tabular-nums">${money(total)} COP</span>
            </div>
          </div>

          <button
            disabled={cart.length === 0}
            onClick={onCheckout}
            className={`w-full py-3.5 rounded-2xl font-black text-sm flex items-center justify-center gap-2 shadow-lg transition-all ${
              cart.length === 0
                ? 'bg-gray-200 dark:bg-gray-800 text-ink-muted cursor-not-allowed'
                : 'bg-solid hover:bg-solid-hover text-solid-ink shadow-[#0A0A0A]/10 active:scale-98 cursor-pointer'
            }`}
          >
            <Banknote className="w-5 h-5" />
            <span>COBRAR EN EFECTIVO [F4]</span>
          </button>
        </div>
      </div>
    </div>
  );
}
