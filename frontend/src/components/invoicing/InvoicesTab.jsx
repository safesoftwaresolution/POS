import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { FileText, RefreshCw, Download, FileCode, Mail, Eye, X, CheckCircle2, Search, Copy, Check, ExternalLink, QrCode, AlertTriangle, Clock } from 'lucide-react';
import {
  listInvoicesApi,
  getSaleInvoiceApi,
  retryInvoiceApi,
  sendInvoiceEmailApi,
  downloadInvoicePdfApi,
  downloadInvoiceXmlApi
} from '../../services/billingApi';
import { deliverBlob } from '../../utils/downloadBlob';
import useAutoRefresh from '../../hooks/useAutoRefresh';
import { useModal } from '../../context/ModalContext';
import { INVOICE_STATUSES, INVOICE_STATUS_LABELS, INVOICE_STATUS_STYLES, FIELD_CLASS, formatDateTime } from './constants';

export default function InvoicesTab({ role, refreshKey, onChanged }) {
  const { notify } = useModal();
  const canRetry = role === 'ADMINISTRATOR' || role === 'SUPERVISOR';
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [retrying, setRetrying] = useState(null);
  const [emailTarget, setEmailTarget] = useState(null);
  const [emailValue, setEmailValue] = useState('');
  const [sendingEmail, setSendingEmail] = useState(false);
  const [detail, setDetail] = useState(null);
  const [copiedId, setCopiedId] = useState(null);

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    try {
      setData(await listInvoicesApi(statusFilter, page, 20));
    } catch (e) {
      console.warn('Error cargando facturas:', e);
      if (!silent) setData({ content: [], totalPages: 0 });
    } finally {
      if (!silent) setLoading(false);
    }
  }, [statusFilter, page]);

  useEffect(() => { load(); }, [load, refreshKey]);
  useAutoRefresh(() => load({ silent: true }));

  const handleRetry = async (invoice) => {
    setRetrying(invoice.saleId);
    try {
      await retryInvoiceApi(invoice.saleId);
      await load();
      onChanged?.();
    } catch (err) {
      notify('Error reintentando la factura: ' + err.message);
    } finally {
      setRetrying(null);
    }
  };

  const handleDownload = async (invoice, type) => {
    try {
      const blob = type === 'pdf' ? await downloadInvoicePdfApi(invoice.saleId) : await downloadInvoiceXmlApi(invoice.saleId);
      deliverBlob(blob, `factura-${invoice.factusNumber || invoice.saleId}.${type}`, { openInTab: type === 'pdf' });
    } catch (err) {
      notify(`Error descargando el ${type.toUpperCase()}: ` + err.message);
    }
  };

  const handleSendEmail = async (e) => {
    e.preventDefault();
    if (!emailTarget) return;
    setSendingEmail(true);
    try {
      await sendInvoiceEmailApi(emailTarget.saleId, emailValue.trim());
      setEmailTarget(null);
    } catch (err) {
      notify('Error enviando la factura por correo: ' + err.message);
    } finally {
      setSendingEmail(false);
    }
  };

  const openDetail = async (invoice) => {
    try {
      setDetail(await getSaleInvoiceApi(invoice.saleId));
    } catch (err) {
      notify('Error cargando el detalle de la factura: ' + err.message);
    }
  };

  const copyCufe = async (invoice) => {
    try {
      await navigator.clipboard.writeText(invoice.cufe);
      setCopiedId(invoice.id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      notify('No se pudo copiar el CUFE. Selecciónalo y cópialo manualmente.');
    }
  };

  const invoices = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return data.content || [];
    return (data.content || []).filter(inv =>
      (inv.factusNumber || '').toLowerCase().includes(q) ||
      (inv.cufe || '').toLowerCase().includes(q) ||
      String(inv.saleId).includes(q)
    );
  }, [data.content, search]);

  const iconBtn = 'p-1.5 rounded-lg border border-line hover:border-[#0A0A0A] dark:hover:border-brand text-ink-soft cursor-pointer transition-colors';

  return (
    <div className="space-y-4">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <div className="relative w-full lg:max-w-md">
          <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por número de factura, CUFE o venta..."
            aria-label="Buscar facturas"
            className="w-full h-10 pl-10 pr-4 bg-card border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink dark:focus:border-brand"
          />
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 lg:pb-0">
          {[['', 'Todas'], ...INVOICE_STATUSES.map(s => [s, INVOICE_STATUS_LABELS[s]])].map(([value, label]) => (
            <button
              key={value || 'all'}
              type="button"
              onClick={() => { setStatusFilter(value); setPage(0); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap cursor-pointer transition-all ${
                statusFilter === value
                  ? 'bg-solid text-white shadow-sm'
                  : 'bg-card text-gray-600 dark:text-gray-300 border border-line hover:border-line-strong'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="bento-card overflow-hidden">
        {loading ? (
          <div className="py-14 text-center text-xs font-bold text-ink-muted">Consultando facturas...</div>
        ) : invoices.length === 0 ? (
          <div className="py-14 px-6 text-center space-y-2">
            <div className="w-12 h-12 rounded-2xl bg-gray-100 dark:bg-white/5 text-gray-400 flex items-center justify-center mx-auto">
              <FileText className="w-6 h-6" />
            </div>
            <p className="text-sm font-bold text-ink">
              {search ? 'Ninguna factura coincide con tu búsqueda' : 'Aún no hay facturas electrónicas'}
            </p>
            <p className="text-[11px] text-ink-soft max-w-sm mx-auto">
              {search
                ? 'La búsqueda revisa las facturas de esta página. Prueba con otro número o cambia de página.'
                : 'Al cobrar en la caja con la opción de factura electrónica activada, la factura aparecerá aquí.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-cards w-full text-left text-xs min-w-[760px]">
              <thead>
                <tr className="border-b border-line bg-gray-50/70 dark:bg-white/[0.03] text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">
                  <th className="py-3.5 px-5">Factura</th>
                  <th className="py-3.5 px-4">Validada el</th>
                  <th className="py-3.5 px-4">Estado DIAN</th>
                  <th className="py-3.5 px-4">CUFE</th>
                  <th className="py-3.5 px-5 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#EAEAEA] dark:divide-line">
                {invoices.map(inv => {
                  const validated = inv.status === 'VALIDATED';
                  return (
                    <tr key={inv.id} className="hover:bg-gray-50/60 dark:hover:bg-white/5">
                      <td data-label="" className="py-3.5 px-5">
                        <span className="font-mono font-bold text-ink block">{inv.factusNumber || '—'}</span>
                        <span className="text-[10px] text-ink-muted font-mono">Venta #{inv.saleId}</span>
                      </td>
                      <td data-label="Validada el" className="py-3.5 px-4 text-gray-600 dark:text-gray-300 font-medium">{formatDateTime(inv.validatedAt)}</td>
                      <td data-label="Estado DIAN" className="py-3.5 px-4">
                        <span className={`inline-flex items-center gap-1 text-[10px] px-2.5 py-1 rounded-full font-extrabold ${INVOICE_STATUS_STYLES[inv.status] || ''}`}>
                          {validated ? <CheckCircle2 className="w-3 h-3" /> : inv.status === 'PENDING' ? <Clock className="w-3 h-3" /> : <AlertTriangle className="w-3 h-3" />}
                          {INVOICE_STATUS_LABELS[inv.status] || inv.status}
                        </span>
                      </td>
                      <td data-label="CUFE" className="py-3.5 px-4">
                        {inv.cufe ? (
                          <div className="flex items-center gap-1.5 max-w-[170px]">
                            <span className="font-mono text-[10px] text-ink-soft truncate" title={inv.cufe}>{inv.cufe}</span>
                            <button
                              type="button"
                              onClick={() => copyCufe(inv)}
                              title="Copiar CUFE"
                              aria-label="Copiar CUFE"
                              className="text-gray-400 hover:text-brand shrink-0 cursor-pointer"
                            >
                              {copiedId === inv.id ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                            </button>
                          </div>
                        ) : (
                          <span className="text-gray-400">—</span>
                        )}
                      </td>
                      <td data-label="Acciones" className="py-3.5 px-5">
                        <div className="flex items-center justify-end gap-1.5">
                          {validated && (
                            <>
                              <button type="button" onClick={() => handleDownload(inv, 'pdf')} title="Descargar PDF" aria-label="Descargar PDF" className={iconBtn}>
                                <Download className="w-3.5 h-3.5 text-brand" />
                              </button>
                              <button type="button" onClick={() => handleDownload(inv, 'xml')} title="Descargar XML" aria-label="Descargar XML" className={iconBtn}>
                                <FileCode className="w-3.5 h-3.5 text-blue-500" />
                              </button>
                              <button type="button" onClick={() => { setEmailTarget(inv); setEmailValue(''); }} title="Enviar por correo" aria-label="Enviar por correo" className={iconBtn}>
                                <Mail className="w-3.5 h-3.5 text-emerald-500" />
                              </button>
                            </>
                          )}
                          <button type="button" onClick={() => openDetail(inv)} title="Ver detalle" aria-label="Ver detalle" className={iconBtn}>
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                          {canRetry && (inv.status === 'ERROR' || inv.status === 'PENDING') && (
                            <button
                              type="button"
                              onClick={() => handleRetry(inv)}
                              disabled={retrying === inv.saleId}
                              className="px-3 py-1.5 rounded-xl bg-solid hover:bg-solid-hover disabled:opacity-50 text-white text-[11px] font-bold flex items-center gap-1.5 cursor-pointer"
                            >
                              <RefreshCw className={`w-3 h-3 text-brand ${retrying === inv.saleId ? 'animate-spin' : ''}`} />
                              Reintentar
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {data.totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 p-4 border-t border-line">
            <button
              type="button"
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 bg-gray-100 dark:bg-white/10 disabled:opacity-40 rounded-xl text-[11px] font-bold cursor-pointer"
            >
              Anterior
            </button>
            <span className="text-[11px] font-semibold text-gray-500 tabular-nums">Página {page + 1} de {data.totalPages}</span>
            <button
              type="button"
              onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
              className="px-3 py-1.5 bg-gray-100 dark:bg-white/10 disabled:opacity-40 rounded-xl text-[11px] font-bold cursor-pointer"
            >
              Siguiente
            </button>
          </div>
        )}
      </div>

      {emailTarget && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSendEmail} className="bento-card max-w-sm w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-ink flex items-center gap-2">
                <Mail className="w-5 h-5 text-emerald-500" /> Enviar factura
              </h3>
              <button type="button" onClick={() => setEmailTarget(null)} aria-label="Cerrar" className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-white cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-[11px] text-ink-soft font-medium">
              Se enviará el PDF y el XML de la factura <span className="font-mono font-bold">{emailTarget.factusNumber}</span>.
            </p>
            <div>
              <label htmlFor="invoice-email" className="font-bold text-xs text-gray-700 dark:text-gray-300 block mb-1">Correo de destino:</label>
              <input
                id="invoice-email"
                type="email"
                required
                value={emailValue}
                onChange={e => setEmailValue(e.target.value)}
                className={FIELD_CLASS}
              />
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setEmailTarget(null)} className="w-1/2 py-3 bg-gray-100 dark:bg-line text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer">Cancelar</button>
              <button type="submit" disabled={sendingEmail} className="w-1/2 py-3 bg-solid hover:bg-solid-hover disabled:opacity-50 text-white rounded-2xl text-xs font-extrabold cursor-pointer">
                {sendingEmail ? 'Enviando...' : 'Enviar'}
              </button>
            </div>
          </form>
        </div>
      )}

      {detail && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-lg w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-line">
              <h3 className="font-extrabold text-base text-ink flex items-center gap-2">
                <FileText className="w-5 h-5 text-brand" /> Factura {detail.factusNumber || `de la venta #${detail.saleId}`}
              </h3>
              <button type="button" onClick={() => setDetail(null)} aria-label="Cerrar" className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-white cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="p-3 rounded-2xl bg-page">
                <span className="text-[10px] text-ink-muted block">Estado en la DIAN</span>
                <span className={`inline-block mt-1 text-[10px] px-2 py-0.5 rounded-full font-extrabold ${INVOICE_STATUS_STYLES[detail.status] || ''}`}>
                  {INVOICE_STATUS_LABELS[detail.status] || detail.status}
                </span>
              </div>
              <div className="p-3 rounded-2xl bg-page">
                <span className="text-[10px] text-ink-muted block">Validada el</span>
                <span className="font-bold text-ink block mt-1">{formatDateTime(detail.validatedAt)}</span>
              </div>
            </div>

            {detail.errorMessage && (
              <p className="text-[11px] font-semibold text-red-600 dark:text-red-400 p-3 rounded-2xl bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800/40">
                {detail.errorMessage}
              </p>
            )}

            <div className="p-3 rounded-2xl bg-page space-y-1">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-extrabold uppercase text-ink-muted">Código único CUFE</span>
                {detail.cufe && (
                  <button type="button" onClick={() => copyCufe(detail)} className="text-brand font-extrabold text-[10px] flex items-center gap-1 cursor-pointer">
                    {copiedId === detail.id ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                    {copiedId === detail.id ? 'Copiado' : 'Copiar'}
                  </button>
                )}
              </div>
              <p className="font-mono text-[10px] text-gray-600 dark:text-gray-300 break-all select-all">{detail.cufe || '—'}</p>
            </div>

            {detail.qrCode && (
              <div className="p-4 rounded-2xl border border-line text-center space-y-2">
                <QrCode className="w-10 h-10 mx-auto text-ink" />
                <p className="text-[11px] text-ink-soft">Consulta esta factura directamente en el catálogo de la DIAN.</p>
                <a
                  href={detail.qrCode}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-solid hover:bg-solid-hover text-white text-xs font-bold"
                >
                  Abrir portal DIAN <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            )}

            <button type="button" onClick={() => setDetail(null)} className="w-full py-3 bg-gray-100 dark:bg-line text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer">
              Cerrar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
