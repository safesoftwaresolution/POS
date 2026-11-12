import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import BillingBento from './BillingBento';
import {
  getBackofficeStoreBillingApi,
  getStoreBillingSetupApi,
  listBackofficeBillingByStatusApi,
  transitionBackofficeBillingStatusApi,
} from '../../services/backoffice/billingApi';
import { getBackofficeStoresApi } from '../../services/backoffice/storesApi';

vi.mock('../../services/backoffice/billingApi', () => ({
  listBackofficeBillingByStatusApi: vi.fn(),
  getBackofficeStoreBillingApi: vi.fn(),
  getStoreBillingSetupApi: vi.fn(),
  updateBackofficeBillingCompanyIdApi: vi.fn(),
  testBackofficeBillingConnectionApi: vi.fn(),
  switchBackofficeBillingEnvironmentApi: vi.fn(),
  transitionBackofficeBillingStatusApi: vi.fn(),
}));
vi.mock('../../services/backoffice/storesApi', () => ({ getBackofficeStoresApi: vi.fn() }));
vi.mock('../../hooks/useModal', () => ({
  default: () => ({ notify: vi.fn(), confirm: vi.fn().mockResolvedValue(true) }),
}));

const WAITING = 'Falta traer la resolución de numeración.';
const READY = 'Ya puedes emitir facturas.';
const step = (key, state, message = '') => ({ key, state, message });
const inSetup = { storeId: 5, status: 'IN_SETUP', environment: 'PRODUCTION', sandboxConfigured: false, productionConfigured: true };
const inSetupSteps = {
  profile: { saved: false },
  steps: [
    step('FISCAL_DATA', 'DONE'),
    step('DIAN_ENABLEMENT', 'DONE'),
    step('NUMBERING', 'CURRENT', WAITING),
    step('READY', 'PENDING'),
  ],
};
const activeSteps = {
  profile: { saved: false },
  steps: [
    step('FISCAL_DATA', 'DONE'),
    step('DIAN_ENABLEMENT', 'DONE'),
    step('NUMBERING', 'DONE'),
    step('READY', 'DONE', READY),
  ],
};

/** La sección de la etapa del detalle (el filtro de la lista también tiene un botón «Activo»). */
const stageSection = async () => (await screen.findByText('Etapa del proceso')).closest('div');

beforeEach(() => {
  vi.clearAllMocks();
  listBackofficeBillingByStatusApi.mockResolvedValue([inSetup]);
  getBackofficeStoresApi.mockResolvedValue({ content: [{ id: 5, name: 'Tienda La Esquina' }] });
  getBackofficeStoreBillingApi.mockResolvedValue(inSetup);
  getStoreBillingSetupApi.mockResolvedValue(inSetupSteps);
});
afterEach(cleanup);

it('shows the new setup steps of the store after its stage is changed, without reopening the detail', async () => {
  transitionBackofficeBillingStatusApi.mockResolvedValue({ ...inSetup, status: 'ACTIVE' });
  render(<BillingBento />);

  fireEvent.click(await screen.findByRole('button', { name: 'Gestionar' }));
  await screen.findAllByText(WAITING);

  getStoreBillingSetupApi.mockResolvedValue(activeSteps);
  fireEvent.click(within(await stageSection()).getByRole('button', { name: 'Activo' }));

  await waitFor(() => expect(transitionBackofficeBillingStatusApi).toHaveBeenCalledWith(5, 'ACTIVE', null));
  await screen.findAllByText(READY);
  expect(screen.queryAllByText(WAITING)).toHaveLength(0);
  expect(getStoreBillingSetupApi).toHaveBeenCalledTimes(2);
});

it('keeps the steps already shown when refreshing them fails after a change', async () => {
  transitionBackofficeBillingStatusApi.mockResolvedValue({ ...inSetup, status: 'ACTIVE' });
  render(<BillingBento />);

  fireEvent.click(await screen.findByRole('button', { name: 'Gestionar' }));
  await screen.findAllByText(WAITING);

  getStoreBillingSetupApi.mockRejectedValue(new Error('sin conexión'));
  fireEvent.click(within(await stageSection()).getByRole('button', { name: 'Activo' }));

  await waitFor(() => expect(transitionBackofficeBillingStatusApi).toHaveBeenCalled());
  await waitFor(() => expect(getStoreBillingSetupApi).toHaveBeenCalledTimes(2));
  expect(screen.queryAllByText(WAITING).length).toBeGreaterThan(0);
});
