import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import NotesTab from './NotesTab';
import { downloadNoteApi, listNotesApi, retryNoteApi } from '../../services/billingNotesApi';
import { deliverBlob } from '../../utils/downloadBlob';

vi.mock('../../services/billingNotesApi', () => ({ listNotesApi: vi.fn(), retryNoteApi: vi.fn(), downloadNoteApi: vi.fn() }));
vi.mock('../../utils/downloadBlob', () => ({ deliverBlob: vi.fn() }));
vi.mock('../../hooks/useModal', () => ({ default: () => ({ notify: vi.fn() }) }));

const notes = [
  { id: 1, saleId: 10, billNumber: 'SETP10', creditNoteNumber: 'NC1', status: 'VALIDATED', environment: 'SANDBOX', cude: 'cude-1' },
  { id: 2, saleId: 11, billNumber: 'SETP11', referenceCode: 'pending-2', status: 'PENDING', errorMessage: 'Esperando respuesta DIAN' },
  { id: 3, saleId: 12, billNumber: 'SETP12', referenceCode: 'rejected-3', status: 'REJECTED', errorMessage: 'NIT incorrecto' },
];

beforeEach(() => {
  vi.clearAllMocks();
  listNotesApi.mockResolvedValue({ content: notes, totalPages: 1, totalElements: 3 });
});
afterEach(cleanup);

it('lets sellers inspect status, sandbox and download documents without mutation controls', async () => {
  const blob = new Blob(['pdf']);
  downloadNoteApi.mockResolvedValue(blob);
  render(<NotesTab role="SELLER" refreshKey={0} />);
  await screen.findByText('NC1');
  expect(screen.getByText('Prueba')).toBeTruthy();
  expect(screen.getByText('Esperando respuesta DIAN')).toBeTruthy();
  expect(screen.getByText('NIT incorrecto')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Crear nota' })).toBeNull();
  expect(screen.queryByRole('button', { name: /Reintentar/ })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Descargar PDF NC1' }));
  await waitFor(() => expect(deliverBlob).toHaveBeenCalledWith(blob, 'nota-NC1.pdf', { openInTab: true }));
});

it('lets supervisors retry pending notes and switch credit/debit lists', async () => {
  render(<NotesTab role="SUPERVISOR" refreshKey={0} />);
  await screen.findByText('NC1');
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar pending-2' }));
  await waitFor(() => expect(retryNoteApi).toHaveBeenCalledWith('credit', 2));
  expect(screen.queryByRole('button', { name: 'Reintentar rejected-3' })).toBeNull();
  fireEvent.change(screen.getByLabelText('Tipo de nota'), { target: { value: 'debit' } });
  await waitFor(() => expect(listNotesApi).toHaveBeenLastCalledWith('debit', '', 0, 20));
});
