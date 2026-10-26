import React, { useState } from 'react';
import useAuth from '../hooks/useAuth';
import { useNavigate, Link } from 'react-router-dom';
import { User, KeyRound, ArrowRight, AlertCircle } from 'lucide-react';
import BentoIcon from '../components/BentoIcon';
import { resendVerificationApi } from '../services/authApi';

export default function LoginBento() {
  const { login, loading } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [needsVerification, setNeedsVerification] = useState(false);
  const [resendEmail, setResendEmail] = useState('');
  const [resendState, setResendState] = useState('idle');

  const handleResend = async () => {
    setResendState('sending');
    try {
      await resendVerificationApi(resendEmail.trim());
      setResendState('sent');
    } catch {
      setResendState('error');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setNeedsVerification(false);
    setResendState('idle');
    try {
      const loggedInUser = await login(username, password);
      navigate(loggedInUser?.role === 'SUPER_ADMIN' ? '/backoffice' : '/');
    } catch (err) {
      const message = err.message || 'Error de autenticación. Verifica tu usuario y contraseña.';
      setError(message);
      setNeedsVerification(/verificar tu correo/i.test(message));
    }
  };

  return (
    <div className="min-h-screen bg-[#0E0E12] text-white flex items-center justify-center p-4 relative overflow-hidden select-none">
      <div className="max-w-md w-full bento-card bg-[#17171B] border-[#272730] p-8 rounded-3xl shadow-2xl space-y-6 relative z-10">

        {/* Brand Header */}
        <div className="text-center space-y-3">
          <BentoIcon size="w-16 h-16 mx-auto" rounded="rounded-3xl" />
          <div className="flex items-baseline justify-center gap-1">
            <span className="font-black text-2xl tracking-tight text-white">Bento</span>
            <span className="font-black text-2xl tracking-tight text-brand">POS</span>
          </div>
          <p className="text-xs text-gray-400 font-semibold tracking-wide">
            Sistema de Punto de Venta • Iniciar Sesión
          </p>
        </div>

        {error && (
          <div className="p-3.5 bg-red-500/10 border border-red-500/30 rounded-2xl text-xs font-bold text-red-300 flex items-center gap-2 animate-in fade-in">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {needsVerification && (
          <div className="p-3.5 bg-brand/10 border border-brand/30 rounded-2xl space-y-2">
            <p className="text-xs text-gray-300 font-semibold">¿No te llegó el correo? Escribe tu correo y te enviamos otro enlace.</p>
            <div className="flex gap-2">
              <input
                type="email"
                value={resendEmail}
                onChange={e => setResendEmail(e.target.value)}
                placeholder="tu@correo.com"
                className="flex-1 min-w-0 px-3 py-2 bg-[#0E0E12] border border-[#272730] rounded-xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand"
              />
              <button
                type="button"
                onClick={handleResend}
                disabled={!resendEmail.trim() || resendState === 'sending'}
                className="px-3 py-2 bg-solid hover:bg-solid-hover text-solid-ink rounded-xl text-xs font-black cursor-pointer disabled:opacity-50"
              >
                {resendState === 'sending' ? 'Enviando...' : 'Reenviar'}
              </button>
            </div>
            {resendState === 'sent' && (
              <p className="text-[11px] text-gray-400">Si la cuenta existe y sigue sin verificar, el correo va en camino. Revisa spam y espera un minuto antes de reintentar.</p>
            )}
            {resendState === 'error' && <p className="text-[11px] text-red-300">No se pudo reenviar. Inténtalo de nuevo.</p>}
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-300 block">Usuario:</label>
            <div className="relative">
              <User className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="Ingresa tu usuario"
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="off"
                spellCheck="false"
                className="w-full pl-10 pr-4 py-2.5 bg-[#0E0E12] border border-[#272730] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-all"
                required
              />
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-300 block">Contraseña:</label>
            <div className="relative">
              <KeyRound className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="off"
                className="w-full pl-10 pr-4 py-2.5 bg-[#0E0E12] border border-[#272730] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-all"
                required
              />
            </div>
          </div>

          <div className="text-right -mt-1">
            <Link to="/forgot-password" className="text-[11px] font-bold text-gray-400 hover:text-white hover:underline">
              ¿Olvidaste tu contraseña?
            </Link>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 bg-solid hover:bg-solid-hover text-solid-ink font-black text-xs rounded-2xl flex items-center justify-center gap-2 shadow-lg shadow-[#0A0A0A]/10 active:scale-98 transition-all cursor-pointer disabled:opacity-50"
          >
            <span>{loading ? 'Autenticando...' : 'Ingresar al Sistema'}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        <p className="text-center text-xs text-gray-400 font-semibold">
          ¿No tienes cuenta?{' '}
          <button
            type="button"
            onClick={() => navigate('/register')}
            className="text-brand hover:text-brand font-extrabold hover:underline cursor-pointer transition-colors"
          >
            Regístrate
          </button>
        </p>

        <p className="text-center text-[11px] text-gray-500 font-semibold space-x-3">
          <Link to="/legal/terms-and-conditions" className="hover:text-gray-300">Términos</Link>
          <Link to="/legal/privacy-policy" className="hover:text-gray-300">Privacidad</Link>
          <Link to="/support" className="hover:text-gray-300">Soporte</Link>
        </p>

      </div>
    </div>
  );
}
