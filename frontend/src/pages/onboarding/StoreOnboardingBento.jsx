import React, { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { Store, ArrowRight, AlertCircle, LogOut, CheckCircle2 } from 'lucide-react';
import { registerOwnStoreApi } from '../../services/storesApi';
import useAuth from '../../hooks/useAuth';
import StoreProfileFields from '../../components/store/StoreProfileFields';
import { onboardingRedirect } from '../../utils/authFlow';

const EMPTY_FORM = { name: '', phone: '', email: '', website: '', address: '', taxId: '' };

export default function StoreOnboardingBento() {
  const navigate = useNavigate();
  const { user, refreshSession, logout } = useAuth();
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  // El local se creó pero no se pudo renovar la sesión (el token sigue sin storeId): no se debe volver a enviar.
  const [registeredWithoutSession, setRegisteredWithoutSession] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const redirect = onboardingRedirect(user);
  if (redirect && !registeredWithoutSession) {
    return <Navigate to={redirect} replace />;
  }

  const handleChange = (field) => (e) => setForm(prev => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await registerOwnStoreApi({ ...form, storeCategoryId: null });
    } catch (err) {
      setError(err.message || 'No se pudo registrar el local.');
      setLoading(false);
      return;
    }
    try {
      await refreshSession();
      navigate('/', { replace: true });
    } catch {
      setRegisteredWithoutSession(true);
    } finally {
      setLoading(false);
    }
  };

  if (registeredWithoutSession) {
    return (
      <div className="min-h-screen bg-page text-ink flex items-center justify-center p-4 select-none">
        <div className="max-w-md w-full bento-card p-8 rounded-3xl shadow-xl space-y-5 text-center">
          <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto" />
          <h1 className="text-lg font-black">Tu local quedó registrado</h1>
          <p className="text-xs text-ink-soft font-semibold leading-relaxed">
            No pudimos actualizar tu sesión. Inicia sesión de nuevo para ver el estado de tu local.
          </p>
          <button
            type="button"
            onClick={handleLogout}
            className="w-full py-3 bg-solid hover:bg-solid-hover text-solid-ink font-black text-xs rounded-2xl flex items-center justify-center gap-2 transition-all cursor-pointer"
          >
            <LogOut className="w-4 h-4" />
            <span>Iniciar sesión de nuevo</span>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-page text-ink flex items-center justify-center p-4 select-none">
      <div className="max-w-lg w-full bento-card p-8 rounded-3xl shadow-xl space-y-6">
        <div className="text-center space-y-2">
          <div className="w-14 h-14 rounded-2xl bg-solid flex items-center justify-center mx-auto shadow-lg shadow-[#0A0A0A]/20">
            <Store className="w-8 h-8 text-brand" />
          </div>
          <h1 className="text-xl font-black tracking-tight">Registra tu local</h1>
          <p className="text-xs text-ink-soft font-semibold">
            Un último paso: cuéntanos sobre tu negocio para activar PosBento.
          </p>
        </div>

        {error && (
          <div className="p-3.5 bg-red-100 dark:bg-red-500/10 border border-red-300 dark:border-red-500/30 rounded-2xl text-xs font-bold text-red-600 dark:text-red-300 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <StoreProfileFields values={form} onChange={handleChange} includeEmail />

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 bg-solid hover:bg-solid-hover text-solid-ink font-black text-xs rounded-2xl flex items-center justify-center gap-2 shadow-lg shadow-[#0A0A0A]/10 active:scale-98 transition-all cursor-pointer disabled:opacity-50"
          >
            <span>{loading ? 'Registrando local...' : 'Registrar mi local'}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        <button
          type="button"
          onClick={handleLogout}
          className="w-full py-2.5 text-ink-soft hover:text-ink font-bold text-xs flex items-center justify-center gap-2 cursor-pointer transition-colors"
        >
          <LogOut className="w-4 h-4" />
          <span>Cerrar sesión</span>
        </button>
      </div>
    </div>
  );
}
