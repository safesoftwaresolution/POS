import React, { useCallback, useEffect, useState } from 'react';
import { Search, ChevronLeft, ChevronRight, ArrowRight } from 'lucide-react';
import { getStoreStatusAuditApi } from '../../services/backoffice/storesApi';
import useAutoRefresh from '../../hooks/useAutoRefresh';

const STATUS_LABELS = {
  PENDING_VERIFICATION: 'Pendiente',
  ACTIVE: 'Aprobado',
  REJECTED: 'Rechazado',
  SUSPENDED: 'Suspendido',
  INACTIVE: 'Inactivo',
  DELETED: 'Eliminado',
  PLAN_CHANGED: 'Plan cambiado',
};

const STATUS_STYLES = {
  PENDING_VERIFICATION: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400',
  ACTIVE: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400',
  SUSPENDED: 'bg-orange-100 dark:bg-orange-500/20 text-orange-700 dark:text-orange-400',
  INACTIVE: 'bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300',
  DELETED: 'bg-red-100 dark:bg-red-500/20 text-red-700 dark:text-red-400',
  PLAN_CHANGED: 'bg-sky-100 dark:bg-sky-500/20 text-sky-700 dark:text-sky-300',
};

const FILTERS = [
  { value: 'ALL', label: 'Todos' },
  { value: 'ACTIVE', label: 'Aprobados' },
  { value: 'REJECTED', label: 'Rechazados' },
  { value: 'PENDING_VERIFICATION', label: 'Registros' },
  { value: 'SUSPENDED', label: 'Suspendidos' },
  { value: 'INACTIVE', label: 'Inactivos' },
  { value: 'DELETED', label: 'Eliminados' },
  { value: 'PLAN_CHANGED', label: 'Cambios de plan' },
];

function StatusBadge({ status }) {
  return (
    <span className={`px-2 py-0.5 rounded-full text-[10px] font-extrabold ${STATUS_STYLES[status] || STATUS_STYLES.INACTIVE}`}>
      {STATUS_LABELS[status] || status}
    </span>
  );
}

const formatDate = (iso) =>
  iso ? new Date(iso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—';

export default function StoreAuditBento() {
  const [status, setStatus] = useState('ALL');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!silent) {
      setLoading(true);
      setErrorMsg('');
    }
    try {
      setData(await getStoreStatusAuditApi(status, query, page));
    } catch (e) {
      if (!silent) setErrorMsg(e.message || 'No se pudo cargar la auditoría.');
    } finally {
      if (!silent) setLoading(false);
    }
  }, [status, query, page]);

  useEffect(() => { load(); }, [load]);
  useAutoRefresh(() => load({ silent: true }));

  const submitSearch = (e) => {
    e.preventDefault();
    setPage(0);
    setQuery(search.trim());
  };

  const changeStatus = (value) => {
    setPage(0);
    setStatus(value);
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Auditoría de locales</h1>
        <p className="text-xs text-ink-soft font-medium">
          Historial de solo lectura: registro, aprobación, rechazo y demás cambios de estado, con quién y cuándo
        </p>
      </div>

      <div className="bento-card p-4 flex flex-col lg:flex-row gap-3 lg:items-center lg:justify-between">
        <div className="flex flex-wrap gap-2">
          {FILTERS.map(f => (
            <button
              key={f.value}
              onClick={() => changeStatus(f.value)}
              className={`px-3 py-1.5 rounded-xl text-[11px] font-bold cursor-pointer ${
                status === f.value ? 'bg-solid text-white' : 'bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
        <form onSubmit={submitSearch} className="relative w-full lg:w-72">
          <Search className="w-4 h-4 text-ink-muted absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Local, NIT, correo o responsable..."
            className="w-full pl-9 pr-3 py-2 bg-page border border-line rounded-xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
          />
        </form>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold">
          {errorMsg}
        </div>
      )}

      <div className="bento-card p-6 space-y-4">
        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando...</div>
        ) : data.content.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">No hay registros de auditoría</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[760px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Fecha</th>
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Cambio</th>
                  <th className="px-2 py-2">Realizado por</th>
                  <th className="px-2 py-2">Motivo</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map(row => (
                  <tr key={row.id} className="border-t border-line align-top">
                    <td className="px-2 py-3 text-ink-soft font-semibold whitespace-nowrap">{formatDate(row.createdAt)}</td>
                    <td className="px-2 py-3">
                      <p className="font-extrabold text-ink">{row.storeName}</p>
                      <p className="text-[10px] text-ink-muted font-mono">NIT: {row.taxId || '—'}</p>
                      <p className="text-[10px] text-ink-muted">{row.storeEmail}</p>
                    </td>
                    <td className="px-2 py-3">
                      <span className="inline-flex items-center gap-1.5">
                        {row.previousStatus ? <StatusBadge status={row.previousStatus} /> : <span className="text-ink-muted">—</span>}
                        <ArrowRight className="w-3 h-3 text-ink-muted" />
                        <StatusBadge status={row.newStatus} />
                      </span>
                    </td>
                    <td className="px-2 py-3 font-semibold text-ink-soft">{row.performedBy || 'No registrado'}</td>
                    <td className="px-2 py-3 text-ink-soft">{row.reason || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data.totalPages > 1 && (
          <div className="flex items-center justify-between pt-2 border-t border-line">
            <p className="text-[11px] font-bold text-ink-muted">
              {data.totalElements} registros · página {page + 1} de {data.totalPages}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-2 rounded-xl bg-gray-100 dark:bg-line disabled:opacity-40 cursor-pointer"
                aria-label="Página anterior"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
                disabled={page >= data.totalPages - 1}
                className="p-2 rounded-xl bg-gray-100 dark:bg-line disabled:opacity-40 cursor-pointer"
                aria-label="Página siguiente"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
