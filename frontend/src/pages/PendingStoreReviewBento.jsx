import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, XCircle, PauseCircle, Ban, WifiOff, LogOut, Send, AlertCircle, RefreshCw, FileCheck2, ChevronDown } from 'lucide-react';
import useAuth from '../hooks/useAuth';
import { updateOwnStoreApi, resubmitOwnStoreApi } from '../services/storesApi';
import StoreProfileFields from '../components/store/StoreProfileFields';
import StoreDocumentsGrid from '../components/store/StoreDocumentsGrid';

const STATUS_COPY = {
  PENDING_VERIFICATION: {
    icon: Clock,
    iconClass: 'text-amber-500',
    title: 'Tu local está en revisión',
    message: 'Ya registramos los datos de tu local. Un administrador de la plataforma lo revisará pronto; te avisaremos por correo en cuanto quede activo.',
  },
  REJECTED: {
    icon: XCircle,
    iconClass: 'text-red-500',
    title: 'Tu local necesita correcciones',
    message: 'La plataforma no aprobó el registro de tu local. Corrige los datos según el motivo y reenvíalo a revisión.',
  },
  SUSPENDED: {
    icon: PauseCircle,
    iconClass: 'text-orange-500',
    title: 'Tu local está suspendido',
    message: 'La plataforma suspendió temporalmente tu local, así que por ahora no puedes operar. Escríbenos desde Soporte para conocer el motivo y reactivarlo.',
  },
  INACTIVE: {
    icon: Ban,
    iconClass: 'text-gray-500',
    title: 'Tu local está inactivo',
    message: 'Tu local fue desactivado. Si crees que es un error, escríbenos desde Soporte.',
  },
};

// El personal (SUPERVISOR, SELLER) no puede corregir el local: solo esperar a que el administrador lo resuelva.
const STAFF_MESSAGE = {
  PENDING_VERIFICATION: 'El local todavía está en revisión. Podrás entrar en cuanto la plataforma lo apruebe.',
  REJECTED: 'El registro del local necesita correcciones. El administrador del local debe corregirlo para que vuelva a revisión.',
  SUSPENDED: 'El local está suspendido temporalmente. Consulta con el administrador del local.',
  INACTIVE: 'El local está inactivo. Consulta con el administrador del local.',
};

const LOAD_ERROR_COPY = {
  icon: WifiOff,
  iconClass: 'text-gray-500',
  title: 'No pudimos consultar tu local',
  message: 'Revisa tu conexión e inténtalo de nuevo en unos segundos.',
};

function toForm(store) {
  return {
    name: store?.name || '',
    phone: store?.phone || '',
    taxId: store?.taxId || '',
    address: store?.address || '',
    website: store?.website || '',
  };
}

/** Formulario para corregir un local rechazado y reenviarlo a revisión. */
function RejectedStoreForm({ store, onStoreChange }) {
  const [form, setForm] = useState(() => toForm(store));
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const handleChange = (field) => (e) => setForm(prev => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSaving(true);
    try {
      await updateOwnStoreApi(form);
      const resubmitted = await resubmitOwnStoreApi();
      onStoreChange?.(resubmitted);
    } catch (err) {
      setError(err.message || 'No se pudo reenviar el local a revisión.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-left">
      {error && (
        <div className="p-3.5 bg-red-100 dark:bg-red-500/10 border border-red-300 dark:border-red-500/30 rounded-2xl text-xs font-bold text-red-600 dark:text-red-300 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}
      <StoreProfileFields values={form} onChange={handleChange} />
      <p className="text-[11px] text-ink-muted font-semibold">
        Guardamos tus cambios y enviamos el local de nuevo a revisión. El correo del local ({store?.email}) no se puede cambiar desde aquí.
      </p>
      <button
        type="submit"
        disabled={saving}
        className="w-full py-3 bg-solid hover:bg-solid-hover text-solid-ink font-black text-xs rounded-2xl flex items-center justify-center gap-2 transition-all cursor-pointer disabled:opacity-50"
      >
        <Send className="w-4 h-4" />
        <span>{saving ? 'Reenviando...' : 'Reenviar a revisión'}</span>
      </button>
    </form>
  );
}

/** Documentos KYC opcionales mientras el local está en revisión: subirlos ayuda a aprobarlo más rápido. */
function OptionalDocuments() {
  return (
    <details className="group text-left rounded-2xl border border-line">
      <summary className="list-none cursor-pointer p-3.5 flex items-center gap-2 text-xs font-bold text-ink">
        <FileCheck2 className="w-4 h-4 text-brand shrink-0" />
        <span className="flex-1">
          Sube los documentos de tu local (opcional)
          <span className="block text-[11px] font-semibold text-ink-muted">
            Ayuda a aprobar más rápido: RUT, Cámara de Comercio, cédula... No son obligatorios para facturar.
          </span>
        </span>
        <ChevronDown className="w-4 h-4 text-ink-muted transition-transform group-open:rotate-180" />
      </summary>
      <div className="p-3.5 pt-0 space-y-3">
        <StoreDocumentsGrid className="grid grid-cols-1 sm:grid-cols-2 gap-3" />
      </div>
    </details>
  );
}

/**
 * Pantalla que reemplaza al POS mientras el local no está ACTIVE. Con `canManage` (ADMINISTRATOR) `store` es el
 * local completo y se puede corregir o adelantar documentos; para el resto del personal solo trae `status`.
 * Con `loadError` se muestra el aviso de error de carga y `onRetry` vuelve a consultar.
 */
export default function PendingStoreReviewBento({ store, loadError = false, canManage = true, onRetry, onStoreChange }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const copy = loadError ? LOAD_ERROR_COPY : (STATUS_COPY[store?.status] || STATUS_COPY.PENDING_VERIFICATION);
  const Icon = copy.icon;
  const rejected = canManage && !loadError && store?.status === 'REJECTED';
  const message = !loadError && !canManage ? (STAFF_MESSAGE[store?.status] || copy.message) : copy.message;
  // Mientras se revisa (o se corrige) el local se pueden adelantar los documentos; no son obligatorios.
  const canUploadDocuments = canManage && !loadError && (store?.status === 'PENDING_VERIFICATION' || rejected);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-page text-ink flex items-center justify-center p-4 select-none">
      <div className={`${canUploadDocuments ? 'max-w-2xl' : 'max-w-md'} w-full bento-card p-8 rounded-3xl shadow-xl space-y-5 text-center`}>
        <div className={`w-16 h-16 rounded-2xl bg-gray-100 dark:bg-white/10 flex items-center justify-center mx-auto ${copy.iconClass}`}>
          <Icon className="w-9 h-9" />
        </div>
        <h1 className="text-lg font-black">{copy.title}</h1>
        <p className="text-xs text-ink-soft font-semibold leading-relaxed">
          {message}
        </p>
        {rejected && store?.rejectionReason && (
          <div className="p-3.5 bg-red-100 dark:bg-red-500/10 border border-red-300 dark:border-red-500/30 rounded-2xl text-xs font-bold text-red-600 dark:text-red-300 text-left">
            Motivo: {store.rejectionReason}
          </div>
        )}
        {rejected && <RejectedStoreForm key={store.id} store={store} onStoreChange={onStoreChange} />}
        {canUploadDocuments && <OptionalDocuments />}
        {loadError && onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="w-full py-3 bg-solid hover:bg-solid-hover text-solid-ink font-black text-xs rounded-2xl flex items-center justify-center gap-2 transition-all cursor-pointer"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Reintentar</span>
          </button>
        )}
        <button
          type="button"
          onClick={handleLogout}
          className="w-full py-3 bg-gray-100 dark:bg-white/10 hover:bg-gray-200 dark:hover:bg-white/20 text-[#17171B] dark:text-white font-black text-xs rounded-2xl flex items-center justify-center gap-2 transition-all cursor-pointer"
        >
          <LogOut className="w-4 h-4" />
          <span>Cerrar sesión</span>
        </button>
      </div>
    </div>
  );
}
