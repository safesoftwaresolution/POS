import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { KeyRound, ArrowRight, AlertCircle, CheckCircle2 } from 'lucide-react';
import { resetPasswordApi } from '../services/authApi';
import BentoIcon from '../components/BentoIcon';

export default function ResetPasswordBento() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (password !== confirm) {
      setError('Las contraseñas no coinciden.');
      return;
    }
    setLoading(true);
    try {
      await resetPasswordApi(token, password);
      setDone(true);
    } catch (err) {
      setError(err.message || 'No se pudo restablecer la contraseña.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0E0E12] text-white flex items-center justify-center p-4 relative overflow-hidden select-none">
      <div className="max-w-md w-full bento-card bg-[#17171B] border-[#272730] p-8 rounded-3xl shadow-2xl space-y-6 relative z-10">

        <div className="text-center space-y-3">
          <BentoIcon size="w-16 h-16 mx-auto" rounded="rounded-3xl" />
          <p className="text-sm font-black text-white">Crear nueva contraseña</p>
        </div>

        {!token ? (
          <div className="p-5 bg-red-500/10 border border-red-500/30 rounded-2xl text-center space-y-3">
            <AlertCircle className="w-8 h-8 text-red-400 mx-auto" />
            <p className="text-xs text-gray-300">El enlace de recuperación no es válido.</p>
            <button
              type="button"
              onClick={() => navigate('/forgot-password')}
              className="text-xs font-extrabold text-brand hover:underline cursor-pointer"
            >
              Solicitar uno nuevo
            </button>
          </div>
        ) : done ? (
          <div className="p-5 bg-emerald-500/10 border border-emerald-500/30 rounded-2xl text-center space-y-3">
            <CheckCircle2 className="w-10 h-10 text-emerald-400 mx-auto" />
            <p className="text-sm font-bold text-white">Contraseña actualizada</p>
            <p className="text-xs text-gray-300">Por seguridad cerramos tus sesiones abiertas. Inicia sesión con tu nueva contraseña.</p>
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-xs font-extrabold text-brand hover:underline cursor-pointer"
            >
              Ir a iniciar sesión
            </button>
          </div>
        ) : (
          <>
            {error && (
              <div className="p-3.5 bg-red-500/10 border border-red-500/30 rounded-2xl text-xs font-bold text-red-300 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-300 block">Nueva contraseña:</label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                  <input
                    type="password"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    placeholder="Mínimo 8 caracteres, letra y número"
                    autoComplete="new-password"
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0E0E12] border border-[#272730] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-all"
                    required
                  />
                </div>
              </div>
              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-300 block">Confirmar contraseña:</label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                  <input
                    type="password"
                    value={confirm}
                    onChange={e => setConfirm(e.target.value)}
                    placeholder="Repite la contraseña"
                    autoComplete="new-password"
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0E0E12] border border-[#272730] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-all"
                    required
                  />
                </div>
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 bg-solid hover:bg-solid-hover text-solid-ink font-black text-xs rounded-2xl flex items-center justify-center gap-2 shadow-lg shadow-[#0A0A0A]/10 active:scale-98 transition-all cursor-pointer disabled:opacity-50"
              >
                <span>{loading ? 'Guardando...' : 'Guardar contraseña'}</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
