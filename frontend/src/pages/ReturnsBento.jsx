import React, { useMemo, useState } from 'react';
import {
  RotateCcw,
  Search,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  History,
  X,
} from 'lucide-react';
import { getSalesApi, getSaleReturnsApi, createSaleReturnApi } from '../services/salesApi';
import useLoad from '../hooks/useLoad';
import asList from '../utils/asList';

const money = (n) => `$${Math.round(n || 0).toLocaleString('es-CO')}`;
const dateFmt = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—');

export default function ReturnsBento() {
  const { data: sales, loading, reload: reloadSales } = useLoad(
    () => asList(getSalesApi(0, 50)),
    [],
    { initial: [], autoRefresh: true },
  );
  const [search, setSearch] = useState('');

  const [selectedSale, setSelectedSale] = useState(null);
  const [pastReturns, setPastReturns] = useState([]);
  const [returnQty, setReturnQty] = useState({}); // { [saleItemId]: number }
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const filtered = sales.filter((s) =>
    (s.invoiceNumber && s.invoiceNumber.toLowerCase().includes(search.toLowerCase())) ||
    (s.customerName && s.customerName.toLowerCase().includes(search.toLowerCase()))
  );

  const openSale = async (sale) => {
    setSelectedSale(sale);
    setReturnQty({});
    setReason('');
    setError('');
    setSuccess('');
    try {
      const returns = await getSaleReturnsApi(sale.id);
      setPastReturns(Array.isArray(returns) ? returns : []);
    } catch {
      setPastReturns([]);
    }
  };

  // Cuánto de cada línea ya se devolvió en devoluciones previas.
  const alreadyReturnedByItem = useMemo(() => {
    const map = {};
    for (const ret of pastReturns) {
      for (const item of ret.items || []) {
        map[item.saleItemId] = (map[item.saleItemId] || 0) + item.quantity;
      }
    }
    return map;
  }, [pastReturns]);

  const returnableFor = (item) => item.quantity - (alreadyReturnedByItem[item.id] || 0);

  const setQty = (saleItemId, value, max) => {
    const n = Math.max(0, Math.min(Number(value) || 0, max));
    setReturnQty((prev) => ({ ...prev, [saleItemId]: n }));
  };

  const itemsToReturn = selectedSale
    ? (selectedSale.items || [])
        .map((item) => ({ saleItemId: item.id, quantity: returnQty[item.id] || 0, item }))
        .filter((x) => x.quantity > 0)
    : [];
  const refundPreview = itemsToReturn.reduce((sum, x) => sum + x.item.unitPrice * x.quantity, 0);

  const submitReturn = async () => {
    if (itemsToReturn.length === 0) {
      setError('Selecciona al menos una cantidad a devolver.');
      return;
    }
    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      await createSaleReturnApi(
        selectedSale.id,
        reason,
        itemsToReturn.map((x) => ({ saleItemId: x.saleItemId, quantity: x.quantity }))
      );
      setSuccess('Devolución registrada. El stock ya fue repuesto.');
      setReturnQty({});
      setReason('');
      const returns = await getSaleReturnsApi(selectedSale.id);
      setPastReturns(Array.isArray(returns) ? returns : []);
    } catch (e) {
      setError(e.message || 'No se pudo registrar la devolución.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-ink">Devoluciones</h1>
          <p className="text-xs text-ink-soft font-medium">
            Busca la venta original y devuelve los ítems necesarios — el stock se repone automáticamente
          </p>
        </div>
        <button onClick={() => reloadSales({ silent: false })} className="text-xs font-bold text-brand dark:text-brand flex items-center gap-1 hover:underline cursor-pointer">
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} /> Actualizar ventas
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
        {/* Lista de ventas */}
        <div className="lg:col-span-2 bento-card p-5 bg-card rounded-3xl border border-line shadow-xs space-y-3">
          <div className="relative">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por factura o cliente..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink dark:focus:border-brand"
            />
          </div>

          <div className="max-h-[560px] overflow-y-auto divide-y divide-[#EAEAEA] dark:divide-line">
            {loading ? (
              <p className="text-xs text-gray-400 font-semibold py-6 text-center">Cargando ventas...</p>
            ) : filtered.length === 0 ? (
              <p className="text-xs text-gray-400 font-semibold py-6 text-center">No se encontraron ventas</p>
            ) : (
              filtered.map((sale) => (
                <button
                  key={sale.id}
                  onClick={() => openSale(sale)}
                  className={`w-full text-left py-3 px-2 rounded-xl transition-colors cursor-pointer ${
                    selectedSale?.id === sale.id ? 'bg-brand/10 border-l-4 border-line-strong' : 'hover:bg-gray-50 dark:hover:bg-white/5'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-extrabold text-ink">{sale.invoiceNumber}</span>
                    <span className="text-xs font-black text-brand dark:text-brand tabular-nums">{money(sale.totalAmount)}</span>
                  </div>
                  <div className="flex items-center justify-between mt-0.5">
                    <span className="text-[11px] text-ink-soft font-medium truncate">{sale.customerName}</span>
                    <span className="text-[10px] text-ink-muted">{dateFmt(sale.createdAt)}</span>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>

        {/* Detalle + formulario de devolución */}
        <div className="lg:col-span-3 bento-card p-6 bg-card rounded-3xl border border-line shadow-xs">
          {!selectedSale ? (
            <div className="py-16 text-center space-y-2">
              <RotateCcw className="w-10 h-10 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
              <p className="text-xs font-bold text-ink-soft">Selecciona una venta para registrar una devolución</p>
            </div>
          ) : (
            <div className="space-y-5">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="text-sm font-extrabold text-ink">{selectedSale.invoiceNumber}</h3>
                  <p className="text-[11px] text-ink-soft font-medium">
                    {selectedSale.customerName} · {dateFmt(selectedSale.createdAt)} · {selectedSale.paymentMethod}
                  </p>
                </div>
                <button onClick={() => setSelectedSale(null)} className="text-gray-400 hover:text-gray-600 cursor-pointer">
                  <X className="w-4 h-4" />
                </button>
              </div>

              {error && (
                <div className="p-3 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/30 rounded-2xl text-xs font-bold text-red-600 dark:text-red-400 flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 shrink-0" /><span>{error}</span>
                </div>
              )}
              {success && (
                <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-2xl text-xs font-bold text-emerald-600 dark:text-emerald-400 flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 shrink-0" /><span>{success}</span>
                </div>
              )}

              <div className="overflow-x-auto">
                <table className="table-cards w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-line text-ink-muted uppercase tracking-wider font-extrabold text-[10px]">
                      <th className="py-2 px-2">Producto</th>
                      <th className="py-2 px-2 text-center">Vendido</th>
                      <th className="py-2 px-2 text-center">Disponible</th>
                      <th className="py-2 px-2 text-center">A devolver</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#EAEAEA] dark:divide-line font-medium">
                    {(selectedSale.items || []).map((item) => {
                      const returnable = returnableFor(item);
                      return (
                        <tr key={item.id}>
                          <td data-label="" className="py-2.5 px-2 font-bold text-ink">{item.productName}</td>
                          <td data-label="Vendido" className="py-2.5 px-2 text-center tabular-nums">{item.quantity}</td>
                          <td data-label="Disponible" className="py-2.5 px-2 text-center tabular-nums text-ink-soft">{returnable}</td>
                          <td data-label="A devolver" className="py-2.5 px-2 text-center">
                            <input
                              type="number"
                              min="0"
                              max={returnable}
                              disabled={returnable <= 0}
                              value={returnQty[item.id] || ''}
                              onChange={(e) => setQty(item.id, e.target.value, returnable)}
                              placeholder="0"
                              className="w-16 p-1.5 text-center bg-page border border-line rounded-xl text-xs font-bold text-ink disabled:opacity-40 focus:outline-none focus:border-ink dark:focus:border-brand"
                            />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-600 dark:text-gray-300">Motivo (opcional):</label>
                <input
                  type="text"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Ej. Producto defectuoso, talla incorrecta..."
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                />
              </div>

              <div className="flex items-center justify-between pt-2 border-t border-line">
                <div>
                  <span className="text-[11px] font-extrabold uppercase text-ink-muted">Total a reembolsar</span>
                  <div className="text-xl font-black text-ink tabular-nums">{money(refundPreview)}</div>
                </div>
                <button
                  onClick={submitReturn}
                  disabled={submitting || itemsToReturn.length === 0}
                  className="px-5 py-2.5 bg-solid hover:bg-solid-hover disabled:opacity-50 disabled:cursor-not-allowed text-solid-ink rounded-2xl text-xs font-extrabold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
                >
                  <RotateCcw className="w-4 h-4" /> {submitting ? 'Procesando...' : 'Registrar Devolución'}
                </button>
              </div>

              {pastReturns.length > 0 && (
                <div className="pt-4 border-t border-gray-100 dark:border-gray-800 space-y-2">
                  <h4 className="text-[11px] font-extrabold uppercase text-ink-muted flex items-center gap-1.5">
                    <History className="w-3.5 h-3.5" /> Devoluciones previas de esta venta
                  </h4>
                  {pastReturns.map((ret) => (
                    <div key={ret.id} className="p-2.5 bg-gray-50 dark:bg-white/5 rounded-xl text-[11px] flex items-center justify-between">
                      <span className="text-gray-600 dark:text-gray-300 font-medium">
                        {dateFmt(ret.createdAt)} · {ret.reason || 'Sin motivo'} · {ret.processedByUsername}
                      </span>
                      <span className="font-extrabold text-[#17171B] dark:text-white tabular-nums">{money(ret.totalRefund)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
