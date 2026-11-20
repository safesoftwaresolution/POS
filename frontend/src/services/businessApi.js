// Negocio con varias sucursales del ADMINISTRATOR dueño. La parte de SUPER_ADMIN vive en services/backoffice/businessesApi.js.
import { fetchApi } from './http';

// Negocio del dueño y la sucursal en la que trabaja (`{ business, activeStoreId }`). Un 404 significa que el usuario
// no es dueño de ningún negocio (un local normal), no un error: devuelve null.
export async function getMyBusinessApi() {
  try {
    return await fetchApi('/business/me');
  } catch (error) {
    if (error?.status === 404) return null;
    throw error;
  }
}

// Cambia la sucursal de la sesión. El refresh token viaja en su cookie HttpOnly, así que solo se envía el local.
// Devuelve el mismo formato que el login, con un access token de esa sucursal.
export async function switchStoreApi(storeId) {
  return fetchApi('/auth/switch-store', {
    method: 'POST',
    body: JSON.stringify({ storeId }),
  });
}

// Crea una sucursal nueva; queda pendiente de revisión de la plataforma.
export async function createBranchApi(payload) {
  return fetchApi('/business/branches', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/** `?from=...&to=...` con las fechas ISO yyyy-MM-dd que haya (sin ellas, el backend usa los últimos 30 días). */
export function periodQuery(from, to) {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  const query = params.toString();
  return query ? `?${query}` : '';
}

// Totales de todas las sucursales juntas.
export async function getBusinessOverviewApi(from, to) {
  return fetchApi(`/business/stats/overview${periodQuery(from, to)}`);
}

// Las mismas cifras por sucursal, ordenadas por venta neta.
export async function getBranchesStatsApi(from, to) {
  return fetchApi(`/business/stats/branches${periodQuery(from, to)}`);
}
