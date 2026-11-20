import React, { useState } from 'react';
import { Button, Input, Modal } from '../ui';
import { createBranchApi } from '../../services/businessApi';
import { BILLING_MODE_HINTS } from '../../utils/business';

const EMPTY = { name: '', email: '', phone: '', address: '', taxId: '' };

/**
 * Alta de una sucursal por el dueño. Nace en revisión: la plataforma la aprueba como a cualquier local. Con NIT
 * compartido no se pide el NIT (se usa el de la sucursal principal); con NIT distinto, sí.
 */
export default function NewBranchModal({ business, onClose, onCreated }) {
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const sharedTaxId = business.billingMode === 'SHARED_TAX_ID';

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    if (!form.name.trim() || !form.email.trim()) {
      setError('El nombre y el correo de la sucursal son obligatorios.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const payload = {
        name: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim() || null,
        address: form.address.trim() || null,
        taxId: sharedTaxId ? null : (form.taxId.trim() || null),
      };
      const branch = await createBranchApi(payload);
      onCreated(branch);
    } catch (err) {
      setError(err?.message || 'No se pudo crear la sucursal.');
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Nueva sucursal"
      subtitle="Quedará en revisión hasta que la plataforma la apruebe."
      onClose={onClose}
      dismissible={!saving}
      footer={(
        <>
          <Button variant="soft" onClick={onClose} disabled={saving}>Cancelar</Button>
          <Button type="submit" form="new-branch-form" loading={saving}>Crear sucursal</Button>
        </>
      )}
    >
      <form id="new-branch-form" onSubmit={submit} className="space-y-4">
        <Input label="Nombre" value={form.name} onChange={set('name')} maxLength={100} autoFocus required />
        <Input label="Correo de la sucursal" type="email" value={form.email} onChange={set('email')} maxLength={100}
          hint="Debe ser distinto al de cualquier otro local de la plataforma." required />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Input label="Teléfono" value={form.phone} onChange={set('phone')} maxLength={20} />
          {sharedTaxId ? (
            <Input label="NIT" value="Igual al de la principal" disabled readOnly hint={BILLING_MODE_HINTS.SHARED_TAX_ID} />
          ) : (
            <Input label="NIT" value={form.taxId} onChange={set('taxId')} maxLength={50} />
          )}
        </div>
        <Input label="Dirección" value={form.address} onChange={set('address')} maxLength={255} />
        {error && <p className="text-xs font-bold text-red-500">{error}</p>}
      </form>
    </Modal>
  );
}
