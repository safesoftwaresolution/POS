import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle, ArrowUpRight, BarChart3, ChevronRight, CreditCard, FileText, Package, Plus, Receipt, RefreshCw, ShoppingBag,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getProductsApi } from '../services/productsApi';
import { searchSalesApi } from '../services/salesApi';
import { listInvoicesApi } from '../services/billingApi';
import {
  getProfitabilityReportApi, getPurchasesReportApi, getSalesReportApi, getStockReportApi, getTopProductsReportApi,
} from '../services/reportsApi';
import useAutoRefresh from '../hooks/useAutoRefresh';
import { Button, EmptyState } from '../components/ui';
import AnalyticsPanel from '../components/analytics/AnalyticsPanel';
import { LoadingRows, RankedList, SectionCard } from '../components/analytics/parts';
import { dayEnd, dayStart, isoDaysAgo, money, paymentLabel } from '../utils/format';

const RANGES = [
  { days: 7, label: '7 días' },
  { days: 30, label: '30 días' },
];
// Tope de ventas traídas para el gráfico y la mezcla de pagos. Los totales de dinero de ADMINISTRATOR
// salen de los reportes (agregados en base de datos), así que no dependen de este tope.
const SALES_SAMPLE = 200;

function KpiCard({ title, icon: Icon, value, hint, hintClass = 'text-ink-muted', onClick }) {
  return (
    <div onClick={onClick} className="bento-card p-6 flex flex-col justify-between cursor-pointer hover:border-line-strong transition-all">
      <div className="flex justify-between items-start">
        <h3 className="text-xs font-semibold text-ink-soft uppercase tracking-wider">{title}</h3>
        <div className="w-7 h-7 rounded-full border border-line flex items-center justify-center text-ink-soft">
          <Icon className="w-4 h-4" />
        </div>
      </div>
      <div className="mt-2">
        <div className="text-2xl font-black tracking-tight tabular-nums text-ink">{value}</div>
        <span className={`text-[11px] mt-1 block font-semibold ${hintClass}`}>{hint}</span>
      </div>
    </div>
  );
}

/** Resumen del negocio: KPIs, ventas por día, mezcla de pagos y analíticas del período seleccionado. */
export default function DashboardBento() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMINISTRATOR';
  const canSeeStock = isAdmin || user?.role === 'SUPERVISOR';

  const [days, setDays] = useState(7);
  const [belowMinStock, setBelowMinStock] = useState(true);
  const [loading, setLoading] = useState(true);

  const [products, setProducts] = useState([]);
  const [sales, setSales] = useState([]);
  const [dianCount, setDianCount] = useState(null);
  const [salesReport, setSalesReport] = useState(null);
  const [topProducts, setTopProducts] = useState([]);
  const [profitability, setProfitability] = useState([]);
  const [purchases, setPurchases] = useState(null);
  const [stock, setStock] = useState([]);

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    const from = dayStart(isoDaysAgo(days - 1));
    const to = dayEnd(isoDaysAgo(0));
    // Cada bloque falla por separado: un reporte caído no deja el panel en blanco.
    const safe = (promise, fallback) => promise.catch(() => fallback);
    const [prods, salesPage, dian, report, top, profit, purch, stockRows] = await Promise.all([
      safe(getProductsApi(), null),
      safe(searchSalesApi({ dateFrom: from, dateTo: to, size: SALES_SAMPLE }), null),
      safe(listInvoicesApi('VALIDATED', 0, 1), null),
      isAdmin ? safe(getSalesReportApi(from, to), null) : null,
      isAdmin ? safe(getTopProductsReportApi(from, to, 8), null) : null,
      isAdmin ? safe(getProfitabilityReportApi(from, to), null) : null,
      isAdmin ? safe(getPurchasesReportApi(from, to), null) : null,
      canSeeStock ? safe(getStockReportApi(belowMinStock), null) : null,
    ]);
    if (Array.isArray(prods)) setProducts(prods);
    if (salesPage) setSales(salesPage.content || []);
    if (dian) setDianCount(dian.totalElements ?? (dian.content || []).length);
    if (report) setSalesReport(report);
    if (Array.isArray(top)) setTopProducts(top);
    if (Array.isArray(profit)) setProfitability(profit);
    if (purch) setPurchases(purch);
    if (Array.isArray(stockRows)) setStock(stockRows);
    setLoading(false);
  }, [days, belowMinStock, isAdmin, canSeeStock]);

  useEffect(() => { load(); }, [load]);
  useAutoRefresh(() => load({ silent: true }));

  const lowStockCount = products.filter((p) => (p.quantityAvailable ?? p.stock ?? 0) <= (p.minStock || 5)).length;

  // ADMINISTRATOR: totales exactos del reporte; el resto: sobre las ventas cargadas.
  const loadedTotal = sales.reduce((sum, s) => sum + Number(s.totalAmount || 0), 0);
  const totalSold = salesReport ? Number(salesReport.totalSold) : loadedTotal;
  const transactions = salesReport ? salesReport.transactionCount : sales.length;
  const averageTicket = transactions > 0 ? totalSold / transactions : 0;

  const daily = useMemo(() => {
    const buckets = new Map();
    for (let i = days - 1; i >= 0; i -= 1) buckets.set(isoDaysAgo(i), 0);
    sales.forEach((s) => {
      const key = (s.createdAt || '').slice(0, 10);
      if (buckets.has(key)) buckets.set(key, buckets.get(key) + Number(s.totalAmount || 0));
    });
    return [...buckets.entries()].map(([date, value]) => {
      const d = new Date(`${date}T12:00:00`);
      return {
        date,
        value,
        label: days <= 7
          ? d.toLocaleDateString('es-CO', { weekday: 'short' }).replace('.', '').toUpperCase()
          : String(d.getDate()),
      };
    });
  }, [sales, days]);
  const maxDay = Math.max(...daily.map((d) => d.value), 1);
  const bestDate = daily.some((d) => d.value > 0) ? daily.reduce((a, b) => (b.value > a.value ? b : a)).date : null;

  const paymentMix = useMemo(() => {
    const totals = {};
    sales.forEach((s) => {
      const code = s.paymentMethod || 'CASH';
      totals[code] = (totals[code] || 0) + Number(s.totalAmount || 0);
    });
    const sum = Object.values(totals).reduce((a, b) => a + b, 0);
    return Object.entries(totals)
      .map(([code, value]) => ({ label: paymentLabel(code), value, pct: sum > 0 ? Math.round((value / sum) * 100) : 0 }))
      .sort((a, b) => b.value - a.value);
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
                  days === r.days ? 'bg-solid text-white' : 'text-ink-soft hover:text-ink'
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => load()}
            className="p-2 text-ink-soft hover:text-brand bg-card border border-line rounded-full transition-colors cursor-pointer"
            title="Recargar datos"
            aria-label="Recargar datos"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-brand' : ''}`} />
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <KpiCard
          title={`Ventas · ${days} días`}
          icon={ArrowUpRight}
          value={money(totalSold)}
          hint={`Ticket promedio: ${money(averageTicket)}`}
          onClick={() => navigate('/sales')}
        />
        <KpiCard
          title="Transacciones"
          icon={Receipt}
          value={transactions}
          hint={`Ventas de los últimos ${days} días`}
          onClick={() => navigate('/sales')}
        />
        <KpiCard
          title="Bajo mínimo"
          icon={Package}
          value={lowStockCount}
          hint={lowStockCount > 0 ? 'Requieren reabastecimiento' : 'Stock en niveles óptimos'}
          hintClass={lowStockCount > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400'}
          onClick={() => navigate('/inventory')}
        />
        <KpiCard
          title="Facturas DIAN"
          icon={FileText}
          value={dianCount ?? '—'}
          hint="Validadas ante la DIAN"
          onClick={() => navigate('/invoicing')}
        />
      </div>

      {isAdmin && (
        <div className="bento-card p-5 flex items-center justify-between gap-4">
          <div>
            <span className="text-xs font-extrabold uppercase text-ink-muted">Invertido en compras · {days} días</span>
            <div className="text-2xl font-black text-ink tabular-nums mt-0.5">{money(purchases?.totalInverted)}</div>
          </div>
          <div className="p-3 bg-brand/10 text-brand rounded-2xl border border-brand/20"><ShoppingBag className="w-6 h-6" /></div>
        </div>
      )}

      {lowStockCount > 0 && (
        <div className="bento-card p-5 border-amber-200 dark:border-amber-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <p className="text-xs text-ink-soft">
              <span className="font-bold text-ink">Bajo stock: </span>
              {lowStockCount} {lowStockCount === 1 ? 'producto está' : 'productos están'} por debajo del mínimo. Reabastécelos para no perder ventas.
            </p>
          </div>
          <Button size="sm" onClick={() => navigate('/inventory')}>Ver inventario <ChevronRight className="w-3.5 h-3.5" /></Button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <SectionCardWide icon={BarChart3} title={`Ventas de los últimos ${days} días`} subtitle="Total vendido por día">
          {loading ? (
            <LoadingRows />
          ) : (
            <div className="flex items-end justify-between gap-1.5 h-[170px] pt-6">
              {daily.map((d) => {
                const isBest = d.date === bestDate;
                return (
                  <div key={d.date} className="flex-1 flex flex-col items-center gap-2 h-full justify-end relative group min-w-0" title={`${d.date}: ${money(d.value)}`}>
                    {isBest && <span className="absolute -top-3 bg-solid text-white text-[9px] font-extrabold px-1.5 py-0.5 rounded-full">PICO</span>}
                    <div
                      style={{ height: `${d.value > 0 ? Math.max(Math.round((d.value / maxDay) * 100), 8) : 3}%` }}
                      className={`w-full rounded-t-lg transition-all duration-500 ${isBest ? 'bg-solid' : 'bg-brand/25 group-hover:bg-brand'}`}
                    />
                    <span className={`text-[10px] font-bold ${isBest ? 'text-brand' : 'text-ink-soft'}`}>{d.label}</span>
                  </div>
                );
              })}
            </div>
          )}
        </SectionCardWide>

        <div className="lg:col-span-4">
          <SectionCard icon={CreditCard} title="Métodos de pago" subtitle={`Cobros de los últimos ${days} días`}>
            {loading ? <LoadingRows /> : (
              <RankedList
                entries={paymentMix}
                formatValue={(v) => `${money(v)} · ${paymentMix.find((e) => e.value === v)?.pct ?? 0}%`}
                emptyLabel="Aún no hay cobros en este período"
              />
            )}
          </SectionCard>
        </div>
      </div>

      {(isAdmin || canSeeStock) && (
        <AnalyticsPanel
          isAdmin={isAdmin}
          loading={loading}
          sales={salesReport}
          topProducts={topProducts}
          profitability={profitability}
          purchases={purchases}
          stock={stock}
          belowMinStock={belowMinStock}
          onBelowMinStockChange={setBelowMinStock}
        />
      )}

      <div className="bento-card p-6 space-y-4">
        <div className="flex justify-between items-center gap-3">
          <div>
            <h3 className="text-base font-bold text-ink">Ventas recientes</h3>
            <p className="text-xs text-ink-soft">Las últimas 5 — el historial completo está en Ventas</p>
          </div>
          <div className="flex gap-2">
            <Button variant="soft" size="sm" onClick={() => navigate('/sales')}>Ver todas</Button>
            <Button size="sm" icon={Plus} onClick={() => navigate('/pos')}>Nueva venta</Button>
          </div>
        </div>

        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando ventas...</div>
        ) : sales.length === 0 ? (
          <EmptyState icon={Package} title="No hay ventas en este período" description="Abre el punto de venta para registrar una" />
        ) : (
          <table className="table-cards w-full text-left text-xs">
            <thead>
              <tr className="border-b border-line text-ink-soft font-bold text-[11px]">
                <th className="py-2.5 px-3">Factura</th>
                <th className="py-2.5 px-3">Fecha</th>
                <th className="py-2.5 px-3">Cliente</th>
                <th className="py-2.5 px-3 text-right">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line font-medium">
              {sales.slice(0, 5).map((s) => (
                <tr key={s.id} className="hover:bg-gray-50 dark:hover:bg-white/5">
                  <td data-label="" className="py-3 px-3 font-bold text-brand">{s.invoiceNumber || `#${s.id}`}</td>
                  <td data-label="Fecha" className="py-3 px-3 text-ink-soft">
                    {s.createdAt ? new Date(s.createdAt).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'}
                  </td>
                  <td data-label="Cliente" className="py-3 px-3 font-semibold text-ink">{s.customerName || 'Cliente General'}</td>
                  <td data-label="Total" className="py-3 px-3 text-right font-black text-ink tabular-nums">{money(s.totalAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

/** SectionCard que ocupa 8 de 12 columnas en desktop (el gráfico junto a la mezcla de pagos). */
function SectionCardWide({ children, ...props }) {
  return (
    <div className="lg:col-span-8">
      <SectionCard {...props}>{children}</SectionCard>
    </div>
  );
}
