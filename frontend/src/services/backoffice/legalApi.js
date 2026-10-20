import { fetchApi } from '../http';

export async function getBackofficeLegalDocumentsApi() {
  return await fetchApi('/backoffice/legal-documents');
}

export async function getBackofficeLegalDocumentApi(id) {
  return await fetchApi(`/backoffice/legal-documents/${id}`);
}

export async function createBackofficeLegalDocumentApi(documentData) {
  return await fetchApi('/backoffice/legal-documents', {
    method: 'POST',
    body: JSON.stringify(documentData),
  });
}

export async function updateBackofficeLegalDocumentApi(id, documentData) {
  return await fetchApi(`/backoffice/legal-documents/${id}`, {
    method: 'PUT',
    body: JSON.stringify(documentData),
  });
}

export async function deleteBackofficeLegalDocumentApi(id) {
  return await fetchApi(`/backoffice/legal-documents/${id}`, { method: 'DELETE' });
}

export async function toggleBackofficeLegalDocumentPublishApi(id) {
  return await fetchApi(`/backoffice/legal-documents/${id}/publish`, { method: 'PATCH' });
}
