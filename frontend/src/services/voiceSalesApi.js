import { fetchApi } from './http';

// Dictado de ventas: el audio viaja como cuerpo binario crudo (sin multipart, sin enlaces, sin local: lo
// resuelve la sesion) y el backend devuelve un borrador editable. Nada de aqui registra una venta.
// Contrato: backend/docs/modules/voicesales/spec.md.

/** Tamaño maximo del audio que acepta el backend (5 MiB). */
export const MAX_AUDIO_BYTES = 5 * 1024 * 1024;

const ACCEPTED_TYPES = ['audio/webm', 'audio/wav'];
const MANUAL_HINT = 'Puedes registrar la venta manualmente.';

// El 404 significa que el backend no tiene el dictado encendido: no se vuelve a ofrecer hasta recargar la pagina.
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

// Mensajes fijos por estado (el cuerpo del backend sigue disponible en `error.data`). El 403 conserva el del
// backend porque distingue rol de local inactivo.
function mapServerError(error) {
  const { status, data } = error;
  const detail = { status, data };
  switch (status) {
    case 403:
      return voiceError('forbidden', data?.message || 'No tienes permiso para usar el dictado.', detail);
    case 404:
      disabledForSession = true;
      return voiceError('disabled', 'El dictado por voz no está habilitado en este servidor.', detail);
    case 413:
      return voiceError('too_large', 'La grabación es demasiado grande. Intenta con un dictado más corto.', detail);
    case 415:
      return voiceError('unsupported_type', 'El formato de audio no es compatible.', detail);
    case 429:
      return voiceError('quota', 'Se alcanzó el límite de dictados por minuto. Espera un momento o registra la venta manualmente.', detail);
    case 503:
      return voiceError('unavailable', `No se pudo interpretar el audio. ${MANUAL_HINT}`, detail);
    default:
      return voiceError('unknown', data?.message || `No se pudo preparar el borrador. ${MANUAL_HINT}`, detail);
  }
}

/**
 * Envia una grabacion y devuelve el borrador (`{ items, customer, paymentMethod, cashReceived, pendingFields }`).
 * Valida tipo y tamaño antes de enviar nada. Los errores llevan `kind`, un mensaje en español y, si vinieron del
 * servidor, `status` y `data`. No guarda el audio en ninguna parte: el Blob solo vive mientras dura la llamada.
 */
export async function previewVoiceSaleApi(blob) {
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

  try {
    return await fetchApi('/voice-sales/preview', {
      method: 'POST',
      headers: { 'Content-Type': blob.type },
      body: blob,
    });
  } catch (error) {
    if (typeof error?.status === 'number') throw mapServerError(error);
    if (error instanceof TypeError) {
      throw voiceError('network', `No se pudo conectar con el servidor. ${MANUAL_HINT}`);
    }
    throw error; // p. ej. sesión expirada: conserva su mensaje.
  }
}
