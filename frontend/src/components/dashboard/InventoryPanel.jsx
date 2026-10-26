import React, { useState } from 'react';
import { Badge, SegmentedControl } from '../ui';
import { expiryLabel, formatExpiryDate } from '../../utils/expiry';

const EXPIRING_WINDOWS = [7, 15, 30, 60, 90];
// Filas visibles en la tarjeta del Dashboard; el resto se consulta en Inventario.
const EXPIRING_VISIBLE_ROWS = 8;

const HEAD = 'py-2 px-2 text-ink-muted uppercase tracking-wider font-extrabold text-[10px]';

function EmptyNote({ children }) {
  return <p className="text-xs text-ink-muted font-semibold py-8 text-center">{children}</p>;
}

function StockTable({ stock }) {
  return (
    <table className="table-cards w-full text-left text-xs">
      <thead>
        <tr className="border-b border-line">
          <th className={HEAD}>Producto</th>
          <th className={`${HEAD} w-1/3`}>Nivel</th>
          <th className={`${HEAD} text-center`}>Stock</th>
          <th className={`${HEAD} text-center`}>Mínimo</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line font-medium">
        {stock.map((p) => {
          const low = p.status !== 'OK';
          const level = p.minStock > 0 ? Math.min(100, (p.quantityAvailable / p.minStock) * 100) : 100;
          return (
            <tr key={p.productId}>
              <td data-label="" className="py-2.5 px-2 font-bold text-ink">{p.productName}</td>
              <td data-label="Nivel" className="py-2.5 px-2">
                <div className="h-2 w-28 md:w-full bg-page rounded-full overflow-hidden">
                  <div className={`h-full rounded-full bar-grow-x ${low ? 'bg-amber-500' : 'bg-emerald-500'}`} style={{ width: `${Math.max(level, 4)}%` }} />
                </div>
              </td>
              <td data-label="Stock" className={`py-2.5 px-2 text-center font-black tabular-nums ${low ? 'text-amber-600 dark:text-amber-400' : 'text-ink'}`}>{p.quantityAvailable}</td>
              <td data-label="Mínimo" className="py-2.5 px-2 text-center font-semibold text-ink-muted tabular-nums">{p.minStock}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function ExpiringTable({ expiring }) {
  return (
    <>
      <table className="table-cards w-full text-left text-xs">
        <thead>
          <tr className="border-b border-line">
            <th className={HEAD}>Producto</th>
            <th className={`${HEAD} text-center`}>Vence</th>
            <th className={`${HEAD} text-center`}>Estado</th>
            <th className={`${HEAD} text-center`}>Uds. del lote</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line font-medium">
          {expiring.slice(0, EXPIRING_VISIBLE_ROWS).map((p) => (
            <tr key={p.lotId}>
              <td data-label="" className="py-2.5 px-2">
                <span className="font-bold text-ink block">{p.productName}</span>
                <span className="text-[10px] text-ink-muted font-mono">{p.internalCode}{p.lotCode ? ` · Lote ${p.lotCode}` : ''}</span>
              </td>
              <td data-label="Vence" className="py-2.5 px-2 text-center font-semibold text-ink-soft tabular-nums">{formatExpiryDate(p.expirationDate)}</td>
              <td data-label="Estado" className="py-2.5 px-2 text-center">
                <Badge tone={p.status === 'EXPIRING' ? 'warning' : 'danger'}>{expiryLabel(p.daysToExpire)}</Badge>
              </td>
              <td data-label="Uds. del lote" className="py-2.5 px-2 text-center font-black tabular-nums text-ink">{p.lotRemaining}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {expiring.length > EXPIRING_VISIBLE_ROWS && (
        <p className="text-[11px] text-ink-muted font-semibold pt-3 text-center">
          y {expiring.length - EXPIRING_VISIBLE_ROWS} lotes más — revísalos en Inventario
        </p>
      )}
    </>
  );
}

/**
 * Inventario del Dashboard en un panel con dos pestañas: stock y vencimientos. Solo para quien gestiona
 * inventario (ADMINISTRATOR y SUPERVISOR). Es presentacional: el Dashboard carga los datos.
 */
export default function InventoryPanel({
  loading, stock, belowMinStock, onBelowMinStockChange, expiring, expiringDays, onExpiringDaysChange,
}) {
  const [tab, setTab] = useState('stock');
  const tabs = [
    { id: 'stock', label: stock.length > 0 ? `Stock (${stock.length})` : 'Stock' },
    { id: 'expiring', label: expiring.length > 0 ? `Vencimientos (${expiring.length})` : 'Vencimientos' },
  ];

  let body;
  if (loading) {
    body = <div className="space-y-3 animate-pulse">{[0, 1, 2].map((i) => <div key={i} className="h-9 rounded-xl bg-page" />)}</div>;
  } else if (tab === 'stock') {
    body = stock.length === 0
      ? <EmptyNote>{belowMinStock ? 'Ningún producto está bajo su stock mínimo 🎉' : 'No hay productos activos'}</EmptyNote>
      : <StockTable stock={stock} />;
  } else {
    body = expiring.length === 0
      ? <EmptyNote>Ningún lote vence en los próximos {expiringDays} días 🎉</EmptyNote>
      : <ExpiringTable expiring={expiring} />;
  }

  return (
    <div className="bento-card hover:transform-none p-6 card-rise" style={{ '--d': '200ms' }}>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
        <SegmentedControl options={tabs} value={tab} onChange={setTab} ariaLabel="Inventario" className="sm:w-96" />
        {tab === 'stock' ? (
          <label className="flex items-center gap-1.5 text-[11px] font-bold text-ink-soft cursor-pointer shrink-0">
            <input type="checkbox" checked={belowMinStock} onChange={(e) => onBelowMinStockChange(e.target.checked)} className="accent-brand" />
            Solo bajo mínimo
          </label>
        ) : (
          <select
            value={expiringDays}
            onChange={(e) => onExpiringDaysChange(Number(e.target.value))}
            aria-label="Ventana de vencimiento"
            className="shrink-0 px-2.5 py-1 bg-page border border-line rounded-xl text-[11px] font-bold text-ink cursor-pointer focus:outline-none focus:border-ink"
          >
            {EXPIRING_WINDOWS.map((d) => <option key={d} value={d}>{d} días</option>)}
          </select>
        )}
      </div>
      {body}
    </div>
  );
}
