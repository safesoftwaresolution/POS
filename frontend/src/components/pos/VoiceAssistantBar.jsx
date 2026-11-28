import React, { useState } from 'react';
import { Loader2, Mic, Send, Sparkles, Square, X } from 'lucide-react';
import useVoiceRecorder from '../../hooks/useVoiceRecorder';
import { isVoiceSalesDisabled } from '../../services/voiceSalesApi';
import { cx } from '../ui/cx';

const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

const STATUS_TEXT = { transcribing: 'Escuchando lo que dijiste…', thinking: 'Armando el ticket…' };

/**
 * Asistente de caja dentro del ticket: se le habla (un toque empieza a grabar, otro termina, o se corta solo a los
 * 45 s) o se le escribe, y el ticket se arma a la vista, sin ventanas. `agent` es el resultado de `useVoiceAgent`.
 * Aqui no se guarda audio ni se registra ninguna venta. Si el navegador no puede grabar, el microfono queda atenuado
 * y explica por que; si el servidor dice que el asistente esta apagado, la barra desaparece.
 */
export default function VoiceAssistantBar({ agent }) {
  const [text, setText] = useState('');
  const recorder = useVoiceRecorder({ onRecorded: agent.sendAudio });

  if (agent.disabled || isVoiceSalesDisabled()) return null;

  const recording = recorder.status === 'recording';
  const requesting = recorder.status === 'requesting';
  const busy = agent.busy || requesting;
  const error = agent.error || recorder.error;
  const lastUser = [...agent.messages].reverse().find((m) => m.role === 'user');
  const lastAssistant = [...agent.messages].reverse().find((m) => m.role === 'assistant');

  const submit = async (event) => {
    event.preventDefault();
    if (busy || recording || !text.trim()) return;
    const sent = text;
    setText('');
    const ok = await agent.sendText(sent);
    if (!ok) setText((current) => current || sent); // no se pierde lo escrito si fallo
  };

  const toggleMic = () => {
    if (busy && !recording) return;
    agent.clearError();
    if (recording) recorder.stop();
    else recorder.start(); // sin soporte, start() deja la explicacion en `recorder.error`
  };

  const dismiss = () => {
    agent.clearError();
    recorder.clearError();
  };

  let micLabel = 'Hablarle al asistente';
  if (recording) micLabel = `Detener, ${clock(recorder.elapsed)}`;
  else if (requesting) micLabel = 'Esperando el permiso del micrófono';

  return (
    <div className="shrink-0 border-t border-line bg-gray-50 dark:bg-white/5 p-3 space-y-2" data-testid="voice-assistant">
      <div className="flex items-center gap-1.5 text-[11px] font-extrabold text-ink-soft">
        <Sparkles className="w-3.5 h-3.5 text-brand" aria-hidden="true" />
        <span>Asistente de caja</span>
      </div>

      {(lastUser || lastAssistant || STATUS_TEXT[agent.status]) && (
        <div className="space-y-1.5 text-[11px] font-semibold" aria-live="polite">
          {lastUser && <p className="text-ink-muted truncate" title={lastUser.text}>Dijiste: «{lastUser.text}»</p>}
          {STATUS_TEXT[agent.status]
            ? <p className="text-ink-soft flex items-center gap-1.5"><Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />{STATUS_TEXT[agent.status]}</p>
            : lastAssistant && <p className="rounded-xl bg-card border border-line px-3 py-2 text-ink">{lastAssistant.text}</p>}
          {!agent.busy && agent.notes.map((note) => (
            <p key={note} className="text-amber-700 dark:text-amber-400">{note}</p>
          ))}
        </div>
      )}

      {error && (
        <div role="alert" className="flex items-start gap-2 rounded-xl border border-line bg-card p-2.5">
          <p className="flex-1 text-[11px] font-semibold text-ink-soft">{error}</p>
          <button type="button" onClick={dismiss} aria-label="Cerrar aviso" className="p-0.5 rounded-lg text-ink-muted hover:text-ink cursor-pointer">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      <form onSubmit={submit} className="flex items-center gap-2">
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={500}
          disabled={busy || recording}
          placeholder="Ej: dos cafés y una agua, paga con Nequi"
          aria-label="Escríbele al asistente"
          className="min-w-0 flex-1 bg-card border border-line rounded-xl px-3 py-2 text-xs font-semibold text-ink placeholder:text-ink-muted focus:outline-none focus:border-ink disabled:opacity-60"
        />
        <button
          type="submit"
          disabled={busy || recording || !text.trim()}
          aria-label="Enviar al asistente"
          className="h-9 w-9 shrink-0 rounded-xl bg-solid text-solid-ink flex items-center justify-center disabled:opacity-40 cursor-pointer disabled:cursor-not-allowed active:scale-95 transition-all"
        >
          <Send className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={toggleMic}
          aria-label={micLabel}
          aria-busy={busy}
          aria-disabled={!recorder.supported || undefined}
          title={recorder.supported ? micLabel : recorder.unsupportedMessage}
          className={cx(
            'h-9 shrink-0 rounded-xl flex items-center justify-center gap-1.5 active:scale-95 transition-all',
            recording ? 'px-2.5 bg-red-500 text-white animate-pulse' : 'w-9 bg-brand text-white',
            !recorder.supported || (busy && !recording) ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer',
          )}
        >
          {busy && !recording && <Loader2 className="w-4 h-4 animate-spin" />}
          {recording && (
            <>
              <Square className="w-3.5 h-3.5 fill-current" />
              <span className="text-[11px] font-black tabular-nums">{clock(recorder.elapsed)}</span>
            </>
          )}
          {!busy && !recording && <Mic className="w-4 h-4" />}
        </button>
      </form>
    </div>
  );
}
