import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Mail, ArrowRight, AlertCircle, MailCheck } from 'lucide-react';
import { forgotPasswordApi } from '../services/authApi';
import BentoIcon from '../components/BentoIcon';

export default function ForgotPasswordBento() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await forgotPasswordApi(email.trim());
      setSent(true);
    } catch (err) {
      setError(err.message || 'No se pudo procesar la solicitud.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0E0E12] text-white flex items-center justify-center p-4 relative overflow-hidden select-none">
      <div className="max-w-md w-full bento-card bg-[#17171B] border-[#272730] p-8 rounded-3xl shadow-2xl space-y-6 relative z-10">

        <div className="text-center space-y-3">
          <BentoIcon size="w-16 h-16 mx-auto" rounded="rounded-3xl" />
          <p className="text-sm font-black text-white">Recuperar contraseña</p>
          <p className="text-xs text-gray-400 font-semibold">
            Escribe el correo de tu cuenta y te enviaremos un enlace para crear una nueva contraseña.
          </p>
        </div>

        {sent ? (
          <div className="p-5 bg-brand/10 border border-brand/30 rounded-2xl text-center space-y-3">
            <MailCheck className="w-10 h-10 text-brand mx-auto" />
            <p className="text-sm font-bold text-white">Revisa tu correo</p>
            <p className="text-xs text-gray-300 leading-relaxed">
              Si <span className="font-bold text-brand">{email}</span> corresponde a una cuenta verificada, te enviamos un enlace
              que vence en 1 hora. Revisa también la carpeta de spam.
            </p>
            <p className="text-[11px] text-gray-400 leading-relaxed">
              ¿No llega? Si nunca confirmaste tu correo, primero debes verificarlo: en el inicio de sesión puedes pedir que
              te reenviemos el enlace de verificación. Si tu cuenta está inactiva, contacta a tu administrador.
            </p>
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
                <label className="text-xs font-bold text-gray-300 block">Correo electrónico:</label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                  <input
                    type="email"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    placeholder="tu@correo.com"
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
                <span>{loading ? 'Enviando...' : 'Enviar enlace'}</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>
          </>
        )}

        <p className="text-center text-xs text-gray-400 font-semibold">
          <button
            type="button"
            onClick={() => navigate('/login')}
            className="text-brand font-extrabold hover:underline cursor-pointer"
          >
            Volver a iniciar sesión
          </button>
        </p>
      </div>
    </div>
  );
}
