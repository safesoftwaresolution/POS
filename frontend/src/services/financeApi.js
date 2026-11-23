import { fetchApi } from './http';

function rangeParams(dateFrom, dateTo) {
  const params = new URLSearchParams();
  if (dateFrom) params.set('dateFrom', dateFrom);
  if (dateTo) params.set('dateTo', dateTo);
  return params;
}

/** Estado financiero (base caja) del rango; las fechas son `yyyy-MM-dd`. */
export async function getFinanceSummaryApi(dateFrom, dateTo) {
  return await fetchApi(`/finance/summary?${rangeParams(dateFrom, dateTo)}`);
}

/** Movimientos manuales (egresos e ingresos) del rango, paginados (`page` empieza en 0). */
export async function getMovementsApi({ dateFrom, dateTo, page = 0, size = 10 } = {}) {
  const params = rangeParams(dateFrom, dateTo);
  params.set('page', page);
  params.set('size', size);
  return await fetchApi(`/finance/movements?${params}`);
}

export async function createMovementApi(payload) {
  return await fetchApi('/finance/movements', { method: 'POST', body: JSON.stringify(payload) });
}

export async function updateMovementApi(id, payload) {
  return await fetchApi(`/finance/movements/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export async function deleteMovementApi(id) {
  return await fetchApi(`/finance/movements/${id}`, { method: 'DELETE' });
}

export async function getFixedCostsApi() {
  return await fetchApi('/finance/fixed-costs');
}

export async function createFixedCostApi(payload) {
  return await fetchApi('/finance/fixed-costs', { method: 'POST', body: JSON.stringify(payload) });
}

export async function updateFixedCostApi(id, payload) {
  return await fetchApi(`/finance/fixed-costs/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export async function deleteFixedCostApi(id) {
  return await fetchApi(`/finance/fixed-costs/${id}`, { method: 'DELETE' });
}
