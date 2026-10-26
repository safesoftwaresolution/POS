import React, { useCallback, useEffect, useState } from 'react';
import { CalendarClock, Pencil, Plus, Trash2 } from 'lucide-react';
import { Badge, Button, inputClass } from '../ui';
import { useModal } from '../../context/ModalContext';
import { addProductLotApi, deleteProductLotApi, getProductLotsApi, updateProductLotApi } from '../../services/productsApi';
import { EXPIRY_WINDOW_DAYS, daysUntil, expiryLabel, expiryStatus, formatExpiryDate } from '../../utils/expiry';

const TONES = { expired: 'danger', today: 'danger', soon: 'warning', ok: 'neutral' };
const ICON_BTN = 'p-1.5 text-ink-muted rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer';

/**
 * Lotes de vencimiento de un producto (al editarlo): lo que queda de cada lote, corregir una fecha, quitar un lote y
 * ponerle fecha a unidades que entraron sin ella. Los lotes nuevos se crean al registrar una entrada de stock
 * (Inventario → Mover stock, o una compra): aquí solo se gestionan los que ya existen.
 */
export default function ProductLotsSection({ productId, onChanged }) {
  const { confirm, notify } = useModal();
  const [state, setState] = useState({ loading: true, data: null, error: '' });
  const [editing, setEditing] = useState(null); // { id, expirationDate, lotCode }
  const [assigning, setAssigning] = useState(null); // { quantity, expirationDate, lotCode }
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    try {
      const data = await getProductLotsApi(productId);
      setState({ loading: false, data, error: '' });
    } catch (e) {
      setState({ loading: false, data: null, error: e.message || 'No se pudieron cargar los lotes.' });
    }
  }, [productId]);

  // Carga inicial: el estado solo cambia cuando responde el servidor (nunca de forma síncrona dentro del efecto).
  useEffect(() => {
    let alive = true;
    getProductLotsApi(productId)
      .then((data) => { if (alive) setState({ loading: false, data, error: '' }); })
      .catch((e) => { if (alive) setState({ loading: false, data: null, error: e.message || 'No se pudieron cargar los lotes.' }); });
    return () => { alive = false; };
  }, [productId]);

  const run = async (action, failMessage) => {
    setSaving(true);
    try {
      await action();
      setEditing(null);
      setAssigning(null);
      await reload();
      onChanged?.();
    } catch (e) {
      notify(`${failMessage}: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  const saveEdit = () => run(
    () => updateProductLotApi(productId, editing.id, {
      expirationDate: editing.expirationDate,
      lotCode: editing.lotCode,
      // Solo se envían las unidades si cambiaron: sin ellas el backend las deja como están.
      quantity: newQuantity !== editing.current ? newQuantity : null,
    }),
    'No se pudo corregir el lote'
  );

  const saveAssign = () => run(
    () => addProductLotApi(productId, {
      quantity: Number(assigning.quantity),
      expirationDate: assigning.expirationDate,
      lotCode: assigning.lotCode,
    }),
    'No se pudo asignar el vencimiento'
  );

  const removeLot = async (lot) => {
    const ok = await confirm(
      `Se quita el registro del lote (${formatExpiryDate(lot.expirationDate)}). El stock no cambia: esas unidades pasan a "sin fecha". `
      + 'Si la mercancía venció, mejor dala de baja con una salida por Vencimiento en Inventario.',
      { title: 'Quitar lote', confirmText: 'Quitar', danger: true }
    );
    if (!ok) return;
    run(() => deleteProductLotApi(productId, lot.id), 'No se pudo quitar el lote');
  };

  const { loading, data, error } = state;
  const lots = data?.lots ?? [];
  const undated = data?.undatedQuantity ?? 0;
  // Cambiar las unidades de un lote mueve unidades entre el lote y las "sin fecha"; el stock del producto no cambia.
  const newQuantity = editing ? Number(editing.quantity) : 0;
  const quantityDiff = editing ? newQuantity - editing.current : 0;
  const exceedsUndated = quantityDiff > undated;
  const canSaveEdit = Boolean(editing?.expirationDate) && newQuantity >= 1 && !exceedsUndated && !saving;
  const assignQuantity = Number(assigning?.quantity);
  const canSaveAssign = Boolean(assigning?.expirationDate) && assignQuantity >= 1 && assignQuantity <= undated && !saving;

  return (
    <div className="space-y-2.5 rounded-2xl border border-line p-3.5">
      <div className="flex items-center gap-2">
        <CalendarClock className="w-4 h-4 text-brand shrink-0" />
        <span className="text-[11px] font-bold uppercase tracking-wider text-ink-soft">Vencimientos por lote</span>
      </div>

      {loading && <p className="text-[11px] text-ink-muted">Cargando lotes...</p>}
      {error && <p className="text-[11px] font-semibold text-red-500">{error}</p>}

      {!loading && !error && lots.length === 0 && undated === 0 && (
        <p className="text-[11px] text-ink-muted">
          Este producto no tiene vencimientos. Cuando entre mercancía que vence, indica la fecha en Inventario → Mover stock o en la compra.
        </p>
      )}

      {lots.map((lot) => {
        const days = daysUntil(lot.expirationDate);
        const status = expiryStatus(lot.expirationDate, EXPIRY_WINDOW_DAYS);
        return editing?.id === lot.id ? (
          <div key={lot.id} className="space-y-2 rounded-xl bg-page p-2.5">
            <div className="grid grid-cols-3 gap-2">
              <label className="space-y-1">
                <span className="block text-[10px] font-bold uppercase tracking-wider text-ink-muted">Unidades</span>
                <input
                  className={inputClass}
                  inputMode="numeric"
                  value={editing.quantity}
                  onChange={(e) => setEditing({ ...editing, quantity: e.target.value.replace(/\D/g, '').slice(0, 7) })}
                />
              </label>
              <label className="space-y-1">
                <span className="block text-[10px] font-bold uppercase tracking-wider text-ink-muted">Vencimiento</span>
                <input
                  type="date"
                  className={inputClass}
                  value={editing.expirationDate}
                  onChange={(e) => setEditing({ ...editing, expirationDate: e.target.value })}
                />
              </label>
              <label className="space-y-1">
                <span className="block text-[10px] font-bold uppercase tracking-wider text-ink-muted">Lote</span>
                <input
                  className={`${inputClass} font-mono`}
                  maxLength={50}
                  placeholder="Opcional"
                  value={editing.lotCode}
                  onChange={(e) => setEditing({ ...editing, lotCode: e.target.value })}
                />
              </label>
            </div>
            {newQuantity < 1 && (
              <p className="text-[11px] font-semibold text-amber-600 dark:text-amber-400">
                Las unidades deben ser al menos 1. Para quitar el lote usa el ícono de la papelera.
              </p>
            )}
            {newQuantity >= 1 && quantityDiff < 0 && (
              <p className="text-[11px] text-ink-muted">
                {-quantityDiff} {-quantityDiff === 1 ? 'unidad pasará' : 'unidades pasarán'} a &quot;sin fecha&quot;. El stock del producto no cambia.
              </p>
            )}
            {quantityDiff > 0 && !exceedsUndated && (
              <p className="text-[11px] text-ink-muted">
                Se toman {quantityDiff} de las {undated} unidades sin fecha. El stock del producto no cambia.
              </p>
            )}
            {exceedsUndated && (
              <p className="text-[11px] font-semibold text-amber-600 dark:text-amber-400">
                {undated === 0
                  ? 'No hay unidades sin fecha para ampliar este lote.'
                  : `Solo hay ${undated} unidades sin fecha para ampliar este lote.`}
                {' '}Sube primero el stock en Inventario (Entrada o Contar y ajustar).
              </p>
            )}
            <p className="text-[10px] text-ink-muted">
              Si el stock del producto también está mal, corrígelo en Inventario → Contar y ajustar.
            </p>
            <div className="flex gap-2">
              <Button size="sm" disabled={!canSaveEdit} onClick={saveEdit}>Guardar</Button>
              <Button size="sm" variant="soft" disabled={saving} onClick={() => setEditing(null)}>Cancelar</Button>
            </div>
          </div>
        ) : (
          <div key={lot.id} className="flex items-center justify-between gap-2 rounded-xl bg-page px-3 py-2">
            <div className="min-w-0 space-y-0.5">
              <p className="text-xs font-bold text-ink tabular-nums">
                {lot.remainingQuantity.toLocaleString('es-CO')} u.
                {lot.remainingQuantity !== lot.quantity && (
                  <span className="font-semibold text-ink-muted"> de {lot.quantity.toLocaleString('es-CO')}</span>
                )}
                {lot.lotCode && <span className="ml-2 font-mono font-semibold text-ink-soft">Lote {lot.lotCode}</span>}
              </p>
              <Badge tone={TONES[status]}>{formatExpiryDate(lot.expirationDate)} · {expiryLabel(days)}</Badge>
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <button
                type="button"
                title="Corregir unidades, fecha o código"
                className={`${ICON_BTN} hover:text-brand`}
                onClick={() => setEditing({
                  id: lot.id,
                  expirationDate: lot.expirationDate,
                  lotCode: lot.lotCode || '',
                  quantity: String(lot.remainingQuantity),
                  current: lot.remainingQuantity,
                })}
              >
                <Pencil className="w-4 h-4" />
              </button>
              <button
                type="button"
                title="Quitar lote"
                className={`${ICON_BTN} hover:text-red-600 dark:hover:text-red-400`}
                onClick={() => removeLot(lot)}
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        );
      })}

      {undated > 0 && !assigning && (
        <div className="flex items-center justify-between gap-2 rounded-xl border border-dashed border-line px-3 py-2">
          <p className="text-[11px] font-semibold text-ink-soft">
            {undated.toLocaleString('es-CO')} {undated === 1 ? 'unidad' : 'unidades'} sin fecha de vencimiento
          </p>
          <Button
            size="sm"
            variant="soft"
            icon={Plus}
            onClick={() => setAssigning({ quantity: String(undated), expirationDate: '', lotCode: '' })}
          >
            Asignar vencimiento
          </Button>
        </div>
      )}

      {assigning && (
        <div className="space-y-2 rounded-xl bg-page p-2.5">
          <p className="text-[11px] font-semibold text-ink-soft">
            ¿A cuántas de las {undated.toLocaleString('es-CO')} unidades sin fecha les pones vencimiento?
          </p>
          <div className="grid grid-cols-3 gap-2">
            <input
              className={inputClass}
              inputMode="numeric"
              aria-label="Unidades"
              value={assigning.quantity}
              onChange={(e) => setAssigning({ ...assigning, quantity: e.target.value.replace(/\D/g, '').slice(0, 7) })}
            />
            <input
              type="date"
              className={inputClass}
              aria-label="Fecha de vencimiento"
              value={assigning.expirationDate}
              onChange={(e) => setAssigning({ ...assigning, expirationDate: e.target.value })}
            />
            <input
              className={`${inputClass} font-mono`}
              maxLength={50}
              placeholder="Lote (opcional)"
              value={assigning.lotCode}
              onChange={(e) => setAssigning({ ...assigning, lotCode: e.target.value })}
            />
          </div>
          {assignQuantity > undated && (
            <p className="text-[11px] font-semibold text-amber-600 dark:text-amber-400">Solo hay {undated} unidades sin fecha.</p>
          )}
          <div className="flex gap-2">
            <Button size="sm" disabled={!canSaveAssign} onClick={saveAssign}>Guardar vencimiento</Button>
            <Button size="sm" variant="soft" disabled={saving} onClick={() => setAssigning(null)}>Cancelar</Button>
          </div>
        </div>
      )}

      {lots.length > 0 && (
        <p className="text-[10px] text-ink-muted">
          Lo que queda de cada lote es una estimación: se asume que se vende primero lo que vence primero.
        </p>
      )}
    </div>
  );
}
