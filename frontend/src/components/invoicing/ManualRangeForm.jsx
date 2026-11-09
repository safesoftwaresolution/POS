import React, { useState } from 'react';
import { Button, Input, Select } from '../ui';
import { createNumberingRangeApi } from '../../services/billingApi';
import { DOCUMENT_OPTIONS, isInvoiceDocument } from './constants';

// Las notas crédito y débito no llevan resolución de la DIAN: el backend acepta cualquier texto como número de
// resolución y la clave técnica vacía (como los rangos de notas que crea la importación).
const NOTE_RESOLUTION = 'SIN-RESOLUCION';
const NOTE_LAST_NUMBER = 99999999;
const NOTE_PREFIX = { 91: 'NC', 92: 'ND' };

const isoDate = (date) => date.toISOString().slice(0, 10);

function emptyForm(document) {
  if (isInvoiceDocument(document)) {
    return { document, prefix: '', resolutionNumber: '', technicalKey: '', from: '', to: '', current: '', startDate: '', endDate: '' };
  }
  return { document, prefix: NOTE_PREFIX[document] || '', resolutionNumber: '', technicalKey: '', from: '1', to: String(NOTE_LAST_NUMBER), current: '1', startDate: '', endDate: '' };
}

/** Registro manual de un rango de numeración (si no se puede traer de la DIAN). */
export default function ManualRangeForm({ onCreated, onCancel }) {
  const [form, setForm] = useState(() => emptyForm('01'));
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const invoice = isInvoiceDocument(form.document);
  const set = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const today = new Date();
    const tenYears = new Date(today);
    tenYears.setFullYear(today.getFullYear() + 10);
    setSaving(true);
    try {
      await createNumberingRangeApi({
        document: form.document,
        prefix: form.prefix.trim().toUpperCase(),
        resolutionNumber: invoice ? form.resolutionNumber.trim() : NOTE_RESOLUTION,
        current: Number(form.current),
        from: Number(form.from),
        to: Number(form.to),
        startDate: invoice ? form.startDate : isoDate(today),
        endDate: invoice ? form.endDate : isoDate(tenYears),
        technicalKey: invoice ? form.technicalKey.trim() : null,
      });
      setForm(emptyForm(form.document));
      onCreated?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Select label="Documento" value={form.document} onChange={(e) => setForm(emptyForm(e.target.value))}>
          {DOCUMENT_OPTIONS.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
        </Select>
        <Input label="Prefijo" required maxLength={4} value={form.prefix} onChange={set('prefix')} placeholder={invoice ? 'FE' : NOTE_PREFIX[form.document]} hint="Hasta 4 letras o números." />
        {invoice && (
          <>
            <Input label="Número de resolución" required maxLength={14} value={form.resolutionNumber} onChange={set('resolutionNumber')} className="font-mono" />
            <Input label="Clave técnica" required value={form.technicalKey} onChange={set('technicalKey')} className="font-mono" hint="Está en la resolución que te dio la DIAN." />
          </>
        )}
        <Input label="Desde el número" required type="number" min={1} value={form.from} onChange={set('from')} />
        <Input label="Hasta el número" required type="number" min={1} value={form.to} onChange={set('to')} />
        <Input label="Próximo número a usar" required type="number" min={1} value={form.current} onChange={set('current')} hint="Si ya usaste números de este rango, el último usado más uno." />
        {invoice && (
          <>
            <Input label="Vigente desde" required type="date" value={form.startDate} onChange={set('startDate')} />
            <Input label="Vigente hasta" required type="date" value={form.endDate} onChange={set('endDate')} />
          </>
        )}
      </div>
      {!invoice && (
        <p className="text-[11px] text-ink-muted">Las notas crédito y débito no llevan resolución de la DIAN: basta con el prefijo y el rango.</p>
      )}
      {error && (
        <p role="alert" className="p-3 rounded-2xl text-xs font-semibold text-red-600 bg-red-50 border border-red-200 dark:text-red-400 dark:bg-red-500/20 dark:border-red-500/20">{error}</p>
      )}
      <div className="flex justify-end gap-2">
        {onCancel && <Button variant="soft" onClick={onCancel}>Cancelar</Button>}
        <Button type="submit" loading={saving}>Registrar rango</Button>
      </div>
    </form>
  );
}
