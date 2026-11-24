import React, { useCallback, useState } from 'react';
import { Loader2, Mic, Square, X } from 'lucide-react';
import useVoiceRecorder from '../../hooks/useVoiceRecorder';
import { isVoiceSalesDisabled, previewVoiceSaleApi } from '../../services/voiceSalesApi';
import { cx } from '../ui/cx';

const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

/**
 * Botón de micrófono del POS: un toque empieza a grabar, otro termina (o se corta solo a los 45 s). El audio sube
 * al backend, que devuelve un borrador; `onDraft(draft)` lo recibe para revisarlo. Aquí no se guarda audio ni se crea
 * ninguna venta. Si el navegador no puede grabar, el botón queda atenuado y explica por qué al tocarlo; si el servidor
 * responde 404 (dictado apagado) avisa una vez y deja de ofrecerse mientras dure la sesión de la página.
 */
export default function VoiceDictationButton({ onDraft }) {
  const [processing, setProcessing] = useState(false);
  const [note, setNote] = useState('');
  const [disabledNow, setDisabledNow] = useState(false);
  const [hidden, setHidden] = useState(isVoiceSalesDisabled);

  // El Blob solo existe dentro de esta llamada: no se guarda en estado, refs ni almacenamiento.
  const handleRecorded = useCallback(async (blob) => {
    setProcessing(true);
    setNote('');
    try {
      onDraft(await previewVoiceSaleApi(blob));
    } catch (e) {
      setNote(e?.message || 'No se pudo preparar el borrador. Puedes registrar la venta manualmente.');
      if (e?.kind === 'disabled') setDisabledNow(true);
    } finally {
      setProcessing(false);
    }
  }, [onDraft]);

  const recorder = useVoiceRecorder({ onRecorded: handleRecorded });

  if (hidden) return null;

  const recording = recorder.status === 'recording';
  const requesting = recorder.status === 'requesting';
  const busy = processing || requesting;
  const message = note || recorder.error;

  const handleClick = () => {
    if (busy || disabledNow) return;
    setNote('');
    if (recording) recorder.stop();
    else recorder.start(); // sin soporte, start() deja la explicación en `recorder.error`
  };

  const dismiss = () => {
    setNote('');
    recorder.clearError();
    if (disabledNow) setHidden(true);
  };

  let label = 'Dictar venta con el micrófono';
  if (recording) label = `Detener dictado, ${clock(recorder.elapsed)}`;
  else if (processing) label = 'Interpretando el dictado';
  else if (requesting) label = 'Esperando el permiso del micrófono';

  return (
    <div className="relative shrink-0">
      <button
        type="button"
        onClick={handleClick}
        disabled={disabledNow}
        aria-label={label}
        aria-busy={busy}
        aria-disabled={!recorder.supported || undefined}
        title={recorder.supported ? label : recorder.unsupportedMessage}
        className={cx(
          'h-12 min-w-12 shrink-0 rounded-2xl flex items-center justify-center gap-2 active:scale-95 transition-all',
          recording ? 'px-3 bg-red-500 text-white animate-pulse' : 'w-12 bg-solid text-solid-ink',
          !recorder.supported || disabledNow || busy ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer',
        )}
      >
        {busy && <Loader2 className="w-5 h-5 animate-spin" />}
        {recording && (
          <>
            <Square className="w-4 h-4 fill-current" />
            <span className="text-xs font-black tabular-nums">{clock(recorder.elapsed)}</span>
          </>
        )}
        {!busy && !recording && <Mic className="w-5 h-5" />}
      </button>

      {message && (
        <div role="alert" className="absolute right-0 top-full mt-2 z-20 w-64 flex items-start gap-2 rounded-2xl border border-line bg-card p-3 shadow-xl">
          <p className="flex-1 text-[11px] font-semibold text-ink-soft">{message}</p>
          <button
            type="button"
            onClick={dismiss}
            aria-label="Cerrar aviso"
            className="p-0.5 rounded-lg text-ink-muted hover:text-ink cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}
    </div>
  );
}
