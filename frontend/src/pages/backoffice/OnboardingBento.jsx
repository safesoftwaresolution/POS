import React, { useCallback, useEffect, useState } from 'react';
import { Clock, Users, Store, Check, X, AlertCircle, Mail, CheckCircle2 } from 'lucide-react';
import { getPendingUsersApi, getOnboardingStatsApi } from '../../services/backoffice/onboardingApi';
import { getBackofficeStoresApi, changeBackofficeStoreStatusApi, rejectBackofficeStoreApi } from '../../services/backoffice/storesApi';

function MetricCard({ label, value, icon: Icon, accent }) {
  return (
    <div className="bento-card p-4 flex items-center gap-3">
      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${accent}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <p className="text-lg font-black text-[#191c1e] dark:text-white leading-tight tabular-nums">{value}</p>
        <p className="text-[10px] font-bold text-gray-500 dark:text-gray-400 truncate">{label}</p>
      </div>
    </div>
  );
}

export default function OnboardingBento() {
  const [stats, setStats] = useState(null);
  const [pendingUsers, setPendingUsers] = useState([]);
  const [pendingStores, setPendingStores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [rejectingStore, setRejectingStore] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [statsData, usersData, storesData] = await Promise.all([
        getOnboardingStatsApi(),
        getPendingUsersApi(),
        getBackofficeStoresApi('PENDING_VERIFICATION'),
      ]);
      setStats(statsData);
      setPendingUsers(usersData.content || []);
      setPendingStores(storesData.content || []);
    } catch (e) {
      console.warn('Error cargando pendientes de onboarding:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

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
        <h1 className="text-2xl font-black text-[#191c1e] dark:text-white">Pendientes</h1>
        <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Usuarios y locales del auto-registro a la espera de verificación o aprobación</p>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
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
        <h2 className="text-sm font-black text-[#161b22] dark:text-[#f0f6fc] flex items-center gap-2">
          <Users className="w-4 h-4 text-amber-500" /> Usuarios pendientes de verificar su correo
        </h2>
        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Cargando...</div>
        ) : pendingUsers.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">No hay usuarios pendientes de verificar</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[560px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-gray-400 dark:text-gray-500 uppercase">
                  <th className="px-2 py-2">Nombre</th>
                  <th className="px-2 py-2">Usuario</th>
                  <th className="px-2 py-2">Correo</th>
                </tr>
              </thead>
              <tbody>
                {pendingUsers.map(u => (
                  <tr key={u.id} className="border-t border-[#e2e8f0] dark:border-[#262f38]">
                    <td className="px-2 py-3 font-extrabold text-[#161b22] dark:text-[#f0f6fc]">{u.fullName}</td>
                    <td className="px-2 py-3 text-gray-500 dark:text-gray-400 font-semibold">{u.username}</td>
                    <td className="px-2 py-3">
                      <span className="flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                        <Mail className="w-3.5 h-3.5 text-gray-400 dark:text-gray-500" /> {u.email}
                      </span>
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
        <h2 className="text-sm font-black text-[#161b22] dark:text-[#f0f6fc] flex items-center gap-2">
          <Store className="w-4 h-4 text-amber-500" /> Locales pendientes de aprobación
        </h2>
        {loading ? (
          <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Cargando...</div>
        ) : pendingStores.length === 0 ? (
          <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">No hay locales pendientes</div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[680px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-gray-400 dark:text-gray-500 uppercase">
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Contacto</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {pendingStores.map(s => (
                  <tr key={s.id} className="border-t border-[#e2e8f0] dark:border-[#262f38]">
                    <td className="px-2 py-3">
                      <p className="font-extrabold text-[#161b22] dark:text-[#f0f6fc]">{s.name}</p>
                      <p className="text-[10px] text-gray-400 dark:text-gray-500 font-mono">NIT: {s.taxId || '—'}</p>
                    </td>
                    <td className="px-2 py-3">
                      <span className="flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                        <Mail className="w-3.5 h-3.5 text-gray-400 dark:text-gray-500" /> {s.email}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-right space-x-2">
                      <button
                        onClick={() => handleApprove(s)}
                        className="px-3 py-1.5 bg-[#067A57] hover:bg-[#055C43] text-white rounded-xl text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer shadow-sm shadow-[#067A57]/20"
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

      {rejectingStore && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card bg-white dark:bg-[#161b22] p-6 rounded-3xl max-w-sm w-full space-y-4">
            <h3 className="text-sm font-black text-[#161b22] dark:text-[#f0f6fc]">Rechazar "{rejectingStore.name}"</h3>
            <textarea
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              placeholder="Motivo del rechazo..."
              className="w-full p-3 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl text-xs font-semibold text-[#161b22] dark:text-[#f0f6fc] focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
              rows={3}
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setRejectingStore(null)}
                className="px-4 py-2 bg-gray-100 dark:bg-[#262f38] text-gray-600 dark:text-gray-300 rounded-xl text-xs font-bold cursor-pointer"
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
