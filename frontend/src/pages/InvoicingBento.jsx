import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  FileText,
  RefreshCw,
  ShieldCheck,
  ShieldAlert,
  Download,
  FileCode,
  Mail,
  Eye,
  X,
  Plus,
  Hash,
  CheckCircle2,
  Ban,
  PauseCircle,
  Star,
  Search,
  Copy,
  Check,
  ExternalLink,
  QrCode,
  AlertTriangle,
  Building2,
  Clock
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
import { deliverBlob } from '../utils/downloadBlob';
import { useAuth } from '../context/AuthContext';
import { useModal } from '../context/ModalContext';

const INVOICE_STATUSES = ['VALIDATED', 'PENDING', 'ERROR', 'REJECTED'];

const INVOICE_STATUS_LABELS = {
  PENDING: 'Pendiente',
  VALIDATED: 'Validada',
  REJECTED: 'Rechazada',
  ERROR: 'Con error'
};

const INVOICE_STATUS_STYLES = {
  PENDING: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  VALIDATED: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
  ERROR: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

const ACTIVATION_LABELS = {
  NOT_REQUESTED: 'Sin solicitar',
  DOCUMENTS_SUBMITTED: 'Documentos enviados',
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

const FIELD_CLASS =
  'w-full p-2.5 bg-white dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-xl text-xs font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]';

const formatDateTime = (value) => (value ? new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—');

function TabButton({ active, onClick, icon: Icon, label, count }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`pb-3 text-sm font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
        active
          ? 'border-[#FF5520] text-[#FF5520]'
          : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-[#0A0A0A] dark:hover:text-white'
      }`}
    >
      <Icon className="w-4 h-4" />
      <span>{label}</span>
      {count != null && (
        <span className="ml-0.5 px-2 py-0.5 rounded-full text-[10px] font-bold tabular-nums bg-gray-100 dark:bg-white/10 text-[#0A0A0A] dark:text-[#F4F4F6]">
          {count}
        </span>
      )}
    </button>
  );
}

function KpiCard({ label, value, hint, hintClass = 'text-gray-500 dark:text-gray-400', icon: Icon, accent }) {
  return (
    <div className="bento-card p-5 flex flex-col justify-between gap-3 min-w-0">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[10px] font-extrabold uppercase tracking-wider text-gray-500 dark:text-gray-400">{label}</span>
        <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${accent}`}>
          <Icon className="w-4 h-4" />
        </div>
      </div>
      <div className="min-w-0">
        <div className={`font-black text-[#0A0A0A] dark:text-[#F4F4F6] tabular-nums truncate ${String(value).length > 8 ? 'text-xl leading-[2.25rem]' : 'text-3xl'}`}>{value}</div>
        <div className={`text-[11px] font-semibold mt-0.5 truncate ${hintClass}`}>{hint}</div>
      </div>
    </div>
  );
}

export default function InvoicingBento() {
  const { user } = useAuth();
  const role = user?.role;
  const canSeeActivation = role === 'ADMINISTRATOR' || role === 'SUPERVISOR';
  const [tab, setTab] = useState('invoices');
  const [refreshKey, setRefreshKey] = useState(0);

  const [totals, setTotals] = useState({ VALIDATED: null, PENDING: null, ERROR: null, REJECTED: null });
  const [activation, setActivation] = useState(null);
  const [ranges, setRanges] = useState([]);
  const [rangesLoading, setRangesLoading] = useState(false);

  const loadTotals = useCallback(async () => {
    const entries = await Promise.all(
      INVOICE_STATUSES.map(async (s) => {
        try {
          const page = await listInvoicesApi(s, 0, 1);
          return [s, page.totalElements ?? (page.content || []).length];
        } catch {
          return [s, null];
        }
      })
    );
    setTotals(Object.fromEntries(entries));
  }, []);

  const loadBillingState = useCallback(async () => {
    if (!canSeeActivation) return;
    setRangesLoading(true);
    try {
      const [act, rgs] = await Promise.all([
        getBillingActivationStatusApi().catch(() => null),
        listNumberingRangesApi().catch(() => [])
      ]);
      setActivation(act);
      setRanges(Array.isArray(rgs) ? rgs : []);
    } finally {
      setRangesLoading(false);
    }
  }, [canSeeActivation]);

  useEffect(() => { loadTotals(); loadBillingState(); }, [loadTotals, loadBillingState, refreshKey]);

  const activeRangeId = activation?.environment === 'PRODUCTION' ? activation?.productionNumberingRangeId : activation?.sandboxNumberingRangeId;
  const activeRange = ranges.find(r => r.id === activeRangeId);
  const remaining = activeRange && activeRange.to != null && activeRange.current != null ? activeRange.to - activeRange.current + 1 : null;

  const incidents = ['PENDING', 'ERROR', 'REJECTED'].reduce((sum, s) => sum + (totals[s] || 0), 0);
  const totalsLoaded = Object.values(totals).every(v => v != null);
  const totalInvoices = INVOICE_STATUSES.reduce((sum, s) => sum + (totals[s] || 0), 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-2xl font-black text-[#0A0A0A] dark:text-white tracking-tight">Facturación Electrónica DIAN</h1>
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-[#FF5520] text-white">Factus</span>
          </div>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium mt-1">
            Seguimiento de facturas, CUFE y numeración oficial de tu local ante la DIAN
          </p>
        </div>

        <button
          type="button"
          onClick={() => setRefreshKey(k => k + 1)}
          className="h-10 px-4 rounded-2xl border border-[#EAEAEA] dark:border-[#272730] bg-white dark:bg-[#17171B] text-gray-600 dark:text-gray-300 hover:text-[#0A0A0A] dark:hover:text-white text-xs font-bold flex items-center gap-2 cursor-pointer self-start md:self-auto"
        >
          <RefreshCw className="w-3.5 h-3.5" /> Sincronizar con la DIAN
        </button>
      </div>

      <div className={`grid grid-cols-1 sm:grid-cols-2 ${canSeeActivation ? 'lg:grid-cols-4' : 'lg:grid-cols-2'} gap-4`}>
        <KpiCard
          label="Facturas validadas"
          value={totals.VALIDATED ?? '—'}
          hint={totalsLoaded && totalInvoices > 0 ? `${totals.VALIDATED} de ${totalInvoices} emitidas` : 'Aceptadas por la DIAN'}
          hintClass="text-emerald-600 dark:text-emerald-400"
          icon={CheckCircle2}
          accent="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
        />
        <KpiCard
          label="Pendientes y errores"
          value={totalsLoaded ? incidents : '—'}
          hint={totalsLoaded ? (incidents > 0 ? 'Requieren revisión o reintento' : 'Sin incidencias en cola') : 'Consultando...'}
          hintClass={incidents > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-gray-500 dark:text-gray-400'}
          icon={AlertTriangle}
          accent="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400"
        />
        {canSeeActivation && (
          <>
            <KpiCard
              label="Consecutivos disponibles"
              value={remaining ?? '—'}
              hint={activeRange ? `Prefijo ${activeRange.prefix} · hasta ${activeRange.to}` : 'Sin rango elegido para facturar'}
              icon={Hash}
              accent="bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400"
            />
            <KpiCard
              label="Habilitación del local"
              value={activation ? (ACTIVATION_LABELS[activation.status] || activation.status) : '—'}
              hint={activation ? `Entorno ${activation.environment}` : 'Consultando...'}
              hintClass={activation?.status === 'ACTIVE' ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-500 dark:text-gray-400'}
              icon={activation?.status === 'ACTIVE' ? ShieldCheck : ShieldAlert}
              accent={activation?.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'}
            />
          </>
        )}
      </div>

      {canSeeActivation && (
        <div className="border-b border-[#EAEAEA] dark:border-[#272730] flex items-center gap-6 overflow-x-auto">
          <TabButton active={tab === 'invoices'} onClick={() => setTab('invoices')} icon={FileText} label="Facturas emitidas" count={totalsLoaded ? totalInvoices : null} />
          <TabButton active={tab === 'activation'} onClick={() => setTab('activation')} icon={Building2} label="Habilitación y rangos" count={ranges.length || null} />
        </div>
      )}

      {tab === 'invoices' || !canSeeActivation ? (
        <InvoicesTab role={role} refreshKey={refreshKey} onChanged={loadTotals} />
      ) : (
        <ActivationTab
          role={role}
          activation={activation}
          ranges={ranges}
          rangesLoading={rangesLoading}
          activeRangeId={activeRangeId}
          onReload={loadBillingState}
        />
      )}
    </div>
  );
}

// ==================== Facturas ====================

function InvoicesTab({ role, refreshKey, onChanged }) {
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

  useEffect(() => { load(); }, [load, refreshKey]);

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

  const iconBtn = 'p-1.5 rounded-lg border border-[#EAEAEA] dark:border-[#272730] hover:border-[#0A0A0A] dark:hover:border-[#FF5520] text-gray-500 dark:text-gray-400 cursor-pointer transition-colors';

  return (
    <div className="space-y-4">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <div className="relative w-full lg:max-w-md">
          <Search className="w-4 h-4 text-gray-400 dark:text-gray-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por número de factura, CUFE o venta..."
            aria-label="Buscar facturas"
            className="w-full h-10 pl-10 pr-4 bg-white dark:bg-[#17171B] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-xs font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
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
                  ? 'bg-[#0A0A0A] text-white shadow-sm'
                  : 'bg-white dark:bg-[#17171B] text-gray-600 dark:text-gray-300 border border-[#EAEAEA] dark:border-[#272730] hover:border-[#D0D0CE]'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="bento-card overflow-hidden">
        {loading ? (
          <div className="py-14 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Consultando facturas...</div>
        ) : invoices.length === 0 ? (
          <div className="py-14 px-6 text-center space-y-2">
            <div className="w-12 h-12 rounded-2xl bg-gray-100 dark:bg-white/5 text-gray-400 flex items-center justify-center mx-auto">
              <FileText className="w-6 h-6" />
            </div>
            <p className="text-sm font-bold text-[#0A0A0A] dark:text-[#F4F4F6]">
              {search ? 'Ninguna factura coincide con tu búsqueda' : 'Aún no hay facturas electrónicas'}
            </p>
            <p className="text-[11px] text-gray-500 dark:text-gray-400 max-w-sm mx-auto">
              {search
                ? 'La búsqueda revisa las facturas de esta página. Prueba con otro número o cambia de página.'
                : 'Al cobrar en la caja con la opción de factura electrónica activada, la factura aparecerá aquí.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs min-w-[760px]">
              <thead>
                <tr className="border-b border-[#EAEAEA] dark:border-[#272730] bg-gray-50/70 dark:bg-white/[0.03] text-[10px] font-extrabold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                  <th className="py-3.5 px-5">Factura</th>
                  <th className="py-3.5 px-4">Validada el</th>
                  <th className="py-3.5 px-4">Estado DIAN</th>
                  <th className="py-3.5 px-4">CUFE</th>
                  <th className="py-3.5 px-5 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#EAEAEA] dark:divide-[#272730]">
                {invoices.map(inv => {
                  const validated = inv.status === 'VALIDATED';
                  return (
                    <tr key={inv.id} className="hover:bg-gray-50/60 dark:hover:bg-white/5">
                      <td className="py-3.5 px-5">
                        <span className="font-mono font-bold text-[#0A0A0A] dark:text-[#F4F4F6] block">{inv.factusNumber || '—'}</span>
                        <span className="text-[10px] text-gray-400 dark:text-gray-500 font-mono">Venta #{inv.saleId}</span>
                      </td>
                      <td className="py-3.5 px-4 text-gray-600 dark:text-gray-300 font-medium">{formatDateTime(inv.validatedAt)}</td>
                      <td className="py-3.5 px-4">
                        <span className={`inline-flex items-center gap-1 text-[10px] px-2.5 py-1 rounded-full font-extrabold ${INVOICE_STATUS_STYLES[inv.status] || ''}`}>
                          {validated ? <CheckCircle2 className="w-3 h-3" /> : inv.status === 'PENDING' ? <Clock className="w-3 h-3" /> : <AlertTriangle className="w-3 h-3" />}
                          {INVOICE_STATUS_LABELS[inv.status] || inv.status}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        {inv.cufe ? (
                          <div className="flex items-center gap-1.5 max-w-[170px]">
                            <span className="font-mono text-[10px] text-gray-500 dark:text-gray-400 truncate" title={inv.cufe}>{inv.cufe}</span>
                            <button
                              type="button"
                              onClick={() => copyCufe(inv)}
                              title="Copiar CUFE"
                              aria-label="Copiar CUFE"
                              className="text-gray-400 hover:text-[#FF5520] shrink-0 cursor-pointer"
                            >
                              {copiedId === inv.id ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                            </button>
                          </div>
                        ) : (
                          <span className="text-gray-400">—</span>
                        )}
                      </td>
                      <td className="py-3.5 px-5">
                        <div className="flex items-center justify-end gap-1.5">
                          {validated && (
                            <>
                              <button type="button" onClick={() => handleDownload(inv, 'pdf')} title="Descargar PDF" aria-label="Descargar PDF" className={iconBtn}>
                                <Download className="w-3.5 h-3.5 text-[#FF5520]" />
                              </button>
                              <button type="button" onClick={() => handleDownload(inv, 'xml')} title="Descargar XML" aria-label="Descargar XML" className={iconBtn}>
                                <FileCode className="w-3.5 h-3.5 text-indigo-500" />
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
                              className="px-3 py-1.5 rounded-xl bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white text-[11px] font-bold flex items-center gap-1.5 cursor-pointer"
                            >
                              <RefreshCw className={`w-3 h-3 text-[#FF5520] ${retrying === inv.saleId ? 'animate-spin' : ''}`} />
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
          <div className="flex items-center justify-center gap-3 p-4 border-t border-[#EAEAEA] dark:border-[#272730]">
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
          <form onSubmit={handleSendEmail} className="bento-card max-w-sm w-full bg-white dark:bg-[#17171B] p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
                <Mail className="w-5 h-5 text-emerald-500" /> Enviar factura
              </h3>
              <button type="button" onClick={() => setEmailTarget(null)} aria-label="Cerrar" className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-white cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-[11px] text-gray-500 dark:text-gray-400 font-medium">
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
              <button type="button" onClick={() => setEmailTarget(null)} className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer">Cancelar</button>
              <button type="submit" disabled={sendingEmail} className="w-1/2 py-3 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white rounded-2xl text-xs font-extrabold cursor-pointer">
                {sendingEmail ? 'Enviando...' : 'Enviar'}
              </button>
            </div>
          </form>
        </div>
      )}

      {detail && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-lg w-full bg-white dark:bg-[#17171B] p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-[#EAEAEA] dark:border-[#272730]">
              <h3 className="font-extrabold text-base text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
                <FileText className="w-5 h-5 text-[#FF5520]" /> Factura {detail.factusNumber || `de la venta #${detail.saleId}`}
              </h3>
              <button type="button" onClick={() => setDetail(null)} aria-label="Cerrar" className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-white cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="p-3 rounded-2xl bg-[#F7F7F5] dark:bg-[#0E0E12]">
                <span className="text-[10px] text-gray-400 dark:text-gray-500 block">Estado en la DIAN</span>
                <span className={`inline-block mt-1 text-[10px] px-2 py-0.5 rounded-full font-extrabold ${INVOICE_STATUS_STYLES[detail.status] || ''}`}>
                  {INVOICE_STATUS_LABELS[detail.status] || detail.status}
                </span>
              </div>
              <div className="p-3 rounded-2xl bg-[#F7F7F5] dark:bg-[#0E0E12]">
                <span className="text-[10px] text-gray-400 dark:text-gray-500 block">Validada el</span>
                <span className="font-bold text-[#0A0A0A] dark:text-[#F4F4F6] block mt-1">{formatDateTime(detail.validatedAt)}</span>
              </div>
            </div>

            {detail.errorMessage && (
              <p className="text-[11px] font-semibold text-red-600 dark:text-red-400 p-3 rounded-2xl bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800/40">
                {detail.errorMessage}
              </p>
            )}

            <div className="p-3 rounded-2xl bg-[#F7F7F5] dark:bg-[#0E0E12] space-y-1">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-extrabold uppercase text-gray-400 dark:text-gray-500">Código único CUFE</span>
                {detail.cufe && (
                  <button type="button" onClick={() => copyCufe(detail)} className="text-[#FF5520] font-extrabold text-[10px] flex items-center gap-1 cursor-pointer">
                    {copiedId === detail.id ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                    {copiedId === detail.id ? 'Copiado' : 'Copiar'}
                  </button>
                )}
              </div>
              <p className="font-mono text-[10px] text-gray-600 dark:text-gray-300 break-all select-all">{detail.cufe || '—'}</p>
            </div>

            {detail.qrCode && (
              <div className="p-4 rounded-2xl border border-[#EAEAEA] dark:border-[#272730] text-center space-y-2">
                <QrCode className="w-10 h-10 mx-auto text-[#0A0A0A] dark:text-white" />
                <p className="text-[11px] text-gray-500 dark:text-gray-400">Consulta esta factura directamente en el catálogo de la DIAN.</p>
                <a
                  href={detail.qrCode}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#0A0A0A] hover:bg-[#202020] text-white text-xs font-bold"
                >
                  Abrir portal DIAN <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            )}

            <button type="button" onClick={() => setDetail(null)} className="w-full py-3 bg-gray-100 dark:bg-[#272730] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer">
              Cerrar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ==================== Habilitación y rangos ====================

function ActivationTab({ role, activation, ranges, rangesLoading, activeRangeId, onReload }) {
  const { notify, confirm } = useModal();
  const isAdmin = role === 'ADMINISTRATOR';
  const [requesting, setRequesting] = useState(false);
  const [dianRanges, setDianRanges] = useState(null);
  const [dianLoading, setDianLoading] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_RANGE_FORM);
  const [creating, setCreating] = useState(false);
  const [editingCurrent, setEditingCurrent] = useState(null);
  const [currentValue, setCurrentValue] = useState('');

  const canRequest = isAdmin && (activation?.status === 'NOT_REQUESTED' || activation?.status === 'REJECTED');

  const handleRequestActivation = async () => {
    setRequesting(true);
    try {
      await requestBillingActivationApi();
      await onReload();
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
      await onReload();
    } catch (err) {
      notify('Error registrando el rango: ' + err.message);
    } finally {
      setCreating(false);
    }
  };

  const handleToggleStatus = async (range) => {
    try {
      await toggleNumberingRangeStatusApi(range.id);
      await onReload();
    } catch (err) {
      notify('Error cambiando el estado del rango: ' + err.message);
    }
  };

  const handleDelete = async (range) => {
    const ok = await confirm(`¿Eliminar el rango ${range.prefix} (resolución ${range.resolutionNumber})?`);
    if (!ok) return;
    try {
      await deleteNumberingRangeApi(range.id);
      await onReload();
    } catch (err) {
      notify('Error eliminando el rango: ' + err.message);
    }
  };

  const handleSetDefault = async (range) => {
    try {
      await setDefaultNumberingRangeApi(range.id);
      await onReload();
    } catch (err) {
      notify('Error eligiendo el rango: ' + err.message);
    }
  };

  const handleSaveCurrent = async (range) => {
    try {
      await updateNumberingRangeCurrentApi(range.id, Number(currentValue));
      setEditingCurrent(null);
      await onReload();
    } catch (err) {
      notify('Error actualizando el consecutivo: ' + err.message);
    }
  };

  const tile = 'p-3 rounded-2xl bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730]';

  return (
    <div className="space-y-6">
      <div className="bento-card p-6 space-y-4">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#0A0A0A] text-[#FF5520] flex items-center justify-center shrink-0">
              <Building2 className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6]">Estado de habilitación ante Factus y la DIAN</h3>
              <p className="text-[11px] text-gray-500 dark:text-gray-400 font-medium">
                {activation ? `Entorno activo: ${activation.environment}` : 'Consultando...'}
              </p>
            </div>
          </div>
          {activation && (
            <span className={`text-[11px] px-3 py-1 rounded-full font-extrabold ${ACTIVATION_STYLES[activation.status] || ''}`}>
              {ACTIVATION_LABELS[activation.status] || activation.status}
            </span>
          )}
        </div>

        {activation?.statusReason && (
          <p className="text-[11px] text-red-500 dark:text-red-400 font-semibold">Motivo: {activation.statusReason}</p>
        )}

        {activation && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
            <div className={tile}>
              <span className="text-[10px] font-extrabold uppercase text-gray-400 dark:text-gray-500">Sandbox</span>
              <p className="font-bold text-[#0A0A0A] dark:text-[#F4F4F6] mt-0.5 flex items-center gap-1.5">
                {activation.sandboxConfigured ? <><CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Configurado</> : 'Sin configurar'}
              </p>
            </div>
            <div className={tile}>
              <span className="text-[10px] font-extrabold uppercase text-gray-400 dark:text-gray-500">Producción</span>
              <p className="font-bold text-[#0A0A0A] dark:text-[#F4F4F6] mt-0.5 flex items-center gap-1.5">
                {activation.productionConfigured ? <><CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Configurada</> : 'Pendiente de paso a producción'}
              </p>
            </div>
            <div className={tile}>
              <span className="text-[10px] font-extrabold uppercase text-gray-400 dark:text-gray-500">Última actualización</span>
              <p className="font-bold text-[#0A0A0A] dark:text-[#F4F4F6] mt-0.5">{formatDateTime(activation.updatedAt)}</p>
            </div>
          </div>
        )}

        {canRequest && (
          <div className="space-y-2">
            <button
              type="button"
              onClick={handleRequestActivation}
              disabled={requesting}
              className="px-4 py-2.5 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white rounded-2xl text-xs font-bold cursor-pointer"
            >
              {requesting ? 'Enviando...' : 'Solicitar activación'}
            </button>
            <p className="text-[11px] text-gray-400 dark:text-gray-500">
              Necesitas al menos un documento (RUT, cámara de comercio, etc.) cargado en <a href="/documents" className="text-[#FF5520] underline">Documentos</a> antes de solicitar.
            </p>
          </div>
        )}
      </div>

      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
            <Hash className="w-4 h-4 text-[#FF5520]" /> Resoluciones y rangos de numeración
          </h3>
          <div className="flex items-center gap-3">
            <button type="button" onClick={loadDianRanges} className="text-xs font-bold text-gray-500 dark:text-gray-400 flex items-center gap-1.5 hover:underline cursor-pointer">
              {dianLoading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Eye className="w-3.5 h-3.5" />} Consultar resoluciones en la DIAN
            </button>
            {isAdmin && (
              <button
                type="button"
                onClick={() => setShowCreateForm(v => !v)}
                className="px-3.5 py-2 bg-[#0A0A0A] hover:bg-[#202020] text-white rounded-xl text-[11px] font-bold flex items-center gap-1.5 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" /> Registrar rango
              </button>
            )}
          </div>
        </div>

        {dianRanges && (
          <div className="p-4 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-[#EAEAEA] dark:border-[#272730] text-[11px] space-y-1.5">
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
          <form onSubmit={handleCreateRange} className="bento-card p-5 space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              {[
                ['range-document', 'Código de documento:', 'document', 'text', '01'],
                ['range-prefix', 'Prefijo:', 'prefix', 'text', 'SETP'],
                ['range-resolution', 'Número de resolución:', 'resolutionNumber', 'text', ''],
                ['range-current', 'Consecutivo actual:', 'current', 'number', '1']
              ].map(([id, label, key, type, ph]) => (
                <div key={id}>
                  <label htmlFor={id} className="font-bold text-gray-700 dark:text-gray-300 block mb-1">{label}</label>
                  <input
                    id={id}
                    required
                    type={type}
                    min={type === 'number' ? 1 : undefined}
                    placeholder={ph}
                    value={createForm[key]}
                    onChange={e => setCreateForm({ ...createForm, [key]: e.target.value })}
                    className={FIELD_CLASS}
                  />
                </div>
              ))}
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setShowCreateForm(false)} className="flex-1 py-2.5 bg-gray-100 dark:bg-[#272730] text-gray-700 dark:text-gray-300 rounded-xl text-[11px] font-bold cursor-pointer">Cancelar</button>
              <button type="submit" disabled={creating} className="flex-1 py-2.5 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white rounded-xl text-[11px] font-extrabold cursor-pointer">
                {creating ? 'Registrando...' : 'Registrar'}
              </button>
            </div>
          </form>
        )}

        {rangesLoading && ranges.length === 0 ? (
          <p className="text-xs font-bold text-gray-400 dark:text-gray-500 py-6 text-center">Consultando rangos...</p>
        ) : ranges.length === 0 ? (
          <div className="py-10 text-center space-y-1 bg-gray-50/50 dark:bg-white/5 rounded-3xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-gray-700 dark:text-gray-300">Sin rangos de numeración registrados</p>
            {isAdmin && <p className="text-[11px] text-gray-400 dark:text-gray-500">Registra la resolución que la DIAN te asignó para empezar a facturar.</p>}
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {ranges.map(r => {
              const isDefault = activeRangeId === r.id;
              const span = r.to != null && r.from != null ? r.to - r.from + 1 : 0;
              const used = r.current != null && r.from != null ? Math.max(0, r.current - r.from) : 0;
              const pct = span > 0 ? Math.min(100, Math.round((used / span) * 100)) : 0;
              return (
                <div
                  key={r.id}
                  className={`p-5 rounded-3xl bg-white dark:bg-[#17171B] border space-y-4 transition-all ${
                    isDefault ? 'border-[#FF5520] ring-1 ring-[#FF5520] shadow-md' : 'border-[#EAEAEA] dark:border-[#272730]'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-mono text-base font-black text-[#0A0A0A] dark:text-[#F4F4F6]">Prefijo {r.prefix}</span>
                        {isDefault && (
                          <span className="px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider bg-[#FF5520] text-white flex items-center gap-1">
                            <Star className="w-2.5 h-2.5 fill-current" /> Para facturar
                          </span>
                        )}
                        {r.isExpired ? (
                          <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400">Vencido</span>
                        ) : r.isActive ? (
                          <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">Activo</span>
                        ) : (
                          <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400">Inactivo</span>
                        )}
                      </div>
                      <p className="text-[11px] text-gray-500 dark:text-gray-400 font-medium mt-0.5">{r.documentName || r.document}</p>
                    </div>

                    {isAdmin && (
                      <div className="flex items-center gap-1 shrink-0">
                        {!isDefault && (
                          <button type="button" onClick={() => handleSetDefault(r)} title="Elegir para facturar" aria-label="Elegir para facturar" className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                            <Star className="w-4 h-4" />
                          </button>
                        )}
                        <button type="button" onClick={() => handleToggleStatus(r)} title={r.isActive ? 'Desactivar' : 'Activar'} aria-label={r.isActive ? 'Desactivar' : 'Activar'} className="p-1.5 text-gray-400 hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                          {r.isActive ? <PauseCircle className="w-4 h-4" /> : <CheckCircle2 className="w-4 h-4" />}
                        </button>
                        <button type="button" onClick={() => handleDelete(r)} title="Eliminar" aria-label="Eliminar" className="p-1.5 text-gray-400 hover:text-red-500 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                          <Ban className="w-4 h-4" />
                        </button>
                      </div>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs pt-3 border-t border-[#EAEAEA] dark:border-[#272730]">
                    <div className="min-w-0">
                      <span className="text-[10px] text-gray-400 dark:text-gray-500 block">Resolución</span>
                      <span className="font-mono font-bold text-[#0A0A0A] dark:text-[#F4F4F6] block truncate">{r.resolutionNumber || '—'}</span>
                    </div>
                    <div className="min-w-0">
                      <span className="text-[10px] text-gray-400 dark:text-gray-500 block">Vigencia</span>
                      <span className="font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] block">{r.startDate && r.endDate ? `${r.startDate} al ${r.endDate}` : '—'}</span>
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between text-[11px] font-semibold gap-2">
                      <span className="text-gray-500 dark:text-gray-400">Consumo del rango</span>
                      {editingCurrent === r.id ? (
                        <span className="flex items-center gap-1">
                          <input
                            type="number"
                            value={currentValue}
                            onChange={e => setCurrentValue(e.target.value)}
                            aria-label="Consecutivo actual"
                            className="w-24 p-1 bg-white dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-lg text-xs font-mono"
                          />
                          <button type="button" onClick={() => handleSaveCurrent(r)} aria-label="Guardar consecutivo" className="text-emerald-500 cursor-pointer"><CheckCircle2 className="w-4 h-4" /></button>
                          <button type="button" onClick={() => setEditingCurrent(null)} aria-label="Cancelar" className="text-gray-400 cursor-pointer"><X className="w-4 h-4" /></button>
                        </span>
                      ) : (
                        <button
                          type="button"
                          disabled={!isAdmin}
                          onClick={() => { setEditingCurrent(r.id); setCurrentValue(String(r.current ?? '')); }}
                          className={`font-mono tabular-nums text-[#0A0A0A] dark:text-[#F4F4F6] ${isAdmin ? 'hover:underline cursor-pointer' : 'cursor-default'}`}
                        >
                          #{r.current ?? '—'} / #{r.to ?? '—'} ({pct}%)
                        </button>
                      )}
                    </div>
                    <div className="w-full h-2 rounded-full bg-gray-100 dark:bg-white/10 overflow-hidden">
                      <div className={`h-full rounded-full ${pct >= 90 ? 'bg-red-500' : 'bg-[#FF5520]'}`} style={{ width: `${pct}%` }} />
                    </div>
                  </div>

                  {r.technicalKey && (
                    <div className="p-2.5 rounded-xl bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] text-[10px]">
                      <span className="text-gray-400 dark:text-gray-500 block">Llave técnica DIAN</span>
                      <p className="font-mono text-gray-600 dark:text-gray-300 truncate" title={r.technicalKey}>{r.technicalKey}</p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
