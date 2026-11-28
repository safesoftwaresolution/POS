import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import PlansBento from './PlansBento';
import { getPlansApi, updatePlanPricesApi } from '../../services/backoffice/storesApi';

vi.mock('../../services/backoffice/storesApi', () => ({ getPlansApi: vi.fn(), updatePlanPricesApi: vi.fn() }));

const plans = [
  { code: 'BASIC', name: 'Básico', maxAdditionalUsers: 1, maxActiveUsers: 2, monthlyPrice: null, yearlyPrice: 500000 },
];

beforeEach(() => {
  vi.clearAllMocks();
  getPlansApi.mockResolvedValue(plans);
  updatePlanPricesApi.mockResolvedValue({ ...plans[0], monthlyPrice: 49900, yearlyPrice: null });
});
afterEach(cleanup);

const openEditor = async () => {
  render(<PlansBento />);
  fireEvent.click(await screen.findByText('Editar precios'));
  const inputs = await screen.findAllByRole('textbox');
  return { monthly: inputs[0], yearly: inputs[1] };
};

it('lists the plans with their prices', async () => {
  render(<PlansBento />);
  expect(await screen.findByText('Básico')).toBeTruthy();
  expect(screen.getByText('BASIC')).toBeTruthy();
  expect(screen.getByText('Precio por definir')).toBeTruthy();
  expect(screen.getByText('$500.000')).toBeTruthy();
});

it('saves an empty yearly price as null', async () => {
  const { monthly, yearly } = await openEditor();
  expect(yearly.value).toBe('500.000');
  fireEvent.change(monthly, { target: { value: '49900' } });
  fireEvent.change(yearly, { target: { value: '' } });
  fireEvent.click(screen.getByText('Guardar'));
  await waitFor(() => expect(updatePlanPricesApi).toHaveBeenCalledWith('BASIC', { monthlyPrice: 49900, yearlyPrice: null }));
  await waitFor(() => expect(screen.queryByText('Guardar')).toBeNull());
  expect(getPlansApi).toHaveBeenCalledTimes(2);
});

it('shows the backend error inside the modal when saving fails', async () => {
  updatePlanPricesApi.mockRejectedValue(new Error('El precio mensual no puede ser negativo'));
  const { monthly } = await openEditor();
  fireEvent.change(monthly, { target: { value: '100' } });
  fireEvent.click(screen.getByText('Guardar'));
  expect(await screen.findByText('El precio mensual no puede ser negativo')).toBeTruthy();
  expect(screen.getByText('Guardar')).toBeTruthy();
});
