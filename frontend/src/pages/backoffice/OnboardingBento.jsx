import React, { useCallback, useEffect, useState } from 'react';
import { Clock, Users, Store, Check, X, AlertCircle, Mail, CheckCircle2, Send, UserCheck, Trash2 } from 'lucide-react';
import {
  getPendingUsersApi,
  getVerifiedUsersApi,
  getOnboardingStatsApi,
  resendUserVerificationApi,
  deletePendingUserApi,
} from '../../services/backoffice/onboardingApi';
import {
  getBackofficeStoresApi,
  changeBackofficeStoreStatusApi,
  rejectBackofficeStoreApi,
  deleteBackofficeStoreApi,
} from '../../services/backoffice/storesApi';

function MetricCard({ label, value, icon: Icon, accent }) {
  return (
    <div className="bento-card p-4 flex items-center gap-3">
      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${accent}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <p className="text-lg font-black text-[#17171B] dark:text-white leading-tight tabular-nums">{value}</p>
        <p className="text-[10px] font-bold text-ink-soft truncate">{label}</p>
      </div>
    </div>
  );
}

export default function OnboardingBento() {
  const [stats, setStats] = useState(null);
  const [pendingUsers, setPendingUsers] = useState([]);
  const [verifiedUsers, setVerifiedUsers] = useState([]);
  const [pendingStores, setPendingStores] = useState([]);
  const [rejectedStores, setRejectedStores] = useState([]);
  const [deleting, setDeleting] = useState(null); // { kind: 'user' | 'store', item }
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [infoMsg, setInfoMsg] = useState('');
  const [resendingId, setResendingId] = useState(null);
  const [rejectingStore, setRejectingStore] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [statsData, usersData, verifiedData, storesData, rejectedData] = await Promise.all([
        getOnboardingStatsApi(),
        getPendingUsersApi(),
        getVerifiedUsersApi(),
        getBackofficeStoresApi('PENDING_VERIFICATION'),
        getBackofficeStoresApi('REJECTED'),
      ]);
      setRejectedStores(rejectedData.content || []);
      setStats(statsData);
      setPendingUsers(usersData.content || []);
      setVerifiedUsers(verifiedData.content || []);
      setPendingStores(storesData.content || []);
    } catch (e) {
      console.warn('Error cargando pendientes de onboarding:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const handleResend = async (user) => {
    setErrorMsg('');
    setInfoMsg('');
    setResendingId(user.id);
    try {
      await resendUserVerificationApi(user.id);
      setInfoMsg(`Correo de verificación reenviado a ${user.email}.`);
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo reenviar el correo.');
    } finally {
      setResendingId(null);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleting) return;
    setErrorMsg('');
    setInfoMsg('');
    setDeleteBusy(true);
    try {
      if (deleting.kind === 'user') {
        await deletePendingUserApi(deleting.item.id);
        setInfoMsg(`Usuario ${deleting.item.username} eliminado definitivamente.`);
      } else {
        await deleteBackofficeStoreApi(deleting.item.id);
        setInfoMsg(`Local "${deleting.item.name}" eliminado definitivamente. Queda registrado en Auditoría.`);
      }
      setDeleting(null);
      await loadAll();
    } catch (err) {
      setDeleting(null);
      setErrorMsg(err.message || 'No se pudo eliminar.');
    } finally {
      setDeleteBusy(false);
    }
  };

  const handleApprove = async (store) => {
    setErrorMsg('');
    try {
      await changeBackofficeStoreStatusApi(store.id, 'ACTIVE');
      await loadAll();
    } catch (err) {
      setErrorMsg(err.message || 'Error al aprobar el local.');
    }
  };

  const handleReject = async () => {
    if (!rejectionReason.trim()) return;
    setErrorMsg('');
    try {
      await rejectBackofficeStoreApi(rejectingStore.id, rejectionReason.trim());
      setRejectingStore(null);
      setRejectionReason('');
      await loadAll();
    } catch (err) {
      setErrorMsg(err.message || 'Error al rechazar el local.');
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Pendientes</h1>
        <p className="text-xs text-ink-soft font-medium">Usuarios y locales del auto-registro a la espera de verificación o aprobación</p>
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

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <MetricCard label="Usuarios sin verificar" value={stats?.pendingUsers ?? '—'} icon={Users} accent="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400" />
        <MetricCard label="Locales pendientes" value={stats?.pendingStores ?? '—'} icon={Clock} accent="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400" />
        <MetricCard label="Aprobados (30 días)" value={stats?.approvedStoresLast30Days ?? '—'} icon={CheckCircle2} accent="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" />
        <MetricCard label="Rechazados (total)" value={stats?.rejectedStoresTotal ?? '—'} icon={X} accent="bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400" />
      </div>

      {/* Usuarios pendientes de verificar */}
      <div className="bento-card p-6 space-y-4">
        <h2 className="text-sm font-black text-ink flex items-center gap-2">
          <Users className="w-4 h-4 text-amber-500" /> Usuarios pendientes de verificar su correo
        </h2>
        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando...</div>
        ) : pendingUsers.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">No hay usuarios pendientes de verificar</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[560px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Nombre</th>
                  <th className="px-2 py-2">Usuario</th>
                  <th className="px-2 py-2">Correo</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {pendingUsers.map(u => (
                  <tr key={u.id} className="border-t border-line">
                    <td className="px-2 py-3 font-extrabold text-ink">{u.fullName}</td>
                    <td className="px-2 py-3 text-ink-soft font-semibold">{u.username}</td>
                    <td className="px-2 py-3">
                      <span className="flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                        <Mail className="w-3.5 h-3.5 text-ink-muted" /> {u.email}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-right">
                      <button
                        onClick={() => handleResend(u)}
                        disabled={resendingId === u.id}
                        className="px-3 py-1.5 bg-solid hover:bg-solid-hover disabled:opacity-50 text-white rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer"
                      >
                        <Send className="w-3.5 h-3.5" /> {resendingId === u.id ? 'Enviando...' : 'Reenviar correo'}
                      </button>
                      <button
                        onClick={() => setDeleting({ kind: 'user', item: u })}
                        className="ml-2 px-3 py-1.5 bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer"
                      >
                        <Trash2 className="w-3.5 h-3.5" /> Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Usuarios que ya verificaron su correo */}
      <div className="bento-card p-6 space-y-4">
        <h2 className="text-sm font-black text-ink flex items-center gap-2">
          <UserCheck className="w-4 h-4 text-emerald-500" /> Usuarios verificados
        </h2>
        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando...</div>
        ) : verifiedUsers.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Aún no hay usuarios verificados</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[560px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Nombre</th>
                  <th className="px-2 py-2">Usuario</th>
                  <th className="px-2 py-2">Correo</th>
                  <th className="px-2 py-2">Local</th>
                </tr>
              </thead>
              <tbody>
                {verifiedUsers.map(u => (
                  <tr key={u.id} className="border-t border-line">
                    <td className="px-2 py-3 font-extrabold text-ink">{u.fullName}</td>
                    <td className="px-2 py-3 text-ink-soft font-semibold">{u.username}</td>
                    <td className="px-2 py-3">
                      <span className="flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                        <Mail className="w-3.5 h-3.5 text-ink-muted" /> {u.email}
                      </span>
                    </td>
                    <td className="px-2 py-3">
                      {u.storeId ? (
                        <span className="text-emerald-600 dark:text-emerald-400 font-bold">Con local registrado</span>
                      ) : (
                        <span className="text-amber-600 dark:text-amber-400 font-bold">Aún sin local</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Locales pendientes de aprobacion */}
      <div className="bento-card p-6 space-y-4">
        <h2 className="text-sm font-black text-ink flex items-center gap-2">
          <Store className="w-4 h-4 text-amber-500" /> Locales pendientes de aprobación
        </h2>
        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando...</div>
        ) : pendingStores.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">No hay locales pendientes</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[680px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Contacto</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {pendingStores.map(s => (
                  <tr key={s.id} className="border-t border-line">
                    <td className="px-2 py-3">
                      <p className="font-extrabold text-ink">{s.name}</p>
                      <p className="text-[10px] text-ink-muted font-mono">NIT: {s.taxId || '—'}</p>
                    </td>
                    <td className="px-2 py-3">
                      <span className="flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                        <Mail className="w-3.5 h-3.5 text-ink-muted" /> {s.email}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-right space-x-2">
                      <button
                        onClick={() => handleApprove(s)}
                        className="px-3 py-1.5 bg-solid hover:bg-solid-hover text-white rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer shadow-sm shadow-[#0A0A0A]/10"
                      >
                        <Check className="w-3.5 h-3.5" /> Aprobar
                      </button>
                      <button
                        onClick={() => { setRejectingStore(s); setRejectionReason(''); }}
                        className="px-3 py-1.5 bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer"
                      >
                        <X className="w-3.5 h-3.5" /> Rechazar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Locales rechazados: se pueden eliminar del sistema */}
      <div className="bento-card p-6 space-y-4">
        <h2 className="text-sm font-black text-ink flex items-center gap-2">
          <X className="w-4 h-4 text-red-500" /> Locales rechazados
        </h2>
        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando...</div>
        ) : rejectedStores.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-ink-muted">No hay locales rechazados</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[680px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Contacto</th>
                  <th className="px-2 py-2">Motivo del rechazo</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {rejectedStores.map(s => (
                  <tr key={s.id} className="border-t border-line">
                    <td className="px-2 py-3">
                      <p className="font-extrabold text-ink">{s.name}</p>
                      <p className="text-[10px] text-ink-muted font-mono">NIT: {s.taxId || '—'}</p>
                    </td>
                    <td className="px-2 py-3">
                      <span className="flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                        <Mail className="w-3.5 h-3.5 text-ink-muted" /> {s.email}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-ink-soft">{s.rejectionReason || '—'}</td>
                    <td className="px-2 py-3 text-right">
                      <button
                        onClick={() => setDeleting({ kind: 'store', item: s })}
                        className="px-3 py-1.5 bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer"
                      >
                        <Trash2 className="w-3.5 h-3.5" /> Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {deleting && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card bg-card p-6 rounded-3xl max-w-sm w-full space-y-4">
            <h3 className="text-sm font-black text-ink flex items-center gap-2">
              <Trash2 className="w-4 h-4 text-red-500" /> Eliminar definitivamente
            </h3>
            {deleting.kind === 'user' ? (
              <p className="text-xs text-ink-soft leading-relaxed">
                Se borrará la cuenta <span className="font-extrabold text-ink">{deleting.item.username}</span> ({deleting.item.email}).
                La persona podrá volver a registrarse. No se puede deshacer.
              </p>
            ) : (
              <p className="text-xs text-ink-soft leading-relaxed">
                Se borrará el local <span className="font-extrabold text-ink">{deleting.item.name}</span>, su usuario
                y sus documentos (también los archivos). Solo es posible si el local nunca operó. La auditoría conserva el
                registro. No se puede deshacer.
              </p>
            )}
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeleting(null)}
                disabled={deleteBusy}
                className="px-4 py-2 bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 rounded-xl text-xs font-bold cursor-pointer disabled:opacity-50"
              >
                Cancelar
              </button>
              <button
                onClick={handleConfirmDelete}
                disabled={deleteBusy}
                className="px-4 py-2 bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white rounded-xl text-xs font-bold cursor-pointer"
              >
                {deleteBusy ? 'Eliminando...' : 'Sí, eliminar'}
              </button>
            </div>
          </div>
        </div>
      )}

      {rejectingStore && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card bg-card p-6 rounded-3xl max-w-sm w-full space-y-4">
            <h3 className="text-sm font-black text-ink">Rechazar "{rejectingStore.name}"</h3>
            <textarea
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              placeholder="Motivo del rechazo..."
              className="w-full p-3 bg-page border border-line rounded-2xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
              rows={3}
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setRejectingStore(null)}
                className="px-4 py-2 bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 rounded-xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                onClick={handleReject}
                disabled={!rejectionReason.trim()}
                className="px-4 py-2 bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white rounded-xl text-xs font-bold cursor-pointer"
              >
                Confirmar rechazo
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
