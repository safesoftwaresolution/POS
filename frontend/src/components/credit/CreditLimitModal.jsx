import React, { useState } from 'react';
import { Button, Modal, MoneyInput } from '../ui';
import { setCreditLimitApi } from '../../services/creditApi';
import { money } from '../../utils/format';

/** Fija o quita el cupo máximo de deuda de un cliente (administrador y supervisor). */
export default function CreditLimitModal({ account, onClose, onSaved }) {
  const [limit, setLimit] = useState(account.creditLimit != null ? String(Math.round(account.creditLimit)) : '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      onSaved(await setCreditLimitApi(account.customerId, limit === '' ? null : Number(limit)));
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Cupo de fiado"
      subtitle={`${account.customerName} debe ${money(account.balance)}`}
      onClose={onClose}
      dismissible={false}
      footer={(
        <>
          <Button variant="soft" onClick={onClose} disabled={saving}>Cancelar</Button>
          <Button type="submit" form="credit-limit-form" loading={saving}>Guardar</Button>
        </>
      )}
    >
      <form id="credit-limit-form" onSubmit={submit} className="space-y-4">
        <MoneyInput
          label="Cupo máximo de deuda"
          hint="Déjalo vacío para no poner límite. Bajarlo por debajo de lo que ya debe solo impide fiarle más."
          autoFocus
          value={limit}
          onChange={setLimit}
          placeholder="Sin límite"
        />
        {error && <p className="text-[11px] font-semibold text-red-500">{error}</p>}
      </form>
    </Modal>
  );
}
