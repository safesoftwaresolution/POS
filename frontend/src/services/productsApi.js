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

// Lotes de vencimiento de un producto: { lots: [{ id, lotCode, quantity, remainingQuantity, expirationDate }], undatedQuantity }.
export async function getProductLotsApi(productId) {
  return await fetchApi(`/products/${productId}/lots`);
}

// Le pone fecha a unidades que ya estaban en el inventario sin ella (no mueve stock).
export async function addProductLotApi(productId, { quantity, expirationDate, lotCode }) {
  return await fetchApi(`/products/${productId}/lots`, {
    method: 'POST',
    body: JSON.stringify({ quantity, expirationDate, lotCode: lotCode || null }),
  });
}

// Corrige la fecha, el código y (si se envía `quantity`) las unidades que le quedan al lote. No toca el stock del producto.
export async function updateProductLotApi(productId, lotId, { expirationDate, lotCode, quantity }) {
  return await fetchApi(`/products/${productId}/lots/${lotId}`, {
    method: 'PUT',
    body: JSON.stringify({ expirationDate, lotCode: lotCode || null, quantity: quantity ?? null }),
  });
}

export async function deleteProductLotApi(productId, lotId) {
  return await fetchApi(`/products/${productId}/lots/${lotId}`, { method: 'DELETE' });
}

export async function changeProductStatusApi(id, active) {
  return await fetchApi(`/products/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

/** Borra de R2 una foto recién subida que no llegó a guardarse en ningún producto. */
export async function deleteStorageFileApi(key) {
  return fetchApi(`/storage/files?key=${encodeURIComponent(key)}`, { method: 'DELETE' });
}
