// Auto-registro self-service del local del ADMINISTRATOR autenticado.
// Distinto de services/backoffice/storesApi.js, que es 100% para SUPER_ADMIN.
import { fetchApi, uploadFileApi } from './http';

export async function registerOwnStoreApi(payload) {
  return fetchApi('/stores', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function getOwnStoreApi() {
  return fetchApi('/stores/me');
}

export async function updateOwnStoreApi(payload) {
  return fetchApi('/stores/me', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export async function getOwnStoreDocumentsApi() {
  return fetchApi('/stores/me/documents');
}

export async function uploadOwnStoreDocumentApi(documentType, file) {
  const formData = new FormData();
  formData.append('documentType', documentType);
  formData.append('file', file);
  return uploadFileApi('/stores/me/documents', formData);
}
