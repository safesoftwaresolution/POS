import React, { useMemo, useState } from 'react';
import { Receipt, Search, RefreshCw, ChevronLeft, ChevronRight, Eye } from 'lucide-react';
import { searchSalesApi } from '../services/salesApi';
import useLoad from '../hooks/useLoad';
import usePermissions from '../hooks/usePermissions';
import { Badge, Button, DateRangeFilter, EmptyState, Modal, PageHeader, inputClass } from '../components/ui';
import { dayEnd, dayStart, formatDateTime, isoDaysAgo, money, paymentLabel } from '../utils/format';

const PAGE_SIZE = 20;
const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0 };

/** Historial de todas las ventas del local: filtro por fechas, búsqueda y detalle de cada venta. */
export default function SalesBento() {
  // El backend ya limita al vendedor a sus propias ventas; aquí solo se ajusta el texto.
  const { isSeller } = usePermissions();
  const [dateFrom, setDateFrom] = useState(isoDaysAgo(6));
  const [dateTo, setDateTo] = useState(isoDaysAgo(0));
  const [search, setSearch] = useState('');
  const [method, setMethod] = useState('ALL');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);

  const { data, loading, error, reload } = useLoad(
    () => searchSalesApi({ dateFrom: dayStart(dateFrom), dateTo: dayEnd(dateTo), page, size: PAGE_SIZE }),
    [dateFrom, dateTo, page],
    { initial: EMPTY_PAGE, autoRefresh: true, errorMessage: 'No se pudieron cargar las ventas.' },
  );

  const changeRange = (from, to) => { setDateFrom(from); setDateTo(to); setPage(0); };

  // Búsqueda y método de pago filtran la página cargada (el rango de fechas sí es del servidor).
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (data.content || []).filter((s) =>
      (method === 'ALL' || s.paymentMethod === method) &&
      (!q || (s.invoiceNumber || '').toLowerCase().includes(q)
        || (s.customerName || '').toLowerCase().includes(q)
        || (s.sellerUsername || '').toLowerCase().includes(q)));
  }, [data.content, search, method]);

  const methods = useMemo(
    () => [...new Set((data.content || []).map((s) => s.paymentMethod).filter(Boolean))],
    [data.content]
  );
  const pageTotal = rows.reduce((sum, s) => sum + Number(s.totalAmount || 0), 0);

  return (
    <div className="space-y-6">
      <PageHeader
        title={isSeller ? 'Mis ventas' : 'Ventas'}
        subtitle={isSeller ? 'Historial de las ventas que has registrado' : 'Historial de todas las ventas de tu local'}
        actions={
          <Button size="sm" icon={RefreshCw} loading={loading} onClick={() => reload({ silent: false })}>Actualizar</Button>
        }
      />

      <div className="bento-card p-4 flex flex-col lg:flex-row gap-3 lg:items-center">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por factura, cliente o vendedor"
            className={`${inputClass} pl-10`}
          />
        </div>
        <div className="flex flex-col lg:flex-row lg:items-start gap-3">
          <DateRangeFilter from={dateFrom} to={dateTo} onChange={changeRange} />
          <select value={method} onChange={(e) => setMethod(e.target.value)} className={`${inputClass} w-auto`}>
            <option value="ALL">Todos los pagos</option>
            {methods.map((m) => <option key={m} value={m}>{paymentLabel(m)}</option>)}
          </select>
        </div>
      </div>

      {error && <div className="p-3.5 rounded-2xl border border-red-200 bg-red-50 dark:bg-red-500/10 text-xs font-bold text-red-600">{error}</div>}

      <div className="bento-card p-6">
        <div className="flex items-center justify-between mb-4 text-xs font-bold text-ink-soft">
          <span>{data.totalElements} {isSeller ? 'ventas tuyas en el período' : 'ventas en el período'}</span>
          <span>Total de esta página: <span className="text-brand tabular-nums">{money(pageTotal)}</span></span>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Cargando ventas...</div>
        ) : rows.length === 0 ? (
          <EmptyState icon={Receipt} title="No hay ventas con estos filtros" description="Prueba ampliando el rango de fechas" />
        ) : (
          <table className="table-cards w-full text-left text-xs">
            <thead>
              <tr className="border-b border-line text-ink-muted uppercase tracking-wider font-extrabold text-[10px]">
                <th className="py-3 px-3">Factura</th>
                <th className="py-3 px-3">Fecha</th>
                <th className="py-3 px-3">Cliente</th>
                <th className="py-3 px-3">Vendedor</th>
                <th className="py-3 px-3">Pago</th>
                <th className="py-3 px-3 text-right">Total</th>
                <th className="py-3 px-3 text-right"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((s) => (
                <tr key={s.id} className="hover:bg-gray-50 dark:hover:bg-white/5 cursor-pointer" onClick={() => setSelected(s)}>
                  <td data-label="" className="py-3 px-3 font-black text-brand">{s.invoiceNumber || `#${s.id}`}</td>
                  <td data-label="Fecha" className="py-3 px-3 text-ink-soft">{formatDateTime(s.createdAt)}</td>
                  <td data-label="Cliente" className="py-3 px-3 font-semibold text-ink">{s.customerName || 'Cliente General'}</td>
                  <td data-label="Vendedor" className="py-3 px-3 text-ink-soft">{s.sellerUsername || '—'}</td>
                  <td data-label="Pago" className="py-3 px-3"><Badge>{paymentLabel(s.paymentMethod)}</Badge></td>
                  <td data-label="Total" className="py-3 px-3 text-right font-black text-ink tabular-nums">{money(s.totalAmount)}</td>
                  <td data-label="Detalle" className="py-3 px-3 text-right">
                    <button type="button" aria-label="Ver detalle" className="p-1.5 text-ink-muted hover:text-brand cursor-pointer">
                      <Eye className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {data.totalPages > 1 && (
          <div className="flex items-center justify-between mt-4 pt-4 border-t border-line text-xs font-bold text-ink-soft">
            <Button variant="soft" size="sm" icon={ChevronLeft} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Anterior</Button>
            <span>Página {page + 1} de {data.totalPages}</span>
            <Button variant="soft" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Siguiente <ChevronRight className="w-4 h-4" />
            </Button>
          </div>
        )}
      </div>

      {selected && (
        <Modal
          title={`Venta ${selected.invoiceNumber || `#${selected.id}`}`}
          subtitle={`${formatDateTime(selected.createdAt)} · ${selected.customerName || 'Cliente General'}`}
          size="lg"
          onClose={() => setSelected(null)}
        >
          <div className="px-6 pb-6 space-y-4">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-line text-ink-muted uppercase text-[10px] font-extrabold">
                  <th className="py-2">Producto / servicio</th>
                  <th className="py-2 text-center">Cant.</th>
                  <th className="py-2 text-right">Precio</th>
                  <th className="py-2 text-right">Subtotal</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {(selected.items || []).map((it) => (
                  <tr key={it.id}>
                    <td className="py-2 font-bold text-ink">
                      {it.productName}
                      {it.itemType === 'SERVICE' && <Badge tone="brand" className="ml-2">Servicio</Badge>}
                    </td>
                    <td className="py-2 text-center tabular-nums">{it.quantity}</td>
                    <td className="py-2 text-right tabular-nums">{money(it.unitPrice)}</td>
                    <td className="py-2 text-right font-bold tabular-nums">{money(it.subtotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="space-y-1.5 text-xs font-semibold text-ink-soft border-t border-line pt-3">
              <div className="flex justify-between"><span>Método de pago</span><span className="text-ink">{paymentLabel(selected.paymentMethod)}</span></div>
              <div className="flex justify-between"><span>Vendedor</span><span className="text-ink">{selected.sellerUsername || '—'}</span></div>
              {selected.paymentMethod === 'CASH' && (
                <>
                  <div className="flex justify-between"><span>Efectivo recibido</span><span className="text-ink tabular-nums">{money(selected.cashReceived)}</span></div>
                  <div className="flex justify-between"><span>Cambio</span><span className="text-ink tabular-nums">{money(selected.cashChange)}</span></div>
                </>
              )}
              <div className="flex justify-between text-base font-black text-ink pt-2 border-t border-line">
                <span>Total</span><span className="text-brand tabular-nums">{money(selected.totalAmount)}</span>
              </div>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
