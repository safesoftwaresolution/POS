import React, { useState } from 'react';
import { SegmentedControl } from '../ui';
import sortedEntries from '../analytics/sortedEntries';
import { money } from '../../utils/format';
import SalesHeatmap from './SalesHeatmap';
import { Podium, Ranking } from './rankings';

const TABS = [
  { id: 'sellers', label: 'Vendedores' },
  { id: 'products', label: 'Productos' },
  { id: 'profit', label: 'Ganancia' },
  { id: 'purchases', label: 'Compras' },
  { id: 'hours', label: 'Horas' },
];

/**
 * Analíticas del período en un solo panel con pestañas (antes eran cuatro tarjetas sueltas).
 * Solo para ADMINISTRATOR: son datos de dinero. Es presentacional; el Dashboard carga los datos.
 */
export default function InsightsPanel({ loading, sales, topProducts, profitability, purchases, heatmap }) {
  const [tab, setTab] = useState('sellers');

  let content = null;
  if (loading) {
    content = <div className="space-y-4 animate-pulse">{[0, 1, 2, 3].map((i) => <div key={i} className="h-9 rounded-xl bg-page" />)}</div>;
  } else if (tab === 'sellers') {
    content = <Ranking avatar entries={sortedEntries(sales?.salesBySeller)} formatValue={money} emptyLabel="Sin ventas en este período" />;
  } else if (tab === 'products') {
    content = <Podium entries={topProducts.map((p) => ({ label: p.productName, value: p.quantitySold }))} formatValue={(v) => `${v} uds`} />;
  } else if (tab === 'profit') {
    content = <Ranking entries={profitability.map((p) => ({ label: p.productName, value: Number(p.profitAmount) }))} formatValue={money} emptyLabel="Sin ganancias en este período" />;
  } else if (tab === 'hours') {
    content = <SalesHeatmap cells={heatmap} />;
  } else {
    content = <Ranking entries={sortedEntries(purchases?.purchasesBySupplier)} formatValue={money} emptyLabel="Sin compras en este período" />;
  }

  const subtitles = {
    sellers: 'Monto vendido por cada vendedor',
    products: 'Unidades vendidas',
    profit: 'Ganancia bruta = (venta − costo) × unidades',
    purchases: 'Monto invertido por proveedor',
    hours: 'Cuándo vendes más · últimos 30 días',
  };

  return (
    <div className="bento-card hover:transform-none p-6 card-rise" style={{ '--d': '160ms' }}>
      <div className="flex flex-col gap-3 mb-5">
        <div>
          <h3 className="text-sm font-extrabold text-ink">Lo que más mueve tu negocio</h3>
          <p className="text-[11px] text-ink-soft font-medium">{subtitles[tab]}</p>
        </div>
        <SegmentedControl options={TABS} value={tab} onChange={setTab} ariaLabel="Analíticas del período" scrollable equal={false} className="sm:w-fit" />
      </div>
      {content}
    </div>
  );
}
