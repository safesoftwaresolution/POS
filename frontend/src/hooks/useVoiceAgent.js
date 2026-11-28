import { useCallback, useEffect, useRef, useState } from 'react';
import { agentTurnApi, transcribeVoiceApi } from '../services/voiceSalesApi';
import { ticketSignature, toAgentTicket } from '../utils/agentTicket';

/** Turnos que se mandan como contexto (el servidor tambien los acota). */
export const HISTORY_LIMIT = 6;
const KEPT_MESSAGES = 20;

const STALE_MESSAGE = 'El ticket cambió mientras el asistente respondía, así que no apliqué nada. Repite el pedido.';

/**
 * Conversacion con el asistente de caja. `ticketState` es el ticket del POS ({ cart, customer, paymentMethod,
 * cashTendered }); el hook lo lee al enviar y, si cambia mientras el asistente responde (el cajero toco algo),
 * descarta la respuesta en vez de pisar su cambio. `onTurn(respuesta)` recibe cada turno aplicable.
 *
 * Estado: `status` es 'idle' | 'transcribing' | 'thinking'; un envio nuevo mientras hay uno en curso se ignora.
 * `messages` es `[{ role: 'user' | 'assistant', text }]` (lo ultimo que se hablo). Nada se guarda fuera de memoria.
 */
export default function useVoiceAgent({ ticketState, onTurn }) {
  const [messages, setMessages] = useState([]);
  const [status, setStatus] = useState('idle');
  const [error, setError] = useState('');
  // Avisos del servidor sobre lo que no pudo conservar del ticket (un articulo que ya no existe, etc.).
  const [notes, setNotes] = useState([]);
  const [disabled, setDisabled] = useState(false);
  const stateRef = useRef(ticketState);
  const messagesRef = useRef([]);
  const busyRef = useRef(false);

  useEffect(() => {
    stateRef.current = ticketState;
  }, [ticketState]);

  const push = useCallback((message) => {
    messagesRef.current = [...messagesRef.current, message].slice(-KEPT_MESSAGES);
    setMessages(messagesRef.current);
  }, []);

  const fail = useCallback((e) => {
    setError(e?.message || 'No se pudo procesar el pedido. Puedes seguir con la venta manual.');
    if (e?.kind === 'disabled') setDisabled(true);
  }, []);

  const turn = useCallback(async (text) => {
    const history = messagesRef.current.slice(-HISTORY_LIMIT);
    const before = ticketSignature(stateRef.current);
    const ticket = toAgentTicket(stateRef.current);
    push({ role: 'user', text });
    setStatus('thinking');
    const response = await agentTurnApi({ text, ticket, history });
    if (response.reply) push({ role: 'assistant', text: response.reply });
    if (ticketSignature(stateRef.current) !== before) {
      setError(STALE_MESSAGE);
      setNotes([]);
      return false;
    }
    setNotes(response.warnings ?? []);
    onTurn(response);
    return true;
  }, [onTurn, push]);

  // Un solo envio a la vez: el estado vive en una ref porque dos toques rapidos ocurren antes de re-renderizar.
  const run = useCallback(async (job) => {
    if (busyRef.current) return false;
    busyRef.current = true;
    setError('');
    try {
      return await job();
    } catch (e) {
      fail(e);
      return false;
    } finally {
      busyRef.current = false;
      setStatus('idle');
    }
  }, [fail]);

  const sendText = useCallback((raw) => {
    const text = (raw ?? '').trim();
    if (!text) return Promise.resolve(false);
    return run(() => turn(text));
  }, [run, turn]);

  const sendAudio = useCallback((blob) => run(async () => {
    setStatus('transcribing');
    const text = (await transcribeVoiceApi(blob)).trim();
    return text ? turn(text) : false;
  }), [run, turn]);

  const clearError = useCallback(() => setError(''), []);
  const reset = useCallback(() => {
    messagesRef.current = [];
    setMessages([]);
    setNotes([]);
    setError('');
  }, []);

  return { messages, notes, status, busy: status !== 'idle', error, disabled, sendText, sendAudio, clearError, reset };
}
