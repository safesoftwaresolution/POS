import React, { useEffect, useRef, useState, useCallback } from 'react';
import { X, Zap, ZapOff, CameraOff, CheckCircle, XCircle } from 'lucide-react';

// Formatos de retail. Se restringen para acelerar la detección y evitar falsos positivos.
const NATIVE_FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'itf'];
const SAME_CODE_COOLDOWN_MS = 1800;
const NATIVE_SCAN_INTERVAL_MS = 120;
const FEEDBACK_MS = 1400;

const CORNERS = [
  'top-0 left-0 border-t-4 border-l-4 rounded-tl-3xl',
  'top-0 right-0 border-t-4 border-r-4 rounded-tr-3xl',
  'bottom-0 left-0 border-b-4 border-l-4 rounded-bl-3xl',
  'bottom-0 right-0 border-b-4 border-r-4 rounded-br-3xl',
];

function cameraErrorMessage(err) {
  if (!window.isSecureContext) return 'La cámara solo está disponible en conexiones seguras (HTTPS).';
  switch (err?.name) {
    case 'NotAllowedError': return 'Permiso de cámara denegado. Habilítalo en los ajustes del navegador.';
    case 'NotFoundError': return 'No se encontró ninguna cámara en este dispositivo.';
    case 'NotReadableError': return 'La cámara está siendo usada por otra aplicación.';
    default: return 'No se pudo iniciar la cámara.';
  }
}

function vibrate(pattern) {
  try { navigator.vibrate?.(pattern); } catch { /* sin soporte: el feedback visual basta */ }
}

/**
 * Escáner de códigos de barras con la cámara, pensado para móvil (pantalla completa).
 * Es independiente del dominio: no sabe qué es un producto. Por cada lectura llama a
 * `onDetect(code)`, que puede devolver `{ ok: boolean, message: string }` para mostrar el
 * resultado sobre la cámara. El escáner sigue abierto (modo continuo) hasta `onClose`.
 *
 * Usa `BarcodeDetector` nativo cuando existe (Chrome/Android) y, si no (iOS Safari,
 * Firefox), carga ZXing bajo demanda para no engordar el bundle principal.
 */
export default function BarcodeScanner({ onDetect, onClose, inline = false }) {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const stopRef = useRef(() => {});
  const lastRef = useRef({ code: '', at: 0 });
  const onDetectRef = useRef(onDetect);
  const feedbackTimerRef = useRef(null);

  const [error, setError] = useState('');
  const [ready, setReady] = useState(false);
  const [torchSupported, setTorchSupported] = useState(false);
  const [torchOn, setTorchOn] = useState(false);
  const [feedback, setFeedback] = useState(null);

  useEffect(() => { onDetectRef.current = onDetect; }, [onDetect]);

  const handleCode = useCallback((raw) => {
    const code = String(raw || '').trim();
    if (!code) return;
    const now = Date.now();
    const last = lastRef.current;
    // La cámara lee el mismo código decenas de veces por segundo: se ignora la repetición inmediata.
    if (code === last.code && now - last.at < SAME_CODE_COOLDOWN_MS) return;
    lastRef.current = { code, at: now };

    const result = onDetectRef.current?.(code);
    vibrate(result?.ok === false ? [60, 40, 60] : 40);
    if (result?.message) {
      setFeedback({ ok: result.ok !== false, message: result.message });
      clearTimeout(feedbackTimerRef.current);
      feedbackTimerRef.current = setTimeout(() => setFeedback(null), FEEDBACK_MS);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;

    const start = async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setError(cameraErrorMessage());
        return;
      }
      let stream;
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: false,
        });
      } catch (err) {
        if (!cancelled) setError(cameraErrorMessage(err));
        return;
      }
      if (cancelled) { stream.getTracks().forEach(t => t.stop()); return; }

      streamRef.current = stream;
      const video = videoRef.current;
      video.srcObject = stream;
      await video.play().catch(() => {});
      if (cancelled) return;
      setReady(true);
      setTorchSupported(!!stream.getVideoTracks()[0]?.getCapabilities?.().torch);

      if ('BarcodeDetector' in window) {
        const detector = new window.BarcodeDetector({ formats: NATIVE_FORMATS });
        let busy = false;
        const id = setInterval(async () => {
          if (busy || video.readyState < 2) return;
          busy = true;
          try {
            const found = await detector.detect(video);
            if (found[0]) handleCode(found[0].rawValue);
          } catch { /* frame no decodificable: se reintenta en el siguiente */ }
          busy = false;
        }, NATIVE_SCAN_INTERVAL_MS);
        stopRef.current = () => clearInterval(id);
      } else {
        try {
          const [{ BrowserMultiFormatReader }, { BarcodeFormat, DecodeHintType }] = await Promise.all([
            import('@zxing/browser'),
            import('@zxing/library'),
          ]);
          if (cancelled) return;
          const hints = new Map([
            [DecodeHintType.POSSIBLE_FORMATS, [
              BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
              BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.ITF,
            ]],
            [DecodeHintType.TRY_HARDER, true],
          ]);
          const reader = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 120 });
          const controls = await reader.decodeFromStream(stream, video, (result) => {
            if (result) handleCode(result.getText());
          });
          if (cancelled) controls.stop();
          else stopRef.current = () => controls.stop();
        } catch {
          if (!cancelled) setError('No se pudo cargar el lector de códigos en este navegador.');
        }
      }
    };

    start();
    return () => {
      cancelled = true;
      stopRef.current();
      clearTimeout(feedbackTimerRef.current);
      streamRef.current?.getTracks().forEach(t => t.stop());
    };
  }, [handleCode]);

  // Evita que la página de atrás haga scroll mientras el escáner cubre la pantalla.
  useEffect(() => {
    const prev = document.body.style.overflow;
    if (!inline) document.body.style.overflow = 'hidden';
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    document.addEventListener('keydown', onKey);
    return () => { document.body.style.overflow = prev; document.removeEventListener('keydown', onKey); };
  }, [onClose, inline]);

  const toggleTorch = async () => {
    const track = streamRef.current?.getVideoTracks()[0];
    try {
      await track.applyConstraints({ advanced: [{ torch: !torchOn }] });
      setTorchOn(v => !v);
    } catch { setTorchSupported(false); }
  };

  // Modo incrustado: franja fija arriba de la pantalla (el catálogo sigue visible y usable debajo).
  if (inline) {
    return (
      <div className="sticky top-0 z-40 h-44 overflow-hidden rounded-2xl bg-black text-white shadow-lg" role="region" aria-label="Escáner de códigos de barras">
        <video ref={videoRef} className="absolute inset-0 w-full h-full object-cover" playsInline muted />

        <div className="absolute top-2 right-2 z-10 flex items-center gap-2">
          {torchSupported && (
            <button
              type="button"
              onClick={toggleTorch}
              aria-label="Linterna"
              aria-pressed={torchOn}
              className={`w-9 h-9 rounded-full flex items-center justify-center cursor-pointer ${torchOn ? 'bg-brand' : 'bg-black/50'}`}
            >
              {torchOn ? <Zap className="w-4 h-4" /> : <ZapOff className="w-4 h-4" />}
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar escáner"
            className="w-9 h-9 rounded-full bg-black/50 flex items-center justify-center cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          {error ? (
            <div className="mx-6 px-4 py-3 rounded-2xl bg-black/70 text-center space-y-1 pointer-events-auto">
              <CameraOff className="w-6 h-6 mx-auto text-white/70" />
              <p className="text-xs font-bold">{error}</p>
            </div>
          ) : (
            <div className="relative w-[70%] h-[60%] rounded-2xl shadow-[0_0_0_9999px_rgba(0,0,0,0.45)]">
              {CORNERS.map(c => <span key={c} className={`absolute w-5 h-5 border-white ${c.replace('border-t-4', 'border-t-2').replace('border-b-4', 'border-b-2').replace('border-l-4', 'border-l-2').replace('border-r-4', 'border-r-2')}`} />)}
              {ready && <span className="absolute left-2 right-2 top-1/2 h-0.5 bg-brand/90 shadow-[0_0_10px_var(--color-brand)] animate-pulse" />}
            </div>
          )}
        </div>

        {feedback && (
          <div className="absolute bottom-2 left-2 right-2 z-10 flex justify-center">
            <div className={`flex items-center gap-2 px-3 py-1.5 rounded-xl text-[11px] font-bold shadow-xl max-w-full ${feedback.ok ? 'bg-emerald-600' : 'bg-red-600'}`}>
              {feedback.ok ? <CheckCircle className="w-3.5 h-3.5 shrink-0" /> : <XCircle className="w-3.5 h-3.5 shrink-0" />}
              <span className="truncate">{feedback.message}</span>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-110 bg-black text-white flex flex-col" role="dialog" aria-modal="true" aria-label="Escáner de códigos de barras">
      <video ref={videoRef} className="absolute inset-0 w-full h-full object-cover" playsInline muted />

      {/* Barra superior */}
      <div className="relative z-10 flex items-center justify-between p-4 pt-[max(1rem,env(safe-area-inset-top))] bg-linear-to-b from-black/70 to-transparent">
        <div>
          <p className="text-sm font-extrabold">Escanear código</p>
          <p className="text-[11px] text-white/70">Apunta la cámara al código de barras</p>
        </div>
        <div className="flex items-center gap-2">
          {torchSupported && (
            <button
              type="button"
              onClick={toggleTorch}
              aria-label="Linterna"
              aria-pressed={torchOn}
              className={`w-11 h-11 rounded-full flex items-center justify-center cursor-pointer ${torchOn ? 'bg-brand' : 'bg-white/15'}`}
            >
              {torchOn ? <Zap className="w-5 h-5" /> : <ZapOff className="w-5 h-5" />}
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar escáner"
            className="w-11 h-11 rounded-full bg-white/15 flex items-center justify-center cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Visor */}
      <div className="relative z-10 flex-1 flex items-center justify-center pointer-events-none">
        {error ? (
          <div className="mx-8 p-6 rounded-3xl bg-black/70 backdrop-blur-sm text-center space-y-3 pointer-events-auto">
            <CameraOff className="w-10 h-10 mx-auto text-white/70" />
            <p className="text-sm font-bold">{error}</p>
            <button type="button" onClick={onClose} className="px-4 py-2 rounded-2xl bg-white text-black text-xs font-bold cursor-pointer">
              Volver
            </button>
          </div>
        ) : (
          <div className="relative w-[78vw] max-w-sm aspect-[3/2] rounded-3xl shadow-[0_0_0_9999px_rgba(0,0,0,0.55)]">
            {CORNERS.map(c => <span key={c} className={`absolute w-8 h-8 border-white ${c}`} />)}
            {ready && <span className="absolute left-3 right-3 top-1/2 h-0.5 bg-brand/90 shadow-[0_0_12px_var(--color-brand)] animate-pulse" />}
          </div>
        )}
      </div>

      {/* Resultado de la última lectura */}
      <div className="relative z-10 p-4 pb-[max(1.5rem,env(safe-area-inset-bottom))] min-h-24 flex items-end justify-center">
        {feedback && (
          <div className={`flex items-center gap-2 px-4 py-3 rounded-2xl text-xs font-bold shadow-xl max-w-full ${feedback.ok ? 'bg-emerald-600' : 'bg-red-600'}`}>
            {feedback.ok ? <CheckCircle className="w-4 h-4 shrink-0" /> : <XCircle className="w-4 h-4 shrink-0" />}
            <span className="truncate">{feedback.message}</span>
          </div>
        )}
      </div>
    </div>
  );
}
