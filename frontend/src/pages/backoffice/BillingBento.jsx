import React, { useState, useEffect, useCallback } from 'react';
import {
  Receipt,
  Key,
  Wifi,
  CheckCircle2,
  XCircle,
  PauseCircle,
  Clock,
  AlertCircle,
  X,
  RefreshCw,
  ArrowLeftRight,
  Ban
} from 'lucide-react';
import {
  listBackofficeBillingByStatusApi,
  getBackofficeStoreBillingApi,
  updateBackofficeBillingCredentialsApi,
  testBackofficeBillingConnectionApi,
  switchBackofficeBillingEnvironmentApi,
  transitionBackofficeBillingStatusApi
} from '../../services/backoffice/billingApi';
import { getBackofficeStoresApi } from '../../services/backoffice/storesApi';
import { useModal } from '../../context/ModalContext';

const STATUSES = ['DOCUMENTS_SUBMITTED', 'AWAITING_PROVIDER', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'NOT_REQUESTED'];

const STATUS_LABELS = {
  NOT_REQUESTED: 'Sin Solicitar',
  DOCUMENTS_SUBMITTED: 'Docs. Enviados',
  AWAITING_PROVIDER: 'Esperando Factus',
  ACTIVE: 'Activo',
  REJECTED: 'Rechazado',
  SUSPENDED: 'Suspendido'
};

const STATUS_STYLES = {
  NOT_REQUESTED: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300',
  DOCUMENTS_SUBMITTED: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  AWAITING_PROVIDER: 'bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400 border border-blue-500/20',
  ACTIVE: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
  SUSPENDED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

// Espejo de BillingActivationStatus#canTransitionTo (backend/src/main/java/.../billing/dto/BillingActivationStatus.java).
// El backend es quien valida de verdad; esto solo evita ofrecer botones que sabemos que van a fallar.
const TRANSITIONS = {
  NOT_REQUESTED: ['DOCUMENTS_SUBMITTED'],
  DOCUMENTS_SUBMITTED: ['AWAITING_PROVIDER', 'REJECTED'],
  AWAITING_PROVIDER: ['ACTIVE', 'REJECTED'],
  ACTIVE: ['SUSPENDED'],
  REJECTED: ['DOCUMENTS_SUBMITTED'],
  SUSPENDED: ['ACTIVE']
};

const REASON_REQUIRED = new Set(['REJECTED', 'SUSPENDED']);

const EMPTY_CREDENTIALS = { clientId: '', clientSecret: '', username: '', password: '' };

export default function BillingBento() {
  const { notify, confirm } = useModal();
  const [statusFilter, setStatusFilter] = useState('DOCUMENTS_SUBMITTED');
  const [rows, setRows] = useState([]);
  const [storesById, setStoresById] = useState({});
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [envTab, setEnvTab] = useState('SANDBOX');
  const [credentialsForm, setCredentialsForm] = useState(EMPTY_CREDENTIALS);
  const [savingCredentials, setSavingCredentials] = useState(false);
  const [testResult, setTestResult] = useState(null);
  const [testing, setTesting] = useState(false);
  const [transitioning, setTransitioning] = useState(false);
  const [reasonTarget, setReasonTarget] = useState(null);
  const [reasonText, setReasonText] = useState('');

  const loadStores = useCallback(async () => {
    try {
      const data = await getBackofficeStoresApi('ALL', '', 0, 500);
      const map = {};
      for (const s of data.content || []) map[s.id] = s;
      setStoresById(map);
    } catch (e) {
      console.warn('Error cargando locales:', e);
    }
  }, []);

  const loadRows = useCallback(async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      setRows(await listBackofficeBillingByStatusApi(statusFilter));
    } catch (e) {
      setErrorMsg(e.message || 'Error al cargar el estado de facturación.');
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => { loadStores(); }, [loadStores]);
  useEffect(() => { loadRows(); }, [loadRows]);

  const openDetail = async (storeId) => {
    setDetailLoading(true);
    setTestResult(null);
    setReasonTarget(null);
    setReasonText('');
    setCredentialsForm(EMPTY_CREDENTIALS);
    try {
      const data = await getBackofficeStoreBillingApi(storeId);
      setDetail(data);
      setEnvTab(data.environment || 'SANDBOX');
    } catch (e) {
      notify('Error cargando el detalle de facturación: ' + e.message);
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetail = () => {
    setDetail(null);
  };

  const handleSaveCredentials = async (e) => {
    e.preventDefault();
    if (!detail) return;
    setSavingCredentials(true);
    setTestResult(null);
    try {
      const updated = await updateBackofficeBillingCredentialsApi(detail.storeId, envTab, credentialsForm);
      setDetail(updated);
      setCredentialsForm(EMPTY_CREDENTIALS);
      await loadRows();
    } catch (err) {
      notify('Error guardando credenciales: ' + err.message);
    } finally {
      setSavingCredentials(false);
    }
  };

  const handleTestConnection = async () => {
    if (!detail) return;
    setTesting(true);
    setTestResult(null);
    try {
      setTestResult(await testBackofficeBillingConnectionApi(detail.storeId, envTab));
    } catch (err) {
      setTestResult({ success: false, environment: envTab, message: err.message });
    } finally {
      setTesting(false);
    }
  };

  const handleSwitchEnvironment = async (environment) => {
    if (!detail) return;
    try {
      const updated = await switchBackofficeBillingEnvironmentApi(detail.storeId, environment);
      setDetail(updated);
      await loadRows();
    } catch (err) {
      notify('Error cambiando el entorno activo: ' + err.message);
    }
  };

  const handleTransition = async (targetStatus) => {
    if (!detail) return;
    if (REASON_REQUIRED.has(targetStatus) && reasonTarget !== targetStatus) {
      setReasonTarget(targetStatus);
      setReasonText('');
      return;
    }
    if (REASON_REQUIRED.has(targetStatus) && !reasonText.trim()) return;

    if (!REASON_REQUIRED.has(targetStatus)) {
      const ok = await confirm(`¿Cambiar el estado de facturación a "${STATUS_LABELS[targetStatus]}"?`, { danger: false });
      if (!ok) return;
    }

    setTransitioning(true);
    try {
      const updated = await transitionBackofficeBillingStatusApi(detail.storeId, targetStatus, reasonText.trim() || null);
      setDetail(updated);
      setReasonTarget(null);
      setReasonText('');
      await loadRows();
    } catch (err) {
      notify('Error cambiando la etapa: ' + err.message);
    } finally {
      setTransitioning(false);
    }
  };

  const storeName = (storeId) => storesById[storeId]?.name || `Local #${storeId}`;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Facturación Electrónica</h1>
          <p className="text-xs text-ink-soft font-medium">Habilitación DIAN/Factus por local: revisión, credenciales y etapa del proceso</p>
        </div>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      <div className="bento-card p-6 space-y-4">
        <div className="flex items-center gap-2 flex-wrap">
          {STATUSES.map(s => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-1.5 rounded-full text-[11px] font-bold cursor-pointer ${statusFilter === s ? 'bg-solid text-white' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'}`}
            >
              {STATUS_LABELS[s]}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">
            Consultando locales...
          </div>
        ) : rows.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Receipt className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay locales en "{STATUS_LABELS[statusFilter]}"</p>
          </div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[760px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Entorno Activo</th>
                  <th className="px-2 py-2 text-center">Sandbox</th>
                  <th className="px-2 py-2 text-center">Producción</th>
                  <th className="px-2 py-2">Rango Activo (S / P)</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.storeId} className="border-t border-line hover:bg-gray-50/60 dark:hover:bg-white/5">
                    <td className="px-2 py-3">
                      <p className="font-extrabold text-ink">{storeName(r.storeId)}</p>
                      <p className="text-[10px] text-ink-muted">{storesById[r.storeId]?.email || '—'}</p>
                    </td>
                    <td className="px-2 py-3 font-semibold text-gray-600 dark:text-gray-300">{r.environment}</td>
                    <td className="px-2 py-3 text-center">
                      {r.sandboxConfigured
                        ? <CheckCircle2 className="w-4 h-4 text-emerald-500 inline" />
                        : <XCircle className="w-4 h-4 text-gray-300 dark:text-gray-600 inline" />}
                    </td>
                    <td className="px-2 py-3 text-center">
                      {r.productionConfigured
                        ? <CheckCircle2 className="w-4 h-4 text-emerald-500 inline" />
                        : <XCircle className="w-4 h-4 text-gray-300 dark:text-gray-600 inline" />}
                    </td>
                    <td className="px-2 py-3 font-mono text-[11px] text-ink-soft">
                      {r.sandboxNumberingRangeId ?? '—'} / {r.productionNumberingRangeId ?? '—'}
                    </td>
                    <td className="px-2 py-3 text-right">
                      <button
                        onClick={() => openDetail(r.storeId)}
                        className="px-3 py-1.5 bg-gray-100 dark:bg-white/10 hover:bg-gray-200 dark:hover:bg-white/20 text-gray-700 dark:text-gray-200 rounded-xl text-[11px] font-bold cursor-pointer"
                      >
                        Gestionar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Detail Modal */}
      {(detail || detailLoading) && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-2xl w-full bg-card p-6 rounded-3xl shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            {detailLoading || !detail ? (
              <div className="py-8 text-center text-xs font-bold text-ink-muted">Cargando...</div>
            ) : (
              <>
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-extrabold text-base text-ink">{storeName(detail.storeId)}</h3>
                    <span className={`inline-block mt-1 text-[10px] px-2 py-0.5 rounded-full font-extrabold ${STATUS_STYLES[detail.status]}`}>
                      {STATUS_LABELS[detail.status]}
                    </span>
                    {detail.statusReason && (
                      <p className="text-[11px] text-red-500 dark:text-red-400 font-semibold mt-1.5">Motivo: {detail.statusReason}</p>
                    )}
                  </div>
                  <button
                    onClick={closeDetail}
                    className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>

                {/* Etapa del proceso */}
                <div className="p-4 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-line space-y-3">
                  <h4 className="font-extrabold text-xs text-ink flex items-center gap-2">
                    <Clock className="w-3.5 h-3.5 text-brand" /> Etapa del proceso
                  </h4>

                  {reasonTarget && (
                    <div className="space-y-2">
                      <input
                        type="text"
                        value={reasonText}
                        onChange={e => setReasonText(e.target.value)}
                        placeholder={`Motivo para "${STATUS_LABELS[reasonTarget]}" (obligatorio)`}
                        className="w-full p-2.5 bg-white dark:bg-page border border-line rounded-xl text-[11px] text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                      />
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => { setReasonTarget(null); setReasonText(''); }}
                          className="flex-1 py-1.5 bg-white dark:bg-line text-gray-600 dark:text-gray-300 rounded-xl text-[11px] font-bold cursor-pointer border border-[#EAEAEA] dark:border-transparent"
                        >
                          Cancelar
                        </button>
                        <button
                          type="button"
                          disabled={!reasonText.trim() || transitioning}
                          onClick={() => handleTransition(reasonTarget)}
                          className="flex-1 py-1.5 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white rounded-xl text-[11px] font-extrabold cursor-pointer"
                        >
                          Confirmar {STATUS_LABELS[reasonTarget]}
                        </button>
                      </div>
                    </div>
                  )}

                  {!reasonTarget && (
                    <div className="flex flex-wrap gap-2">
                      {(TRANSITIONS[detail.status] || []).map(target => (
                        <button
                          key={target}
                          type="button"
                          disabled={transitioning}
                          onClick={() => handleTransition(target)}
                          className={`px-3 py-1.5 rounded-xl text-[11px] font-extrabold cursor-pointer disabled:opacity-50 flex items-center gap-1.5 ${
                            target === 'REJECTED' || target === 'SUSPENDED'
                              ? 'bg-white dark:bg-line border border-red-200 dark:border-red-800/40 text-red-600 dark:text-red-400'
                              : 'bg-solid hover:bg-solid-hover text-white shadow-sm shadow-[#0A0A0A]/10'
                          }`}
                        >
                          {target === 'REJECTED' && <Ban size={14} />}
                          {target === 'SUSPENDED' && <PauseCircle className="w-3.5 h-3.5" />}
                          {target === 'ACTIVE' && <CheckCircle2 className="w-3.5 h-3.5" />}
                          {STATUS_LABELS[target]}
                        </button>
                      ))}
                      {(TRANSITIONS[detail.status] || []).length === 0 && (
                        <p className="text-[11px] text-ink-muted">No hay transiciones disponibles desde este estado.</p>
                      )}
                    </div>
                  )}
                </div>

                {/* Entorno activo */}
                <div className="p-4 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-line space-y-3">
                  <h4 className="font-extrabold text-xs text-ink flex items-center gap-2">
                    <ArrowLeftRight className="w-3.5 h-3.5 text-brand" /> Entorno activo para facturar: <span className="text-brand">{detail.environment}</span>
                  </h4>
                  <div className="flex gap-2">
                    {['SANDBOX', 'PRODUCTION'].map(env => (
                      <button
                        key={env}
                        type="button"
                        disabled={detail.environment === env}
                        onClick={() => handleSwitchEnvironment(env)}
                        className="flex-1 py-1.5 bg-white dark:bg-line border border-[#EAEAEA] dark:border-transparent disabled:opacity-40 text-gray-700 dark:text-gray-200 rounded-xl text-[11px] font-bold cursor-pointer"
                      >
                        Usar {env}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Credenciales */}
                <div className="p-4 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-line space-y-3">
                  <h4 className="font-extrabold text-xs text-ink flex items-center gap-2">
                    <Key className="w-3.5 h-3.5 text-brand" /> Credenciales de Factus
                  </h4>

                  <div className="flex gap-2">
                    {['SANDBOX', 'PRODUCTION'].map(env => (
                      <button
                        key={env}
                        type="button"
                        onClick={() => { setEnvTab(env); setCredentialsForm(EMPTY_CREDENTIALS); setTestResult(null); }}
                        className={`px-3 py-1.5 rounded-full text-[11px] font-bold cursor-pointer ${envTab === env ? 'bg-solid text-white' : 'bg-white dark:bg-white/10 text-gray-600 dark:text-gray-300'}`}
                      >
                        {env} {env === 'SANDBOX' ? (detail.sandboxConfigured ? '✓' : '') : (detail.productionConfigured ? '✓' : '')}
                      </button>
                    ))}
                  </div>

                  <form onSubmit={handleSaveCredentials} className="space-y-2 text-xs">
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Client ID:</label>
                        <input
                          type="text"
                          value={credentialsForm.clientId}
                          onChange={e => setCredentialsForm({ ...credentialsForm, clientId: e.target.value })}
                          className="w-full p-2.5 bg-white dark:bg-page border border-line rounded-xl text-[11px] text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                        />
                      </div>
                      <div>
                        <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Client Secret:</label>
                        <input
                          type="password"
                          value={credentialsForm.clientSecret}
                          onChange={e => setCredentialsForm({ ...credentialsForm, clientSecret: e.target.value })}
                          placeholder="Vacío = conservar el guardado"
                          className="w-full p-2.5 bg-white dark:bg-page border border-line rounded-xl text-[11px] text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                        />
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Usuario:</label>
                        <input
                          type="text"
                          value={credentialsForm.username}
                          onChange={e => setCredentialsForm({ ...credentialsForm, username: e.target.value })}
                          className="w-full p-2.5 bg-white dark:bg-page border border-line rounded-xl text-[11px] text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                        />
                      </div>
                      <div>
                        <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Contraseña:</label>
                        <input
                          type="password"
                          value={credentialsForm.password}
                          onChange={e => setCredentialsForm({ ...credentialsForm, password: e.target.value })}
                          placeholder="Vacío = conservar la guardada"
                          className="w-full p-2.5 bg-white dark:bg-page border border-line rounded-xl text-[11px] text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                        />
                      </div>
                    </div>

                    {testResult && (
                      <div className={`p-2.5 rounded-xl text-[11px] font-bold flex items-center gap-2 ${
                        testResult.success
                          ? 'bg-emerald-50 dark:bg-emerald-950/30 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40'
                          : 'bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40'
                      }`}>
                        {testResult.success ? <CheckCircle2 className="w-3.5 h-3.5 shrink-0" /> : <XCircle className="w-3.5 h-3.5 shrink-0" />}
                        <span>{testResult.message}</span>
                      </div>
                    )}

                    <div className="flex gap-2 pt-1">
                      <button
                        type="button"
                        onClick={handleTestConnection}
                        disabled={testing}
                        className="flex-1 py-2 bg-white dark:bg-line border border-[#EAEAEA] dark:border-transparent disabled:opacity-50 text-gray-700 dark:text-gray-200 rounded-xl text-[11px] font-bold cursor-pointer flex items-center justify-center gap-1.5"
                      >
                        {testing ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Wifi className="w-3.5 h-3.5" />}
                        Probar Conexión
                      </button>
                      <button
                        type="submit"
                        disabled={savingCredentials}
                        className="flex-1 py-2 bg-solid hover:bg-solid-hover disabled:opacity-50 text-white rounded-xl text-[11px] font-extrabold cursor-pointer shadow-sm shadow-[#0A0A0A]/10"
                      >
                        {savingCredentials ? 'Guardando...' : `Guardar Credenciales ${envTab}`}
                      </button>
                    </div>
                  </form>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
