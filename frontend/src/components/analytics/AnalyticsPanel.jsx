import React from 'react';
import { BarChart3, DollarSign, Package, TrendingUp, Truck } from 'lucide-react';
import { LoadingRows, RankedList, SectionCard } from './parts';
import sortedEntries from './sortedEntries';

/**
 * Analíticas del período del Dashboard: ventas por vendedor, productos más vendidos, rentabilidad,
 * compras por proveedor y estado del inventario. Es presentacional: el Dashboard carga los datos.
 * `isAdmin` muestra los bloques de dinero (solo ADMINISTRATOR); el inventario lo ven también los supervisores.
 */
export default function AnalyticsPanel({
  isAdmin, loading, sales, topProducts, profitability, purchases, stock, belowMinStock, onBelowMinStockChange,
}) {
  const sellerEntries = sortedEntries(sales?.salesBySeller);
  const supplierEntries = sortedEntries(purchases?.purchasesBySupplier);
  const topProductEntries = topProducts.map((p) => ({ label: p.productName, value: p.quantitySold }));
  const profitabilityEntries = profitability.map((p) => ({ label: p.productName, value: Number(p.profitAmount) }));

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
      {isAdmin && (
        <>
          <SectionCard icon={TrendingUp} title="Ventas por vendedor" subtitle="Monto vendido en el período">
            {loading ? <LoadingRows /> : <RankedList entries={sellerEntries} />}
          </SectionCard>
          <SectionCard icon={BarChart3} title="Productos más vendidos" subtitle="Unidades vendidas en el período">
            {loading ? <LoadingRows /> : <RankedList entries={topProductEntries} formatValue={(v) => `${v} uds`} />}
          </SectionCard>
          <SectionCard icon={DollarSign} title="Rentabilidad por producto" subtitle="Ganancia bruta = (venta − costo) × unidades">
            {loading ? <LoadingRows /> : <RankedList entries={profitabilityEntries} />}
          </SectionCard>
          <SectionCard icon={Truck} title="Compras por proveedor" subtitle="Monto invertido en el período">
            {loading ? <LoadingRows /> : <RankedList entries={supplierEntries} />}
          </SectionCard>
        </>
      )}

      <SectionCard
        icon={Package}
        title="Estado de inventario"
        subtitle={belowMinStock ? 'Solo productos bajo su stock mínimo' : 'Todos los productos activos'}
        action={
          <label className="flex items-center gap-1.5 text-[11px] font-bold text-ink-soft cursor-pointer shrink-0">
            <input
              type="checkbox"
              checked={belowMinStock}
              onChange={(e) => onBelowMinStockChange(e.target.checked)}
              className="accent-brand"
            />
            Solo bajo mínimo
          </label>
        }
      >
        {loading ? (
          <LoadingRows />
        ) : stock.length === 0 ? (
          <p className="text-xs text-ink-muted font-semibold py-6 text-center">
            {belowMinStock ? 'Ningún producto está bajo su stock mínimo 🎉' : 'No hay productos activos'}
          </p>
        ) : (
          <table className="table-cards w-full text-left text-xs">
            <thead>
              <tr className="border-b border-line text-ink-muted uppercase tracking-wider font-extrabold text-[10px]">
                <th className="py-2 px-2">Producto</th>
                <th className="py-2 px-2 text-center">Stock</th>
                <th className="py-2 px-2 text-center">Mínimo</th>
                <th className="py-2 px-2 text-center">Estado</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line font-medium">
              {stock.map((p) => (
                <tr key={p.productId}>
                  <td data-label="" className="py-2.5 px-2 font-bold text-ink">{p.productName}</td>
                  <td data-label="Stock" className="py-2.5 px-2 text-center font-black tabular-nums text-ink">{p.quantityAvailable}</td>
                  <td data-label="Mínimo" className="py-2.5 px-2 text-center font-semibold text-ink-muted tabular-nums">{p.minStock}</td>
                  <td data-label="Estado" className="py-2.5 px-2 text-center">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-extrabold ${
                      p.status === 'OK'
                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                        : 'bg-amber-100 dark:bg-amber-500/20 text-amber-800 dark:text-amber-400 border border-amber-300 dark:border-amber-500/30'
                    }`}>
                      {p.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </SectionCard>
    </div>
  );
}
