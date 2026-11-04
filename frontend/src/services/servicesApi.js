import { fetchApi } from './http';

export async function getServicesApi(search = '', categoryId = null, page = 0, size = 50, active = true) {
  const params = new URLSearchParams({ page, size });
  if (search) params.append('search', search);
  if (categoryId) params.append('categoryId', categoryId);
  if (active !== null) params.append('active', active);

  const data = await fetchApi(`/services?${params.toString()}`);
  return data.content || data || [];
}

export async function createServiceApi(service) {
  return await fetchApi('/services', {
    method: 'POST',
    body: JSON.stringify(service),
  });
}

export async function updateServiceApi(id, service) {
  return await fetchApi(`/services/${id}`, {
    method: 'PUT',
    body: JSON.stringify(service),
  });
}

export async function changeServiceStatusApi(id, active) {
  return await fetchApi(`/services/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

/** Baja lógica: el servicio queda inactivo, las ventas ya emitidas siguen refiriéndolo. */
export async function deleteServiceApi(id) {
  return await fetchApi(`/services/${id}`, { method: 'DELETE' });
}
