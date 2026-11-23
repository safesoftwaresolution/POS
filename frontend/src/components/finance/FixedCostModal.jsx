import React, { useState } from 'react';
import { Button, Input, Modal, MoneyInput } from '../ui';
import { createFixedCostApi, updateFixedCostApi } from '../../services/financeApi';
import { fixedCostFormError, fixedCostPayload } from '../../utils/finance';

/** Alta o edición de un costo fijo mensual. `cost` null = costo nuevo. */
export default function FixedCostModal({ cost, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: cost?.name || '',
    monthlyAmount: cost ? String(Math.round(cost.monthlyAmount)) : '',
    dueDay: cost?.dueDay ? String(cost.dueDay) : '',
    active: cost?.active ?? true,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    const invalid = fixedCostFormError(form);
    if (invalid) return setError(invalid);
    setSaving(true);
    setError('');
    try {
      const payload = fixedCostPayload(form);
      await (cost ? updateFixedCostApi(cost.id, payload) : createFixedCostApi(payload));
      onSaved();
    } catch (err) {
      setError(err.message || 'No se pudo guardar el costo fijo.');
      setSaving(false);
    }
  };

  return (
    <Modal
      title={cost ? 'Editar costo fijo' : 'Nuevo costo fijo'}
      subtitle="Un gasto que se repite cada mes: arriendo, nómina, internet…"
      onClose={onClose}
      dismissible={false}
    >
      <form onSubmit={submit} className="space-y-4">
        <Input
          label="Nombre"
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          maxLength={100}
          placeholder="Arriendo, nómina, internet…"
          autoFocus
        />
        <MoneyInput
          label="Valor mensual"
          hint="Se reparte en 30 días y se resta de la utilidad de cada período"
          value={form.monthlyAmount}
          onChange={(v) => setForm((f) => ({ ...f, monthlyAmount: v }))}
          placeholder="0"
        />
        <Input
          label="Día del mes en que se paga (opcional)"
          type="number"
          min={1}
          max={31}
          inputMode="numeric"
          value={form.dueDay}
          onChange={(e) => setForm((f) => ({ ...f, dueDay: e.target.value }))}
          hint="Te recordamos cuánto falta. En un mes corto vale el último día (31 → 28 o 30)"
          placeholder="Ej. 5"
        />
        <label className="flex items-center gap-2 text-xs font-bold text-ink cursor-pointer">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
          />
          Activo (cuenta en la utilidad)
        </label>
        {error && <p className="text-xs font-bold text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="soft" onClick={onClose} disabled={saving}>Cancelar</Button>
          <Button type="submit" loading={saving}>{cost ? 'Guardar cambios' : 'Crear costo fijo'}</Button>
        </div>
      </form>
    </Modal>
  );
}
