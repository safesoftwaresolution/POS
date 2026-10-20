import { fetchApi } from './http';

export async function getSubcategoriesApi(categoryId = null) {
  const query = categoryId ? `?categoryId=${categoryId}` : '';
  const data = await fetchApi(`/subcategories${query}`);
  return data.content || data || [];
}

export async function createSubcategoryApi(categoryId, name, description = '') {
  return await fetchApi('/subcategories', {
    method: 'POST',
    body: JSON.stringify({ categoryId, name, description }),
  });
}

export async function updateSubcategoryApi(id, name, description = '') {
  return await fetchApi(`/subcategories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description }),
  });
}

export async function deleteSubcategoryApi(id) {
  return await fetchApi(`/subcategories/${id}`, { method: 'DELETE' });
}
