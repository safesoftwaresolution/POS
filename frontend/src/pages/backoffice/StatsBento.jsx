import React, { useEffect, useState } from 'react';
import {
  Store, Users, Receipt, TrendingUp, UserPlus, ShoppingCart, Gauge, AlertCircle,
  PieChart, BarChart3, FileText,
} from 'lucide-react';
import { getBackofficeStatsOverviewApi } from '../../services/backoffice/statsApi';
import useAutoRefresh from '../../hooks/useAutoRefresh';

const money = (n) => `$${Math.round(Number(n) || 0).toLocaleString('es-CO')}`;
// Importes grandes en formato corto para las etiquetas de las barras (1,2 M).
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
const ROLE_LABELS = { SUPER_ADMIN: 'Super admin', ADMINISTRATOR: 'Administradores', SUPERVISOR: 'Supervisores', SELLER: 'Vendedores' };
const INVOICE_LABELS = { VALIDATED: 'Validadas', PENDING: 'Pendientes', REJECTED: 'Rechazadas', ERROR: 'Con error' };

// Un color por categoría; los estados con significado (activo/rechazado) conservan verde/rojo.
const STATUS_COLORS = {
  ACTIVE: '#10b981',
  PENDING_VERIFICATION: '#f59e0b',
  REJECTED: '#ef4444',
  SUSPENDED: '#f97316',
  INACTIVE: '#9ca3af',
};

const MONTH_NAMES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const monthLabel = (yyyyMm) => MONTH_NAMES[Number(yyyyMm.slice(5, 7)) - 1];

function SectionCard({ icon: Icon, title, subtitle, children }) {
  return (
    <div className="bento-card p-6 bg-card rounded-3xl border border-line shadow-xs">
      <div className="flex items-start gap-2.5 mb-5">
        <div className="w-8 h-8 rounded-xl bg-gray-100 dark:bg-white/10 flex items-center justify-center shrink-0">
          <Icon className="w-4 h-4 text-ink-soft" />
        </div>
        <div className="min-w-0">
          <h2 className="text-sm font-black text-ink leading-tight">{title}</h2>
          {subtitle && <p className="text-[11px] text-ink-muted font-semibold">{subtitle}</p>}
        </div>
      </div>
      {children}
    </div>
  );
}

function KpiCard({ label, value, hint, icon: Icon, accent }) {
  return (
    <div className="bento-card p-4 flex items-center gap-3">
      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${accent}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <p className="text-lg font-black text-ink leading-tight tabular-nums truncate">{value}</p>
        <p className="text-[10px] font-bold text-ink-soft truncate">{label}</p>
        {hint && <p className="text-[10px] font-semibold text-ink-muted truncate">{hint}</p>}
      </div>
    </div>
  );
}

const Empty = ({ children = 'Sin datos todavía' }) => (
  <p className="text-xs text-ink-muted font-semibold py-8 text-center">{children}</p>
);

/** Dona de una sola medida (cantidad por categoría) con leyenda directa al lado. */
function Donut({ items, colorOf }) {
  const total = items.reduce((s, i) => s + i.value, 0);
  if (total === 0) return <Empty />;

  const radius = 40;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;

  return (
    <div className="flex flex-col sm:flex-row items-center gap-6">
      <svg viewBox="0 0 100 100" className="w-40 h-40 shrink-0 -rotate-90" role="img"
        aria-label={`Total ${total}: ${items.map(i => `${i.label} ${i.value}`).join(', ')}`}>
        <circle cx="50" cy="50" r={radius} fill="none" strokeWidth="14" className="stroke-gray-100 dark:stroke-white/10" />
        {items.map((i) => {
          const length = (i.value / total) * circumference;
          const circle = (
            <circle key={i.label} cx="50" cy="50" r={radius} fill="none" strokeWidth="14"
              stroke={colorOf(i)} strokeDasharray={`${length} ${circumference - length}`} strokeDashoffset={-offset}>
              <title>{`${i.label}: ${i.value}`}</title>
            </circle>
          );
          offset += length;
          return circle;
        })}
        <text x="50" y="50" textAnchor="middle" dominantBaseline="central" transform="rotate(90 50 50)"
          className="fill-current text-ink" style={{ fontSize: 16, fontWeight: 900 }}>{total}</text>
      </svg>
      <ul className="space-y-2 w-full">
        {items.map((i) => (
          <li key={i.label} className="flex items-center justify-between gap-3 text-xs">
            <span className="flex items-center gap-2 font-bold text-ink min-w-0">
              <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: colorOf(i) }} />
              <span className="truncate">{i.label}</span>
            </span>
            <span className="font-extrabold text-ink tabular-nums shrink-0">
              {i.value} <span className="text-ink-muted font-semibold">({Math.round((i.value / total) * 100)}%)</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Columnas de una sola serie (una por mes) con el valor sobre cada barra. */
function ColumnChart({ points, formatValue, ariaLabel }) {
  const max = Math.max(...points.map(p => p.value), 0);
  if (max === 0) return <Empty>Sin datos en los últimos 12 meses</Empty>;

  const width = 480;
  const height = 170;
  const top = 20;
  const bottom = 22;
  const slot = width / points.length;
  const barWidth = slot * 0.62;

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-auto" role="img" aria-label={ariaLabel}>
      <line x1="0" x2={width} y1={height - bottom} y2={height - bottom} className="stroke-gray-200 dark:stroke-white/10" />
      {points.map((p, idx) => {
        const barHeight = Math.max(((p.value / max) * (height - top - bottom)), p.value > 0 ? 3 : 0);
        const x = idx * slot + (slot - barWidth) / 2;
        const y = height - bottom - barHeight;
        return (
          <g key={p.month}>
            <rect x={x} y={y} width={barWidth} height={barHeight} rx="4" className="fill-brand">
              <title>{`${p.month}: ${formatValue(p.value)}`}</title>
            </rect>
            {p.value > 0 && (
              <text x={x + barWidth / 2} y={y - 5} textAnchor="middle" className="fill-current text-ink"
                style={{ fontSize: 10, fontWeight: 800 }}>{formatValue(p.value)}</text>
            )}
            <text x={x + barWidth / 2} y={height - 6} textAnchor="middle" className="fill-current text-ink-muted"
              style={{ fontSize: 10, fontWeight: 700 }}>{monthLabel(p.month)}</text>
          </g>
        );
      })}
    </svg>
  );
}

/** Barras horizontales de una sola serie, con etiqueta y valor directos. */
function BarList({ items, formatValue = (n) => n.toLocaleString('es-CO'), emptyLabel }) {
  if (!items.length) return <Empty>{emptyLabel}</Empty>;
  const max = Math.max(...items.map(i => i.value), 1);
  return (
    <div className="space-y-3.5">
      {items.map((i) => (
        <div key={i.label} className="space-y-1" title={`${i.label}: ${formatValue(i.value)}`}>
          <div className="flex items-center justify-between text-xs">
            <span className="font-bold text-ink truncate pr-2">{i.label}</span>
            <span className="font-extrabold text-ink tabular-nums shrink-0">{formatValue(i.value)}</span>
          </div>
          <div className="h-2.5 w-full bg-[#F5F5F3] dark:bg-line rounded-full overflow-hidden">
            <div className="h-full bg-solid dark:bg-brand rounded-full transition-all duration-500"
              style={{ width: `${Math.max((i.value / max) * 100, i.value > 0 ? 4 : 0)}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function StatsBento() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  const load = async ({ silent = false } = {}) => {
    try {
      setStats(await getBackofficeStatsOverviewApi());
      setErrorMsg('');
    } catch (e) {
      if (!silent) setErrorMsg(e.message || 'No se pudieron cargar las estadísticas.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);
  useAutoRefresh(() => load({ silent: true }));

  const k = stats?.kpis;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Estadísticas</h1>
        <p className="text-xs text-ink-soft font-medium">Panorama general de la plataforma: locales, usuarios, ventas y facturación</p>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {loading ? (
        <div className="py-16 text-center text-xs font-bold text-ink-muted">Cargando estadísticas...</div>
      ) : !stats ? null : (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <KpiCard label="Locales totales" value={k.totalStores} hint={`${k.activeStores} activos`} icon={Store}
              accent="bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300" />
            <KpiCard label="Usuarios activos" value={k.activeUsers} icon={Users}
              accent="bg-sky-100 dark:bg-sky-500/20 text-sky-700 dark:text-sky-400" />
            <KpiCard label="Locales nuevos (30 días)" value={k.newStoresLast30Days} icon={UserPlus}
              accent="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" />
            <KpiCard label="Locales en el tope de su plan" value={k.storesAtUserLimit} icon={Gauge}
              hint="Usuarios al máximo permitido"
              accent="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400" />
            <KpiCard label="Ventas (30 días)" value={k.salesLast30Days.toLocaleString('es-CO')} icon={ShoppingCart}
              accent="bg-violet-100 dark:bg-violet-500/20 text-violet-700 dark:text-violet-300" />
            <KpiCard label="Importe vendido (30 días)" value={money(k.salesAmountLast30Days)} icon={TrendingUp}
              accent="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" />
            <KpiCard label="Locales con ventas (30 días)" value={k.storesWithSalesLast30Days}
              hint={`de ${k.activeStores} activos`} icon={Store}
              accent="bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300" />
            <KpiCard label="Facturas electrónicas"
              value={stats.invoicesByStatus.reduce((s, i) => s + i.count, 0).toLocaleString('es-CO')} icon={Receipt}
              accent="bg-sky-100 dark:bg-sky-500/20 text-sky-700 dark:text-sky-400" />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <SectionCard icon={PieChart} title="Locales por estado" subtitle="Cuántos locales hay en cada etapa">
              <Donut
                items={stats.storesByStatus.map(i => ({ key: i.key, label: STATUS_LABELS[i.key] || i.key, value: i.count }))}
                colorOf={(i) => STATUS_COLORS[i.key] || '#9ca3af'}
              />
            </SectionCard>

            <SectionCard icon={BarChart3} title="Locales por plan" subtitle="Distribución de los planes contratados">
              <BarList
                items={stats.storesByPlan.map(i => ({ label: i.key, value: i.count }))}
                formatValue={(n) => `${n} ${n === 1 ? 'local' : 'locales'}`}
                emptyLabel="Aún no hay locales"
              />
            </SectionCard>

            <SectionCard icon={UserPlus} title="Locales nuevos por mes" subtitle="Registros de los últimos 12 meses">
              <ColumnChart
                points={stats.newStoresByMonth.map(p => ({ month: p.month, value: p.count }))}
                formatValue={(n) => String(n)}
                ariaLabel="Locales nuevos por mes en los últimos 12 meses"
              />
            </SectionCard>

            <SectionCard icon={TrendingUp} title="Ventas por mes" subtitle="Importe vendido por todos los locales, 12 meses">
              <ColumnChart
                points={stats.salesByMonth.map(p => ({ month: p.month, value: Number(p.amount) }))}
                formatValue={shortMoney}
                ariaLabel="Importe vendido por mes en los últimos 12 meses"
              />
            </SectionCard>

            <SectionCard icon={Users} title="Usuarios por rol" subtitle="Usuarios activos de toda la plataforma">
              <BarList
                items={stats.usersByRole.map(i => ({ label: ROLE_LABELS[i.key] || i.key, value: i.count }))}
                emptyLabel="Aún no hay usuarios"
              />
            </SectionCard>

            <SectionCard icon={FileText} title="Facturación electrónica" subtitle="Estado de las facturas emitidas ante la DIAN">
              <BarList
                items={stats.invoicesByStatus.map(i => ({ label: INVOICE_LABELS[i.key] || i.key, value: i.count }))}
                emptyLabel="Aún no se han emitido facturas"
              />
            </SectionCard>
          </div>

          <SectionCard icon={Store} title="Locales con más ventas" subtitle="Top 5 por importe en los últimos 30 días">
            <BarList
              items={stats.topStoresBySales.map(s => ({ label: `${s.name} · ${s.sales} ventas`, value: Number(s.amount) }))}
              formatValue={money}
              emptyLabel="Ningún local registró ventas en los últimos 30 días"
            />
          </SectionCard>
        </>
      )}
    </div>
  );
}
