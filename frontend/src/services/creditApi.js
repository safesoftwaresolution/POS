import { fetchApi } from './http';

/** Cuentas de fiado (un vendedor solo recibe las de los clientes a quienes él les fió). Página de Spring. */
export async function getCreditAccountsApi({ search, onlyWithBalance = true, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ onlyWithBalance: String(onlyWithBalance), page: String(page), size: String(size) });
  if (search) params.set('search', search);
  return await fetchApi(`/credit/accounts?${params.toString()}`);
}

/** Saldo y cupo de un cliente (en cero y sin cupo si nunca se le ha fiado). Lo consulta cualquier rol de venta. */
export async function getCreditAccountApi(customerId) {
  return await fetchApi(`/credit/accounts/${customerId}`);
}

export async function getCreditMovementsApi(customerId, page = 0, size = 20) {
  return await fetchApi(`/credit/accounts/${customerId}/movements?page=${page}&size=${size}`);
}

export async function registerCreditPaymentApi(customerId, { amount, paymentMethod, note }) {
  return await fetchApi(`/credit/accounts/${customerId}/payments`, {
    method: 'POST',
    body: JSON.stringify({ amount, paymentMethod, note: note || null }),
  });
}

// Solo ADMINISTRATOR y SUPERVISOR. `creditLimit` null quita el cupo.
export async function setCreditLimitApi(customerId, creditLimit) {
  return await fetchApi(`/credit/accounts/${customerId}/limit`, {
    method: 'PUT',
    body: JSON.stringify({ creditLimit }),
  });
}
