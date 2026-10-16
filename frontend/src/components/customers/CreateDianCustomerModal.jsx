import React, { useState } from 'react';
import { X, Building2, User, FileText, CheckCircle2, MapPin, Mail, Phone, AlertCircle } from 'lucide-react';
import { createCustomerApi, updateCustomerApi } from '../../services/customersApi';

// Personas según el catálogo de la DIAN: 1 = jurídica (NIT con DV), 13 = natural.
const LEGAL_ORG_JURIDICA = 1;
const LEGAL_ORG_NATURAL = 13;
// Responsabilidad tributaria: 1 = responsable de IVA, 21 = no responsable / consumidor final.
const TRIBUTE_IVA = 1;
const TRIBUTE_NO_RESPONSABLE = 21;

const INPUT_CLASS =
  'w-full h-11 px-3.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink dark:focus:border-brand';

// Dígito de verificación del NIT (módulo 11, algoritmo oficial de la DIAN).
function calculateDv(nit) {
  const clean = nit.replace(/\D/g, '');
  if (!clean) return '';
  const weights = [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71];
  let sum = 0;
  for (let i = 0; i < clean.length; i++) {
    sum += parseInt(clean.charAt(clean.length - 1 - i), 10) * weights[i];
  }
  const rem = sum % 11;
  return String(rem === 0 || rem === 1 ? rem : 11 - rem);
}

const EMPTY_FORM = {
  legalOrganizationId: LEGAL_ORG_JURIDICA,
  tributeId: TRIBUTE_IVA,
  fullName: '',
  identification: '',
  dv: '',
  email: '',
  phone: '',
  municipalityId: '',
  address: ''
};

// Con `customer` el modal edita un cliente existente (la identificación no se puede cambiar).
function formFromCustomer(c) {
  return {
    legalOrganizationId: c.legalOrganizationId ?? LEGAL_ORG_NATURAL,
    tributeId: c.tributeId ?? TRIBUTE_NO_RESPONSABLE,
    fullName: c.fullName || '',
    identification: c.identification || '',
    dv: c.dv || '',
    email: c.email || '',
    phone: c.phone || '',
    municipalityId: c.municipalityId ?? '',
    address: c.address || ''
  };
}

export default function CreateDianCustomerModal({ onClose, onCreated, initialName = '', customer = null }) {
  const isEdit = !!customer;
  const [form, setForm] = useState(isEdit ? formFromCustomer(customer) : { ...EMPTY_FORM, fullName: initialName });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const isJuridica = form.legalOrganizationId === LEGAL_ORG_JURIDICA;
  const set = (patch) => setForm(prev => ({ ...prev, ...patch }));

  const selectPersonType = (legalOrganizationId) => {
    if (legalOrganizationId === LEGAL_ORG_JURIDICA) {
      set({ legalOrganizationId, tributeId: TRIBUTE_IVA, dv: calculateDv(form.identification) });
    } else {
      set({ legalOrganizationId, tributeId: TRIBUTE_NO_RESPONSABLE, dv: '' });
    }
  };

  const handleIdentificationChange = (value) => {
    const clean = value.replace(/\D/g, '');
    set({ identification: clean, dv: isJuridica && clean.length >= 7 ? calculateDv(clean) : form.dv });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!form.fullName.trim()) return setError('El nombre o razón social es obligatorio.');
    if (!form.identification.trim()) return setError('El número de identificación es obligatorio.');
    if (!form.email.trim()) return setError('La DIAN exige un correo para entregar el PDF y el XML de la factura.');

    setSaving(true);
    try {
      const payload = {
        fullName: form.fullName.trim(),
        identification: form.identification.trim(),
        email: form.email.trim(),
        phone: form.phone.trim() || undefined,
        address: form.address.trim() || undefined,
        dv: isJuridica ? (form.dv.trim() || calculateDv(form.identification)) : undefined,
        legalOrganizationId: form.legalOrganizationId,
        tributeId: form.tributeId,
        municipalityId: form.municipalityId ? Number(form.municipalityId) : undefined
      };
      const created = isEdit
        ? await updateCustomerApi(customer.id, payload)
        : await createCustomerApi(payload);
      onCreated?.(created);
      onClose();
    } catch (err) {
      setError(err.message || 'No se pudo registrar el cliente. Intenta nuevamente.');
    } finally {
      setSaving(false);
    }
  };

  const personTypes = [
    { id: LEGAL_ORG_JURIDICA, label: 'Persona Jurídica', icon: Building2 },
    { id: LEGAL_ORG_NATURAL, label: 'Persona Natural', icon: User }
  ];
  const tributes = [
    { id: TRIBUTE_IVA, label: 'Responsable de IVA', hint: 'Discrimina IVA en la factura' },
    { id: TRIBUTE_NO_RESPONSABLE, label: 'No responsable', hint: 'Consumidor final / régimen simplificado' }
  ];

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-[70] flex items-center justify-center p-4">
      <form
        onSubmit={handleSubmit}
        className="bento-card max-w-lg w-full bg-card rounded-3xl shadow-2xl max-h-[92vh] overflow-hidden flex flex-col border border-line"
      >
        <div className="p-6 pb-4 border-b border-line flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-10 h-10 rounded-2xl bg-brand/10 border border-brand/25 flex items-center justify-center text-brand shrink-0">
              <FileText className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h3 className="font-extrabold text-base text-ink truncate">{isEdit ? 'Editar Cliente Fiscal DIAN' : 'Registrar Cliente Fiscal DIAN'}</h3>
              <p className="text-[11px] text-ink-soft font-medium">Datos que la DIAN exige para la factura electrónica</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto text-xs">
          {error && (
            <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl font-bold flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="space-y-1.5">
            <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">Tipo de persona</span>
            <div className="grid grid-cols-2 gap-2">
              {personTypes.map(t => {
                const Icon = t.icon;
                const active = form.legalOrganizationId === t.id;
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => selectPersonType(t.id)}
                    className={`p-3 rounded-2xl border flex items-center gap-2.5 font-bold transition-all cursor-pointer ${
                      active
                        ? 'bg-solid text-white border-line-strong shadow-sm'
                        : 'bg-white dark:bg-[#0E0E12] text-gray-600 dark:text-gray-300 border-line hover:border-line-strong'
                    }`}
                  >
                    <Icon className={`w-4 h-4 ${active ? 'text-brand' : ''}`} />
                    <span>{t.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <label htmlFor="dian-name" className="font-bold text-gray-700 dark:text-gray-300 block mb-1">
              {isJuridica ? 'Razón social' : 'Nombre completo'} <span className="text-brand">*</span>
            </label>
            <input
              id="dian-name"
              type="text"
              required
              value={form.fullName}
              onChange={e => set({ fullName: e.target.value })}
              className={INPUT_CLASS}
            />
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div className="col-span-2">
              <label htmlFor="dian-id" className="font-bold text-gray-700 dark:text-gray-300 block mb-1">
                {isJuridica ? 'NIT' : 'Cédula / Identificación'} <span className="text-brand">*</span>
              </label>
              <input
                id="dian-id"
                type="text"
                inputMode="numeric"
                required
                value={form.identification}
                onChange={e => handleIdentificationChange(e.target.value)}
                disabled={isEdit}
                title={isEdit ? 'La identificación no se puede modificar' : undefined}
                className={`${INPUT_CLASS} font-mono disabled:opacity-60`}
              />
            </div>
            <div>
              <label htmlFor="dian-dv" className="font-bold text-gray-700 dark:text-gray-300 block mb-1">DV</label>
              <input
                id="dian-dv"
                type="text"
                maxLength={1}
                disabled={!isJuridica}
                value={form.dv}
                onChange={e => set({ dv: e.target.value.replace(/\D/g, '') })}
                className={`${INPUT_CLASS} text-center font-mono disabled:opacity-50`}
              />
            </div>
          </div>

          <div>
            <label htmlFor="dian-email" className="font-bold text-gray-700 dark:text-gray-300 flex items-center gap-1.5 mb-1">
              <Mail className="w-3.5 h-3.5 text-brand" /> Correo de recepción <span className="text-brand">*</span>
            </label>
            <input
              id="dian-email"
              type="email"
              required
              value={form.email}
              onChange={e => set({ email: e.target.value })}
              className={INPUT_CLASS}
            />
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <label htmlFor="dian-phone" className="font-bold text-gray-700 dark:text-gray-300 flex items-center gap-1.5 mb-1">
                <Phone className="w-3.5 h-3.5 text-gray-400" /> Teléfono
              </label>
              <input
                id="dian-phone"
                type="text"
                value={form.phone}
                onChange={e => set({ phone: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
            <div>
              <label htmlFor="dian-city" className="font-bold text-gray-700 dark:text-gray-300 flex items-center gap-1.5 mb-1">
                <MapPin className="w-3.5 h-3.5 text-gray-400" /> Código DANE del municipio
              </label>
              <input
                id="dian-city"
                type="text"
                inputMode="numeric"
                value={form.municipalityId}
                onChange={e => set({ municipalityId: e.target.value.replace(/\D/g, '') })}
                className={`${INPUT_CLASS} font-mono`}
              />
            </div>
          </div>

          <div>
            <label htmlFor="dian-address" className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Dirección fiscal</label>
            <input
              id="dian-address"
              type="text"
              value={form.address}
              onChange={e => set({ address: e.target.value })}
              className={INPUT_CLASS}
            />
          </div>

          <div className="space-y-1.5">
            <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">Responsabilidad tributaria</span>
            <div className="grid grid-cols-2 gap-2">
              {tributes.map(t => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => set({ tributeId: t.id })}
                  className={`p-3 rounded-2xl border text-left transition-all cursor-pointer ${
                    form.tributeId === t.id
                      ? 'bg-page border-[#0A0A0A] dark:border-brand text-ink'
                      : 'bg-card border-line text-ink-soft'
                  }`}
                >
                  <span className="font-bold block">{t.label}</span>
                  <span className="text-[10px] text-ink-muted">{t.hint}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="p-6 pt-4 border-t border-line flex gap-2">
          <button
            type="button"
            onClick={onClose}
            className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={saving}
            className="w-1/2 py-3 bg-solid hover:bg-solid-hover disabled:opacity-50 text-white rounded-2xl text-xs font-extrabold flex items-center justify-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
          >
            <CheckCircle2 className="w-4 h-4 text-brand" />
            {saving ? 'Guardando...' : isEdit ? 'Guardar Cambios' : 'Guardar Cliente DIAN'}
          </button>
        </div>
      </form>
    </div>
  );
}
