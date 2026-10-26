import React, { useState } from 'react';
import { AlertCircle, CheckCircle2, Gauge, Store, UserPlus, Users } from 'lucide-react';
import { getBackofficeStatsOverviewApi } from '../../services/backoffice/statsApi';
import useLoad from '../../hooks/useLoad';
import useCountUp from '../../hooks/useCountUp';
import { SegmentedControl } from '../../components/ui';
import { AreaChart, Donut } from '../../components/dashboard/charts';
import { Podium, Ranking } from '../../components/dashboard/rankings';
import { formatNumber } from '../../utils/format';
import { ROLE_LABELS_PLURAL } from '../../constants/roles';

const money = (n) => `$${Math.round(Number(n) || 0).toLocaleString('es-CO')}`;
// Importes grandes en formato corto para el resumen del mejor mes (1,2 M).
const shortMoney = (n) => {
  const v = Number(n) || 0;
  if (v >= 1_000_000) return `${(v / 1_000_000).toLocaleString('es-CO', { maximumFractionDigits: 1 })} M`;
  if (v >= 1_000) return `${Math.round(v / 1_000).toLocaleString('es-CO')} k`;
  return String(Math.round(v));
};

const STATUS_LABELS = {
  ACTIVE: 'Activos',
  PENDING_VERIFICATION: 'Pendientes',
  REJECTED: 'Rechazados',
  SUSPENDED: 'Suspendidos',
  INACTIVE: 'Inactivos',
};
const INVOICE_LABELS = { VALIDATED: 'Validadas', PENDING: 'Pendientes', REJECTED: 'Rechazadas', ERROR: 'Con error' };

// Los estados con significado (activo, rechazado) conservan verde y rojo.
const STATUS_COLORS = {
  ACTIVE: '#10b981',
  PENDING_VERIFICATION: '#f59e0b',
  REJECTED: '#ef4444',
  SUSPENDED: '#f97316',
  INACTIVE: '#9ca3af',
};

const MONTH_NAMES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const MONTH_FULL = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** Puntos de gráfica a partir de `[{ month: 'YYYY-MM', ... }]`: etiqueta corta y mes completo para el tooltip. */
const monthSeries = (rows, valueOf) => rows.map((row) => {
  const index = Number(row.month.slice(5, 7)) - 1;
  return { key: row.month, label: MONTH_NAMES[index], detail: `${MONTH_FULL[index]} ${row.month.slice(0, 4)}`, value: valueOf(row) };
});

const TABS = [
  { id: 'stores', label: 'Top locales' },
  { id: 'plans', label: 'Planes' },
  { id: 'users', label: 'Usuarios' },
  { id: 'invoices', label: 'Facturas' },
];

function Skeleton({ className }) {
  return <div className={`rounded-3xl bg-card border border-line animate-pulse ${className}`} />;
}

function Segment({ icon: Icon, tone, title, value, hint }) {
  const tones = {
    ok: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
    warn: 'bg-amber-500/10 text-amber-600 dark:text-amber-400',
    info: 'bg-brand/10 text-brand',
    neutral: 'bg-sky-500/10 text-sky-600 dark:text-sky-400',
  };
  return (
    <div className="flex items-center gap-3.5 p-4 sm:p-5 min-w-0">
      <div className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 ${tones[tone]}`}><Icon className="w-5 h-5" /></div>
      <div className="min-w-0">
        <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">{title}</span>
        <div className="text-xl font-black text-ink tabular-nums truncate">{value}</div>
        <span className="text-[11px] font-semibold text-ink-soft block truncate">{hint}</span>
      </div>
    </div>
  );
}

function MiniStat({ title, value, hint }) {
  return (
    <div className="p-3 sm:p-4 min-w-0">
      <span className="text-[9px] sm:text-[10px] font-extrabold uppercase tracking-wider text-ink-muted leading-tight">{title}</span>
      <div className="text-base sm:text-xl font-black text-ink tabular-nums mt-0.5 truncate">{value}</div>
      <span className="text-[10px] sm:text-[11px] font-semibold text-ink-soft leading-tight">{hint}</span>
    </div>
  );
}

/** Estadísticas globales de la plataforma: ventas de todos los locales, crecimiento, planes, usuarios y facturación. */
export default function StatsBento() {
  const [tab, setTab] = useState('stores');
  const { data: stats, loading, error: errorMsg } = useLoad(
    () => getBackofficeStatsOverviewApi(),
    [],
    { autoRefresh: true, errorMessage: 'No se pudieron cargar las estadísticas.' },
  );

  const k = stats?.kpis;
  const animatedAmount = useCountUp(Number(k?.salesAmountLast30Days) || 0);

  let body = null;
  if (loading) {
    body = (
      <div className="space-y-6">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <Skeleton className="lg:col-span-8 h-[28rem]" />
          <Skeleton className="lg:col-span-4 h-[28rem]" />
        </div>
        <Skeleton className="h-24" />
      </div>
    );
  } else if (stats) {
    const salesSeries = monthSeries(stats.salesByMonth, (p) => Number(p.amount));
    const bestMonth = salesSeries.reduce((best, p) => (p.value > (best?.value ?? 0) ? p : best), null);
    const signups = monthSeries(stats.newStoresByMonth, (p) => p.count);
    const invoiceTotal = stats.invoicesByStatus.reduce((sum, i) => sum + i.count, 0);
    const statusSegments = stats.storesByStatus.map((i) => ({ label: STATUS_LABELS[i.key] || i.key, value: i.count, color: STATUS_COLORS[i.key] || '#9ca3af' }));
    const statusTotal = statusSegments.reduce((sum, s) => sum + s.value, 0);

    const topStores = stats.topStoresBySales.map((s) => ({ label: s.name, value: Number(s.amount) }));
    const insights = {
      stores: <Podium entries={topStores} formatValue={(v) => shortMoney(v)} emptyLabel="Ningún local registró ventas en los últimos 30 días" />,
      plans: <Ranking entries={stats.storesByPlan.map((i) => ({ label: i.key, value: i.count }))} formatValue={(n) => `${n} ${n === 1 ? 'local' : 'locales'}`} emptyLabel="Aún no hay locales" />,
      users: <Ranking avatar entries={stats.usersByRole.map((i) => ({ label: ROLE_LABELS_PLURAL[i.key] || i.key, value: i.count }))} formatValue={formatNumber} emptyLabel="Aún no hay usuarios" />,
      invoices: <Ranking entries={stats.invoicesByStatus.map((i) => ({ label: INVOICE_LABELS[i.key] || i.key, value: i.count }))} formatValue={formatNumber} emptyLabel="Aún no se han emitido facturas" />,
    };
    const subtitles = {
      stores: 'Top 5 por importe en los últimos 30 días',
      plans: 'Distribución de los planes contratados',
      users: 'Usuarios activos de toda la plataforma',
      invoices: 'Estado de las facturas emitidas ante la DIAN',
    };

    body = (
      <>
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          <div className="lg:col-span-8 bento-card hover:transform-none p-6 sm:p-7 card-rise">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
              <div>
                <span className="text-[11px] font-extrabold uppercase tracking-wider text-ink-muted">Importe vendido · últimos 30 días</span>
                <div className="text-4xl sm:text-5xl font-black tracking-tight text-ink tabular-nums mt-1">{money(animatedAmount)}</div>
              </div>
              {bestMonth && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-brand/10 text-brand text-[11px] font-extrabold self-start">
                  Mejor mes: {bestMonth.label} · ${shortMoney(bestMonth.value)}
                </span>
              )}
            </div>
            <div className="mt-2">
              {salesSeries.every((p) => p.value === 0)
                ? <p className="text-xs text-ink-muted font-semibold py-16 text-center">Sin ventas en los últimos 12 meses</p>
                : <AreaChart data={salesSeries} formatValue={money} />}
            </div>
            <div className="mt-4 pt-3 border-t border-line grid grid-cols-3 divide-x divide-line">
              <MiniStat title="Ventas" value={formatNumber(k.salesLast30Days)} hint="en 30 días" />
              <MiniStat title="Locales con ventas" value={k.storesWithSalesLast30Days} hint={`de ${k.activeStores} activos`} />
              <MiniStat title="Facturas DIAN" value={formatNumber(invoiceTotal)} hint="emitidas en total" />
            </div>
          </div>

          <div className="lg:col-span-4 bento-card hover:transform-none p-6 card-rise" style={{ '--d': '80ms' }}>
            <h3 className="text-sm font-extrabold text-ink">Locales por estado</h3>
            <p className="text-[11px] text-ink-soft font-medium mb-5">Cuántos locales hay en cada etapa</p>
            {statusTotal === 0 ? (
              <p className="text-xs text-ink-muted font-semibold py-10 text-center">Aún no hay locales</p>
            ) : (
              <div className="flex flex-col items-center gap-5">
                <Donut segments={statusSegments}>
                  <span className="text-3xl font-black text-ink tabular-nums">{statusTotal}</span>
                  <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">locales</span>
                </Donut>
                <ul className="w-full space-y-2.5">
                  {statusSegments.map((s) => (
                    <li key={s.label} className="flex items-center gap-2.5 text-xs">
                      <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
                      <span className="font-bold text-ink flex-1 truncate">{s.label}</span>
                      <span className="font-black text-ink tabular-nums">{s.value}</span>
                      <span className="font-semibold text-ink-muted tabular-nums w-10 text-right">{Math.round((s.value / statusTotal) * 100)}%</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>

        <div className="bento-card hover:transform-none overflow-hidden grid grid-cols-1 md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-line card-rise" style={{ '--d': '120ms' }}>
          <Segment icon={CheckCircle2} tone="ok" title="Locales activos" value={`${k.activeStores} de ${k.totalStores}`} hint="Operando en la plataforma" />
          <Segment icon={UserPlus} tone="info" title="Nuevos · 30 días" value={k.newStoresLast30Days} hint={`${formatNumber(k.activeUsers)} usuarios activos en total`} />
          <Segment
            icon={k.storesAtUserLimit > 0 ? Gauge : Users}
            tone={k.storesAtUserLimit > 0 ? 'warn' : 'neutral'}
            title="En el tope de su plan"
            value={k.storesAtUserLimit}
            hint={k.storesAtUserLimit > 0 ? 'Con usuarios al máximo: oportunidad de upgrade' : 'Ningún local al máximo de usuarios'}
          />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          <div className="lg:col-span-7 bento-card hover:transform-none p-6 card-rise" style={{ '--d': '160ms' }}>
            <div className="flex flex-col gap-3 mb-5">
              <div>
                <h3 className="text-sm font-extrabold text-ink">Lo que más pesa en la plataforma</h3>
                <p className="text-[11px] text-ink-soft font-medium">{subtitles[tab]}</p>
              </div>
              <SegmentedControl options={TABS} value={tab} onChange={setTab} ariaLabel="Detalle de la plataforma" scrollable equal={false} className="sm:w-fit" />
            </div>
            {insights[tab]}
          </div>

          <div className="lg:col-span-5 bento-card hover:transform-none p-6 card-rise" style={{ '--d': '200ms' }}>
            <div className="flex items-center gap-3 mb-2">
              <div className="w-10 h-10 rounded-full bg-brand/10 flex items-center justify-center text-brand shrink-0"><Store className="w-5 h-5" /></div>
              <div>
                <h3 className="text-sm font-extrabold text-ink">Locales nuevos por mes</h3>
                <p className="text-[11px] text-ink-soft font-medium">Registros de los últimos 12 meses</p>
              </div>
            </div>
            {signups.every((p) => p.value === 0)
              ? <p className="text-xs text-ink-muted font-semibold py-16 text-center">Sin registros en los últimos 12 meses</p>
              : <AreaChart data={signups} formatValue={(n) => `${n} ${n === 1 ? 'local' : 'locales'}`} className="h-44" />}
          </div>
        </div>
      </>
    );
  }

  return (
    <div className="space-y-6 pb-12">
      <div>
        <h1 className="text-2xl font-black text-ink">Estadísticas</h1>
        <p className="text-xs text-ink-soft font-medium">Panorama general de la plataforma: locales, usuarios, ventas y facturación</p>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {body}
    </div>
  );
}
