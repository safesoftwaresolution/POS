import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import BillingSetupWizard from './BillingSetupWizard';
import { getBillingSetupApi, importNumberingApi } from '../../services/billingApi';

vi.mock('../../services/billingApi', () => ({
  getBillingSetupApi: vi.fn(),
  importNumberingApi: vi.fn(),
  saveFiscalProfileApi: vi.fn(),
  startDianEnablementApi: vi.fn(),
  getDivipolaApi: vi.fn(),
}));

const step = (key, state, message = '') => ({ key, state, message });

const waitingForNumbering = {
  status: 'IN_SETUP',
  environment: 'PRODUCTION',
  platformReady: true,
  profile: null,
  invoiceRangesCount: 0,
  steps: [
    step('FISCAL_DATA', 'DONE'),
    step('DIAN_ENABLEMENT', 'DONE'),
    step('NUMBERING', 'CURRENT', 'Trae tu resolución de numeración.'),
    step('READY', 'PENDING'),
  ],
};

const active = {
  ...waitingForNumbering,
  status: 'ACTIVE',
  invoiceRangesCount: 1,
  steps: [
    step('FISCAL_DATA', 'DONE'),
    step('DIAN_ENABLEMENT', 'DONE'),
    step('NUMBERING', 'DONE'),
    step('READY', 'DONE', 'Ya puedes emitir facturas.'),
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  getBillingSetupApi.mockResolvedValue(waitingForNumbering);
});
afterEach(cleanup);

const renderWizard = () => render(
  <MemoryRouter><BillingSetupWizard canEdit refreshKey={0} onChanged={vi.fn()} /></MemoryRouter>,
);

it('keeps the import summary visible when the import activates the store and the wizard jumps to the ready step', async () => {
  importNumberingApi.mockResolvedValue({ importedCount: 1, noteRangesCreated: 2, skipped: [], setup: active });
  renderWizard();

  fireEvent.click(await screen.findByRole('button', { name: /Traer mis rangos de la DIAN/ }));

  await screen.findByText(/Tu facturación electrónica está activa/);
  expect(screen.getByText(/Trajimos 1 resolución de facturas y creamos 2 rangos de notas/)).toBeTruthy();
});

it('lists what the import skipped next to the summary', async () => {
  importNumberingApi.mockResolvedValue({
    importedCount: 1, noteRangesCreated: 0, skipped: ['La resolución 18760000002 ya venció.'], setup: active,
  });
  renderWizard();

  fireEvent.click(await screen.findByRole('button', { name: /Traer mis rangos de la DIAN/ }));

  await screen.findByText('La resolución 18760000002 ya venció.');
});

it('keeps the summary on the numbering step when the store is not active yet', async () => {
  importNumberingApi.mockResolvedValue({ importedCount: 0, noteRangesCreated: 0, skipped: [], setup: waitingForNumbering });
  renderWizard();

  fireEvent.click(await screen.findByRole('button', { name: /Traer mis rangos de la DIAN/ }));

  await screen.findByText('No encontramos rangos nuevos para traer.');
});
