import { fetchApi, getAuthToken, API_BASE_URL } from './http';

// ---------- Habilitación (estado del local) ----------

export async function getBillingActivationStatusApi() {
  return await fetchApi('/billing/activation/status');
}

/** Para el POS (cualquier rol): `{ active, environment }`. En SANDBOX las facturas son de prueba. */
export async function getBillingSummaryApi() {
  return await fetchApi('/billing/activation/summary');
}

export async function requestBillingActivationApi() {
  return await fetchApi('/billing/activation/request', { method: 'POST' });
}

// ---------- Asistente de habilitación (autoservicio, producción) ----------

/** Avance paso a paso: `{ status, environment, platformReady, steps[], profile, invoiceRangesCount }`. */
export async function getBillingSetupApi() {
  return await fetchApi('/billing/setup');
}

/** Datos del RUT. Devuelve el asistente actualizado (un fallo del proveedor llega como paso en ERROR). */
export async function saveFiscalProfileApi(profile) {
  return await fetchApi('/billing/setup/fiscal-profile', {
    method: 'PUT',
    body: JSON.stringify(profile),
  });
}

/** Envía el TestSetId que entregó la DIAN. Devuelve el asistente actualizado. */
export async function startDianEnablementApi(testSetId) {
  return await fetchApi('/billing/setup/dian-enablement', {
    method: 'POST',
    body: JSON.stringify({ testSetId }),
  });
}

/** Trae las resoluciones de la DIAN como rangos: `{ importedCount, noteRangesCreated, skipped[], setup }`. */
export async function importNumberingApi() {
  return await fetchApi('/billing/setup/numbering/import', { method: 'POST' });
}

// ---------- Catálogos ----------

/** Departamentos y municipios de Colombia (DIVIPOLA del DANE). */
export async function getDivipolaApi() {
  return await fetchApi('/billing/catalogs/divipola');
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

export async function createNumberingRangeApi({ document, prefix, resolutionNumber, current, from, to, startDate, endDate, technicalKey }) {
  return await fetchApi('/billing/numbering-ranges', {
    method: 'POST',
    body: JSON.stringify({ document, prefix, resolution_number: resolutionNumber, current, from, to, startDate, endDate, technicalKey }),
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
