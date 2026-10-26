import React, { useMemo, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import { getProductsApi } from '../services/productsApi';
import { searchSalesApi } from '../services/salesApi';
import { listInvoicesApi } from '../services/billingApi';
import {
  getExpiringReportApi, getProfitabilityReportApi, getPurchasesReportApi, getSalesReportApi, getStockReportApi,
  getTopProductsReportApi,
} from '../services/reportsApi';
import useLoad from '../hooks/useLoad';
import GettingStarted from '../components/dashboard/GettingStarted';
import SalesHero from '../components/dashboard/SalesHero';
import PaymentsCard from '../components/dashboard/PaymentsCard';
import StatusStrip from '../components/dashboard/StatusStrip';
import InsightsPanel from '../components/dashboard/InsightsPanel';
import InventoryPanel from '../components/dashboard/InventoryPanel';
import RecentSales from '../components/dashboard/RecentSales';
import { dayEnd, dayStart, isoDaysAgo, paymentLabel } from '../utils/format';
import { EXPIRY_WINDOW_DAYS } from '../utils/expiry';

const RANGES = [
  { days: 7, label: '7 días' },
  { days: 30, label: '30 días' },
];
// Tope de ventas traídas para el gráfico y la mezcla de pagos. Los totales de dinero de ADMINISTRATOR
// salen de los reportes (agregados en base de datos), así que no dependen de este tope.
const SALES_SAMPLE = 200;
const EMPTY_DASHBOARD = {
  products: [], sales: [], dianCount: null, salesReport: null, hasAnySale: false, previousTotal: null,
  topProducts: [], profitability: [], purchases: null, stock: [], expiring: [],
};

const sumSales = (sales) => sales.reduce((sum, s) => sum + Number(s.totalAmount || 0), 0);

/** Resumen del negocio: ventas con tendencia, mezcla de pagos, estado del inventario y analíticas del período. */
export default function DashboardBento() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMINISTRATOR';
  const canSeeStock = isAdmin || user?.role === 'SUPERVISOR';

  const [days, setDays] = useState(7);
  const [belowMinStock, setBelowMinStock] = useState(true);
  const [expiringDays, setExpiringDays] = useState(EXPIRY_WINDOW_DAYS);

  const { data: dashboard, loading, reload } = useLoad(async (previous) => {
    const from = dayStart(isoDaysAgo(days - 1));
    const to = dayEnd(isoDaysAgo(0));
    // El período anterior de la misma duración, para mostrar si el negocio sube o baja.
    const previousFrom = dayStart(isoDaysAgo(days * 2 - 1));
    const previousTo = dayEnd(isoDaysAgo(days));
    // Cada bloque falla por separado: un reporte caído no deja el panel en blanco.
    const safe = (promise, fallback) => promise.catch(() => fallback);
    const [prods, salesPage, anySale, dian, report, previousReport, previousPage, top, profit, purch, stockRows, expiringRows] = await Promise.all([
      safe(getProductsApi(), null),
      safe(searchSalesApi({ dateFrom: from, dateTo: to, size: SALES_SAMPLE }), null),
      safe(searchSalesApi({ size: 1 }), null),
      safe(listInvoicesApi('VALIDATED', 0, 1), null),
      isAdmin ? safe(getSalesReportApi(from, to), null) : null,
      isAdmin ? safe(getSalesReportApi(previousFrom, previousTo), null) : null,
      isAdmin ? null : safe(searchSalesApi({ dateFrom: previousFrom, dateTo: previousTo, size: SALES_SAMPLE }), null),
      isAdmin ? safe(getTopProductsReportApi(from, to, 8), null) : null,
      isAdmin ? safe(getProfitabilityReportApi(from, to), null) : null,
      isAdmin ? safe(getPurchasesReportApi(from, to), null) : null,
      canSeeStock ? safe(getStockReportApi(belowMinStock), null) : null,
      canSeeStock ? safe(getExpiringReportApi(expiringDays), null) : null,
    ]);
    let previousTotal = previous.previousTotal;
    if (previousReport) previousTotal = Number(previousReport.totalSold);
    else if (previousPage) previousTotal = sumSales(previousPage.content || []);
    // Un bloque que falló conserva lo que ya se veía en vez de quedar en blanco.
    return {
      products: Array.isArray(prods) ? prods : previous.products,
      sales: salesPage ? salesPage.content || [] : previous.sales,
      hasAnySale: anySale ? (anySale.totalElements ?? (anySale.content || []).length) > 0 : previous.hasAnySale,
      dianCount: dian ? dian.totalElements ?? (dian.content || []).length : previous.dianCount,
      salesReport: report || previous.salesReport,
      previousTotal,
      topProducts: Array.isArray(top) ? top : previous.topProducts,
      profitability: Array.isArray(profit) ? profit : previous.profitability,
      purchases: purch || previous.purchases,
      stock: Array.isArray(stockRows) ? stockRows : previous.stock,
      expiring: Array.isArray(expiringRows) ? expiringRows : previous.expiring,
    };
  }, [days, belowMinStock, expiringDays, isAdmin, canSeeStock], { initial: EMPTY_DASHBOARD, autoRefresh: true });
  const { products, sales, hasAnySale, previousTotal, dianCount, salesReport, topProducts, profitability, purchases, stock, expiring } = dashboard;

  const lowStockCount = products.filter((p) => (p.quantityAvailable ?? p.stock ?? 0) <= (p.minStock || 5)).length;

  // ADMINISTRATOR: totales exactos del reporte; el resto: sobre las ventas cargadas.
  const totalSold = salesReport ? Number(salesReport.totalSold) : sumSales(sales);
  const transactions = salesReport ? salesReport.transactionCount : sales.length;
  const averageTicket = transactions > 0 ? totalSold / transactions : 0;

  const daily = useMemo(() => {
    const buckets = new Map();
    for (let i = days - 1; i >= 0; i -= 1) buckets.set(isoDaysAgo(i), { value: 0, count: 0 });
    sales.forEach((s) => {
      const bucket = buckets.get((s.createdAt || '').slice(0, 10));
      if (bucket) {
        bucket.value += Number(s.totalAmount || 0);
        bucket.count += 1;
      }
    });
    return [...buckets.entries()].map(([date, { value, count }]) => {
      const d = new Date(`${date}T12:00:00`);
      return {
        key: date,
        value,
        count,
        detail: d.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'short' }),
        label: days <= 7
          ? d.toLocaleDateString('es-CO', { weekday: 'short' }).replace('.', '').toUpperCase()
          : String(d.getDate()),
      };
    });
  }, [sales, days]);

  const { paymentMix, paymentTotal } = useMemo(() => {
    const totals = {};
    sales.forEach((s) => {
      const code = s.paymentMethod || 'CASH';
      totals[code] = (totals[code] || 0) + Number(s.totalAmount || 0);
    });
    const sum = Object.values(totals).reduce((a, b) => a + b, 0);
    return {
      paymentTotal: sum,
      paymentMix: Object.entries(totals)
        .map(([code, value]) => ({ label: paymentLabel(code), value, pct: sum > 0 ? Math.round((value / sum) * 100) : 0 }))
        .sort((a, b) => b.value - a.value),
    };
  }, [sales]);

  return (
    <div className="space-y-6 pb-12 select-none">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end gap-4">
        <div>
          <h1 className="text-2xl sm:text-4xl font-light text-ink tracking-tight">
            Bienvenido, <span className="font-extrabold text-brand">{user?.fullName || user?.username || 'Usuario'}</span>
          </h1>
          <p className="text-xs text-ink-soft font-medium mt-1">Resumen y analíticas de tu negocio, siempre al día</p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex bg-card border border-line rounded-full p-1">
            {RANGES.map((r) => (
              <button
                key={r.days}
                type="button"
                onClick={() => setDays(r.days)}
                className={`px-3.5 py-1 text-xs font-bold rounded-full cursor-pointer transition-colors ${
                  days === r.days ? 'bg-solid dark:bg-white text-white dark:text-gray-950' : 'text-ink-soft hover:text-ink'
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => reload({ silent: false })}
            className="p-2 text-ink-soft hover:text-brand bg-card border border-line rounded-full transition-colors cursor-pointer"
            title="Recargar datos"
            aria-label="Recargar datos"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-brand' : ''}`} />
          </button>
        </div>
      </div>

      {!loading && (
        <GettingStarted hasProducts={products.length > 0} hasSales={hasAnySale} canCreateProducts={canSeeStock} />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div className="lg:col-span-8">
          <SalesHero
            days={days}
            loading={loading}
            totalSold={totalSold}
            previousTotal={previousTotal}
            daily={daily}
            transactions={transactions}
            averageTicket={averageTicket}
            dianCount={dianCount}
            onNavigate={navigate}
          />
        </div>
        <div className="lg:col-span-4">
          <PaymentsCard days={days} loading={loading} entries={paymentMix} total={paymentTotal} />
        </div>
      </div>

      <StatusStrip
        days={days}
        lowStockCount={lowStockCount}
        expiring={expiring}
        expiringDays={expiringDays}
        purchasesTotal={purchases?.totalInverted}
        showExpiring={canSeeStock}
        showPurchases={isAdmin}
        onNavigate={navigate}
      />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {isAdmin && (
          <div className="lg:col-span-7">
            <InsightsPanel loading={loading} sales={salesReport} topProducts={topProducts} profitability={profitability} purchases={purchases} />
          </div>
        )}
        <div className={isAdmin ? 'lg:col-span-5' : 'lg:col-span-12'}>
          <RecentSales loading={loading} sales={sales} onNavigate={navigate} />
        </div>
      </div>

      {canSeeStock && (
        <InventoryPanel
          loading={loading}
          stock={stock}
          belowMinStock={belowMinStock}
          onBelowMinStockChange={setBelowMinStock}
          expiring={expiring}
          expiringDays={expiringDays}
          onExpiringDaysChange={setExpiringDays}
        />
      )}
    </div>
  );
}
