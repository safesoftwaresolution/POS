import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';
import { verifyEmailApi } from '../services/authApi';
import { useAuth } from '../context/AuthContext';

export default function VerifyEmailBento() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { setSessionFromResponse } = useAuth();
  const [status, setStatus] = useState('verifying'); // verifying | success | error
  const [error, setError] = useState('');
  const attempted = useRef(false);

  useEffect(() => {
    if (attempted.current) return;
    attempted.current = true;

    const token = searchParams.get('token');
    if (!token) {
      setStatus('error');
      setError('Enlace de verificación inválido.');
      return;
    }

    (async () => {
      try {
        const data = await verifyEmailApi(token);
        setSessionFromResponse(data);
        setStatus('success');
        setTimeout(() => navigate('/onboarding/store', { replace: true }), 1200);
      } catch (err) {
        setStatus('error');
        setError(err.message || 'No se pudo verificar tu correo.');
      }
    })();
  }, [searchParams, navigate, setSessionFromResponse]);

  return (
    <div className="min-h-screen bg-[#0d1117] text-white flex items-center justify-center p-4 relative overflow-hidden select-none">
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-[#067A57]/15 rounded-full blur-3xl pointer-events-none"></div>
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-[#00D47E]/10 rounded-full blur-3xl pointer-events-none"></div>

      <div className="max-w-md w-full bento-card bg-[#161b22] border-[#262f38] p-8 rounded-3xl shadow-2xl space-y-6 relative z-10 text-center">
        <img
          src="/logo-icon.png"
          alt="BentoPOS"
          className="w-16 h-16 rounded-2xl mx-auto object-cover shadow-lg border border-white/10"
        />

        {status === 'verifying' && (
          <div className="space-y-3">
            <Loader2 className="w-8 h-8 text-[#067A57] mx-auto animate-spin" />
            <p className="text-sm font-bold text-white">Verificando tu correo...</p>
          </div>
        )}

        {status === 'success' && (
          <div className="space-y-3">
            <CheckCircle2 className="w-10 h-10 text-emerald-400 mx-auto" />
            <p className="text-sm font-bold text-white">¡Correo verificado con éxito!</p>
            <p className="text-xs text-gray-400">Te llevamos a crear tu local...</p>
          </div>
        )}

        {status === 'error' && (
          <div className="space-y-3">
            <XCircle className="w-10 h-10 text-red-400 mx-auto" />
            <p className="text-sm font-bold text-white">No pudimos verificar tu correo</p>
            <p className="text-xs text-gray-400">{error}</p>
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-xs font-extrabold text-[#067A57] hover:text-[#00D47E] hover:underline cursor-pointer transition-colors"
            >
              Volver a iniciar sesión
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
