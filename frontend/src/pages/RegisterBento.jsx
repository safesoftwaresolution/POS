import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { User, Mail, KeyRound, IdCard, ArrowRight, AlertCircle, MailCheck } from 'lucide-react';
import { registerApi } from '../services/authApi';

const EMPTY_FORM = { fullName: '', username: '', email: '', password: '' };

export default function RegisterBento() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [submittedEmail, setSubmittedEmail] = useState(null);

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

  return (
    <div className="min-h-screen bg-[#0d1117] text-white flex items-center justify-center p-4 relative overflow-hidden select-none">
      {/* Ambient Vermilion Glows */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-[#B3654A]/15 rounded-full blur-3xl pointer-events-none"></div>
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-[#D0906D]/10 rounded-full blur-3xl pointer-events-none"></div>

      <div className="max-w-md w-full bento-card bg-[#161b22] border-[#262f38] p-8 rounded-3xl shadow-2xl space-y-6 relative z-10">

        <div className="text-center space-y-3">
          <img
            src="/logo-full.png"
            alt="BentoPOS"
            className="h-24 mx-auto object-contain drop-shadow-md"
          />
          <p className="text-xs text-gray-400 font-semibold tracking-wide">
            Crea tu cuenta para registrar tu local en BentoPOS
          </p>
        </div>

        {submittedEmail ? (
          <div className="p-5 bg-[#B3654A]/10 border border-[#B3654A]/30 rounded-2xl text-center space-y-3">
            <MailCheck className="w-10 h-10 text-[#B3654A] mx-auto" />
            <p className="text-sm font-bold text-white">Revisa tu correo</p>
            <p className="text-xs text-gray-300 leading-relaxed">
              Enviamos un enlace de verificación a <span className="font-bold text-[#D0906D]">{submittedEmail}</span>.
              Ábrelo para activar tu cuenta y continuar con el registro de tu local.
            </p>
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-xs font-extrabold text-[#B3654A] hover:text-[#D0906D] hover:underline cursor-pointer"
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
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0d1117] border border-[#262f38] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-[#B3654A] focus:ring-1 focus:ring-[#B3654A] transition-all"
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
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0d1117] border border-[#262f38] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-[#B3654A] focus:ring-1 focus:ring-[#B3654A] transition-all"
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
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0d1117] border border-[#262f38] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-[#B3654A] focus:ring-1 focus:ring-[#B3654A] transition-all"
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
                    className="w-full pl-10 pr-4 py-2.5 bg-[#0d1117] border border-[#262f38] rounded-2xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-[#B3654A] focus:ring-1 focus:ring-[#B3654A] transition-all"
                    required
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 bg-[#B3654A] hover:bg-[#96503A] text-white font-black text-xs rounded-2xl flex items-center justify-center gap-2 shadow-lg shadow-[#B3654A]/30 active:scale-98 transition-all cursor-pointer disabled:opacity-50"
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
                className="text-[#B3654A] hover:text-[#D0906D] font-extrabold hover:underline cursor-pointer transition-colors"
              >
                Inicia sesión
              </button>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
