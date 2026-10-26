import { fetchApi, newIdempotencyKey, uploadFileApi, downloadFileApi } from './http';

// `lot` (opcional, solo en entradas): { expirationDate: 'YYYY-MM-DD', lotCode } guarda un lote con estas unidades.
export async function createInventoryMovementApi(productId, movementType, quantity, reason = '', lot = {}) {
  return await fetchApi('/inventory/movements', {
    method: 'POST',
    body: JSON.stringify({
      productId, movementType, quantity, reason,
      expirationDate: lot.expirationDate || null,
      lotCode: lot.expirationDate ? (lot.lotCode || null) : null,
    }),
  });
}

// Kardex del local, del más reciente al más antiguo. `dateFrom`/`dateTo` son ISO con hora (`2026-10-24T00:00:00`).
export async function getInventoryMovementsApi({ productId, dateFrom, dateTo, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page, size });
  if (productId) params.set('productId', productId);
  if (dateFrom) params.set('dateFrom', dateFrom);
  if (dateTo) params.set('dateTo', dateTo);
  return await fetchApi(`/inventory/movements?${params.toString()}`);
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
