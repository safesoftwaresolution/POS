import React, { useState } from 'react';
import {
  Store,
  Plus,
  Search,
  Edit3,
  Mail,
  MailCheck,
  FileText,
  AlertCircle,
  X,
  Check,
  Ban,
  Clock,
  Building2,
  Users,
  Trash2
} from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  getBackofficeMetricsApi,
  getBackofficeStoresApi,
  createBackofficeStoreApi,
  updateBackofficeStoreApi,
  changeBackofficeStoreStatusApi,
  verifyBackofficeStoreEmailApi,
  getBackofficeStoreDocumentsApi,
  reviewBackofficeStoreDocumentApi,
  getBackofficeStoreCategoriesApi,
  getPlansApi,
  getStorePlanUsageApi,
  changeBackofficeStorePlanApi,
  deleteBackofficeStoreApi
} from '../../services/backoffice/storesApi';
import useModal from '../../hooks/useModal';
import useLoad from '../../hooks/useLoad';
import useDebouncedValue from '../../hooks/useDebouncedValue';
import asList from '../../utils/asList';
import { DOC_TYPE_LABELS, DOC_STATUS_STYLES } from '../../constants/storeDocuments';

const STATUSES = ['ACTIVE', 'INACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED'];

const STATUS_LABELS = {
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo',
  PENDING_VERIFICATION: 'Pend. Verificación',
  SUSPENDED: 'Suspendido'
};

const STATUS_STYLES = {
  ACTIVE: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  INACTIVE: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300',
  PENDING_VERIFICATION: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  SUSPENDED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

const EMPTY_STORE_FORM = {
  name: '', storeCategoryId: '', phone: '', email: '', website: '', address: '', taxId: ''
};

function MetricCard({ label, value, icon: Icon, accent }) {
  return (
    <div className="bento-card p-4 flex items-center gap-3">
      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${accent}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <p className="text-lg font-black text-ink leading-tight tabular-nums">{value}</p>
        <p className="text-[10px] font-bold text-ink-soft truncate">{label}</p>
      </div>
    </div>
  );
}

export default function StoresBento() {
  const { notify } = useModal();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const debouncedSearch = useDebouncedValue(search, 250);

  const { data: metrics, reload: reloadMetrics } = useLoad(() => getBackofficeMetricsApi(), [], { initial: null, autoRefresh: true });
  const { data: stores, loading, reload: reloadStores } = useLoad(
    async () => (await getBackofficeStoresApi(statusFilter, debouncedSearch)).content || [],
    [statusFilter, debouncedSearch],
    { initial: [], autoRefresh: true },
  );
  const { data: categories, reload: reloadCategories } = useLoad(() => asList(getBackofficeStoreCategoriesApi()), [], { initial: [] });
  const { data: planData, reload: reloadPlans } = useLoad(
    async () => {
      const [planList, usage] = await Promise.all([getPlansApi(), getStorePlanUsageApi()]);
      return { plans: planList, planUsage: Object.fromEntries(usage.map((u) => [u.storeId, u])) };
    },
    [],
    { initial: { plans: [], planUsage: {} }, autoRefresh: true },
  );
  const { plans, planUsage } = planData;
  const [errorMsg, setErrorMsg] = useState('');

  const [showStoreModal, setShowStoreModal] = useState(false);
  const [editingStore, setEditingStore] = useState(null);
  const [storeForm, setStoreForm] = useState(EMPTY_STORE_FORM);
  const [formError, setFormError] = useState('');

  const [docsStore, setDocsStore] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [docsLoading, setDocsLoading] = useState(false);
  const [rejectingDoc, setRejectingDoc] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');

  const [deletingStore, setDeletingStore] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState('');
  const [deleteBusy, setDeleteBusy] = useState(false);

  const refreshAll = () => {
    reloadMetrics();
    reloadStores();
    reloadCategories();
    reloadPlans();
  };

  const handleChangePlan = async (store, planCode) => {
    setErrorMsg('');
    try {
      await changeBackofficeStorePlanApi(store.id, planCode);
      reloadStores();
      reloadPlans();
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo cambiar el plan.');
    }
  };

  const openDeleteStore = (store) => {
    setErrorMsg('');
    setDeleteConfirm('');
    setDeletingStore(store);
  };

  const confirmDeleteStore = async () => {
    if (!deletingStore) return;
    setDeleteBusy(true);
    try {
      await deleteBackofficeStoreApi(deletingStore.id, deleteConfirm.trim());
      notify(`El local "${deletingStore.name}" fue eliminado. Sus administradores quedaron sin local.`,
        { title: 'Local eliminado', danger: false });
      setDeletingStore(null);
      refreshAll();
    } catch (err) {
      setDeletingStore(null);
      setErrorMsg(err.message || 'No se pudo eliminar el local.');
    } finally {
      setDeleteBusy(false);
    }
  };

  // ---------- Store CRUD ----------
  const openCreateStore = () => {
    setEditingStore(null);
    setStoreForm(EMPTY_STORE_FORM);
    setFormError('');
    setShowStoreModal(true);
  };

  const openEditStore = (store) => {
    setEditingStore(store);
    setStoreForm({
      name: store.name || '',
      storeCategoryId: store.storeCategoryId || '',
      phone: store.phone || '',
      email: store.email || '',
      website: store.website || '',
      address: store.address || '',
      taxId: store.taxId || ''
    });
    setFormError('');
    setShowStoreModal(true);
  };

  const handleSubmitStore = async (e) => {
    e.preventDefault();
    setFormError('');
    const payload = {
      ...storeForm,
      storeCategoryId: storeForm.storeCategoryId ? Number(storeForm.storeCategoryId) : null
    };
    try {
      if (editingStore) {
        await updateBackofficeStoreApi(editingStore.id, payload);
      } else {
        await createBackofficeStoreApi(payload);
      }
      setShowStoreModal(false);
      refreshAll();
    } catch (err) {
      setFormError(err.message || 'Error al guardar el local. Intenta nuevamente.');
    }
  };

  const handleChangeStatus = async (store, status) => {
    setErrorMsg('');
    try {
      await changeBackofficeStoreStatusApi(store.id, status);
      refreshAll();
    } catch (err) {
      setErrorMsg(err.message || 'Error al cambiar el estado del local.');
    }
  };

  const handleVerifyEmail = async (store) => {
    setErrorMsg('');
    try {
      await verifyBackofficeStoreEmailApi(store.id);
      refreshAll();
    } catch (err) {
      setErrorMsg(err.message || 'Error al verificar el correo del local.');
    }
  };

  // ---------- Documents ----------
  const openDocuments = async (store) => {
    setDocsStore(store);
    setDocsLoading(true);
    setRejectingDoc(null);
    setRejectionReason('');
    try {
      setDocuments(await getBackofficeStoreDocumentsApi(store.id));
    } catch (e) {
      console.warn('Error cargando documentos:', e);
      setDocuments([]);
    } finally {
      setDocsLoading(false);
    }
  };

  const handleApproveDoc = async (doc) => {
    try {
      await reviewBackofficeStoreDocumentApi(docsStore.id, doc.id, 'APPROVED');
      setDocuments(await getBackofficeStoreDocumentsApi(docsStore.id));
    } catch (err) {
      notify('Error aprobando documento: ' + err.message);
    }
  };

  const handleRejectDoc = async (doc) => {
    if (!rejectionReason.trim()) return;
    try {
      await reviewBackofficeStoreDocumentApi(docsStore.id, doc.id, 'REJECTED', rejectionReason.trim());
      setRejectingDoc(null);
      setRejectionReason('');
      setDocuments(await getBackofficeStoreDocumentsApi(docsStore.id));
    } catch (err) {
      notify('Error rechazando documento: ' + err.message);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Locales</h1>
          <p className="text-xs text-ink-soft font-medium">Administración global de locales y verificación KYC</p>
        </div>

        <button
          onClick={openCreateStore}
          className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
        >
          <Plus className="w-4 h-4" /> Nuevo Local
        </button>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Metrics */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <MetricCard label="Locales Totales" value={metrics?.totalStores ?? '—'} icon={Store} accent="bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300" />
        <MetricCard label="Activos" value={metrics?.activeStores ?? '—'} icon={Check} accent="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" />
        <MetricCard label="Inactivos" value={metrics?.inactiveStores ?? '—'} icon={Ban} accent="bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300" />
        <MetricCard label="Pend. Verificación" value={metrics?.pendingVerification ?? '—'} icon={Clock} accent="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400" />
        <MetricCard label="Suspendidos" value={metrics?.suspended ?? '—'} icon={AlertCircle} accent="bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400" />
        <MetricCard label="Correos Verificados" value={metrics?.emailVerified ?? '—'} icon={MailCheck} accent="bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400" />
      </div>

      {/* Stores list */}
      <div className="bento-card p-6 space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por nombre o correo..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink dark:focus:border-brand"
            />
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <button
              onClick={() => setStatusFilter('ALL')}
              className={`px-3 py-1.5 rounded-full text-[11px] font-bold cursor-pointer ${statusFilter === 'ALL' ? 'bg-solid text-white' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'}`}
            >
              Todos
            </button>
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
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">
            Consultando locales...
          </div>
        ) : stores.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Building2 className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No se encontraron locales</p>
              <p className="text-[11px] text-ink-muted">Crea el primer local del SaaS para empezar</p>
            </div>
          </div>
        ) : (
          <div className="md:overflow-x-auto md:-mx-2">
            <table className="table-cards w-full text-xs md:min-w-[920px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Local</th>
                  <th className="px-2 py-2">Categoría</th>
                  <th className="px-2 py-2">Contacto</th>
                  <th className="px-2 py-2">Estado</th>
                  <th className="px-2 py-2">Plan</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {stores.map(s => (
                  <tr key={s.id} className="border-t border-line hover:bg-gray-50/60 dark:hover:bg-white/5">
                    <td data-label="" className="px-2 py-3">
                      <p className="font-extrabold text-ink">{s.name}</p>
                      <p className="text-[10px] text-ink-muted font-mono">NIT: {s.taxId || '—'}</p>
                    </td>
                    <td data-label="Categoría" className="px-2 py-3 text-ink-soft font-semibold">{s.categoryName || 'Sin categoría'}</td>
                    <td data-label="Contacto" className="px-2 py-3">
                      <p className="flex items-center gap-1.5 font-semibold text-gray-600 dark:text-gray-300">
                        <Mail className="w-3.5 h-3.5 text-ink-muted" /> {s.email}
                      </p>
                      <span className={`inline-flex items-center gap-1 mt-1 text-[10px] font-extrabold ${s.emailVerified ? 'text-emerald-600 dark:text-emerald-400' : 'text-ink-muted'}`}>
                        <MailCheck className="w-3 h-3" /> {s.emailVerified ? 'Verificado' : 'Sin verificar'}
                      </span>
                    </td>
                    <td data-label="Estado" className="px-2 py-3">
                      <select
                        value={s.status}
                        onChange={(e) => handleChangeStatus(s, e.target.value)}
                        className={`text-[10px] font-extrabold px-2 py-1 rounded-full border-0 cursor-pointer ${STATUS_STYLES[s.status] || ''}`}
                      >
                        {STATUSES.map(st => (
                          <option key={st} value={st}>{STATUS_LABELS[st]}</option>
                        ))}
                      </select>
                    </td>
                    <td data-label="Plan" className="px-2 py-3">
                      <select
                        value={s.planCode || ''}
                        onChange={(e) => handleChangePlan(s, e.target.value)}
                        className="text-[10px] font-extrabold px-2 py-1 rounded-full border border-line bg-card text-ink cursor-pointer"
                      >
                        {plans.map(p => (
                          <option key={p.code} value={p.code}>{p.name}</option>
                        ))}
                      </select>
                      {planUsage[s.id] && (
                        <p className={`mt-1 text-[10px] font-bold tabular-nums ${
                          planUsage[s.id].activeUsers > planUsage[s.id].maxActiveUsers
                            ? 'text-red-500'
                            : planUsage[s.id].activeUsers === planUsage[s.id].maxActiveUsers
                              ? 'text-amber-600 dark:text-amber-400'
                              : 'text-ink-muted'
                        }`}>
                          {planUsage[s.id].activeUsers}/{planUsage[s.id].maxActiveUsers} usuarios
                        </p>
                      )}
                    </td>
                    <td data-label="Acciones" className="px-2 py-3">
                      <div className="flex items-center justify-end gap-1">
                        {!s.emailVerified && (
                          <button
                            onClick={() => handleVerifyEmail(s)}
                            title="Marcar correo como verificado"
                            className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <MailCheck className="w-4 h-4" />
                          </button>
                        )}
                        <Link
                          to={`/backoffice/users?storeId=${s.id}`}
                          title="Ver usuarios del local"
                          className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                        >
                          <Users className="w-4 h-4" />
                        </Link>
                        <button
                          onClick={() => openDocuments(s)}
                          title="Documentos KYC"
                          className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                        >
                          <FileText className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => openEditStore(s)}
                          title="Editar local"
                          className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                        >
                          <Edit3 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => openDeleteStore(s)}
                          title="Eliminar local definitivamente"
                          className="p-1.5 text-ink-muted hover:text-red-500 rounded-lg hover:bg-red-50 dark:hover:bg-red-500/10 cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create/Edit Store Modal */}
      {deletingStore && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card bg-card p-6 rounded-3xl max-w-md w-full space-y-4">
            <h3 className="text-sm font-black text-ink flex items-center gap-2">
              <Trash2 className="w-4 h-4 text-red-500" /> Eliminar "{deletingStore.name}"
            </h3>
            <p className="text-xs text-ink-soft leading-relaxed">
              Se borra el local con todos sus datos: productos, clientes, ventas, compras, inventario, documentos y su
              personal (supervisores y vendedores). No se puede deshacer. Los administradores <span className="font-bold text-ink">no
              se eliminan</span>: quedan sin local y podrás reasignarlos o eliminarlos desde Usuarios. Si el local tiene facturas
              electrónicas validadas ante la DIAN no se podrá eliminar.
            </p>
            <div className="space-y-1">
              <label className="text-[11px] font-bold text-ink-soft block">
                Escribe el nombre del local para confirmar:
              </label>
              <input
                value={deleteConfirm}
                onChange={(e) => setDeleteConfirm(e.target.value)}
                placeholder={deletingStore.name}
                className="w-full p-3 bg-page border border-line rounded-2xl text-xs font-semibold text-ink focus:outline-none focus:border-red-500"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeletingStore(null)}
                disabled={deleteBusy}
                className="px-4 py-2 bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 rounded-xl text-xs font-bold cursor-pointer disabled:opacity-50"
              >
                Cancelar
              </button>
              <button
                onClick={confirmDeleteStore}
                disabled={deleteBusy || deleteConfirm.trim().toLowerCase() !== (deletingStore.name || '').trim().toLowerCase()}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold cursor-pointer"
              >
                {deleteBusy ? 'Eliminando...' : 'Eliminar definitivamente'}
              </button>
            </div>
          </div>
        </div>
      )}

      {showStoreModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmitStore} className="bento-card max-w-lg w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <h3 className="font-extrabold text-base text-ink">
              {editingStore ? `Editar Local: ${editingStore.name}` : 'Crear Nuevo Local'}
            </h3>

            {formError && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre del Local:</label>
                <input
                  type="text"
                  value={storeForm.name}
                  onChange={e => setStoreForm({ ...storeForm, name: e.target.value })}
                  placeholder="Ej. Tienda La Esquina"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Categoría:</label>
                  <select
                    value={storeForm.storeCategoryId}
                    onChange={e => setStoreForm({ ...storeForm, storeCategoryId: e.target.value })}
                    className="w-full p-2.5 bg-page border border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  >
                    <option value="">Sin categoría</option>
                    {categories.map(c => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">NIT / Tax ID:</label>
                  <input
                    type="text"
                    value={storeForm.taxId}
                    onChange={e => setStoreForm({ ...storeForm, taxId: e.target.value })}
                    placeholder="900123456-1"
                    className="w-full p-2.5 bg-page border border-line rounded-2xl font-mono text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Correo Electrónico:</label>
                  <input
                    type="email"
                    value={storeForm.email}
                    onChange={e => setStoreForm({ ...storeForm, email: e.target.value })}
                    placeholder="contacto@local.com"
                    className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                    required
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Teléfono:</label>
                  <input
                    type="text"
                    value={storeForm.phone}
                    onChange={e => setStoreForm({ ...storeForm, phone: e.target.value })}
                    placeholder="3001234567"
                    className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  />
                </div>
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Sitio Web:</label>
                <input
                  type="text"
                  value={storeForm.website}
                  onChange={e => setStoreForm({ ...storeForm, website: e.target.value })}
                  placeholder="https://mitienda.com"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                />
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Dirección:</label>
                <input
                  type="text"
                  value={storeForm.address}
                  onChange={e => setStoreForm({ ...storeForm, address: e.target.value })}
                  placeholder="Calle 10 # 5-20"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowStoreModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
              >
                {editingStore ? 'Guardar Cambios' : 'Crear Local'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Documents Modal */}
      {docsStore && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bento-card max-w-lg w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-extrabold text-base text-ink">Documentos KYC</h3>
                <p className="text-[11px] text-ink-muted font-semibold">{docsStore.name}</p>
              </div>
              <button
                onClick={() => setDocsStore(null)}
                className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {docsLoading ? (
              <div className="py-8 text-center text-xs font-bold text-ink-muted">Consultando documentos...</div>
            ) : documents.length === 0 ? (
              <div className="py-8 text-center text-xs font-bold text-ink-muted">
                Este local no ha enviado documentos aún.
              </div>
            ) : (
              <div className="space-y-3">
                {documents.map(doc => (
                  <div key={doc.id} className="p-4 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-line space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="font-extrabold text-xs text-ink">{DOC_TYPE_LABELS[doc.documentType] || doc.documentType}</span>
                      <span className={`text-[10px] px-2 py-0.5 rounded-full font-extrabold ${DOC_STATUS_STYLES[doc.status] || ''}`}>{doc.status}</span>
                    </div>
                    <a
                      href={doc.documentUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-[11px] text-brand dark:text-brand font-semibold underline break-all"
                    >
                      Ver documento
                    </a>
                    {doc.rejectionReason && (
                      <p className="text-[11px] text-red-500 dark:text-red-400 font-semibold">Motivo de rechazo: {doc.rejectionReason}</p>
                    )}

                    {doc.status === 'PENDING' && (
                      rejectingDoc === doc.id ? (
                        <div className="space-y-2 pt-1">
                          <input
                            type="text"
                            value={rejectionReason}
                            onChange={e => setRejectionReason(e.target.value)}
                            placeholder="Motivo del rechazo (obligatorio)"
                            className="w-full p-2 bg-page border border-line rounded-xl text-[11px] text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                          />
                          <div className="flex gap-2">
                            <button
                              type="button"
                              onClick={() => { setRejectingDoc(null); setRejectionReason(''); }}
                              className="flex-1 py-1.5 bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 rounded-xl text-[11px] font-bold cursor-pointer"
                            >
                              Cancelar
                            </button>
                            <button
                              type="button"
                              onClick={() => handleRejectDoc(doc)}
                              disabled={!rejectionReason.trim()}
                              className="flex-1 py-1.5 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white rounded-xl text-[11px] font-extrabold cursor-pointer"
                            >
                              Confirmar Rechazo
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex gap-2 pt-1">
                          <button
                            type="button"
                            onClick={() => handleApproveDoc(doc)}
                            className="flex-1 py-1.5 bg-solid hover:bg-solid-hover text-white rounded-xl text-[11px] font-extrabold flex items-center justify-center gap-1 cursor-pointer shadow-sm shadow-[#0A0A0A]/10"
                          >
                            <Check className="w-3.5 h-3.5" /> Aprobar
                          </button>
                          <button
                            type="button"
                            onClick={() => setRejectingDoc(doc.id)}
                            className="flex-1 py-1.5 bg-white dark:bg-line border border-red-200 dark:border-red-800/40 text-red-600 dark:text-red-400 rounded-xl text-[11px] font-extrabold flex items-center justify-center gap-1 cursor-pointer"
                          >
                            <Ban className="w-3.5 h-3.5" /> Rechazar
                          </button>
                        </div>
                      )
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
