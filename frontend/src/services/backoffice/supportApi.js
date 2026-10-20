import { fetchApi } from '../http';

export async function getBackofficeSupportMetricsApi() {
  return await fetchApi('/backoffice/support/metrics');
}

export async function getBackofficeSupportTicketsApi({ type = '', status = '', priority = '', storeId = '', q = '', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page, size });
  if (type && type !== 'ALL') params.append('type', type);
  if (status && status !== 'ALL') params.append('status', status);
  if (priority && priority !== 'ALL') params.append('priority', priority);
  if (storeId) params.append('storeId', storeId);
  if (q) params.append('q', q);
  return await fetchApi(`/backoffice/support/tickets?${params.toString()}`);
}

export async function getBackofficeSupportTicketApi(id) {
  return await fetchApi(`/backoffice/support/tickets/${id}`);
}

export async function updateBackofficeSupportTicketApi(id, ticketData) {
  return await fetchApi(`/backoffice/support/tickets/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(ticketData),
  });
}
