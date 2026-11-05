import { fetchApi } from '../http';

export async function getBackofficeServiceCategoriesApi() {
  return await fetchApi('/backoffice/service-categories');
}

export async function createBackofficeServiceCategoryApi(name, description = '', storeCategoryIds = []) {
  return await fetchApi('/backoffice/service-categories', {
    method: 'POST',
    body: JSON.stringify({ name, description, storeCategoryIds }),
  });
}

export async function updateBackofficeServiceCategoryApi(id, name, description = '', storeCategoryIds = []) {
  return await fetchApi(`/backoffice/service-categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description, storeCategoryIds }),
  });
}

export async function deactivateBackofficeServiceCategoryApi(id) {
  return await fetchApi(`/backoffice/service-categories/${id}`, { method: 'DELETE' });
}
