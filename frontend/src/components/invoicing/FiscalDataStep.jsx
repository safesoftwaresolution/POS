import React, { useState } from 'react';
import { Save } from 'lucide-react';
import { Button, Input, Select } from '../ui';
import { cx } from '../ui/cx';
import MunicipalityPicker from './MunicipalityPicker';
import StepMessage from './StepMessage';
import FiscalSummary from './FiscalSummary';
import { saveFiscalProfileApi } from '../../services/billingApi';
import { formatNitWithDv } from '../../utils/dianCheckDigit';
import {
  ORGANIZATION_TYPES,
  RESPONSIBILITIES,
  fieldErrorsFromMessage,
  formToRequest,
  profileToForm,
  validateFiscalForm,
} from './fiscalProfile';

/** Paso 1: datos del RUT. Con ellos se registra la empresa del local ante el proveedor (en producción). */
export default function FiscalDataStep({ setup, step, canEdit, onSaved }) {
  const [form, setForm] = useState(() => profileToForm(setup.profile));
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState('');
  const [saving, setSaving] = useState(false);

  const set = (patch) => setForm((prev) => ({ ...prev, ...patch }));
  const platformWaiting = step.state === 'ERROR' && !setup.platformReady;

  if (!canEdit) {
    return (
      <div className="space-y-4">
        <StepMessage step={step} platformWaiting={platformWaiting} />
        {setup.profile?.saved && <FiscalSummary profile={setup.profile} />}
      </div>
    );
  }

  const toggleResponsibility = (code) => set({
    responsibilities: form.responsibilities.includes(code)
      ? form.responsibilities.filter((c) => c !== code)
      : [...form.responsibilities, code],
  });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setGeneral('');
    const found = validateFiscalForm(form);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    try {
      onSaved(await saveFiscalProfileApi(formToRequest(form)));
    } catch (err) {
      const parsed = fieldErrorsFromMessage(err.message);
      setErrors(parsed.fields);
      setGeneral(parsed.general);
    } finally {
      setSaving(false);
    }
  };

  const nitWithDv = formatNitWithDv(form.nit);

  return (
    <form onSubmit={handleSubmit} className="space-y-5" noValidate>
      <StepMessage step={step} platformWaiting={platformWaiting} />

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Input
          label="Razón social *"
          value={form.legalName}
          onChange={(e) => set({ legalName: e.target.value })}
          error={errors.legalName}
          hint="Como aparece en tu RUT."
          maxLength={450}
        />
        <Input
          label="Nombre comercial"
          value={form.tradeName}
          onChange={(e) => set({ tradeName: e.target.value })}
          error={errors.tradeName}
          hint="Opcional: el nombre con que te conocen tus clientes."
          maxLength={450}
        />
        <Input
          label="NIT *"
          inputMode="numeric"
          value={form.nit}
          onChange={(e) => set({ nit: e.target.value.replace(/\D/g, '') })}
          error={errors.nit}
          hint={nitWithDv ? `Tu NIT con dígito de verificación: ${nitWithDv}` : 'Solo números, sin el dígito de verificación.'}
          maxLength={15}
          className="font-mono"
        />
        <Select
          label="Tipo de persona *"
          value={form.organizationType}
          onChange={(e) => set({ organizationType: e.target.value })}
          error={errors.organizationType}
        >
          {ORGANIZATION_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
        </Select>
      </div>

      <fieldset className="space-y-2">
        <legend className="block text-[11px] font-bold uppercase tracking-wider text-ink-soft">Responsabilidades del RUT *</legend>
        <p className="text-[11px] text-ink-muted">Están en la casilla 53 de tu RUT. Marca todas las que tengas.</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {RESPONSIBILITIES.map((r) => {
            const checked = form.responsibilities.includes(r.code);
            return (
              <label
                key={r.code}
                className={cx(
                  'flex items-start gap-2.5 p-3 rounded-2xl border cursor-pointer text-xs font-semibold transition-all',
                  checked ? 'border-brand bg-brand/5 text-ink' : 'border-line bg-page text-ink-soft hover:border-line-strong',
                )}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggleResponsibility(r.code)}
                  className="mt-0.5 accent-brand"
                />
                <span>{r.label}</span>
              </label>
            );
          })}
        </div>
        {errors.responsibilities && <p className="text-[11px] font-semibold text-red-500">{errors.responsibilities}</p>}
      </fieldset>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Input
          label="Correo de facturación *"
          type="email"
          value={form.email}
          onChange={(e) => set({ email: e.target.value })}
          error={errors.email}
          hint="Aquí te llegan las copias de tus facturas."
          maxLength={255}
        />
        <Input
          label="Teléfono"
          type="tel"
          value={form.phone}
          onChange={(e) => set({ phone: e.target.value })}
          error={errors.phone}
          maxLength={30}
        />
      </div>

      <Input
        label="Dirección *"
        value={form.address}
        onChange={(e) => set({ address: e.target.value })}
        error={errors.address}
        hint="La dirección que figura en tu RUT."
        maxLength={255}
      />

      <MunicipalityPicker
        cityCode={form.cityCode}
        departmentCode={form.departmentCode}
        onChange={(patch) => set(patch)}
        error={errors.cityCode}
        required
      />

      <div className="sm:w-1/2">
        <Input
          label="Actividad económica (CIIU)"
          inputMode="numeric"
          value={form.economicActivity}
          onChange={(e) => set({ economicActivity: e.target.value.replace(/\D/g, '').slice(0, 4) })}
          error={errors.economicActivity}
          hint="Opcional: el código de 4 números de la casilla 46 del RUT."
          className="font-mono"
        />
      </div>

      {general && (
        <p role="alert" className="p-3 rounded-2xl text-xs font-semibold text-red-600 bg-red-50 border border-red-200 dark:text-red-400 dark:bg-red-500/20 dark:border-red-500/20">
          {general}
        </p>
      )}

      <div className="flex justify-end">
        <Button type="submit" icon={Save} loading={saving}>
          {setup.profile?.saved ? 'Guardar cambios' : 'Guardar y registrar mi empresa'}
        </Button>
      </div>
    </form>
  );
}

