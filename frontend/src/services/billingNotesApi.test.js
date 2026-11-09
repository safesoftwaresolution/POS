import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchApi } from './http';
import { downloadBillingBinary } from './billingApi';
import { createCreditNoteApi, createDebitNoteApi, downloadNoteApi, listNotesApi, retryNoteApi } from './billingNotesApi';

vi.mock('./http', () => ({ fetchApi: vi.fn() }));
vi.mock('./billingApi', () => ({ downloadBillingBinary: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

describe('billing note API contracts', () => {
  it('uses the paged credit/debit endpoints and encodes state', async () => {
    await listNotesApi('credit', 'PENDING', 2, 20);
    expect(fetchApi).toHaveBeenLastCalledWith('/billing/credit-notes?page=2&size=20&status=PENDING');
    await listNotesApi('debit');
    expect(fetchApi).toHaveBeenLastCalledWith('/billing/debit-notes?page=0&size=20');
  });

  it('creates a full-sale credit note with encoded explanation and a debit request unchanged', async () => {
    await createCreditNoteApi(10, 'Devolución & ajuste');
    expect(fetchApi).toHaveBeenLastCalledWith('/billing/credit-notes/sales/10?conceptCode=2&observation=Devoluci%C3%B3n+%26+ajuste', { method: 'POST' });
    const request = { reference_code: 'stable', items: [{ price: '100.50', quantity: '2' }] };
    await createDebitNoteApi(10, request);
    expect(fetchApi).toHaveBeenLastCalledWith('/billing/debit-notes/sales/10', { method: 'POST', body: JSON.stringify(request) });
  });

  it('reuses authenticated binary downloads and retries by local note ID', async () => {
    await downloadNoteApi('credit', 8, 'pdf');
    expect(downloadBillingBinary).toHaveBeenLastCalledWith('/billing/credit-notes/8/pdf');
    await downloadNoteApi('debit', 9, 'xml');
    expect(downloadBillingBinary).toHaveBeenLastCalledWith('/billing/debit-notes/9/xml');
    await retryNoteApi('debit', 9);
    expect(fetchApi).toHaveBeenLastCalledWith('/billing/debit-notes/9/retry', { method: 'POST' });
  });

  it('rejects unsupported paths before making a request', async () => {
    await expect(listNotesApi('invoice')).rejects.toThrow('Tipo de nota no válido');
    await expect(downloadNoteApi('credit', 1, 'html')).rejects.toThrow('Formato de archivo no válido');
    expect(fetchApi).not.toHaveBeenCalled();
    expect(downloadBillingBinary).not.toHaveBeenCalled();
  });
});
