import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import CreateNoteModal from './CreateNoteModal';
import { getSaleInvoiceApi } from '../../services/billingApi';
import { createCreditNoteApi, createDebitNoteApi } from '../../services/billingNotesApi';

vi.mock('../../services/billingApi', () => ({ getSaleInvoiceApi: vi.fn() }));
vi.mock('../../services/billingNotesApi', () => ({ createCreditNoteApi: vi.fn(), createDebitNoteApi: vi.fn() }));

beforeEach(() => {
  vi.clearAllMocks();
  getSaleInvoiceApi.mockResolvedValue({ saleId: 10, legalNumber: 'SETP10', status: 'VALIDATED', environment: 'SANDBOX' });
});
afterEach(cleanup);

async function chooseSale() {
  fireEvent.change(screen.getByLabelText('Venta asociada'), { target: { value: '10' } });
  fireEvent.click(screen.getByRole('button', { name: 'Consultar factura' }));
  await screen.findByText(/SETP10/);
}

it('requires a validated original invoice and warns that credit does not refund or restock', async () => {
  getSaleInvoiceApi.mockResolvedValue({ saleId: 10, status: 'PENDING' });
  render(<CreateNoteModal type="credit" onClose={vi.fn()} onCreated={vi.fn()} />);
  expect(screen.getByText(/no devuelve dinero ni modifica el inventario/)).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Venta asociada'), { target: { value: '10' } });
  fireEvent.click(screen.getByRole('button', { name: 'Consultar factura' }));
  await screen.findByText(/necesita una factura validada/);
  expect(screen.getByRole('button', { name: 'Emitir nota crédito' }).disabled).toBe(true);
  expect(createCreditNoteApi).not.toHaveBeenCalled();
});

it('keeps a debit request reference and its original values across an uncertain retry', async () => {
  createDebitNoteApi.mockRejectedValueOnce(new Error('Tiempo de espera agotado')).mockResolvedValueOnce({ id: 8, status: 'PENDING' });
  render(<CreateNoteModal type="debit" onClose={vi.fn()} onCreated={vi.fn()} />);
  await chooseSale();
  fireEvent.change(screen.getByLabelText('Descripción del ajuste'), { target: { value: 'Intereses' } });
  fireEvent.change(screen.getByLabelText('Precio unitario antes de impuestos (COP)'), { target: { value: '100.50' } });
  fireEvent.change(screen.getByLabelText('Motivo u observación'), { target: { value: 'Ajuste del periodo' } });
  fireEvent.submit(screen.getByRole('button', { name: 'Emitir nota débito' }).closest('form'));
  await screen.findByText('Tiempo de espera agotado');
  const first = createDebitNoteApi.mock.calls[0][1];
  expect(first.reference_code).toBeTruthy();
  expect(first.items[0].price).toBe('100.50');
  expect(screen.getByLabelText('Descripción del ajuste').disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar la misma solicitud' }));
  await waitFor(() => expect(createDebitNoteApi).toHaveBeenCalledTimes(2));
  expect(createDebitNoteApi.mock.calls[1][1]).toEqual(first);
  await screen.findByText(/Estado: Pendiente/);
});

it('submits a full-sale credit once even if the form is submitted again while waiting', async () => {
  let resolve;
  createCreditNoteApi.mockReturnValue(new Promise(done => { resolve = done; }));
  const onCreated = vi.fn();
  render(<CreateNoteModal type="credit" onClose={vi.fn()} onCreated={onCreated} />);
  await chooseSale();
  fireEvent.change(screen.getByLabelText('Motivo u observación'), { target: { value: 'Anulación de la venta' } });
  const form = screen.getByRole('button', { name: 'Emitir nota crédito' }).closest('form');
  fireEvent.submit(form);
  fireEvent.submit(form);
  expect(createCreditNoteApi).toHaveBeenCalledExactlyOnceWith(10, 'Anulación de la venta');
  resolve({ id: 5, status: 'VALIDATED' });
  await screen.findByText('Estado: Validada');
  expect(onCreated).toHaveBeenCalledExactlyOnceWith({ id: 5, status: 'VALIDATED' });
});

it('unlocks the form after a definite validation rejection so the values can be corrected', async () => {
  const rejected = Object.assign(new Error('El precio del ajuste no es válido'), { status: 400 });
  createDebitNoteApi.mockRejectedValueOnce(rejected).mockResolvedValueOnce({ id: 9, status: 'VALIDATED' });
  render(<CreateNoteModal type="debit" onClose={vi.fn()} onCreated={vi.fn()} />);
  await chooseSale();
  fireEvent.change(screen.getByLabelText('Descripción del ajuste'), { target: { value: 'Intereses' } });
  fireEvent.change(screen.getByLabelText('Precio unitario antes de impuestos (COP)'), { target: { value: '0.01' } });
  fireEvent.change(screen.getByLabelText('Motivo u observación'), { target: { value: 'Ajuste' } });
  fireEvent.submit(screen.getByRole('button', { name: 'Emitir nota débito' }).closest('form'));
  await screen.findByText('El precio del ajuste no es válido');
  const price = screen.getByLabelText('Precio unitario antes de impuestos (COP)');
  expect(price.disabled).toBe(false);
  fireEvent.change(price, { target: { value: '250' } });
  fireEvent.submit(screen.getByRole('button', { name: 'Emitir nota débito' }).closest('form'));
  await screen.findByText('Estado: Validada');
  const [first, second] = createDebitNoteApi.mock.calls.map(call => call[1]);
  expect(second.items[0].price).toBe('250');
  expect(second.reference_code).toBe(first.reference_code);
});

it('lets a credit note be retried after an uncertain failure without changing its request', async () => {
  createCreditNoteApi.mockRejectedValueOnce(new Error('Tiempo de espera agotado')).mockResolvedValueOnce({ id: 6, status: 'VALIDATED' });
  render(<CreateNoteModal type="credit" onClose={vi.fn()} onCreated={vi.fn()} />);
  await chooseSale();
  fireEvent.change(screen.getByLabelText('Motivo u observación'), { target: { value: 'Anulación' } });
  fireEvent.submit(screen.getByRole('button', { name: 'Emitir nota crédito' }).closest('form'));
  await screen.findByText('Tiempo de espera agotado');
  expect(screen.getByLabelText('Motivo u observación').disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar la misma solicitud' }));
  await screen.findByText('Estado: Validada');
  expect(createCreditNoteApi.mock.calls).toEqual([[10, 'Anulación'], [10, 'Anulación']]);
});
