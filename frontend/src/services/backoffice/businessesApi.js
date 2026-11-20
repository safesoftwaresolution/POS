import { fetchApi } from '../http';

export async function getBusinessesApi(q = '', page = 0, size = 20) {
  const params = new URLSearchParams({ page, size });
  if (q) params.append('q', q);
  return fetchApi(`/backoffice/businesses?${params.toString()}`);
}

export async function createBusinessApi(payload) {
  return fetchApi('/backoffice/businesses', { method: 'POST', body: JSON.stringify(payload) });
}

// Solo viajan los campos que cambian: nombre y/o máximo de sucursales.
export async function updateBusinessApi(id, payload) {
  return fetchApi(`/backoffice/businesses/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

// El backend responde 409 si alguna sucursal ya tiene facturas validadas por la DIAN.
export async function changeBusinessBillingModeApi(id, billingMode) {
  return fetchApi(`/backoffice/businesses/${id}/billing-mode`, {
    method: 'PATCH',
    body: JSON.stringify({ billingMode }),
  });
}

export async function addBusinessBranchApi(id, storeId) {
  return fetchApi(`/backoffice/businesses/${id}/stores`, { method: 'POST', body: JSON.stringify({ storeId }) });
}

export async function removeBusinessBranchApi(id, storeId) {
  return fetchApi(`/backoffice/businesses/${id}/stores/${storeId}`, { method: 'DELETE' });
}

// Los locales quedan independientes; no se elimina ninguno.
export async function dissolveBusinessApi(id) {
  return fetchApi(`/backoffice/businesses/${id}`, { method: 'DELETE' });
}
