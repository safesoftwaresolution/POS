import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { User, Mail, KeyRound, IdCard, ArrowRight, AlertCircle, MailCheck } from 'lucide-react';
import { registerApi, resendVerificationApi } from '../services/authApi';
import BentoIcon from '../components/BentoIcon';
import { LANDING_URL } from '../constants/site';

const EMPTY_FORM = { fullName: '', username: '', email: '', password: '' };

export default function RegisterBento() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [submittedEmail, setSubmittedEmail] = useState(null);
  const [resendState, setResendState] = useState('idle');

  const handleChange = (field) => (e) => setForm(prev => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await registerApi(form);
      setSubmittedEmail(form.email);
    } catch (err) {
      setError(err.message || 'No se pudo completar el registro.');
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    setResendState('sending');
    try {
      await resendVerificationApi(submittedEmail);
      setResendState('sent');
    } catch {
      setResendState('error');
    }
  };

  return (
    <div className="min-h-screen bg-[#0E0E12] text-white flex items-center justify-center p-4 relative overflow-hidden select-none">
      <div className="max-w-md w-full bento-card bg-[#17171B] border-[#272730] p-8 rounded-3xl shadow-2xl space-y-6 relative z-10">

        <div className="text-center space-y-3">
          <a href={LANDING_URL} aria-label="BentoPOS, ir al sitio" className="block space-y-3">
            <BentoIcon size="w-16 h-16 mx-auto" rounded="rounded-3xl" />
            <div className="flex items-baseline justify-center gap-1">
              <span className="font-black text-2xl tracking-tight text-white">Bento</span>
              <span className="font-black text-2xl tracking-tight text-brand">POS</span>
            </div>
          </a>
          <p className="text-xs text-gray-400 font-semibold tracking-wide">
            Crea tu cuenta para registrar tu local en BentoPOS
          </p>
        </div>

        {submittedEmail ? (
          <div className="p-5 bg-brand/10 border border-brand/30 rounded-2xl text-center space-y-3">
            <MailCheck className="w-10 h-10 text-brand mx-auto" />
            <p className="text-sm font-bold text-white">Revisa tu correo</p>
            <p className="text-xs text-gray-300 leading-relaxed">
              Enviamos un enlace de verificación a <span className="font-bold text-brand">{submittedEmail}</span>.
              Ábrelo para activar tu cuenta y continuar con el registro de tu local.
            </p>
            <div>
              <button
                type="button"
                onClick={handleResend}
                disabled={resendState === 'sending'}
                className="text-xs font-extrabold text-gray-300 hover:text-white hover:underline cursor-pointer disabled:opacity-50"
              >
                {resendState === 'sending' ? 'Reenviando...' : '¿No te llegó? Reenviar correo'}
              </button>
              {resendState === 'sent' && (
                <p className="text-[11px] text-gray-400 mt-1">Listo. Si el correo no aparece, revisa spam y espera un minuto antes de reintentar.</p>
              )}
              {resendState === 'error' && (
                <p className="text-[11px] text-red-300 mt-1">No se pudo reenviar. Inténtalo de nuevo en un momento.</p>
              )}
            </div>
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-xs font-extrabold text-brand hover:text-brand hover:underline cursor-pointer"
            >
              Volver a iniciar sesión
            </button>
          </div>
        ) : (
          <>
            {error && (
              <div className="p-3.5 bg-red-500/10 border border-red-500/30 rounded-2xl text-xs font-bold text-red-300 flex items-center gap-2 animate-in fade-in">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-300 block">Nombre completo:</label>
                <div className="relative">
                  <IdCard className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                  <input
                    type="text"
                    value={form.fullName}
                    onChange={handleChange('fullName')}
                    placeholder="Carlos Martínez"
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0E0E12] border border-[#272730] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-all"
                    required
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-300 block">Usuario:</label>
                <div className="relative">
                  <User className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                  <input
                    type="text"
                    value={form.username}
                    onChange={handleChange('username')}
                    placeholder="carlos.m"
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0E0E12] border border-[#272730] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-all"
                    required
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-300 block">Correo electrónico:</label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                  <input
                    type="email"
                    value={form.email}
                    onChange={handleChange('email')}
                    placeholder="carlos@empresa.com"
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
                    value={form.password}
                    onChange={handleChange('password')}
                    placeholder="Mínimo 8 caracteres, letra y número"
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
                <span>{loading ? 'Creando cuenta...' : 'Crear mi cuenta'}</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>

            <p className="text-center text-xs text-gray-400 font-semibold">
              ¿Ya tienes cuenta?{' '}
              <button
                type="button"
                onClick={() => navigate('/login')}
                className="text-brand hover:text-brand font-extrabold hover:underline cursor-pointer transition-colors"
              >
                Inicia sesión
              </button>
            </p>

            <p className="text-center text-[11px] text-gray-500 font-semibold">
              Al crear tu cuenta aceptas los{' '}
              <Link to="/legal/terms-and-conditions" className="underline hover:text-gray-300">Términos y Condiciones</Link>
              {' '}y la{' '}
              <Link to="/legal/privacy-policy" className="underline hover:text-gray-300">Política de Privacidad</Link>.
            </p>
          </>
        )}
      </div>
    </div>
  );
}
