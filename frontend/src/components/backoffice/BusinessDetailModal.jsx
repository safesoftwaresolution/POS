import React, { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import useLoad from '../../hooks/useLoad';
import useModal from '../../hooks/useModal';
import { Badge, Button, Input, Modal, Select } from '../ui';
import { getBackofficeStoresApi } from '../../services/backoffice/storesApi';
import {
  addBusinessBranchApi, changeBusinessBillingModeApi, dissolveBusinessApi, removeBusinessBranchApi, updateBusinessApi,
} from '../../services/backoffice/businessesApi';
import {
  BILLING_MODE_HINTS, BILLING_MODE_LABELS, additionalBranches, branchStatusLabel, branchStatusTone,
} from '../../utils/business';

const BILLING_MODES = Object.keys(BILLING_MODE_LABELS);

/** Administra un negocio: nombre y tope, modo de facturación, sucursales y disolución. Cada acción se aplica al instante. */
export default function BusinessDetailModal({ business: initial, onClose, onChanged, onDissolved }) {
  const { confirm } = useModal();
  const [business, setBusiness] = useState(initial);
  const [name, setName] = useState(initial.name);
  const [maxBranches, setMaxBranches] = useState(String(initial.maxBranches));
  const [billingMode, setBillingMode] = useState(initial.billingMode);
  const [storeToAdd, setStoreToAdd] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState({ text: '', tone: 'ok' });

  const { data: stores } = useLoad(() => getBackofficeStoresApi('', '', 0, 100), [], { initial: { content: [] } });
  const inBusiness = new Set(business.branches.map((b) => b.storeId));
  const addable = stores.content.filter((s) => s.status !== 'REJECTED' && !inBusiness.has(s.id));

  const run = async (action, okText) => {
    setBusy(true);
    setMessage({ text: '', tone: 'ok' });
    try {
      const updated = await action();
      if (updated) {
        setBusiness(updated);
        setName(updated.name);
        setMaxBranches(String(updated.maxBranches));
        setBillingMode(updated.billingMode);
        onChanged(updated);
      }
      if (okText) setMessage({ text: okText, tone: 'ok' });
      return true;
    } catch (e) {
      setMessage({ text: e?.message || 'No se pudo completar la acción.', tone: 'error' });
      return false;
    } finally {
      setBusy(false);
    }
  };

  const saveBasics = () => {
    const payload = {};
    if (name.trim() !== business.name) payload.name = name.trim();
    if (Number(maxBranches) !== business.maxBranches) payload.maxBranches = Number(maxBranches);
    if (Object.keys(payload).length === 0) return;
    run(() => updateBusinessApi(business.id, payload), 'Datos guardados.');
  };

  const saveBillingMode = () => {
    if (billingMode === business.billingMode) return;
    run(() => changeBusinessBillingModeApi(business.id, billingMode), 'Modo de facturación actualizado.');
  };

  const addBranch = async () => {
    if (!storeToAdd) return;
    if (await run(() => addBusinessBranchApi(business.id, Number(storeToAdd)), 'Sucursal agregada.')) setStoreToAdd('');
  };

  const removeBranch = async (branch) => {
    const ok = await confirm(`«${branch.name}» dejará de ser sucursal de este negocio, pero el local no se elimina.`, {
      title: 'Sacar sucursal', confirmText: 'Sacar',
    });
    if (ok) run(() => removeBusinessBranchApi(business.id, branch.storeId), 'Sucursal retirada.');
  };

  const dissolve = async () => {
    const ok = await confirm(
      'Todos los locales quedan independientes y el negocio se elimina. No se borra ningún local ni sus datos, '
      + 'pero las sucursales dejarán de facturar con la empresa de la principal.',
      { title: `Disolver «${business.name}»`, confirmText: 'Disolver' },
    );
    if (!ok) return;
    if (await run(() => dissolveBusinessApi(business.id).then(() => null))) onDissolved(business);
  };

  const full = additionalBranches(business) >= business.maxBranches;

  return (
    <Modal title={business.name} subtitle="Administración del negocio" onClose={onClose} size="lg" dismissible={!busy}>
      <div className="space-y-6">
        {message.text && (
          <p className={`text-xs font-bold ${message.tone === 'error' ? 'text-red-500' : 'text-emerald-600 dark:text-emerald-400'}`}>
            {message.text}
          </p>
        )}

        <section className="space-y-3">
          <h4 className="text-[11px] font-extrabold uppercase tracking-wider text-ink-muted">Datos</h4>
          <div className="grid grid-cols-1 sm:grid-cols-[1fr_10rem_auto] gap-3 items-end">
            <Input label="Nombre" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} />
            <Input label="Sucursales adicionales (máx.)" type="number" min="1" value={maxBranches} onChange={(e) => setMaxBranches(e.target.value)} />
            <Button variant="outline" onClick={saveBasics} disabled={busy}>Guardar</Button>
          </div>
        </section>

        <section className="space-y-3">
          <h4 className="text-[11px] font-extrabold uppercase tracking-wider text-ink-muted">Facturación</h4>
          <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-3 items-end">
            <Select label="Modo" value={billingMode} onChange={(e) => setBillingMode(e.target.value)} hint={BILLING_MODE_HINTS[billingMode]}>
              {BILLING_MODES.map((mode) => <option key={mode} value={mode}>{BILLING_MODE_LABELS[mode]}</option>)}
            </Select>
            <Button variant="outline" onClick={saveBillingMode} disabled={busy || billingMode === business.billingMode}>Cambiar modo</Button>
          </div>
          <p className="text-[11px] text-ink-muted">
            No se puede cambiar una vez que alguna sucursal tiene facturas validadas por la DIAN.
          </p>
        </section>

        <section className="space-y-3">
          <h4 className="text-[11px] font-extrabold uppercase tracking-wider text-ink-muted">
            Sucursales (principal + {additionalBranches(business)} de {business.maxBranches})
          </h4>
          <ul className="divide-y divide-line border border-line rounded-2xl">
            {business.branches.map((branch) => (
              <li key={branch.storeId} className="flex items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-bold text-ink truncate">{branch.name}</div>
                  <div className="text-[10px] font-semibold text-ink-muted">{branch.main ? 'Principal' : 'Sucursal'}</div>
                </div>
                <Badge tone={branchStatusTone(branch.status)}>{branchStatusLabel(branch.status)}</Badge>
                {!branch.main && (
                  <button
                    type="button"
                    onClick={() => removeBranch(branch)}
                    disabled={busy}
                    aria-label={`Sacar ${branch.name} del negocio`}
                    className="p-1.5 rounded-xl text-ink-muted hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 cursor-pointer disabled:opacity-50"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
              </li>
            ))}
          </ul>
          <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-3 items-end">
            <Select label="Agregar un local existente" value={storeToAdd} onChange={(e) => setStoreToAdd(e.target.value)}
              disabled={full} hint={full ? 'El negocio alcanzó su máximo de sucursales adicionales.' : 'El local no debe pertenecer a otro negocio.'}>
              <option value="">Selecciona un local…</option>
              {addable.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </Select>
            <Button variant="outline" icon={Plus} onClick={addBranch} disabled={busy || full || !storeToAdd}>Agregar</Button>
          </div>
        </section>

        <section className="pt-4 border-t border-line flex items-center justify-between gap-4">
          <p className="text-[11px] text-ink-muted max-w-sm">
            Disolver deja a todos los locales independientes. No elimina ninguno.
          </p>
          <Button variant="danger" icon={Trash2} onClick={dissolve} disabled={busy}>Disolver negocio</Button>
        </section>
      </div>
    </Modal>
  );
}
