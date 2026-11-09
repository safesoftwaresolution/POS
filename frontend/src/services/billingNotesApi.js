import { fetchApi } from './http';
import { downloadBillingBinary } from './billingApi';

function endpoint(type) {
  if (type !== 'credit' && type !== 'debit') throw new Error('Tipo de nota no válido');
  return `/billing/${type}-notes`;
}

export async function listNotesApi(type, status = '', page = 0, size = 20) {
  const params = new URLSearchParams({ page, size });
  if (status) params.append('status', status);
  return await fetchApi(`${endpoint(type)}?${params}`);
}

export async function createCreditNoteApi(saleId, observation = '') {
  const params = new URLSearchParams({ conceptCode: '2', observation });
  return await fetchApi(`${endpoint('credit')}/sales/${encodeURIComponent(saleId)}?${params}`, { method: 'POST' });
}

export async function createDebitNoteApi(saleId, request) {
  return await fetchApi(`${endpoint('debit')}/sales/${encodeURIComponent(saleId)}`, {
    method: 'POST', body: JSON.stringify(request),
  });
}

export async function retryNoteApi(type, id) {
  return await fetchApi(`${endpoint(type)}/${encodeURIComponent(id)}/retry`, { method: 'POST' });
}

export async function downloadNoteApi(type, id, format) {
  if (format !== 'pdf' && format !== 'xml') throw new Error('Formato de archivo no válido');
  return await downloadBillingBinary(`${endpoint(type)}/${encodeURIComponent(id)}/${format}`);
}
