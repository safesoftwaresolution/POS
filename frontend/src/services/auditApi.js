import { fetchApi } from './http';

/** Historial de cambios de una entidad del local (solo ADMINISTRATOR). Devuelve la página de Spring. */
export async function getAuditHistoryApi(entityType, entityId, page = 0, size = 50) {
  const params = new URLSearchParams({ entityType, entityId: String(entityId), page: String(page), size: String(size) });
  return await fetchApi(`/audit?${params.toString()}`);
}
