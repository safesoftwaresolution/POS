import React, { useRef, useState } from 'react';
import { FileText, Plus, RefreshCw } from 'lucide-react';
import { Badge, Button, EmptyState, Select } from '../ui';
import Pager from '../ui/Pager';
import useLoad from '../../hooks/useLoad';
import useModal from '../../hooks/useModal';
import { downloadNoteApi, listNotesApi, retryNoteApi } from '../../services/billingNotesApi';
import { deliverBlob } from '../../utils/downloadBlob';
import { formatDateTime } from '../../utils/format';
import { INVOICE_STATUSES, INVOICE_STATUS_LABELS } from './constants';
import CreateNoteModal from './CreateNoteModal';

export default function NotesTab({ role, refreshKey, onChanged }) {
  const canManage = role === 'ADMINISTRATOR' || role === 'SUPERVISOR';
  const { notify } = useModal();
  const [type, setType] = useState('credit');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(null);
  const retryInFlight = useRef(false);
  const { data, loading, error, reload } = useLoad(() => listNotesApi(type, status, page, 20),
    [type, status, page, refreshKey], { initial: { content: [], totalPages: 0 }, autoRefresh: true });

  const download = async (note, format) => {
    try {
      const blob = await downloadNoteApi(type, note.id, format);
      const number = note.creditNoteNumber || note.debitNoteNumber || note.id;
      deliverBlob(blob, `nota-${number}.${format}`, { openInTab: format === 'pdf' });
    } catch (err) {
      notify(`No se pudo descargar la nota: ${err.message}`);
    }
  };

  const retry = async (note) => {
    if (retryInFlight.current) return;
    retryInFlight.current = true;
    setBusy(note.id);
    try {
      await retryNoteApi(type, note.id);
      reload();
      onChanged?.();
    } catch (err) {
      notify(`No se pudo recuperar la nota: ${err.message}`);
    } finally {
      retryInFlight.current = false;
      setBusy(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <Select label="Tipo de nota" value={type} onChange={e => { setType(e.target.value); setPage(0); }}>
          <option value="credit">Notas crédito</option><option value="debit">Notas débito</option>
        </Select>
        <Select label="Estado de la nota" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">Todos</option>
          {INVOICE_STATUSES.map(value => <option key={value} value={value}>{INVOICE_STATUS_LABELS[value]}</option>)}
        </Select>
        <Button variant="outline" icon={RefreshCw} onClick={() => reload({ silent: false })}>Actualizar notas</Button>
        {canManage && <Button icon={Plus} onClick={() => setCreating(true)}>Crear nota</Button>}
      </div>
      <p className="text-xs text-ink-soft">Las notas ajustan documentos fiscales. No realizan por sí solas devoluciones de dinero ni movimientos de inventario.</p>
      {error && <p role="alert" className="text-sm text-ink">{error}</p>}
      {loading ? <p className="py-8 text-sm text-ink-muted">Consultando notas...</p> : data.content?.length ? (
        <div className="space-y-3">
          {data.content.map(note => {
            const number = note.creditNoteNumber || note.debitNoteNumber || note.referenceCode || `#${note.id}`;
            return (
              <div key={note.id} className="bento-card p-4 space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2 text-sm font-bold text-ink">
                    <span>{number}</span>
                    <Badge tone={note.status === 'VALIDATED' ? 'success' : note.status === 'PENDING' ? 'warning' : 'danger'}>
                      {INVOICE_STATUS_LABELS[note.status] || note.status}
                    </Badge>
                    {note.environment === 'SANDBOX' && <Badge tone="warning">Prueba</Badge>}
                  </div>
                  <span className="text-xs text-ink-muted">{formatDateTime(note.validatedAt || note.createdAt)}</span>
                </div>
                <p className="text-xs text-ink-soft">Factura {note.billNumber || '—'} · Venta #{note.saleId || '—'}</p>
                {note.cude && <p className="text-xs font-mono text-ink-muted break-all">CUDE: {note.cude}</p>}
                {note.errorMessage && <p className="text-xs text-ink" role="status">{note.errorMessage}</p>}
                <div className="flex flex-wrap gap-2">
                  {note.status === 'VALIDATED' && <>
                    <Button size="sm" variant="outline" aria-label={`Descargar PDF ${number}`} onClick={() => download(note, 'pdf')}>PDF</Button>
                    <Button size="sm" variant="outline" aria-label={`Descargar XML ${number}`} onClick={() => download(note, 'xml')}>XML</Button>
                  </>}
                  {canManage && ['ERROR', 'PENDING'].includes(note.status) && (
                    <Button size="sm" variant="outline" disabled={busy != null} loading={busy === note.id}
                      aria-label={`Reintentar ${number}`} onClick={() => retry(note)}>Consultar / reintentar</Button>
                  )}
                </div>
              </div>
            );
          })}
          <Pager page={page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
        </div>
      ) : !error && <EmptyState icon={FileText} title="No hay notas para esta consulta" description="Las notas creadas sobre facturas validadas aparecerán aquí." />}
      {creating && <CreateNoteModal type={type} onClose={() => setCreating(false)} onCreated={() => { reload(); onChanged?.(); }} />}
    </div>
  );
}
