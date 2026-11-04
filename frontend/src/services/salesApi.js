import { fetchApi, newIdempotencyKey } from './http';

export async function getSalesApi(page = 0, size = 20) {
  const data = await fetchApi(`/sales?page=${page}&size=${size}`);
  return data.content || data || [];
}

/**
 * Búsqueda paginada de ventas del local. `dateFrom`/`dateTo` son ISO con hora (`2026-10-24T00:00:00`).
 * Devuelve la página completa de Spring: `{ content, totalElements, totalPages, number }`.
 */
export async function searchSalesApi({ dateFrom, dateTo, customerId, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (dateFrom) params.set('dateFrom', dateFrom);
  if (dateTo) params.set('dateTo', dateTo);
  if (customerId) params.set('customerId', String(customerId));
  const data = await fetchApi(`/sales?${params.toString()}`);
  return Array.isArray(data)
    ? { content: data, totalElements: data.length, totalPages: 1, number: 0 }
    : data;
}

export async function getSaleByIdApi(id) {
  return await fetchApi(`/sales/${id}`);
}

export async function getSaleReturnsApi(saleId) {
  return await fetchApi(`/sales/${saleId}/returns`);
}

export async function createSaleReturnApi(saleId, reason, items) {
  return await fetchApi(`/sales/${saleId}/returns`, {
    method: 'POST',
    body: JSON.stringify({
      reason: reason || null,
      items: items.map((item) => ({
        saleItemId: item.saleItemId,
        quantity: item.quantity,
      })),
    }),
  });
}

export async function createSaleApi(saleCommand, idempotencyKey) {
  // La Idempotency-Key evita ventas duplicadas si el request se reintenta (doble clic,
  // reintento de red o renovación 401). El mismo valor se reenvía en cada reintento.
  const key = idempotencyKey || newIdempotencyKey();
  return await fetchApi('/sales', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify({
      customerId: saleCommand.customerId || null,
      items: saleCommand.items.map(item => ({
        productId: item.productId ?? null,
        serviceId: item.serviceId ?? null,
        quantity: item.quantity,
        unitPrice: item.unitPrice
      })),
      cashReceived: saleCommand.cashReceived,
      sendToFactus: Boolean(saleCommand.sendToFactus),
      paymentMethod: saleCommand.paymentMethod || 'CASH'
    }),
  });
}
