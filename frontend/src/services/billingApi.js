import { fetchApi, getAuthToken, API_BASE_URL } from './http';

// ---------- Habilitación (estado del local) ----------

export async function getBillingActivationStatusApi() {
  return await fetchApi('/billing/activation/status');
}

export async function requestBillingActivationApi() {
  return await fetchApi('/billing/activation/request', { method: 'POST' });
}

// ---------- Rangos de numeración DIAN ----------

export async function getDianNumberingRangesApi() {
  return await fetchApi('/billing/numbering-ranges/dian');
}

export async function listNumberingRangesApi() {
  return await fetchApi('/billing/numbering-ranges');
}

export async function getNumberingRangeApi(id) {
  return await fetchApi(`/billing/numbering-ranges/${id}`);
}

export async function createNumberingRangeApi({ document, prefix, resolutionNumber, current }) {
  return await fetchApi('/billing/numbering-ranges', {
    method: 'POST',
    body: JSON.stringify({ document, prefix, resolution_number: resolutionNumber, current }),
  });
}

export async function deleteNumberingRangeApi(id) {
  return await fetchApi(`/billing/numbering-ranges/${id}`, { method: 'DELETE' });
}

export async function toggleNumberingRangeStatusApi(id) {
  return await fetchApi(`/billing/numbering-ranges/${id}/toggle-status`, { method: 'PATCH' });
}

export async function updateNumberingRangeCurrentApi(id, current) {
  return await fetchApi(`/billing/numbering-ranges/${id}/current`, {
    method: 'PATCH',
    body: JSON.stringify({ current }),
  });
}

export async function setDefaultNumberingRangeApi(id) {
  return await fetchApi(`/billing/numbering-ranges/${id}/set-default`, { method: 'POST' });
}

// ---------- Facturas electrónicas de venta ----------

export async function listInvoicesApi(status = '', page = 0, size = 20) {
  const params = new URLSearchParams({ page, size });
  if (status) params.append('status', status);
  return await fetchApi(`/billing/invoices?${params.toString()}`);
}

export async function getSaleInvoiceApi(saleId) {
  return await fetchApi(`/billing/sales/${saleId}`);
}

export async function retryInvoiceApi(saleId) {
  return await fetchApi(`/billing/sales/${saleId}/retry`, { method: 'POST' });
}

export async function sendInvoiceEmailApi(saleId, email) {
  return await fetchApi(`/billing/sales/${saleId}/send-email`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

// Descarga binaria (PDF/XML): fetchApi asume JSON, así que estas van directo contra fetch
// para poder adjuntar el Bearer token y devolver un Blob que el navegador pueda abrir/guardar.
async function downloadBinary(endpoint) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    headers: { Authorization: `Bearer ${getAuthToken()}` },
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Error HTTP ${response.status} al descargar el archivo`);
  }
  return await response.blob();
}

export async function downloadInvoicePdfApi(saleId) {
  return await downloadBinary(`/billing/sales/${saleId}/pdf`);
}

export async function downloadInvoiceXmlApi(saleId) {
  return await downloadBinary(`/billing/sales/${saleId}/xml`);
}
