import React, { useRef, useState } from 'react';
import { Badge, Button, Input, Modal, Select } from '../ui';
import { getSaleInvoiceApi } from '../../services/billingApi';
import { createCreditNoteApi, createDebitNoteApi } from '../../services/billingNotesApi';
import { INVOICE_STATUS_LABELS } from './constants';
import { buildDebitNoteRequest, isDefiniteRejection } from './notePayload';

export default function CreateNoteModal({ type, onClose, onCreated }) {
  const credit = type === 'credit';
  const [saleId, setSaleId] = useState('');
  const [invoice, setInvoice] = useState(null);
  const [observation, setObservation] = useState('');
  const [description, setDescription] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [price, setPrice] = useState('');
  const [taxRate, setTaxRate] = useState('19');
  const [concept, setConcept] = useState('1');
  const [reference] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [locked, setLocked] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const inFlight = useRef(false);

  const consultInvoice = async () => {
    if (inFlight.current || !saleId) return;
    inFlight.current = true;
    setBusy(true);
    setError('');
    setInvoice(null);
    try {
      const original = await getSaleInvoiceApi(saleId);
      if (original.status !== 'VALIDATED') throw new Error('La nota necesita una factura validada por la DIAN.');
      setInvoice(original);
    } catch (err) {
      setError(err.message);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  const submit = async event => {
    event.preventDefault();
    if (inFlight.current || !invoice || result) return;
    inFlight.current = true;
    setBusy(true);
    setLocked(true);
    setError('');
    try {
      const note = credit
        ? await createCreditNoteApi(invoice.saleId, observation.trim())
        : await createDebitNoteApi(invoice.saleId, buildDebitNoteRequest({
          reference, concept, invoice, observation, description, quantity, price, taxRate,
        }));
      setResult(note);
      onCreated?.(note);
    } catch (err) {
      setError(err.message);
      // Nothing was created: the values can be corrected and sent again with the same reference.
      if (isDefiniteRejection(err)) setLocked(false);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <Modal title={`Crear nota ${credit ? 'crédito' : 'débito'}`} onClose={busy ? undefined : onClose} dismissible={!busy} size="lg">
      <p className="text-xs text-ink-soft">{credit
        ? 'Esta nota anula fiscalmente el valor completo de la venta; no devuelve dinero ni modifica el inventario. Registra la devolución comercial por separado.'
        : 'Esta nota añade un ajuste fiscal a una factura validada. Los valores del ajuste se ingresan antes de impuestos.'}</p>
      <form onSubmit={submit} className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          <Input label="Venta asociada" type="number" min="1" step="1" required value={saleId} disabled={busy || locked}
            onChange={e => { setSaleId(e.target.value); setInvoice(null); }} />
          <Button variant="outline" disabled={!saleId || busy || locked} onClick={consultInvoice}>Consultar factura</Button>
        </div>
        {invoice && <p className="text-xs text-ink flex flex-wrap items-center gap-2">Factura {invoice.legalNumber} · Venta #{invoice.saleId}
          {invoice.environment === 'SANDBOX' && <Badge tone="warning">Prueba</Badge>}
        </p>}
        {!credit && <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Select label="Concepto del débito" value={concept} onChange={e => setConcept(e.target.value)} disabled={locked || busy}>
            <option value="1">Intereses</option><option value="2">Gastos por cobrar</option>
            <option value="3">Cambio del valor</option><option value="4">Otros</option>
          </Select>
          <Input label="Descripción del ajuste" required maxLength={300} value={description}
            onChange={e => setDescription(e.target.value)} disabled={locked || busy} />
          <Input label="Cantidad" type="number" min="0.01" step="0.01" required value={quantity}
            onChange={e => setQuantity(e.target.value)} disabled={locked || busy} />
          <Input label="Precio unitario antes de impuestos (COP)" type="number" min="0.01" step="0.01" required value={price}
            onChange={e => setPrice(e.target.value)} disabled={locked || busy} />
          <Select label="IVA del ajuste" value={taxRate} onChange={e => setTaxRate(e.target.value)} disabled={locked || busy}>
            <option value="0">0 %</option><option value="5">5 %</option><option value="19">19 %</option>
          </Select>
        </div>}
        <Input label="Motivo u observación" required maxLength={500} value={observation}
          onChange={e => setObservation(e.target.value)} disabled={locked || busy} />
        {locked && !result && <p className="text-xs text-ink-muted">La solicitud conserva sus datos y referencia para recuperarla sin crear otra nota. Si hubo un corte de conexión, reintenta esta misma solicitud.</p>}
        {error && <p role="alert" className="text-sm text-ink">{error}</p>}
        {result ? <div role="status" className="rounded-2xl border border-line bg-page p-3 space-y-1">
          <p className="text-sm font-bold text-ink">Estado: {INVOICE_STATUS_LABELS[result.status] || result.status}</p>
          {result.errorMessage && <p className="text-xs text-ink-soft">{result.errorMessage}</p>}
          <p className="text-xs text-ink-soft">Consulta el seguimiento y los archivos desde la lista de notas.</p>
        </div> : <Button type="submit" loading={busy} disabled={!invoice || busy}>
          {locked ? 'Reintentar la misma solicitud' : `Emitir nota ${credit ? 'crédito' : 'débito'}`}
        </Button>}
        <Button variant="outline" onClick={onClose} disabled={busy}>Cerrar</Button>
      </form>
    </Modal>
  );
}
