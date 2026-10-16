import { fetchApi } from './http';

export async function getProductsApi(search = '', categoryId = null, page = 0, size = 50, active = true) {
  const params = new URLSearchParams({ page, size });
  if (search) params.append('search', search);
  if (categoryId) params.append('categoryId', categoryId);
  if (active !== null) params.append('active', active);

  const data = await fetchApi(`/products?${params.toString()}`);
  return data.content || data || [];
}

export async function createProductApi(productData) {
  return await fetchApi('/products', {
    method: 'POST',
    body: JSON.stringify({
      name: productData.name,
      description: productData.description || productData.name,
      internalCode: productData.internalCode,
      barcode: productData.barcode,
      categoryId: productData.categoryId,
      subcategoryId: productData.subcategoryId || null,
      purchasePrice: parseFloat(productData.purchasePrice || productData.price * 0.7),
      salePrice: parseFloat(productData.price || productData.salePrice),
      minStock: parseInt(productData.minStock || 5),
      imageUrl: productData.imageUrl || null,
    }),
  });
}

export async function updateProductApi(id, productData) {
  return await fetchApi(`/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(productData),
  });
}

export async function deleteProductApi(id) {
  return await fetchApi(`/products/${id}`, { method: 'DELETE' });
}

export async function changeProductStatusApi(id, active) {
  return await fetchApi(`/products/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}
