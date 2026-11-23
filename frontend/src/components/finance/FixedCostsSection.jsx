import React from 'react';
import { CalendarClock, Info, Landmark, Pencil, Trash2 } from 'lucide-react';
import useLoad from '../../hooks/useLoad';
import useModal from '../../hooks/useModal';
import { Badge, EmptyState } from '../ui';
import { LoadingRows, SectionCard } from '../analytics/parts';
import { deleteFixedCostApi, getFixedCostsApi } from '../../services/financeApi';
import { dueLabel, nextDueDate } from '../../utils/finance';
import { formatLongDate, money } from '../../utils/format';

/** Vence pronto: en 5 días o menos (hoy incluido). */
const SOON_DAYS = 5;

function DueInfo({ cost }) {
  const due = cost.active ? nextDueDate(cost.dueDay) : null;
  if (!cost.dueDay) return <span>Sin día de cobro</span>;
  if (!due) return <span>Se paga el día {cost.dueDay}</span>;
  return (
    <span className="inline-flex items-center gap-1.5 flex-wrap">
      <CalendarClock className="w-3 h-3" />
      Se paga el día {cost.dueDay} · próximo {formatLongDate(`${due.date}T00:00:00`)}
      <Badge tone={due.daysLeft <= SOON_DAYS ? 'warning' : 'neutral'}>{dueLabel(due.daysLeft)}</Badge>
    </span>
  );
}

/**
 * Costos fijos mensuales (arriendo, nómina…): se prorratean por día en la utilidad. El alta vive en el botón de
 * arriba de la página; `version` cambia cuando algo se guardó, para volver a pedir la lista.
 */
export default function FixedCostsSection({ version, onEdit, onChanged }) {
  const { confirm, notify } = useModal();

  const { data, loading, error } = useLoad(
    () => getFixedCostsApi(),
    [version],
    { initial: [], errorMessage: 'No se pudieron cargar los costos fijos.' },
  );

  const remove = async (cost) => {
    const ok = await confirm(`Se eliminará el costo fijo "${cost.name}". Para conservarlo sin que cuente, desactívalo en su lugar.`,
      { title: 'Eliminar costo fijo', confirmText: 'Eliminar' });
    if (!ok) return;
    try {
      await deleteFixedCostApi(cost.id);
      onChanged();
    } catch (err) {
      notify(err.message || 'No se pudo eliminar el costo fijo.');
    }
  };

  const activeMonthly = data.filter((c) => c.active).reduce((sum, c) => sum + Number(c.monthlyAmount), 0);

  return (
    <SectionCard icon={Landmark} title="Costos fijos" subtitle={`Activos: ${money(activeMonthly)} al mes`}>
      <div className="mb-4 p-3 rounded-2xl bg-page border border-line flex gap-2 text-[11px] text-ink-soft font-medium">
        <Info className="w-4 h-4 shrink-0 text-brand" />
        <p>
          Un costo fijo es un gasto que se repite cada mes. No lo registres de nuevo como egreso: la utilidad ya
          descuenta su valor repartido por día (mensual ÷ 30). El día de cobro es solo un recordatorio.
        </p>
      </div>
      {error && <p className="text-xs font-bold text-red-600 mb-3">{error}</p>}
      {loading ? <LoadingRows /> : data.length === 0 ? (
        <EmptyState
          icon={Landmark}
          title="Sin costos fijos"
          description="Usa «Nuevo costo fijo» arriba para agregar arriendo, nómina o servicios."
        />
      ) : (
        <div className="divide-y divide-line">
          {data.map((c) => (
            <div key={c.id} className="py-3 flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="text-xs font-extrabold text-ink truncate">{c.name}</p>
                <p className="text-[11px] text-ink-muted font-medium tabular-nums">{money(c.monthlyAmount)} / mes</p>
                <p className="text-[11px] text-ink-muted font-medium mt-0.5"><DueInfo cost={c} /></p>
              </div>
              <Badge tone={c.active ? 'success' : 'neutral'}>{c.active ? 'Activo' : 'Inactivo'}</Badge>
              <button onClick={() => onEdit(c)} aria-label="Editar costo fijo" className="p-1.5 rounded-xl text-ink-muted hover:text-ink hover:bg-gray-100 dark:hover:bg-white/5 cursor-pointer">
                <Pencil className="w-3.5 h-3.5" />
              </button>
              <button onClick={() => remove(c)} aria-label="Eliminar costo fijo" className="p-1.5 rounded-xl text-ink-muted hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10 cursor-pointer">
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}
    </SectionCard>
  );
}
