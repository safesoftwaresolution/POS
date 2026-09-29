import React, { useState, useEffect, useCallback } from 'react';
import {
  FileCheck2,
  RefreshCw,
  ShieldCheck,
  ShieldAlert,
  Download,
  Mail,
  Eye,
  X,
  Plus,
  Hash,
  CheckCircle2,
  Ban,
  PauseCircle,
  Star
} from 'lucide-react';
import {
  listInvoicesApi,
  getSaleInvoiceApi,
  retryInvoiceApi,
  sendInvoiceEmailApi,
  downloadInvoicePdfApi,
  downloadInvoiceXmlApi,
  getBillingActivationStatusApi,
  requestBillingActivationApi,
  getDianNumberingRangesApi,
  listNumberingRangesApi,
  createNumberingRangeApi,
  deleteNumberingRangeApi,
  toggleNumberingRangeStatusApi,
  updateNumberingRangeCurrentApi,
  setDefaultNumberingRangeApi
} from '../services/billingApi';
import { useAuth } from '../context/AuthContext';
import { useModal } from '../context/ModalContext';

const INVOICE_STATUSES = ['PENDING', 'VALIDATED', 'REJECTED', 'ERROR'];

const INVOICE_STATUS_LABELS = {
  PENDING: 'Pendiente',
  VALIDATED: 'Validada',
  REJECTED: 'Rechazada',
  ERROR: 'Error'
};

const INVOICE_STATUS_STYLES = {
  PENDING: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  VALIDATED: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
  ERROR: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

const ACTIVATION_LABELS = {
  NOT_REQUESTED: 'Sin Solicitar',
  DOCUMENTS_SUBMITTED: 'Documentos Enviados',
  AWAITING_PROVIDER: 'Esperando a Factus',
  ACTIVE: 'Activa',
  REJECTED: 'Rechazada',
  SUSPENDED: 'Suspendida'
};

const ACTIVATION_STYLES = {
  NOT_REQUESTED: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300',
  DOCUMENTS_SUBMITTED: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  AWAITING_PROVIDER: 'bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400 border border-blue-500/20',
  ACTIVE: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
  SUSPENDED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

const EMPTY_RANGE_FORM = { document: '', prefix: '', resolutionNumber: '', current: '' };

export default function InvoicingBento() {
  const { user } = useAuth();
  const canSeeActivation = user?.role === 'ADMINISTRATOR' || user?.role === 'SUPERVISOR';
  const [tab, setTab] = useState('invoices');

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Facturación DIAN</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Facturas electrónicas emitidas mediante Factus y habilitación del local ante la DIAN</p>
        </div>

        {canSeeActivation && (
          <div className="flex items-center gap-2">
            <button
              onClick={() => setTab('invoices')}
              className={`px-3.5 py-2 rounded-2xl text-xs font-bold cursor-pointer ${tab === 'invoices' ? 'bg-[#0A0A0A] text-white' : 'bg-white dark:bg-white/10 text-gray-600 dark:text-gray-300 border border-[#EAEAEA] dark:border-transparent'}`}
            >
              Facturas
            </button>
            <button
              onClick={() => setTab('activation')}
              className={`px-3.5 py-2 rounded-2xl text-xs font-bold cursor-pointer ${tab === 'activation' ? 'bg-[#0A0A0A] text-white' : 'bg-white dark:bg-white/10 text-gray-600 dark:text-gray-300 border border-[#EAEAEA] dark:border-transparent'}`}
            >
              Habilitación DIAN
            </button>
          </div>
        )}
      </div>

      {tab === 'invoices' ? <InvoicesTab role={user?.role} /> : <ActivationTab role={user?.role} />}
    </div>
  );
}

// ==================== Facturas ====================

function InvoicesTab({ role }) {
  const { notify } = useModal();
  const canRetry = role === 'ADMINISTRATOR' || role === 'SUPERVISOR';
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [retrying, setRetrying] = useState(null);
  const [emailTarget, setEmailTarget] = useState(null);
  const [emailValue, setEmailValue] = useState('');
  const [sendingEmail, setSendingEmail] = useState(false);
  const [detail, setDetail] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await listInvoicesApi(statusFilter, page, 20));
    } catch (e) {
      console.warn('Error cargando facturas:', e);
      setData({ content: [], totalPages: 0 });
    } finally {
      setLoading(false);
    }
  }, [statusFilter, page]);

  useEffect(() => { load(); }, [load]);

  const handleRetry = async (invoice) => {
    setRetrying(invoice.saleId);
    try {
      await retryInvoiceApi(invoice.saleId);
      await load();
    } catch (err) {
      notify('Error reintentando la factura: ' + err.message);
    } finally {
      setRetrying(null);
    }
  };

  const handleDownload = async (invoice, type) => {
    try {
      const blob = type === 'pdf' ? await downloadInvoicePdfApi(invoice.saleId) : await downloadInvoiceXmlApi(invoice.saleId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `factura-${invoice.saleId}.${type}`;
      if (type === 'pdf') a.target = '_blank';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
    } catch (err) {
      notify(`Error descargando el ${type.toUpperCase()}: ` + err.message);
    }
  };

  const openEmailModal = (invoice) => {
    setEmailTarget(invoice);
    setEmailValue('');
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

  const invoices = data.content || [];

  return (
    <div className="bento-card p-6 space-y-4">
      <div className="flex items-center justify-between border-b border-[#EAEAEA] dark:border-[#272730] pb-3 flex-wrap gap-3">
        <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6]">Monitoreo de Emisión DIAN</h3>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setStatusFilter('')}
            className={`px-3 py-1.5 rounded-full text-[11px] font-bold cursor-pointer ${statusFilter === '' ? 'bg-[#0A0A0A] text-white' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'}`}
          >
            Todas
          </button>
          {INVOICE_STATUSES.map(s => (
            <button
              key={s}
              onClick={() => { setStatusFilter(s); setPage(0); }}
              className={`px-3 py-1.5 rounded-full text-[11px] font-bold cursor-pointer ${statusFilter === s ? 'bg-[#0A0A0A] text-white' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'}`}
            >
              {INVOICE_STATUS_LABELS[s]}
            </button>
          ))}
          <button
            onClick={load}
            className="text-xs font-bold text-[#FF5520] dark:text-[#FF5520] flex items-center gap-1 hover:underline cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Sincronizar
          </button>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Consultando facturas...</div>
      ) : invoices.length === 0 ? (
        <div className="py-12 text-center space-y-2 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
          <FileCheck2 className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
          <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay comprobantes de factura electrónica generados aún</p>
          <p className="text-[11px] text-gray-400 dark:text-gray-500">
            Al realizar una venta en la caja seleccionando un cliente con NIT/Cédula y marcando transmitir DIAN, la factura aparecerá aquí.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-800 text-gray-400 dark:text-gray-500 uppercase tracking-wider font-extrabold text-[10px]">
                <th className="py-3 px-3">Comprobante</th>
                <th className="py-3 px-3">CUFE</th>
                <th className="py-3 px-3">Venta</th>
                <th className="py-3 px-3 text-center">Estado DIAN</th>
                <th className="py-3 px-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800 font-medium">
              {invoices.map(inv => (
                <tr key={inv.id} className="hover:bg-gray-50/60 dark:hover:bg-white/5">
                  <td className="py-3 px-3 font-mono text-[11px] text-[#0A0A0A] dark:text-[#F4F4F6]">{inv.factusNumber || '—'}</td>
                  <td className="py-3 px-3 font-mono text-[10px] text-gray-500 dark:text-gray-400 max-w-[160px] truncate" title={inv.cufe}>{inv.cufe || '—'}</td>
                  <td className="py-3 px-3 text-gray-600 dark:text-gray-300">#{inv.saleId}</td>
                  <td className="py-3 px-3 text-center">
                    <span className={`text-[10px] px-2 py-0.5 rounded-full font-extrabold ${INVOICE_STATUS_STYLES[inv.status] || ''}`}>
                      {INVOICE_STATUS_LABELS[inv.status] || inv.status}
                    </span>
                  </td>
                  <td className="py-3 px-3">
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={() => openDetail(inv)} title="Ver detalle" className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                        <Eye className="w-4 h-4" />
                      </button>
                      {canRetry && (inv.status === 'ERROR' || inv.status === 'PENDING') && (
                        <button
                          onClick={() => handleRetry(inv)}
                          disabled={retrying === inv.saleId}
                          title="Reintentar emisión"
                          className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer disabled:opacity-40"
                        >
                          <RefreshCw className={`w-4 h-4 ${retrying === inv.saleId ? 'animate-spin' : ''}`} />
                        </button>
                      )}
                      {inv.status === 'VALIDATED' && (
                        <>
                          <button onClick={() => handleDownload(inv, 'pdf')} title="Descargar PDF" className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                            <Download className="w-4 h-4" />
                          </button>
                          <button onClick={() => openEmailModal(inv)} title="Enviar por correo" className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                            <Mail className="w-4 h-4" />
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 pt-4">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 bg-gray-100 dark:bg-white/10 disabled:opacity-40 rounded-xl text-[11px] font-bold cursor-pointer"
              >
                Anterior
              </button>
              <span className="text-[11px] font-semibold text-gray-500">Página {page + 1} de {data.totalPages}</span>
              <button
                onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
                disabled={page >= data.totalPages - 1}
                className="px-3 py-1.5 bg-gray-100 dark:bg-white/10 disabled:opacity-40 rounded-xl text-[11px] font-bold cursor-pointer"
              >
                Siguiente
              </button>
            </div>
          )}
        </div>
      )}

      {/* Email modal */}
      {emailTarget && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSendEmail} className="bento-card max-w-sm w-full bg-white dark:bg-[#17171B] p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6]">Enviar factura por correo</h3>
              <button type="button" onClick={() => setEmailTarget(null)} className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-white cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>
            <input
              type="email"
              required
              value={emailValue}
              onChange={e => setEmailValue(e.target.value)}
              placeholder="cliente@correo.com"
              className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-xl text-xs text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
            />
            <button
              type="submit"
              disabled={sendingEmail}
              className="w-full py-2.5 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white rounded-2xl text-xs font-extrabold cursor-pointer"
            >
              {sendingEmail ? 'Enviando...' : 'Enviar'}
            </button>
          </form>
        </div>
      )}

      {/* Detail modal */}
      {detail && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-md w-full bg-white dark:bg-[#17171B] p-6 rounded-3xl shadow-2xl space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6]">Factura #{detail.factusNumber || detail.saleId}</h3>
              <button onClick={() => setDetail(null)} className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-white cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="text-xs space-y-1.5">
              <p><span className="font-bold text-gray-500">Estado:</span> {INVOICE_STATUS_LABELS[detail.status] || detail.status}</p>
              <p className="break-all"><span className="font-bold text-gray-500">CUFE:</span> {detail.cufe || '—'}</p>
              {detail.errorMessage && <p className="text-red-500 dark:text-red-400"><span className="font-bold">Error:</span> {detail.errorMessage}</p>}
              <p><span className="font-bold text-gray-500">Validada:</span> {detail.validatedAt || '—'}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ==================== Habilitación DIAN ====================

function ActivationTab({ role }) {
  const { notify, confirm } = useModal();
  const isAdmin = role === 'ADMINISTRATOR';
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [requesting, setRequesting] = useState(false);

  const [ranges, setRanges] = useState([]);
  const [rangesLoading, setRangesLoading] = useState(true);
  const [dianRanges, setDianRanges] = useState(null);
  const [dianLoading, setDianLoading] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_RANGE_FORM);
  const [creating, setCreating] = useState(false);
  const [editingCurrent, setEditingCurrent] = useState(null);
  const [currentValue, setCurrentValue] = useState('');

  const loadStatus = useCallback(async () => {
    setLoading(true);
    try {
      setStatus(await getBillingActivationStatusApi());
    } catch (e) {
      console.warn('Error cargando estado de facturación:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadRanges = useCallback(async () => {
    setRangesLoading(true);
    try {
      setRanges(await listNumberingRangesApi());
    } catch (e) {
      console.warn('Error cargando rangos de numeración:', e);
      setRanges([]);
    } finally {
      setRangesLoading(false);
    }
  }, []);

  useEffect(() => { loadStatus(); loadRanges(); }, [loadStatus, loadRanges]);

  const handleRequestActivation = async () => {
    setRequesting(true);
    try {
      setStatus(await requestBillingActivationApi());
    } catch (err) {
      notify('Error solicitando la activación: ' + err.message);
    } finally {
      setRequesting(false);
    }
  };

  const loadDianRanges = async () => {
    setDianLoading(true);
    try {
      setDianRanges(await getDianNumberingRangesApi());
    } catch (err) {
      notify('Error consultando resoluciones DIAN: ' + err.message);
      setDianRanges([]);
    } finally {
      setDianLoading(false);
    }
  };

  const handleCreateRange = async (e) => {
    e.preventDefault();
    setCreating(true);
    try {
      await createNumberingRangeApi({
        document: createForm.document.trim(),
        prefix: createForm.prefix.trim(),
        resolutionNumber: createForm.resolutionNumber.trim(),
        current: Number(createForm.current)
      });
      setShowCreateForm(false);
      setCreateForm(EMPTY_RANGE_FORM);
      await Promise.all([loadRanges(), loadStatus()]);
    } catch (err) {
      notify('Error registrando el rango: ' + err.message);
    } finally {
      setCreating(false);
    }
  };

  const handleToggleStatus = async (range) => {
    try {
      await toggleNumberingRangeStatusApi(range.id);
      await loadRanges();
    } catch (err) {
      notify('Error cambiando el estado del rango: ' + err.message);
    }
  };

  const handleDelete = async (range) => {
    const ok = await confirm(`¿Eliminar el rango ${range.prefix} (resolución ${range.resolutionNumber})?`);
    if (!ok) return;
    try {
      await deleteNumberingRangeApi(range.id);
      await loadRanges();
    } catch (err) {
      notify('Error eliminando el rango: ' + err.message);
    }
  };

  const handleSetDefault = async (range) => {
    try {
      await setDefaultNumberingRangeApi(range.id);
      await Promise.all([loadRanges(), loadStatus()]);
    } catch (err) {
      notify('Error eligiendo el rango: ' + err.message);
    }
  };

  const openEditCurrent = (range) => {
    setEditingCurrent(range.id);
    setCurrentValue(String(range.current ?? ''));
  };

  const handleSaveCurrent = async (range) => {
    try {
      await updateNumberingRangeCurrentApi(range.id, Number(currentValue));
      setEditingCurrent(null);
      await loadRanges();
    } catch (err) {
      notify('Error actualizando el consecutivo: ' + err.message);
    }
  };

  const activeRangeId = status?.environment === 'PRODUCTION' ? status?.productionNumberingRangeId : status?.sandboxNumberingRangeId;

  return (
    <div className="space-y-6">
      {/* Activation status */}
      <div className="bento-card p-6 space-y-4">
        <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
          {status?.status === 'ACTIVE' ? <ShieldCheck className="w-4 h-4 text-emerald-500" /> : <ShieldAlert className="w-4 h-4 text-amber-500" />}
          Estado de Habilitación
        </h3>

        {loading ? (
          <p className="text-xs font-bold text-gray-400 dark:text-gray-500">Consultando...</p>
        ) : (
          <>
            <div className="flex items-center gap-3 flex-wrap">
              <span className={`text-[11px] px-3 py-1 rounded-full font-extrabold ${ACTIVATION_STYLES[status?.status] || ''}`}>
                {ACTIVATION_LABELS[status?.status] || status?.status}
              </span>
              <span className="text-[11px] font-semibold text-gray-500 dark:text-gray-400">Entorno activo: {status?.environment}</span>
            </div>

            {status?.statusReason && (
              <p className="text-[11px] text-red-500 dark:text-red-400 font-semibold">Motivo: {status.statusReason}</p>
            )}

            {isAdmin && (status?.status === 'NOT_REQUESTED' || status?.status === 'REJECTED') && (
              <button
                onClick={handleRequestActivation}
                disabled={requesting}
                className="px-4 py-2.5 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white rounded-2xl text-xs font-bold cursor-pointer"
              >
                {requesting ? 'Enviando...' : 'Solicitar Activación'}
              </button>
            )}
            {isAdmin && (status?.status === 'NOT_REQUESTED' || status?.status === 'REJECTED') && (
              <p className="text-[11px] text-gray-400 dark:text-gray-500">Necesitas al menos un documento (RUT, cámara de comercio, etc.) subido en <a href="/documents" className="text-[#FF5520] underline">Documentos</a> antes de solicitar.</p>
            )}
          </>
        )}
      </div>

      {/* Numbering ranges */}
      <div className="bento-card p-6 space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
            <Hash className="w-4 h-4 text-[#FF5520]" /> Rangos de Numeración DIAN
          </h3>
          <div className="flex items-center gap-2">
            <button onClick={loadDianRanges} className="text-xs font-bold text-gray-500 dark:text-gray-400 flex items-center gap-1 hover:underline cursor-pointer">
              {dianLoading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Eye className="w-3.5 h-3.5" />} Consultar resoluciones DIAN
            </button>
            {isAdmin && (
              <button
                onClick={() => setShowCreateForm(v => !v)}
                className="px-3 py-1.5 bg-[#0A0A0A] hover:bg-[#202020] text-white rounded-xl text-[11px] font-bold flex items-center gap-1.5 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" /> Registrar Rango
              </button>
            )}
          </div>
        </div>

        {dianRanges && (
          <div className="p-3 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-[#EAEAEA] dark:border-[#272730] text-[11px] space-y-1.5">
            <p className="font-extrabold text-gray-600 dark:text-gray-300">Resoluciones autorizadas por la DIAN para este local:</p>
            {dianRanges.length === 0 ? (
              <p className="text-gray-400">No hay resoluciones asociadas todavía.</p>
            ) : dianRanges.map((r, idx) => (
              <p key={idx} className="font-mono text-gray-500 dark:text-gray-400">
                {r.prefix} · Res. {r.resolutionNumber} · {r.startDate} a {r.endDate}
              </p>
            ))}
          </div>
        )}

        {isAdmin && showCreateForm && (
          <form onSubmit={handleCreateRange} className="p-4 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-[#EAEAEA] dark:border-[#272730] space-y-3">
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Código de documento:</label>
                <input required value={createForm.document} onChange={e => setCreateForm({ ...createForm, document: e.target.value })}
                  placeholder="01" className="w-full p-2.5 bg-white dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-xl text-xs focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]" />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Prefijo:</label>
                <input required value={createForm.prefix} onChange={e => setCreateForm({ ...createForm, prefix: e.target.value })}
                  placeholder="SETP" className="w-full p-2.5 bg-white dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-xl text-xs focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]" />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Número de resolución:</label>
                <input required value={createForm.resolutionNumber} onChange={e => setCreateForm({ ...createForm, resolutionNumber: e.target.value })}
                  placeholder="18764000001234" className="w-full p-2.5 bg-white dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-xl text-xs focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]" />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Consecutivo actual:</label>
                <input required type="number" min="1" value={createForm.current} onChange={e => setCreateForm({ ...createForm, current: e.target.value })}
                  placeholder="1" className="w-full p-2.5 bg-white dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-xl text-xs focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]" />
              </div>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setShowCreateForm(false)} className="flex-1 py-2 bg-white dark:bg-[#272730] border border-[#EAEAEA] dark:border-transparent rounded-xl text-[11px] font-bold cursor-pointer">Cancelar</button>
              <button type="submit" disabled={creating} className="flex-1 py-2 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white rounded-xl text-[11px] font-extrabold cursor-pointer">
                {creating ? 'Registrando...' : 'Registrar'}
              </button>
            </div>
          </form>
        )}

        {rangesLoading ? (
          <p className="text-xs font-bold text-gray-400 dark:text-gray-500 py-6 text-center">Consultando rangos...</p>
        ) : ranges.length === 0 ? (
          <div className="py-8 text-center space-y-2 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-gray-700 dark:text-gray-300">Sin rangos de numeración registrados</p>
          </div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[720px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-gray-400 dark:text-gray-500 uppercase">
                  <th className="px-2 py-2">Prefijo</th>
                  <th className="px-2 py-2">Documento</th>
                  <th className="px-2 py-2">Rango</th>
                  <th className="px-2 py-2">Actual</th>
                  <th className="px-2 py-2 text-center">Estado</th>
                  {isAdmin && <th className="px-2 py-2 text-right">Acciones</th>}
                </tr>
              </thead>
              <tbody>
                {ranges.map(r => (
                  <tr key={r.id} className="border-t border-[#EAEAEA] dark:border-[#272730]">
                    <td className="px-2 py-3 font-extrabold text-[#0A0A0A] dark:text-[#F4F4F6]">
                      {r.prefix} {activeRangeId === r.id && <Star className="w-3 h-3 text-[#FF5520] inline fill-current" />}
                    </td>
                    <td className="px-2 py-3 text-gray-500 dark:text-gray-400">{r.documentName || r.document}</td>
                    <td className="px-2 py-3 font-mono text-gray-500 dark:text-gray-400">{r.from ?? '—'} - {r.to ?? '—'}</td>
                    <td className="px-2 py-3">
                      {editingCurrent === r.id ? (
                        <div className="flex items-center gap-1">
                          <input
                            type="number"
                            value={currentValue}
                            onChange={e => setCurrentValue(e.target.value)}
                            className="w-20 p-1.5 bg-white dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-lg text-xs"
                          />
                          <button onClick={() => handleSaveCurrent(r)} className="text-emerald-500 cursor-pointer"><CheckCircle2 className="w-4 h-4" /></button>
                          <button onClick={() => setEditingCurrent(null)} className="text-gray-400 cursor-pointer"><X className="w-4 h-4" /></button>
                        </div>
                      ) : (
                        <span
                          onClick={() => isAdmin && openEditCurrent(r)}
                          className={`font-mono ${isAdmin ? 'cursor-pointer hover:underline' : ''}`}
                        >
                          {r.current ?? '—'}
                        </span>
                      )}
                    </td>
                    <td className="px-2 py-3 text-center">
                      {r.isExpired
                        ? <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400">Vencido</span>
                        : r.isActive
                          ? <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">Activo</span>
                          : <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400">Inactivo</span>}
                    </td>
                    {isAdmin && (
                      <td className="px-2 py-3">
                        <div className="flex items-center justify-end gap-1">
                          {activeRangeId !== r.id && (
                            <button onClick={() => handleSetDefault(r)} title="Elegir para facturar" className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                              <Star className="w-4 h-4" />
                            </button>
                          )}
                          <button onClick={() => handleToggleStatus(r)} title={r.isActive ? 'Desactivar' : 'Activar'} className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                            {r.isActive ? <PauseCircle className="w-4 h-4" /> : <CheckCircle2 className="w-4 h-4" />}
                          </button>
                          <button onClick={() => handleDelete(r)} title="Eliminar" className="p-1.5 text-gray-400 hover:text-red-500 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                            <Ban className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
