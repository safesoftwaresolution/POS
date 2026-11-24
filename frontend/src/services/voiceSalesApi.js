import { fetchApi } from './http';

// Asistente de caja por voz o texto. El audio viaja como cuerpo binario crudo (sin multipart, sin enlaces, sin local:
// lo resuelve la sesion) y solo se convierte en texto; cada turno aplica ese texto al ticket y devuelve el ticket
// resultante. Nada de aqui registra una venta: el cobro lo hace el POS con la API de ventas.
// Contrato: backend/docs/modules/voicesales/spec.md.

/** Tamaño maximo del audio que acepta el backend (5 MiB). */
export const MAX_AUDIO_BYTES = 5 * 1024 * 1024;

const ACCEPTED_TYPES = ['audio/webm', 'audio/wav'];
const MANUAL_HINT = 'Puedes seguir con la venta manual.';

// El 404 significa que el backend no tiene el asistente encendido: no se vuelve a ofrecer hasta recargar la pagina.
let disabledForSession = false;

export const isVoiceSalesDisabled = () => disabledForSession;

/** Solo para pruebas: devuelve el servicio al estado inicial de la sesion. */
export const resetVoiceSalesAvailability = () => {
  disabledForSession = false;
};

function voiceError(kind, message, { status, data } = {}) {
  const error = new Error(message);
  error.kind = kind;
  if (status !== undefined) error.status = status;
  if (data !== undefined) error.data = data;
  return error;
}

// Mensajes fijos por estado (el cuerpo del backend sigue disponible en `error.data`). El 400 y el 403 conservan el del
// backend: uno explica que falta en el pedido, el otro distingue rol de local inactivo.
function mapServerError(error) {
  const { status, data } = error;
  const detail = { status, data };
  switch (status) {
    case 400:
      return voiceError('invalid', data?.message || 'No se entendió el pedido.', detail);
    case 403:
      return voiceError('forbidden', data?.message || 'No tienes permiso para usar el asistente.', detail);
    case 404:
      disabledForSession = true;
      return voiceError('disabled', 'El asistente por voz no está habilitado en este servidor.', detail);
    case 413:
      return voiceError('too_large', 'Es demasiado grande. Intenta con un pedido más corto.', detail);
    case 415:
      return voiceError('unsupported_type', 'El formato de audio no es compatible.', detail);
    case 429:
      return voiceError('quota', `Se alcanzó el límite por minuto. Espera un momento o sigue con la venta manual.`, detail);
    case 503:
      return voiceError('unavailable', `El asistente no está disponible ahora. ${MANUAL_HINT}`, detail);
    default:
      return voiceError('unknown', data?.message || `No se pudo procesar el pedido. ${MANUAL_HINT}`, detail);
  }
}

async function call(endpoint, options) {
  try {
    return await fetchApi(endpoint, options);
  } catch (error) {
    if (typeof error?.status === 'number') throw mapServerError(error);
    if (error instanceof TypeError) {
      throw voiceError('network', `No se pudo conectar con el servidor. ${MANUAL_HINT}`);
    }
    throw error; // p. ej. sesión expirada: conserva su mensaje.
  }
}

/**
 * Pasa una grabacion a texto. Valida tipo y tamaño antes de enviar nada. Los errores llevan `kind`, un mensaje en
 * español y, si vinieron del servidor, `status` y `data`. No guarda el audio en ninguna parte: el Blob solo vive
 * mientras dura la llamada.
 * @returns {Promise<string>} lo que se dijo
 */
export async function transcribeVoiceApi(blob) {
  if (!blob || blob.size === 0) {
    throw voiceError('empty', 'No se grabó audio. Intenta de nuevo.');
  }
  const baseType = (blob.type || '').split(';')[0].trim().toLowerCase();
  if (!ACCEPTED_TYPES.includes(baseType)) {
    throw voiceError('unsupported_type', 'El formato de audio no es compatible.', { status: 415 });
  }
  if (blob.size > MAX_AUDIO_BYTES) {
    throw voiceError('too_large', 'La grabación es demasiado grande. Intenta con un dictado más corto.', { status: 413 });
  }
  const result = await call('/voice-sales/agent/transcribe', {
    method: 'POST',
    headers: { 'Content-Type': blob.type },
    body: blob,
  });
  return result.text;
}

/**
 * Un turno del asistente: lo que dijo el cajero, el ticket actual (`toAgentTicket`) y un historial corto
 * (`[{ role: 'user' | 'assistant', text }]`). Devuelve `{ reply, ticket, warnings }`.
 */
export async function agentTurnApi({ text, ticket, history }) {
  return call('/voice-sales/agent/turn', {
    method: 'POST',
    body: JSON.stringify({ text, ticket, history }),
  });
}
