import React, { useState, useEffect, useCallback } from 'react';
import {
  LifeBuoy,
  Search,
  AlertCircle,
  X,
  Bug,
  MessageSquareWarning,
  FileWarning,
  Lightbulb,
  ScrollText,
  Clock,
  CheckCircle2,
  Archive,
  Flame
} from 'lucide-react';
import {
  getBackofficeSupportMetricsApi,
  getBackofficeSupportTicketsApi,
  getBackofficeSupportTicketApi,
  updateBackofficeSupportTicketApi
} from '../../services/backoffice/supportApi';

const TYPES = ['PETITION', 'COMPLAINT', 'CLAIM', 'SUGGESTION', 'BUG_REPORT'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

const TYPE_LABELS = {
  PETITION: 'Petición',
  COMPLAINT: 'Queja',
  CLAIM: 'Reclamo',
  SUGGESTION: 'Sugerencia',
  BUG_REPORT: 'Reporte de Bug'
};

const TYPE_ICONS = {
  PETITION: ScrollText,
  COMPLAINT: MessageSquareWarning,
  CLAIM: FileWarning,
  SUGGESTION: Lightbulb,
  BUG_REPORT: Bug
};

const PRIORITY_LABELS = { LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta', CRITICAL: 'Crítica' };

const PRIORITY_STYLES = {
  LOW: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300',
  MEDIUM: 'bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400',
  HIGH: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400',
  CRITICAL: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400'
};

const STATUS_LABELS = { OPEN: 'Abierto', IN_PROGRESS: 'En Progreso', RESOLVED: 'Resuelto', CLOSED: 'Cerrado' };

const STATUS_STYLES = {
  OPEN: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  IN_PROGRESS: 'bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400 border border-blue-500/20',
  RESOLVED: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  CLOSED: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'
};

function MetricCard({ label, value, icon: Icon, accent }) {
  return (
    <div className="bento-card p-4 flex items-center gap-3">
      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${accent}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <p className="text-lg font-black text-[#0A0A0A] dark:text-[#F4F4F6] leading-tight tabular-nums">{value}</p>
        <p className="text-[10px] font-bold text-gray-500 dark:text-gray-400 truncate">{label}</p>
      </div>
    </div>
  );
}

export default function SupportTicketsBento() {
  const [metrics, setMetrics] = useState(null);
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [priorityFilter, setPriorityFilter] = useState('ALL');

  const [detailTicket, setDetailTicket] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [savingDetail, setSavingDetail] = useState(false);
  const [statusDraft, setStatusDraft] = useState('OPEN');
  const [priorityDraft, setPriorityDraft] = useState('NORMAL');
  const [notesDraft, setNotesDraft] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [m, t] = await Promise.all([
        getBackofficeSupportMetricsApi().catch(() => null),
        getBackofficeSupportTicketsApi({
          type: typeFilter === 'ALL' ? undefined : typeFilter,
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          priority: priorityFilter === 'ALL' ? undefined : priorityFilter,
          q: search.trim() || undefined
        }).catch(() => [])
      ]);
      setMetrics(m);
      setTickets(Array.isArray(t?.content) ? t.content : Array.isArray(t) ? t : []);
    } finally {
      setLoading(false);
    }
  }, [typeFilter, statusFilter, priorityFilter, search]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const openDetail = async (ticket) => {
    setDetailTicket(ticket);
    setStatusDraft(ticket.status);
    setPriorityDraft(ticket.priority);
    setNotesDraft(ticket.resolutionNotes || '');
    setDetailLoading(true);
    setDetailError('');
    try {
      const full = await getBackofficeSupportTicketApi(ticket.id);
      setDetailTicket(full);
      setStatusDraft(full.status);
      setPriorityDraft(full.priority);
      setNotesDraft(full.resolutionNotes || '');
    } catch (e) {
      setDetailError(e.message || 'Error cargando detalle del ticket.');
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetail = () => {
    setDetailTicket(null);
    setDetailError('');
  };

  const handleSaveDetail = async () => {
    if (!detailTicket) return;
    setSavingDetail(true);
    setDetailError('');
    try {
      const updated = await updateBackofficeSupportTicketApi(detailTicket.id, {
        status: statusDraft,
        priority: priorityDraft,
        resolutionNotes: notesDraft
      });
      setDetailTicket(updated);
      await loadData();
    } catch (e) {
      setDetailError(e.message || 'Error actualizando ticket.');
    } finally {
      setSavingDetail(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#0A0A0A] dark:text-[#F4F4F6]">Soporte y PQRs</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Gestión de peticiones, quejas, reclamos y reportes de bugs</p>
        </div>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
        <MetricCard label="Abiertos" value={metrics?.totalOpen ?? '—'} icon={Clock} accent="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400" />
        <MetricCard label="En Progreso" value={metrics?.inProgress ?? '—'} icon={LifeBuoy} accent="bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400" />
        <MetricCard label="Resueltos" value={metrics?.resolved ?? '—'} icon={CheckCircle2} accent="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" />
        <MetricCard label="Cerrados" value={metrics?.closed ?? '—'} icon={Archive} accent="bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300" />
        <MetricCard label="Bugs Críticos" value={metrics?.bugsCritical ?? '—'} icon={Flame} accent="bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400" />
      </div>

      {/* Tickets list */}
      <div className="bento-card p-6 space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 text-gray-400 dark:text-gray-500 absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por título o radicado..."
              className="w-full pl-10 pr-3 py-1.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-xs font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
            />
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="px-3 py-1.5 rounded-full text-[11px] font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 cursor-pointer"
            >
              <option value="ALL">Todos los tipos</option>
              {TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
            </select>
            <select
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="px-3 py-1.5 rounded-full text-[11px] font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 cursor-pointer"
            >
              <option value="ALL">Toda prioridad</option>
              {PRIORITIES.map(p => <option key={p} value={p}>{PRIORITY_LABELS[p]}</option>)}
            </select>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-1.5 rounded-full text-[11px] font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 cursor-pointer"
            >
              <option value="ALL">Todo estado</option>
              {STATUSES.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
            </select>
          </div>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-gray-400 dark:text-gray-500">
            Consultando tickets...
          </div>
        ) : tickets.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <LifeBuoy className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No se encontraron tickets</p>
              <p className="text-[11px] text-gray-400 dark:text-gray-500">Los PQRs y reportes de bugs radicados aparecerán aquí</p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[820px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-gray-400 dark:text-gray-500 uppercase">
                  <th className="px-2 py-2">Radicado</th>
                  <th className="px-2 py-2">Tipo</th>
                  <th className="px-2 py-2">Contacto</th>
                  <th className="px-2 py-2">Prioridad</th>
                  <th className="px-2 py-2">Estado</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map(t => {
                  const Icon = TYPE_ICONS[t.type] || ScrollText;
                  return (
                    <tr
                      key={t.id}
                      onClick={() => openDetail(t)}
                      className="border-t border-gray-100 dark:border-gray-800 hover:bg-gray-50/60 dark:hover:bg-white/5 cursor-pointer"
                    >
                      <td className="px-2 py-3">
                        <p className="font-extrabold text-[#17171B] dark:text-white font-mono">{t.ticketNumber}</p>
                        <p className="text-[10px] text-gray-400 dark:text-gray-500 truncate max-w-[220px]">{t.title}</p>
                      </td>
                      <td className="px-2 py-3">
                        <span className="inline-flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                          <Icon className="w-3.5 h-3.5 text-gray-400 dark:text-gray-500" /> {TYPE_LABELS[t.type] || t.type}
                        </span>
                      </td>
                      <td className="px-2 py-3">
                        <p className="font-semibold text-gray-600 dark:text-gray-300">{t.contactName}</p>
                        <p className="text-[10px] text-gray-400 dark:text-gray-500">{t.contactEmail}</p>
                      </td>
                      <td className="px-2 py-3">
                        <span className={`text-[10px] px-2 py-0.5 rounded-full font-extrabold ${PRIORITY_STYLES[t.priority] || ''}`}>
                          {PRIORITY_LABELS[t.priority] || t.priority}
                        </span>
                      </td>
                      <td className="px-2 py-3">
                        <span className={`text-[10px] px-2 py-0.5 rounded-full font-extrabold ${STATUS_STYLES[t.status] || ''}`}>
                          {STATUS_LABELS[t.status] || t.status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Ticket Detail Modal */}
      {detailTicket && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-lg w-full bg-white dark:bg-[#17171B] p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-extrabold text-base text-[#0A0A0A] dark:text-[#F4F4F6] font-mono">{detailTicket.ticketNumber}</h3>
                <p className="text-[11px] text-gray-400 dark:text-gray-500 font-semibold">{TYPE_LABELS[detailTicket.type] || detailTicket.type}</p>
              </div>
              <button
                onClick={closeDetail}
                className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {detailLoading ? (
              <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Cargando detalle...</div>
            ) : (
              <>
                {detailError && (
                  <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{detailError}</span>
                  </div>
                )}

                <div className="space-y-3 text-xs">
                  <div>
                    <p className="font-bold text-gray-700 dark:text-gray-300 mb-1">Título:</p>
                    <p className="text-[#0A0A0A] dark:text-[#F4F4F6] font-semibold">{detailTicket.title}</p>
                  </div>
                  <div>
                    <p className="font-bold text-gray-700 dark:text-gray-300 mb-1">Descripción:</p>
                    <p className="text-gray-600 dark:text-gray-300 whitespace-pre-wrap p-3 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-[#EAEAEA] dark:border-[#272730]">{detailTicket.description}</p>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <p className="font-bold text-gray-700 dark:text-gray-300 mb-1">Contacto:</p>
                      <p className="text-gray-600 dark:text-gray-300 font-semibold">{detailTicket.contactName}</p>
                      <p className="text-[11px] text-gray-400 dark:text-gray-500">{detailTicket.contactEmail}</p>
                      {detailTicket.contactPhone && <p className="text-[11px] text-gray-400 dark:text-gray-500">{detailTicket.contactPhone}</p>}
                    </div>
                    {detailTicket.systemInfo && (
                      <div>
                        <p className="font-bold text-gray-700 dark:text-gray-300 mb-1">Info del sistema:</p>
                        <p className="text-[11px] text-gray-500 dark:text-gray-400 break-words">{detailTicket.systemInfo}</p>
                      </div>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-2 pt-1">
                    <div>
                      <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Estado:</label>
                      <select
                        value={statusDraft}
                        onChange={e => setStatusDraft(e.target.value)}
                        className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                      >
                        {STATUSES.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Prioridad:</label>
                      <select
                        value={priorityDraft}
                        onChange={e => setPriorityDraft(e.target.value)}
                        className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                      >
                        {PRIORITIES.map(p => <option key={p} value={p}>{PRIORITY_LABELS[p]}</option>)}
                      </select>
                    </div>
                  </div>

                  <div>
                    <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Notas de resolución:</label>
                    <textarea
                      value={notesDraft}
                      onChange={e => setNotesDraft(e.target.value)}
                      placeholder="Notas internas sobre la resolución del ticket..."
                      rows={3}
                      className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-xs text-[#0A0A0A] dark:text-[#F4F4F6] resize-y focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                    />
                  </div>
                </div>

                <div className="flex gap-2 pt-2">
                  <button
                    type="button"
                    onClick={closeDetail}
                    className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-[#2C2C36] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
                  >
                    Cerrar
                  </button>
                  <button
                    type="button"
                    onClick={handleSaveDetail}
                    disabled={savingDetail}
                    className="w-1/2 py-3 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
                  >
                    {savingDetail ? 'Guardando...' : 'Guardar Cambios'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
