import React, { useState } from 'react';
import { ArrowDownCircle, ArrowUpCircle, Pencil, Receipt, Trash2 } from 'lucide-react';
import useLoad from '../../hooks/useLoad';
import useModal from '../../hooks/useModal';
import { EmptyState } from '../ui';
import Pager from '../ui/Pager';
import { LoadingRows, SectionCard } from '../analytics/parts';
import { deleteMovementApi, getMovementsApi } from '../../services/financeApi';
import { money, paymentLabel } from '../../utils/format';

const EMPTY_PAGE = { content: [], totalPages: 0, totalElements: 0 };

/**
 * Egresos e ingresos manuales del período, con edición y baja. El alta vive en los botones de arriba de la página;
 * `version` cambia cuando algo se guardó, para volver a pedir la lista.
 */
export default function MovementsSection({ dateFrom, dateTo, version, onEdit, onChanged }) {
  const { confirm, notify } = useModal();
  const [page, setPage] = useState(0);

  // Al cambiar el rango se vuelve a la primera página (ajuste durante el render, no en un efecto).
  const [prevRange, setPrevRange] = useState(`${dateFrom}|${dateTo}`);
  if (prevRange !== `${dateFrom}|${dateTo}`) {
    setPrevRange(`${dateFrom}|${dateTo}`);
    setPage(0);
  }

  const { data, loading, error } = useLoad(
    () => getMovementsApi({ dateFrom, dateTo, page, size: 10 }),
    [dateFrom, dateTo, page, version],
    { initial: EMPTY_PAGE, errorMessage: 'No se pudieron cargar los movimientos.' },
  );

  const remove = async (m) => {
    const label = m.type === 'INCOME' ? 'ingreso' : 'egreso';
    const ok = await confirm(`Se eliminará el ${label} de ${money(m.amount)} (${m.category}). Esta acción no se puede deshacer.`,
      { title: `Eliminar ${label}`, confirmText: 'Eliminar' });
    if (!ok) return;
    try {
      await deleteMovementApi(m.id);
      onChanged();
    } catch (err) {
      notify(err.message || 'No se pudo eliminar el movimiento.');
    }
  };

  return (
    <SectionCard
      icon={Receipt}
      title="Egresos e ingresos manuales"
      subtitle="Lo que registraste a mano en este período"
    >
      {error && <p className="text-xs font-bold text-red-600 mb-3">{error}</p>}
      {loading ? <LoadingRows /> : data.content.length === 0 ? (
        <EmptyState
          icon={Receipt}
          title="Sin movimientos en este período"
          description="Usa «Registrar egreso» o «Registrar ingreso» arriba para agregar el primero."
        />
      ) : (
        <div className="divide-y divide-line">
          {data.content.map((m) => {
            const income = m.type === 'INCOME';
            const Icon = income ? ArrowUpCircle : ArrowDownCircle;
            return (
              <div key={m.id} className="py-3 flex items-center gap-3">
                <Icon className={`w-4 h-4 shrink-0 ${income ? 'text-emerald-500' : 'text-red-500'}`} aria-label={income ? 'Ingreso' : 'Egreso'} />
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-extrabold text-ink truncate">{m.category}{m.description ? ` · ${m.description}` : ''}</p>
                  <p className="text-[11px] text-ink-muted font-medium">{m.movementDate} · {paymentLabel(m.paymentMethod)}{m.username ? ` · ${m.username}` : ''}</p>
                </div>
                <span className={`text-xs font-black tabular-nums shrink-0 ${income ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600'}`}>
                  {income ? '+' : '-'}{money(m.amount)}
                </span>
                <button onClick={() => onEdit(m)} aria-label="Editar movimiento" className="p-1.5 rounded-xl text-ink-muted hover:text-ink hover:bg-gray-100 dark:hover:bg-white/5 cursor-pointer">
                  <Pencil className="w-3.5 h-3.5" />
                </button>
                <button onClick={() => remove(m)} aria-label="Eliminar movimiento" className="p-1.5 rounded-xl text-ink-muted hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10 cursor-pointer">
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            );
          })}
        </div>
      )}
      <Pager page={page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
    </SectionCard>
  );
}
