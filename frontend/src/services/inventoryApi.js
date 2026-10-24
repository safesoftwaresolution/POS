import { fetchApi, newIdempotencyKey, uploadFileApi, downloadFileApi } from './http';

export async function createInventoryMovementApi(productId, movementType, quantity, reason = '') {
  return await fetchApi('/inventory/movements', {
    method: 'POST',
    body: JSON.stringify({ productId, movementType, quantity, reason }),
  });
}

export async function createPurchaseApi(purchaseData, idempotencyKey) {
  const key = idempotencyKey || newIdempotencyKey();
  return await fetchApi('/purchases', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify(purchaseData),
  });
}

// Importación masiva desde Excel. withProducts=true trae la plantilla llena con los productos del local
// (para hacer un conteo físico).
export async function downloadProductImportTemplateApi(withProducts = false) {
  return await downloadFileApi(`/inventory/import/template?withProducts=${withProducts}`);
}

function fileForm(file) {
  const formData = new FormData();
  formData.append('file', file);
  return formData;
}

// Valida el archivo y devuelve qué pasaría con cada fila, sin guardar nada.
export async function previewProductImportApi(file) {
  return await uploadFileApi('/inventory/import/preview', fileForm(file));
}

// Importa "todo o nada". Si hay filas con errores falla con status 422 y la vista previa en err.data.preview.
export async function importProductsApi(file) {
  return await uploadFileApi('/inventory/import', fileForm(file));
}
