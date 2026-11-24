import { useCallback, useEffect, useRef, useState } from 'react';
import { MAX_AUDIO_BYTES } from '../services/voiceSalesApi';

/** Corte automático: un pedido dictado cabe de sobra y el audio se queda lejos del tope de 5 MiB. */
export const MAX_RECORDING_SECONDS = 45;

// El backend acepta audio/webm (con o sin codecs) y audio/wav; MediaRecorder solo produce WebM de forma fiable.
const MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm'];

const MESSAGES = {
  noMicrophone: 'Este navegador no permite usar el micrófono. Puedes registrar la venta manualmente.',
  noRecorder: 'Este navegador no permite grabar audio. Puedes registrar la venta manualmente.',
  noWebm: 'Este navegador no puede grabar en formato WebM, necesario para el dictado. Puedes registrar la venta manualmente.',
  permission: 'Se necesita permiso para usar el micrófono. Revisa los permisos del navegador.',
  notFound: 'No se encontró un micrófono.',
  generic: 'No se pudo acceder al micrófono.',
  failed: 'Falló la grabación. Intenta de nuevo.',
  empty: 'No se grabó audio. Intenta de nuevo.',
  tooLarge: 'La grabación supera los 5 MB. Intenta con un dictado más corto.',
};

// Se evalúa al usarlo (no al importar): depende de lo que el navegador exponga en ese momento.
function detectSupport() {
  if (typeof navigator === 'undefined' || typeof navigator.mediaDevices?.getUserMedia !== 'function') {
    return { mimeType: null, message: MESSAGES.noMicrophone };
  }
  if (typeof MediaRecorder === 'undefined') return { mimeType: null, message: MESSAGES.noRecorder };
  const supports = typeof MediaRecorder.isTypeSupported === 'function' ? MediaRecorder.isTypeSupported : () => false;
  const mimeType = MIME_CANDIDATES.find((type) => supports.call(MediaRecorder, type));
  return mimeType ? { mimeType, message: '' } : { mimeType: null, message: MESSAGES.noWebm };
}

function microphoneMessage(error) {
  switch (error?.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return MESSAGES.permission;
    case 'NotFoundError':
    case 'OverconstrainedError':
      return MESSAGES.notFound;
    default:
      return MESSAGES.generic;
  }
}

const stopTracks = (stream) => stream?.getTracks().forEach((track) => track.stop());

/**
 * Graba un dictado con el micrófono y entrega UN Blob WebM a `onRecorded` al terminar (a mano o por el corte de
 * `maxSeconds`). Privacidad: el audio vive solo en memoria mientras se graba; no se guarda en el navegador, no se
 * crean URLs del Blob, las pistas del micrófono se detienen al terminar, al fallar y al desmontar, y un audio vacío
 * o de más de 5 MiB se rechaza aquí, antes de subirlo.
 *
 *   const { supported, unsupportedMessage, status, elapsed, error, start, stop, clearError } = useVoiceRecorder({ onRecorded });
 *
 * `status`: 'idle' | 'requesting' (esperando el permiso del micrófono) | 'recording'.
 */
export default function useVoiceRecorder({ onRecorded, maxSeconds = MAX_RECORDING_SECONDS } = {}) {
  const [status, setStatus] = useState('idle');
  const [elapsed, setElapsed] = useState(0);
  const [error, setError] = useState('');
  const sessionRef = useRef(null); // grabación en curso: { recorder, stream, chunks, capTimer, tick, mimeType }
  const requestingRef = useRef(false);
  const mountedRef = useRef(true);
  const onRecordedRef = useRef(onRecorded);

  useEffect(() => { onRecordedRef.current = onRecorded; }, [onRecorded]);

  // Cierra la grabación en curso sin entregarla: temporizadores, manejadores, grabadora y micrófono.
  const discard = useCallback(() => {
    const session = sessionRef.current;
    if (!session) return;
    sessionRef.current = null;
    clearTimeout(session.capTimer);
    clearInterval(session.tick);
    session.recorder.ondataavailable = null;
    session.recorder.onstop = null;
    session.recorder.onerror = null;
    try {
      if (session.recorder.state !== 'inactive') session.recorder.stop();
    } catch {
      // Ya estaba detenida: solo falta soltar el micrófono.
    }
    stopTracks(session.stream);
    session.chunks = [];
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      discard();
    };
  }, [discard]);

  // Al terminar la grabadora: arma el Blob, suelta el micrófono y lo entrega si es válido.
  const finish = useCallback((session) => {
    if (sessionRef.current !== session) return;
    const blob = new Blob(session.chunks, { type: session.recorder.mimeType || session.mimeType });
    discard();
    if (!mountedRef.current) return;
    setStatus('idle');
    setElapsed(0);
    if (blob.size === 0) setError(MESSAGES.empty);
    else if (blob.size > MAX_AUDIO_BYTES) setError(MESSAGES.tooLarge);
    else onRecordedRef.current?.(blob);
  }, [discard]);

  const stop = useCallback(() => {
    const session = sessionRef.current;
    if (!session) return;
    clearTimeout(session.capTimer);
    clearInterval(session.tick);
    if (session.recorder.state !== 'inactive') session.recorder.stop();
  }, []);

  const start = useCallback(async () => {
    if (requestingRef.current || sessionRef.current) return;
    setError('');
    const support = detectSupport();
    if (support.message) {
      setError(support.message);
      return;
    }

    requestingRef.current = true;
    setStatus('requesting');
    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (e) {
      requestingRef.current = false;
      if (mountedRef.current) {
        setStatus('idle');
        setError(microphoneMessage(e));
      }
      return;
    }
    requestingRef.current = false;
    if (!mountedRef.current) {
      stopTracks(stream); // el permiso llegó cuando ya no había pantalla que lo use
      return;
    }

    try {
      const recorder = new MediaRecorder(stream, { mimeType: support.mimeType });
      const session = { recorder, stream, chunks: [], mimeType: support.mimeType, capTimer: null, tick: null };
      recorder.ondataavailable = (event) => { if (event.data?.size > 0) session.chunks.push(event.data); };
      recorder.onstop = () => finish(session);
      recorder.onerror = () => {
        discard();
        if (mountedRef.current) {
          setStatus('idle');
          setElapsed(0);
          setError(MESSAGES.failed);
        }
      };
      sessionRef.current = session;
      recorder.start();
      session.capTimer = setTimeout(stop, maxSeconds * 1000);
      session.tick = setInterval(() => setElapsed((seconds) => seconds + 1), 1000);
      setElapsed(0);
      setStatus('recording');
    } catch {
      discard();
      stopTracks(stream);
      setStatus('idle');
      setError(MESSAGES.failed);
    }
  }, [discard, finish, stop, maxSeconds]);

  const clearError = useCallback(() => setError(''), []);
  const { message: unsupportedMessage } = detectSupport();

  return { supported: !unsupportedMessage, unsupportedMessage, status, elapsed, error, start, stop, clearError };
}
