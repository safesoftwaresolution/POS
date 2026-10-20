import { fetchApi } from '../http';

export async function getBackofficeMetricsApi() {
  return await fetchApi('/backoffice/metrics');
}

export async function getBackofficeStoresApi(status = '', q = '', page = 0, size = 20) {
  const params = new URLSearchParams({ page, size });
  if (status && status !== 'ALL') params.append('status', status);
  if (q) params.append('q', q);
  return await fetchApi(`/backoffice/stores?${params.toString()}`);
}

export async function getBackofficeStoreApi(id) {
  return await fetchApi(`/backoffice/stores/${id}`);
}

export async function createBackofficeStoreApi(storeData) {
  return await fetchApi('/backoffice/stores', {
    method: 'POST',
    body: JSON.stringify(storeData),
  });
}

export async function updateBackofficeStoreApi(id, storeData) {
  return await fetchApi(`/backoffice/stores/${id}`, {
    method: 'PUT',
    body: JSON.stringify(storeData),
  });
}

export async function changeBackofficeStoreStatusApi(id, status) {
  return await fetchApi(`/backoffice/stores/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export async function verifyBackofficeStoreEmailApi(id) {
  return await fetchApi(`/backoffice/stores/${id}/verify-email`, {
    method: 'PATCH',
  });
}

export async function rejectBackofficeStoreApi(id, reason) {
  return await fetchApi(`/backoffice/stores/${id}/reject`, {
    method: 'PATCH',
    body: JSON.stringify({ reason }),
  });
}

export async function getBackofficeStoreDocumentsApi(storeId) {
  return await fetchApi(`/backoffice/stores/${storeId}/documents`);
}

export async function reviewBackofficeStoreDocumentApi(storeId, docId, status, rejectionReason = null) {
  return await fetchApi(`/backoffice/stores/${storeId}/documents/${docId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status, rejectionReason }),
  });
}

export async function getBackofficeStoreCategoriesApi() {
  return await fetchApi('/backoffice/store-categories');
}

export async function createBackofficeStoreCategoryApi(name, description = '') {
  return await fetchApi('/backoffice/store-categories', {
    method: 'POST',
    body: JSON.stringify({ name, description }),
  });
}

export async function updateBackofficeStoreCategoryApi(id, name, description = '') {
  return await fetchApi(`/backoffice/store-categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description }),
  });
}

export async function deleteBackofficeStoreCategoryApi(id) {
  return await fetchApi(`/backoffice/store-categories/${id}`, { method: 'DELETE' });
}
