import React, { useState } from 'react';
import { Button, Input, Modal, MoneyInput, SegmentedControl, Select } from '../ui';
import { createMovementApi, updateMovementApi } from '../../services/financeApi';
import { MOVEMENT_CATEGORIES, MOVEMENT_TYPES, movementFormError, movementPayload } from '../../utils/finance';
import { isoDaysAgo } from '../../utils/format';

const METHODS = [['CASH', 'Efectivo'], ['TRANSFER', 'Transferencia'], ['NEQUI', 'Nequi'], ['CARD', 'Tarjeta']];

const COPY = {
  EXPENSE: { title: 'Registrar egreso', hint: 'Dinero que sale: arriendo, servicios, transporte…', method: 'Pagado con' },
  INCOME: { title: 'Registrar ingreso', hint: 'Dinero que entra y no es una venta del POS (las ventas ya se cuentan solas)', method: 'Recibido por' },
};

/** Alta o edición de un egreso o un ingreso manual. `movement` null = nuevo; `initialType` es el botón con el que se abrió. */
export default function MovementModal({ movement, initialType = 'EXPENSE', onClose, onSaved }) {
  const today = isoDaysAgo(0);
  const [form, setForm] = useState({
    type: movement?.type || initialType,
    category: movement?.category || '',
    description: movement?.description || '',
    amount: movement ? String(Math.round(movement.amount)) : '',
    paymentMethod: movement?.paymentMethod || 'CASH',
    movementDate: movement?.movementDate || today,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const set = (field) => (value) => setForm((f) => ({ ...f, [field]: value }));
  const copy = COPY[form.type];

  const submit = async (e) => {
    e.preventDefault();
    const invalid = movementFormError(form, today);
    if (invalid) return setError(invalid);
    setSaving(true);
    setError('');
    try {
      const payload = movementPayload(form);
      await (movement ? updateMovementApi(movement.id, payload) : createMovementApi(payload));
      onSaved();
    } catch (err) {
      setError(err.message || 'No se pudo guardar el movimiento.');
      setSaving(false);
    }
  };

  return (
    <Modal
      title={movement ? 'Editar movimiento' : copy.title}
      subtitle={copy.hint}
      onClose={onClose}
      dismissible={false}
    >
      <form onSubmit={submit} className="space-y-4">
        <SegmentedControl options={MOVEMENT_TYPES} value={form.type} onChange={set('type')} ariaLabel="Tipo de movimiento" />
        <Input
          label="Categoría"
          list="movement-categories"
          value={form.category}
          onChange={(e) => set('category')(e.target.value)}
          maxLength={60}
          placeholder={form.type === 'EXPENSE' ? 'Servicios, Arriendo…' : 'Otros ingresos…'}
          autoFocus
        />
        <datalist id="movement-categories">
          {MOVEMENT_CATEGORIES[form.type].map((c) => <option key={c} value={c} />)}
        </datalist>
        <MoneyInput label="Monto" value={form.amount} onChange={set('amount')} placeholder="0" />
        <div className="grid grid-cols-2 gap-3">
          <Select label={copy.method} value={form.paymentMethod} onChange={(e) => set('paymentMethod')(e.target.value)}>
            {METHODS.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
          </Select>
          <Input
            label="Fecha"
            type="date"
            value={form.movementDate}
            max={today}
            hint="Cuenta en el período de esta fecha"
            onChange={(e) => set('movementDate')(e.target.value)}
          />
        </div>
        <Input
          label="Descripción (opcional)"
          value={form.description}
          onChange={(e) => set('description')(e.target.value)}
          maxLength={255}
        />
        {error && <p className="text-xs font-bold text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="soft" onClick={onClose} disabled={saving}>Cancelar</Button>
          <Button type="submit" variant={form.type === 'EXPENSE' ? 'danger' : 'brand'} loading={saving}>
            {movement ? 'Guardar cambios' : form.type === 'EXPENSE' ? 'Registrar egreso' : 'Registrar ingreso'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
