import React, { useState } from 'react';
import { ChevronLeft, ChevronRight, History, RefreshCw } from 'lucide-react';
import { getInventoryMovementsApi } from '../../services/inventoryApi';
import useLoad from '../../hooks/useLoad';
import { Badge, Button, DateRangeFilter, EmptyState } from '../ui';
import ProductPicker from './ProductPicker';
import { movementTypeInfo } from './movementReasons';
import { dayEnd, dayStart, formatDateTime, isoDaysAgo } from '../../utils/format';

const PAGE_SIZE = 20;

/** Kardex del local: cada entrada, salida, venta, compra y ajuste, del más reciente al más antiguo. */
export default function MovementsPanel() {
  const [product, setProduct] = useState(null);
  const [dateFrom, setDateFrom] = useState(isoDaysAgo(6));
  const [dateTo, setDateTo] = useState(isoDaysAgo(0));
  const [page, setPage] = useState(0);

  const { data, loading, error, reload } = useLoad(
    () => getInventoryMovementsApi({
      productId: product?.id,
      dateFrom: dayStart(dateFrom),
      dateTo: dayEnd(dateTo),
      page,
      size: PAGE_SIZE,
    }),
    [product?.id, dateFrom, dateTo, page],
    {
      initial: { content: [], totalElements: 0, totalPages: 0 },
      autoRefresh: true,
      errorMessage: 'No se pudieron cargar los movimientos.',
    },
  );

  const changeRange = (from, to) => { setDateFrom(from); setDateTo(to); setPage(0); };
  const changeProduct = (next) => { setProduct(next); setPage(0); };
  const rows = data.content || [];

  return (
    <div className="space-y-6">
      <div className="bento-card p-4 flex flex-col lg:flex-row gap-3 lg:items-start bg-card border-line">
        <div className="flex-1 space-y-1.5">
          <span className="block text-[11px] font-bold uppercase tracking-wider text-ink-soft">Producto</span>
          <ProductPicker value={product} onChange={changeProduct} placeholder="Todos los productos (escribe para filtrar)" />
        </div>
        <div className="flex flex-col sm:flex-row sm:items-start gap-3">
          <DateRangeFilter from={dateFrom} to={dateTo} onChange={changeRange} />
          <Button size="sm" icon={RefreshCw} loading={loading} onClick={() => reload({ silent: false })}>Actualizar</Button>
        </div>
      </div>

      {error && <div className="p-3.5 rounded-2xl border border-red-200 bg-red-50 dark:bg-red-500/10 text-xs font-bold text-red-600">{error}</div>}

      <div className="bento-card p-6 bg-card border-line">
        <div className="mb-4 text-xs font-bold text-ink-soft">{data.totalElements} movimientos en el período</div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Cargando movimientos...</div>
        ) : rows.length === 0 ? (
          <EmptyState icon={History} title="No hay movimientos con estos filtros" description="Prueba ampliando el rango de fechas o quitando el producto" />
        ) : (
          <table className="table-cards w-full text-left text-xs">
            <thead>
              <tr className="border-b border-line text-ink-muted uppercase tracking-wider font-extrabold text-[10px]">
                <th className="py-3 px-3">Producto</th>
                <th className="py-3 px-3">Tipo</th>
                <th className="py-3 px-3 text-right">Cantidad</th>
                <th className="py-3 px-3 text-center">Stock</th>
                <th className="py-3 px-3">Motivo</th>
                <th className="py-3 px-3">Usuario</th>
                <th className="py-3 px-3">Fecha</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((m) => {
                const info = movementTypeInfo(m.movementType);
                // El signo sale de la diferencia de stock, no del tipo: vale igual para cualquier alias del kardex.
                const delta = m.newStock - m.previousStock;
                return (
                  <tr key={m.id} className="hover:bg-gray-50 dark:hover:bg-white/5">
                    <td data-label="" className="py-3 px-3 font-bold text-ink">{m.productName}</td>
                    <td data-label="Tipo" className="py-3 px-3"><Badge tone={info.tone}>{info.label}</Badge></td>
                    <td
                      data-label="Cantidad"
                      className={`py-3 px-3 text-right font-black tabular-nums ${
                        delta > 0 ? 'text-emerald-600 dark:text-emerald-400' : delta < 0 ? 'text-red-600 dark:text-red-400' : 'text-ink-soft'
                      }`}
                    >
                      {delta > 0 ? '+' : delta < 0 ? '−' : ''}{Math.abs(delta === 0 ? m.quantity : delta).toLocaleString('es-CO')}
                    </td>
                    <td data-label="Stock" className="py-3 px-3 text-center text-ink-soft tabular-nums">
                      {m.previousStock.toLocaleString('es-CO')} → <span className="font-bold text-ink">{m.newStock.toLocaleString('es-CO')}</span>
                    </td>
                    <td data-label="Motivo" className="py-3 px-3 text-ink-soft max-w-xs">{m.reason || '—'}</td>
                    <td data-label="Usuario" className="py-3 px-3 text-ink-soft">{m.user || '—'}</td>
                    <td data-label="Fecha" className="py-3 px-3 text-ink-soft">{formatDateTime(m.createdAt)}</td>
                  </tr>
                );
              })}
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
    </div>
  );
}
