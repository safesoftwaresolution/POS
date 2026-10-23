import React, { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, ChevronLeft, ChevronRight, AlertCircle, CheckCircle2, UserPlus, X, Store, UserX, UserCheck, Trash2 } from 'lucide-react';
import useAutoRefresh from '../../hooks/useAutoRefresh';
import {
  getUserDirectoryApi,
  getAssignableOwnersApi,
  getStoresWithoutOwnerApi,
  assignStoreOwnerApi,
  getOwnerAssignmentsApi,
  setUserActiveApi,
  deleteUserApi,
} from '../../services/backoffice/usersDirectoryApi';

const ROLE_LABELS = {
  SUPER_ADMIN: 'Super admin',
  ADMINISTRATOR: 'Administrador',
  SUPERVISOR: 'Supervisor',
  SELLER: 'Vendedor',
};

const ROLE_STYLES = {
  SUPER_ADMIN: 'bg-violet-100 dark:bg-violet-500/20 text-violet-700 dark:text-violet-300',
  ADMINISTRATOR: 'bg-brand/15 text-brand',
  SUPERVISOR: 'bg-sky-100 dark:bg-sky-500/20 text-sky-700 dark:text-sky-300',
  SELLER: 'bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300',
};

const STORE_STATUS_LABELS = {
  ACTIVE: 'Activo',
  PENDING_VERIFICATION: 'Pendiente',
  SUSPENDED: 'Suspendido',
  INACTIVE: 'Inactivo',
  REJECTED: 'Rechazado',
};

const ROLE_FILTERS = [
  { value: 'ALL', label: 'Todos los roles' },
  { value: 'ADMINISTRATOR', label: 'Administrador' },
  { value: 'SUPERVISOR', label: 'Supervisor' },
  { value: 'SELLER', label: 'Vendedor' },
  { value: 'SUPER_ADMIN', label: 'Super admin' },
];

const formatDate = (iso) =>
  iso ? new Date(iso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—';

function Pill({ className, children }) {
  return <span className={`px-2 py-0.5 rounded-full text-[10px] font-extrabold ${className}`}>{children}</span>;
}

function Pager({ page, totalPages, totalElements, onChange }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between pt-2 border-t border-line">
      <p className="text-[11px] font-bold text-ink-muted">
        {totalElements} registros · página {page + 1} de {totalPages}
      </p>
      <div className="flex gap-2">
        <button
          onClick={() => onChange(Math.max(0, page - 1))}
          disabled={page === 0}
          className="p-2 rounded-xl bg-gray-100 dark:bg-line disabled:opacity-40 cursor-pointer"
          aria-label="Página anterior"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>
        <button
          onClick={() => onChange(Math.min(totalPages - 1, page + 1))}
          disabled={page >= totalPages - 1}
          className="p-2 rounded-xl bg-gray-100 dark:bg-line disabled:opacity-40 cursor-pointer"
          aria-label="Página siguiente"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

const EMPTY_PAGE = { content: [], totalPages: 0, totalElements: 0 };

export default function UsersDirectoryBento() {
  const [searchParams, setSearchParams] = useSearchParams();
  const storeIdFilter = searchParams.get('storeId') || '';

  const [role, setRole] = useState('ALL');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [unassigned, setUnassigned] = useState(false);
  const [page, setPage] = useState(0);
  const [directory, setDirectory] = useState(EMPTY_PAGE);
  const [loading, setLoading] = useState(true);

  const [ownerless, setOwnerless] = useState([]);
  const [candidates, setCandidates] = useState([]);
  const [assigning, setAssigning] = useState(null); // local al que se asigna
  const [selectedUserId, setSelectedUserId] = useState('');
  const [assignBusy, setAssignBusy] = useState(false);
  const [deleting, setDeleting] = useState(null); // usuario pendiente de confirmar su eliminación
  const [actionBusy, setActionBusy] = useState(false);

  const [history, setHistory] = useState(EMPTY_PAGE);
  const [historyPage, setHistoryPage] = useState(0);

  const [errorMsg, setErrorMsg] = useState('');
  const [infoMsg, setInfoMsg] = useState('');

  // `silent` = refresco automático: sin "Cargando..." y sin mostrar errores transitorios.
  const loadDirectory = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    try {
      setDirectory(await getUserDirectoryApi({ role, storeId: storeIdFilter, q: query, unassigned, page }));
    } catch (e) {
      if (!silent) setErrorMsg(e.message || 'No se pudo cargar el directorio de usuarios.');
    } finally {
      if (!silent) setLoading(false);
    }
  }, [role, storeIdFilter, query, unassigned, page]);

  const loadOwnership = useCallback(async ({ silent = false } = {}) => {
    try {
      const [stores, owners] = await Promise.all([getStoresWithoutOwnerApi(), getAssignableOwnersApi()]);
      setOwnerless(stores);
      setCandidates(owners);
    } catch (e) {
      if (!silent) setErrorMsg(e.message || 'No se pudieron cargar los locales sin administrador.');
    }
  }, []);

  const loadHistory = useCallback(async ({ silent = false } = {}) => {
    try {
      setHistory(await getOwnerAssignmentsApi(historyPage));
    } catch (e) {
      if (!silent) setErrorMsg(e.message || 'No se pudo cargar el historial de asignaciones.');
    }
  }, [historyPage]);

  useEffect(() => { loadDirectory(); }, [loadDirectory]);
  useEffect(() => { loadOwnership(); }, [loadOwnership]);
  useEffect(() => { loadHistory(); }, [loadHistory]);
  useAutoRefresh(() => Promise.all([
    loadDirectory({ silent: true }), loadOwnership({ silent: true }), loadHistory({ silent: true }),
  ]));

  const submitSearch = (e) => {
    e.preventDefault();
    setPage(0);
    setQuery(search.trim());
  };

  const clearStoreFilter = () => {
    setPage(0);
    setSearchParams({});
  };

  const openAssign = (store) => {
    setAssigning(store);
    setSelectedUserId('');
    setErrorMsg('');
    setInfoMsg('');
  };

  const confirmAssign = async () => {
    if (!assigning || !selectedUserId) return;
    setAssignBusy(true);
    try {
      await assignStoreOwnerApi(assigning.id, Number(selectedUserId));
      const user = candidates.find(c => String(c.id) === String(selectedUserId));
      setInfoMsg(`${user?.fullName || 'El usuario'} fue asignado como administrador de "${assigning.name}".`);
      setAssigning(null);
      await Promise.all([loadDirectory(), loadOwnership(), loadHistory()]);
    } catch (e) {
      setAssigning(null);
      setErrorMsg(e.message || 'No se pudo asignar el usuario.');
    } finally {
      setAssignBusy(false);
    }
  };

  const toggleActive = async (u) => {
    setErrorMsg('');
    setInfoMsg('');
    setActionBusy(true);
    try {
      await setUserActiveApi(u.id, !u.active);
      setInfoMsg(u.active ? `${u.fullName} fue inactivado y sus sesiones se cerraron.` : `${u.fullName} fue reactivado.`);
      await Promise.all([loadDirectory(), loadOwnership()]);
    } catch (e) {
      setErrorMsg(e.message || 'No se pudo cambiar el estado del usuario.');
    } finally {
      setActionBusy(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    setActionBusy(true);
    try {
      await deleteUserApi(deleting.id);
      setInfoMsg(`${deleting.fullName} fue eliminado definitivamente.`);
      setDeleting(null);
      await Promise.all([loadDirectory(), loadOwnership()]);
    } catch (e) {
      setDeleting(null);
      setErrorMsg(e.message || 'No se pudo eliminar el usuario.');
    } finally {
      setActionBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Usuarios por local</h1>
        <p className="text-xs text-ink-soft font-medium">
          Usuarios de la plataforma, el local al que pertenecen y su rol. Puedes asignar un administrador a un local sin
          administrador, inactivar o reactivar usuarios y eliminar a los que no tienen historial.
        </p>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}
      {infoMsg && (
        <div className="p-3 bg-emerald-50 dark:bg-emerald-950/30 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{infoMsg}</span>
        </div>
      )}

      {/* Locales sin administrador */}
      <div className="bento-card p-6 space-y-4">
        <h2 className="text-sm font-black text-ink flex items-center gap-2">
          <Store className="w-4 h-4 text-amber-500" /> Locales sin administrador
          <span className="text-[10px] font-extrabold text-ink-muted">({ownerless.length})</span>
        </h2>
        {ownerless.length === 0 ? (
          <div className="py-6 text-center text-xs font-bold text-ink-muted">Todos los locales tienen un administrador asignado</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[640px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Correo</th>
                  <th className="px-2 py-2">Estado</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {ownerless.map(s => (
                  <tr key={s.id} className="border-t border-line">
                    <td className="px-2 py-3">
                      <p className="font-extrabold text-ink">{s.name}</p>
                      <p className="text-[10px] text-ink-muted font-mono">NIT: {s.taxId || '—'}</p>
                    </td>
                    <td className="px-2 py-3 text-ink-soft font-semibold">{s.email}</td>
                    <td className="px-2 py-3 text-ink-soft font-semibold">{STORE_STATUS_LABELS[s.status] || s.status}</td>
                    <td className="px-2 py-3 text-right">
                      <button
                        onClick={() => openAssign(s)}
                        className="px-3 py-1.5 bg-solid hover:bg-solid-hover text-white rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer"
                      >
                        <UserPlus className="w-3.5 h-3.5" /> Asignar usuario
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Directorio */}
      <div className="bento-card p-6 space-y-4">
        <div className="flex flex-col lg:flex-row gap-3 lg:items-center lg:justify-between">
          <h2 className="text-sm font-black text-ink">Directorio de usuarios</h2>
          <div className="flex flex-col sm:flex-row gap-2">
            <select
              value={role}
              onChange={(e) => { setPage(0); setRole(e.target.value); }}
              className="px-3 py-2 bg-page border border-line rounded-xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
            >
              {ROLE_FILTERS.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
            <label className="flex items-center gap-2 px-3 py-2 bg-page border border-line rounded-xl text-xs font-semibold text-ink cursor-pointer">
              <input
                type="checkbox"
                checked={unassigned}
                onChange={(e) => { setPage(0); setUnassigned(e.target.checked); }}
              />
              Solo sin local
            </label>
            <form onSubmit={submitSearch} className="relative">
              <Search className="w-4 h-4 text-ink-muted absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Nombre, usuario, correo o local..."
                className="w-full sm:w-64 pl-9 pr-3 py-2 bg-page border border-line rounded-xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
              />
            </form>
          </div>
        </div>

        {storeIdFilter && (
          <div className="inline-flex items-center gap-2 px-3 py-1.5 bg-brand/10 border border-brand/30 rounded-xl text-[11px] font-bold text-ink">
            Filtrando por el local #{storeIdFilter}
            <button onClick={clearStoreFilter} className="cursor-pointer text-ink-muted hover:text-ink" aria-label="Quitar filtro de local">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando...</div>
        ) : directory.content.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">No hay usuarios con esos filtros</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[760px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Usuario</th>
                  <th className="px-2 py-2">Correo</th>
                  <th className="px-2 py-2">Rol</th>
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Estado</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {directory.content.map(u => (
                  <tr key={u.id} className="border-t border-line">
                    <td className="px-2 py-3">
                      <p className="font-extrabold text-ink">{u.fullName}</p>
                      <p className="text-[10px] text-ink-muted font-mono">{u.username}</p>
                    </td>
                    <td className="px-2 py-3 text-ink-soft font-semibold">{u.email}</td>
                    <td className="px-2 py-3"><Pill className={ROLE_STYLES[u.role] || ROLE_STYLES.SELLER}>{ROLE_LABELS[u.role] || u.role}</Pill></td>
                    <td className="px-2 py-3">
                      {u.storeId ? (
                        <>
                          <p className="font-extrabold text-ink">{u.storeName}</p>
                          <p className="text-[10px] text-ink-muted">{STORE_STATUS_LABELS[u.storeStatus] || u.storeStatus}</p>
                        </>
                      ) : u.role === 'SUPER_ADMIN' ? (
                        <span className="text-ink-muted font-semibold">Plataforma</span>
                      ) : (
                        <span className="text-amber-600 dark:text-amber-400 font-bold">Sin local</span>
                      )}
                    </td>
                    <td className="px-2 py-3 space-x-1">
                      <Pill className={u.active ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400'}>
                        {u.active ? 'Activo' : 'Inactivo'}
                      </Pill>
                      {!u.emailVerified && (
                        <Pill className="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400">Sin verificar</Pill>
                      )}
                    </td>
                    <td className="px-2 py-3 text-right whitespace-nowrap">
                      {u.role === 'SUPER_ADMIN' ? (
                        <span className="text-ink-muted font-semibold">—</span>
                      ) : (
                        <div className="inline-flex gap-1.5">
                          <button
                            onClick={() => toggleActive(u)}
                            disabled={actionBusy}
                            title={u.active ? 'Inactivar: impide el acceso y cierra sus sesiones' : 'Reactivar: vuelve a ocupar un cupo del plan'}
                            className="px-2.5 py-1.5 bg-gray-100 dark:bg-line text-ink rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer disabled:opacity-50"
                          >
                            {u.active ? <UserX className="w-3.5 h-3.5" /> : <UserCheck className="w-3.5 h-3.5" />}
                            {u.active ? 'Inactivar' : 'Reactivar'}
                          </button>
                          <button
                            onClick={() => { setErrorMsg(''); setInfoMsg(''); setDeleting(u); }}
                            disabled={actionBusy}
                            title="Eliminar definitivamente"
                            className="px-2.5 py-1.5 bg-red-50 dark:bg-red-500/10 text-red-600 dark:text-red-400 rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer disabled:opacity-50"
                          >
                            <Trash2 className="w-3.5 h-3.5" /> Eliminar
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={page} totalPages={directory.totalPages} totalElements={directory.totalElements} onChange={setPage} />
      </div>

      {/* Historial de asignaciones */}
      <div className="bento-card p-6 space-y-4">
        <h2 className="text-sm font-black text-ink">Historial de asignaciones</h2>
        {history.content.length === 0 ? (
          <div className="py-6 text-center text-xs font-bold text-ink-muted">Aún no se han asignado usuarios a locales desde aquí</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[640px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Fecha</th>
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Usuario asignado</th>
                  <th className="px-2 py-2">Realizado por</th>
                </tr>
              </thead>
              <tbody>
                {history.content.map(h => (
                  <tr key={h.id} className="border-t border-line">
                    <td className="px-2 py-3 text-ink-soft font-semibold whitespace-nowrap">{formatDate(h.createdAt)}</td>
                    <td className="px-2 py-3 font-extrabold text-ink">{h.storeName}</td>
                    <td className="px-2 py-3">
                      <p className="font-semibold text-ink">{h.fullName}</p>
                      <p className="text-[10px] text-ink-muted font-mono">{h.username} · {ROLE_LABELS[h.role] || h.role}</p>
                    </td>
                    <td className="px-2 py-3 text-ink-soft font-semibold">{h.performedBy || 'No registrado'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={historyPage} totalPages={history.totalPages} totalElements={history.totalElements} onChange={setHistoryPage} />
      </div>

      {deleting && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card bg-card p-6 rounded-3xl max-w-md w-full space-y-4">
            <h3 className="text-sm font-black text-ink flex items-center gap-2">
              <Trash2 className="w-4 h-4 text-red-500" /> Eliminar a {deleting.fullName}
            </h3>
            <p className="text-xs text-ink-soft leading-relaxed">
              Se borra la cuenta <span className="font-bold text-ink">{deleting.username}</span> ({deleting.email}) y no se
              puede deshacer. Si ya tiene ventas, compras, movimientos o devoluciones no se podrá eliminar: en ese caso
              inactívalo para impedir su acceso.
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeleting(null)}
                disabled={actionBusy}
                className="px-4 py-2 bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 rounded-xl text-xs font-bold cursor-pointer disabled:opacity-50"
              >
                Cancelar
              </button>
              <button
                onClick={confirmDelete}
                disabled={actionBusy}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold cursor-pointer"
              >
                {actionBusy ? 'Eliminando...' : 'Eliminar definitivamente'}
              </button>
            </div>
          </div>
        </div>
      )}

      {assigning && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card bg-card p-6 rounded-3xl max-w-md w-full space-y-4">
            <h3 className="text-sm font-black text-ink flex items-center gap-2">
              <UserPlus className="w-4 h-4 text-brand" /> Asignar administrador a "{assigning.name}"
            </h3>
            {candidates.length === 0 ? (
              <p className="text-xs text-ink-soft leading-relaxed">
                No hay usuarios administradores disponibles. Para asignar uno, la persona debe haberse registrado, haber
                verificado su correo y no tener todavía un local.
              </p>
            ) : (
              <>
                <p className="text-xs text-ink-soft leading-relaxed">
                  Elige un administrador verificado que aún no tiene local. Quedará registrado en el historial y no podrás
                  moverlo después desde aquí.
                </p>
                <select
                  value={selectedUserId}
                  onChange={(e) => setSelectedUserId(e.target.value)}
                  className="w-full p-3 bg-page border border-line rounded-2xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                >
                  <option value="">Selecciona un usuario...</option>
                  {candidates.map(c => (
                    <option key={c.id} value={c.id}>{c.fullName} · {c.username} · {c.email}</option>
                  ))}
                </select>
              </>
            )}
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setAssigning(null)}
                disabled={assignBusy}
                className="px-4 py-2 bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 rounded-xl text-xs font-bold cursor-pointer disabled:opacity-50"
              >
                Cancelar
              </button>
              {candidates.length > 0 && (
                <button
                  onClick={confirmAssign}
                  disabled={!selectedUserId || assignBusy}
                  className="px-4 py-2 bg-solid hover:bg-solid-hover disabled:opacity-50 text-white rounded-xl text-xs font-bold cursor-pointer"
                >
                  {assignBusy ? 'Asignando...' : 'Asignar'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
