// Auto-registro self-service del local del ADMINISTRATOR autenticado.
// Distinto de services/backoffice/storesApi.js, que es 100% para SUPER_ADMIN.
import { fetchApi } from './http';

export async function registerOwnStoreApi(payload) {
  return fetchApi('/stores', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function getOwnStoreApi() {
  return fetchApi('/stores/me');
}

// Plantilla del local (RETAIL, FOOD, APPOINTMENTS). A diferencia de /stores/me, la puede leer cualquier rol del local.
export async function getOwnStoreTemplateApi() {
  return fetchApi('/stores/me/template');
}

export async function updateOwnStoreApi(payload) {
  return fetchApi('/stores/me', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

// Reenvía a revisión un local rechazado (REJECTED → PENDING_VERIFICATION). Devuelve el local actualizado.
export async function resubmitOwnStoreApi() {
  return fetchApi('/stores/me/resubmit', { method: 'POST' });
}

// Estado del local (`{ status }`) para cualquier rol del local; responde aunque el local no esté activo.
export async function getOwnStoreStatusApi() {
  return fetchApi('/stores/me/status');
}
