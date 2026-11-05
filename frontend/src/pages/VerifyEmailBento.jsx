import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';
import { verifyEmailApi, resendVerificationApi } from '../services/authApi';
import useAuth from '../hooks/useAuth';
import BentoIcon from '../components/BentoIcon';
import { destinationAfterVerification } from '../utils/authFlow';

/** Pide un enlace nuevo cuando el que se abrió no sirve (vencido, ya usado o reemplazado). */
function ResendVerification() {
  const [email, setEmail] = useState('');
  const [state, setState] = useState('idle'); // idle | sending | sent | error

  const handleSubmit = async (e) => {
    e.preventDefault();
    setState('sending');
    try {
      await resendVerificationApi(email.trim());
      setState('sent');
    } catch {
      setState('error');
    }
  };

  return (
    <form onSubmit={handleSubmit} className="p-3.5 bg-brand/10 border border-brand/30 rounded-2xl space-y-2 text-left">
      <p className="text-xs text-gray-300 font-semibold">Escribe tu correo y te enviamos un enlace nuevo.</p>
      <div className="flex gap-2">
        <input
          type="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          placeholder="tu@correo.com"
          required
          className="flex-1 min-w-0 px-3 py-2 bg-[#0E0E12] border border-[#272730] rounded-xl text-xs font-semibold text-white placeholder-gray-500 focus:outline-none focus:border-brand"
        />
        <button
          type="submit"
          disabled={!email.trim() || state === 'sending'}
          className="px-3 py-2 bg-solid hover:bg-solid-hover text-solid-ink rounded-xl text-xs font-black cursor-pointer disabled:opacity-50"
        >
          {state === 'sending' ? 'Enviando...' : 'Reenviar'}
        </button>
      </div>
      {state === 'sent' && (
        <p className="text-[11px] text-gray-400">Si la cuenta existe y sigue sin verificar, el correo va en camino. Revisa spam y espera un minuto antes de reintentar.</p>
      )}
      {state === 'error' && <p className="text-[11px] text-red-300">No se pudo reenviar. Inténtalo de nuevo.</p>}
    </form>
  );
}

export default function VerifyEmailBento() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { setSessionFromResponse } = useAuth();
  const token = searchParams.get('token');
  const [result, setResult] = useState({ status: 'verifying', error: '', destination: '/' }); // verifying | success | error
  const attempted = useRef(false);

  // Sin token no hay nada que verificar: se muestra el error directamente, sin pasar por un efecto.
  const { status, error, destination } = token ? result : { status: 'error', error: 'Enlace de verificación inválido.' };

  useEffect(() => {
    // El enlace sirve una sola vez: aunque React ejecute el efecto dos veces (modo estricto), se consume una vez.
    if (!token || attempted.current) return;
    attempted.current = true;

    (async () => {
      try {
        const data = await verifyEmailApi(token);
        setSessionFromResponse(data);
        // Un operador invitado entra al backoffice; quien ya tiene local (asignado por la plataforma) entra al POS.
        const next = destinationAfterVerification(data);
        setResult({ status: 'success', error: '', destination: next });
        setTimeout(() => navigate(next, { replace: true }), 1200);
      } catch (err) {
        setResult({ status: 'error', error: err.message || 'No se pudo verificar tu correo.', destination: '/' });
      }
    })();
  }, [token, navigate, setSessionFromResponse]);

  return (
    <div className="min-h-screen bg-[#0E0E12] text-white flex items-center justify-center p-4 relative overflow-hidden select-none">
      <div className="max-w-md w-full bento-card bg-[#17171B] border-[#272730] p-8 rounded-3xl shadow-2xl space-y-6 relative z-10 text-center">
        <BentoIcon size="w-16 h-16 mx-auto" rounded="rounded-3xl" />

        {status === 'verifying' && (
          <div className="space-y-3">
            <Loader2 className="w-8 h-8 text-brand mx-auto animate-spin" />
            <p className="text-sm font-bold text-white">Verificando tu correo...</p>
          </div>
        )}

        {status === 'success' && (
          <div className="space-y-3">
            <CheckCircle2 className="w-10 h-10 text-emerald-400 mx-auto" />
            <p className="text-sm font-bold text-white">¡Correo verificado con éxito!</p>
            <p className="text-xs text-gray-400">
              {destination === '/onboarding/store' ? 'Te llevamos a crear tu local...' : 'Te llevamos a tu cuenta...'}
            </p>
          </div>
        )}

        {status === 'error' && (
          <div className="space-y-3">
            <XCircle className="w-10 h-10 text-red-400 mx-auto" />
            <p className="text-sm font-bold text-white">No pudimos verificar tu correo</p>
            <p className="text-xs text-gray-400">{error}</p>
            <ResendVerification />
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-xs font-extrabold text-brand hover:text-brand hover:underline cursor-pointer transition-colors"
            >
              Volver a iniciar sesión
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
