import React, { useMemo, useState } from 'react';
import {
  ArrowDownCircle, ArrowUpCircle, BarChart3, CalendarClock, CreditCard, PiggyBank, RefreshCw, ShoppingBag, TrendingDown, TrendingUp,
} from 'lucide-react';
import useLoad from '../hooks/useLoad';
import { Button, DateRangeFilter, PageHeader } from '../components/ui';
import { LoadingRows, RankedList, SectionCard } from '../components/analytics/parts';
import DailyFlowChart from '../components/finance/DailyFlowChart';
import FixedCostModal from '../components/finance/FixedCostModal';
import FixedCostsSection from '../components/finance/FixedCostsSection';
import MovementModal from '../components/finance/MovementModal';
import MovementsSection from '../components/finance/MovementsSection';
import { getFinanceSummaryApi } from '../services/financeApi';
import { formatVariation } from '../utils/finance';
import { isoDaysAgo, money, paymentLabel } from '../utils/format';

/** Variación frente al período anterior; en los egresos subir es malo (`inverse`). */
function Variation({ pct, inverse = false }) {
  const { text, direction } = formatVariation(pct);
  const good = direction === 'flat' ? null : (direction === 'up') !== inverse;
  const tone = good === null ? 'text-ink-muted' : good ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400';
  return <span className={`text-[11px] font-extrabold ${tone}`}>{text} vs. período anterior</span>;
}

function StatCard({ icon: Icon, label, value, pct, inverse, loading, note, valueClass = 'text-ink' }) {
  return (
    <div className="bento-card p-5 space-y-2">
      <div className="flex items-center gap-2 text-[11px] font-extrabold uppercase text-ink-muted">
        <Icon className="w-4 h-4 text-brand" /> {label}
      </div>
      <div className={`text-2xl font-black tabular-nums ${valueClass}`}>{loading ? '…' : Number(value) < 0 ? `-${money(-value)}` : money(value)}</div>
      {!loading && <Variation pct={pct} inverse={inverse} />}
      {!loading && note && <p className="text-[11px] text-ink-soft font-medium">{note}</p>}
    </div>
  );
}

/** Los tres botones de alta, siempre a la vista: qué registrar y cuándo usar cada uno. */
function QuickActions({ onExpense, onIncome, onFixedCost }) {
  return (
    <div className="bento-card p-5 space-y-4">
      <div>
        <h3 className="text-sm font-extrabold text-ink">¿Qué quieres registrar?</h3>
        <p className="text-[11px] text-ink-soft font-medium">Las ventas, compras y abonos de fiado ya se cuentan solos; aquí solo va lo que haces a mano.</p>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="space-y-1.5">
          <Button variant="danger" icon={ArrowDownCircle} className="w-full" onClick={onExpense}>Registrar egreso</Button>
          <p className="text-[11px] text-ink-muted">Dinero que sale: servicios, transporte, reparaciones…</p>
        </div>
        <div className="space-y-1.5">
          <Button variant="brand" icon={ArrowUpCircle} className="w-full" onClick={onIncome}>Registrar ingreso</Button>
          <p className="text-[11px] text-ink-muted">Dinero que entra y no es una venta del POS.</p>
        </div>
        <div className="space-y-1.5">
          <Button variant="outline" icon={CalendarClock} className="w-full" onClick={onFixedCost}>Nuevo costo fijo</Button>
          <p className="text-[11px] text-ink-muted">Gasto mensual que se repite: arriendo, nómina, internet.</p>
        </div>
      </div>
    </div>
  );
}

/** Finanzas (base caja): lo que entró, lo que salió y la utilidad del período, con movimientos manuales y costos fijos. */
export default function FinanceBento() {
  const [dateFrom, setDateFrom] = useState(isoDaysAgo(29));
  const [dateTo, setDateTo] = useState(isoDaysAgo(0));
  // Diálogos: undefined = cerrado; `{ movement, type }` (movement null = nuevo) y `{ cost }` (cost null = nuevo).
  const [movementModal, setMovementModal] = useState(undefined);
  const [fixedCostModal, setFixedCostModal] = useState(undefined);
  // Sube cuando algo se guarda o se borra, para que las listas vuelvan a pedir sus datos.
  const [version, setVersion] = useState(0);

  const { data: summary, loading, error, reload } = useLoad(
    () => getFinanceSummaryApi(dateFrom, dateTo),
    [dateFrom, dateTo],
    { errorMessage: 'No se pudo cargar el estado financiero.' },
  );

  const changed = () => {
    setMovementModal(undefined);
    setFixedCostModal(undefined);
    setVersion((v) => v + 1);
    reload();
  };

  const incomeByMethod = useMemo(
    () => (summary?.incomeByPaymentMethod || []).map((m) => ({ label: paymentLabel(m.paymentMethod), value: Number(m.total) })),
    [summary],
  );
  const expensesByCategory = useMemo(
    () => (summary?.expensesByCategory || []).map((c) => ({ label: c.category, value: Number(c.total) })),
    [summary],
  );
  const outflowBreakdown = summary ? [
    { label: 'Compras a proveedores', value: Number(summary.purchases) },
    { label: 'Egresos manuales', value: Number(summary.expenses) },
    { label: 'Costos fijos (prorrateados)', value: Number(summary.fixedCosts) },
  ] : [];

  const profit = Number(summary?.profit || 0);
  const otherIncome = Number(summary?.otherIncome || 0);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Finanzas"
        subtitle="Estado financiero en base caja: lo que entró, lo que salió y la utilidad"
        actions={<Button size="sm" icon={RefreshCw} loading={loading} onClick={() => reload({ silent: false })}>Actualizar</Button>}
      />

      <QuickActions
        onExpense={() => setMovementModal({ movement: null, type: 'EXPENSE' })}
        onIncome={() => setMovementModal({ movement: null, type: 'INCOME' })}
        onFixedCost={() => setFixedCostModal({ cost: null })}
      />

      <div className="bento-card p-4">
        <DateRangeFilter from={dateFrom} to={dateTo} onChange={(from, to) => { setDateFrom(from); setDateTo(to); }} />
      </div>

      {error && <div className="p-3.5 rounded-2xl border border-red-200 bg-red-50 dark:bg-red-500/10 text-xs font-bold text-red-600">{error}</div>}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <StatCard
          icon={TrendingUp}
          label="Ingresos"
          value={summary?.income}
          pct={summary?.incomeVariationPct}
          loading={loading}
          note={otherIncome > 0 ? `Incluye ${money(otherIncome)} de ingresos manuales` : undefined}
        />
        <StatCard icon={TrendingDown} label="Egresos" value={summary?.totalOutflow} pct={summary?.outflowVariationPct} inverse loading={loading} />
        <StatCard
          icon={PiggyBank}
          label="Utilidad"
          value={summary?.profit}
          pct={summary?.profitVariationPct}
          loading={loading}
          valueClass={profit < 0 ? 'text-red-600' : 'text-emerald-600 dark:text-emerald-400'}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <MovementsSection
          dateFrom={dateFrom}
          dateTo={dateTo}
          version={version}
          onEdit={(movement) => setMovementModal({ movement, type: movement.type })}
          onChanged={changed}
        />
        <FixedCostsSection version={version} onEdit={(cost) => setFixedCostModal({ cost })} onChanged={changed} />
      </div>

      <SectionCard icon={BarChart3} title="Flujo diario" subtitle="Los costos fijos no tienen fecha: se descuentan del total, no de cada día">
        {loading || !summary ? <LoadingRows /> : <DailyFlowChart daily={summary.daily} />}
      </SectionCard>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <SectionCard icon={ShoppingBag} title="De dónde salió el dinero" subtitle={`Egresos: ${money(summary?.totalOutflow)}`}>
          {loading ? <LoadingRows /> : <RankedList entries={outflowBreakdown} emptyLabel="Sin egresos" />}
        </SectionCard>
        <SectionCard icon={CreditCard} title="Ingresos por método de pago" subtitle="Ventas, abonos de fiado e ingresos manuales, menos devoluciones">
          {loading ? <LoadingRows /> : <RankedList entries={incomeByMethod} emptyLabel="Sin ingresos en este período" />}
        </SectionCard>
        <SectionCard icon={ShoppingBag} title="Egresos manuales por categoría" subtitle="Solo los que registraste a mano">
          {loading ? <LoadingRows /> : <RankedList entries={expensesByCategory} emptyLabel="Sin egresos manuales en este período" />}
        </SectionCard>
      </div>

      {movementModal && (
        <MovementModal
          movement={movementModal.movement}
          initialType={movementModal.type}
          onClose={() => setMovementModal(undefined)}
          onSaved={changed}
        />
      )}
      {fixedCostModal && (
        <FixedCostModal cost={fixedCostModal.cost} onClose={() => setFixedCostModal(undefined)} onSaved={changed} />
      )}
    </div>
  );
}
