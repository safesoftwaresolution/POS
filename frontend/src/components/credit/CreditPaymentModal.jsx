import React, { useState } from 'react';
import { Button, Field, Input, Modal, MoneyInput, Select } from '../ui';
import { getParameterValuesApi } from '../../services/parametersApi';
import { registerCreditPaymentApi } from '../../services/creditApi';
import useLoad from '../../hooks/useLoad';
import asList from '../../utils/asList';
import { money } from '../../utils/format';

/** Registra un abono a la deuda de un cliente. No puede superar lo que debe ni pagarse "con fiado". */
export default function CreditPaymentModal({ account, onClose, onSaved }) {
  const balance = Number(account.balance || 0);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('CASH');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const { data: methods } = useLoad(
    async () => (await asList(getParameterValuesApi('PAYMENT_METHODS')).catch(() => []))
      .filter((m) => m.active && m.code !== 'CREDIT'),
    [],
    { initial: [] },
  );

  const value = Number(amount) || 0;
  const invalid = value <= 0 || value > balance;

  const submit = async (e) => {
    e.preventDefault();
    if (invalid) return;
    setSaving(true);
    setError('');
    try {
      const movement = await registerCreditPaymentApi(account.customerId, { amount: value, paymentMethod: method, note: note.trim() });
      onSaved(movement);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Registrar abono"
      subtitle={`${account.customerName} debe ${money(balance)}`}
      onClose={onClose}
      dismissible={false}
      footer={(
        <>
          <Button variant="soft" onClick={onClose} disabled={saving}>Cancelar</Button>
          <Button type="submit" form="credit-payment-form" disabled={invalid} loading={saving}>Registrar abono</Button>
        </>
      )}
    >
      <form id="credit-payment-form" onSubmit={submit} className="space-y-4">
        <Field label="Monto del abono" error={value > balance ? 'No puede superar lo que debe.' : undefined}>
          <MoneyInput autoFocus value={amount} onChange={setAmount} placeholder="0" />
        </Field>
        <div className="flex flex-wrap gap-1.5">
          <Button variant="outline" size="sm" onClick={() => setAmount(String(Math.round(balance)))}>Pagar todo ({money(balance)})</Button>
        </div>
        <Select label="Método de pago" value={method} onChange={(e) => setMethod(e.target.value)}>
          {methods.length === 0 && <option value="CASH">Efectivo</option>}
          {methods.map((m) => <option key={m.code} value={m.code}>{m.label}</option>)}
        </Select>
        <Input label="Nota (opcional)" value={note} maxLength={255} onChange={(e) => setNote(e.target.value)} placeholder="Ej. Pagó el viernes" />
        {error && <p className="text-[11px] font-semibold text-red-500">{error}</p>}
      </form>
    </Modal>
  );
}
