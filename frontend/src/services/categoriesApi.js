import { fetchApi } from './http';

export async function getCategoriesApi() {
  const data = await fetchApi('/categories');
  return data.content || data || [];
}

export async function createCategoryApi(name, description = '') {
  return await fetchApi('/categories', {
    method: 'POST',
    body: JSON.stringify({ name, description }),
  });
}

export async function updateCategoryApi(id, name, description = '') {
  return await fetchApi(`/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description }),
  });
}

export async function deleteCategoryApi(id) {
  return await fetchApi(`/categories/${id}`, { method: 'DELETE' });
}
