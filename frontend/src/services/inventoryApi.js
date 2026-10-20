import { fetchApi, newIdempotencyKey } from './http';

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
