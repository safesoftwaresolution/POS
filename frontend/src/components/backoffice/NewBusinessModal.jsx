import React, { useState } from 'react';
import useLoad from '../../hooks/useLoad';
import { Button, Input, Modal, Select } from '../ui';
import { getBackofficeStoresApi } from '../../services/backoffice/storesApi';
import { getUserDirectoryApi } from '../../services/backoffice/usersDirectoryApi';
import { createBusinessApi } from '../../services/backoffice/businessesApi';
import { BILLING_MODE_HINTS, BILLING_MODE_LABELS } from '../../utils/business';

const BILLING_MODES = Object.keys(BILLING_MODE_LABELS);

/**
 * Activa varias sucursales para un dueño: se parte de un local existente (la sucursal principal) cuyo administrador
 * pasa a ser el dueño del negocio, y se eligen los dos modos. El catálogo compartido llega en una fase posterior.
 */
export default function NewBusinessModal({ onClose, onCreated }) {
  const [form, setForm] = useState({ name: '', mainStoreId: '', billingMode: 'SEPARATE_TAX_ID', maxBranches: '3' });
  const [ownerId, setOwnerId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const { data: stores } = useLoad(() => getBackofficeStoresApi('', '', 0, 100), [], { initial: { content: [] } });
  const { data: admins, loading: loadingAdmins } = useLoad(
    () => getUserDirectoryApi({ role: 'ADMINISTRATOR', storeId: form.mainStoreId, size: 50 }),
    [form.mainStoreId],
    { initial: { content: [] }, enabled: Boolean(form.mainStoreId) },
  );

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  const candidates = stores.content.filter((s) => s.status !== 'REJECTED');
  const owners = form.mainStoreId ? admins.content.filter((u) => u.active) : [];
  // Si solo hay un administrador activo, es el dueño; si hay varios, se elige.
  const effectiveOwnerId = owners.length === 1 ? String(owners[0].id) : ownerId;

  const submit = async (e) => {
    e.preventDefault();
    if (!form.name.trim() || !form.mainStoreId || !effectiveOwnerId) {
      setError('Indica el nombre, la sucursal principal y su dueño.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const business = await createBusinessApi({
        name: form.name.trim(),
        ownerUserId: Number(effectiveOwnerId),
        mainStoreId: Number(form.mainStoreId),
        billingMode: form.billingMode,
        catalogMode: 'INDEPENDENT',
        maxBranches: Number(form.maxBranches),
      });
      onCreated(business);
    } catch (err) {
      setError(err?.message || 'No se pudo crear el negocio.');
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Nuevo negocio"
      subtitle="Agrupa varias sucursales bajo un mismo dueño."
      onClose={onClose}
      dismissible={!saving}
      footer={(
        <>
          <Button variant="soft" onClick={onClose} disabled={saving}>Cancelar</Button>
          <Button type="submit" form="new-business-form" loading={saving}>Crear negocio</Button>
        </>
      )}
    >
      <form id="new-business-form" onSubmit={submit} className="space-y-4">
        <Input label="Nombre del negocio" value={form.name} onChange={set('name')} maxLength={100} autoFocus required />

        <Select label="Sucursal principal" value={form.mainStoreId} onChange={(e) => { set('mainStoreId')(e); setOwnerId(''); }}
          hint="Un local que ya existe. Su administrador será el dueño del negocio." required>
          <option value="">Selecciona un local…</option>
          {candidates.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </Select>

        {form.mainStoreId && (owners.length > 1 ? (
          <Select label="Dueño" value={ownerId} onChange={(e) => setOwnerId(e.target.value)} required>
            <option value="">Selecciona al administrador…</option>
            {owners.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>)}
          </Select>
        ) : (
          <p className="text-xs font-semibold text-ink-soft">
            {loadingAdmins ? 'Buscando al administrador…'
              : owners.length === 1 ? `Dueño: ${owners[0].fullName} (${owners[0].username})`
                : 'Ese local no tiene un administrador activo; asígnale uno antes.'}
          </p>
        ))}

        <Select label="Facturación" value={form.billingMode} onChange={set('billingMode')} hint={BILLING_MODE_HINTS[form.billingMode]}>
          {BILLING_MODES.map((mode) => <option key={mode} value={mode}>{BILLING_MODE_LABELS[mode]}</option>)}
        </Select>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Select label="Catálogo de productos" value="INDEPENDENT" disabled hint="El catálogo compartido estará disponible próximamente.">
            <option value="INDEPENDENT">Independiente por sucursal</option>
            <option value="SHARED" disabled>Compartido (próximamente)</option>
          </Select>
          <Input label="Máximo de sucursales" type="number" min="1" value={form.maxBranches} onChange={set('maxBranches')}
            hint="Incluye la sucursal principal." required />
        </div>

        {error && <p className="text-xs font-bold text-red-500">{error}</p>}
      </form>
    </Modal>
  );
}
