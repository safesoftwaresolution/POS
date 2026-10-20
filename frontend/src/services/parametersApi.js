import { fetchApi } from './http';

// Lectura: accesible para SUPER_ADMIN, ADMINISTRATOR, SUPERVISOR y SELLER.
export async function getParameterTopicsApi() {
  return await fetchApi('/settings/parameters/topics');
}

export async function getParameterValuesApi(topicCode) {
  return await fetchApi(`/settings/parameters/topics/${topicCode}/values`);
}

// Escritura: solo SUPER_ADMIN (catálogos globales de la plataforma).
export async function createParameterTopicApi(code, name, description = '') {
  return await fetchApi('/settings/parameters/topics', {
    method: 'POST',
    body: JSON.stringify({ code, name, description }),
  });
}

export async function addParameterValueApi(topicCode, code, label, extraValue = '', sortOrder = 0) {
  return await fetchApi(`/settings/parameters/topics/${topicCode}/values`, {
    method: 'POST',
    body: JSON.stringify({ code, label, extraValue, sortOrder }),
  });
}

export async function updateParameterValueApi(id, { label, extraValue, sortOrder, active }) {
  return await fetchApi(`/settings/parameters/values/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ label, extraValue, sortOrder, active }),
  });
}

export async function deactivateParameterValueApi(id) {
  return await fetchApi(`/settings/parameters/values/${id}`, { method: 'DELETE' });
}
