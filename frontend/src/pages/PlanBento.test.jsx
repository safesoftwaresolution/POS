import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PlanBento from './PlanBento';
import { getMyPlansApi } from '../services/storesApi';
import { getUserQuotaApi } from '../services/usersApi';

vi.mock('../services/storesApi', () => ({ getMyPlansApi: vi.fn() }));
vi.mock('../services/usersApi', () => ({ getUserQuotaApi: vi.fn() }));

const plans = [
  { code: 'BASIC', name: 'Básico', maxAdditionalUsers: 1, maxActiveUsers: 2, monthlyPrice: 49900, yearlyPrice: null },
  { code: 'PRO', name: 'Pro', maxAdditionalUsers: 5, maxActiveUsers: 6, monthlyPrice: null, yearlyPrice: 0 },
];

const renderPage = () => render(<MemoryRouter><PlanBento /></MemoryRouter>);

beforeEach(() => {
  vi.clearAllMocks();
  getMyPlansApi.mockResolvedValue({ currentPlanCode: 'BASIC', plans });
  getUserQuotaApi.mockResolvedValue({ planName: 'Básico', activeUsers: 1, maxActiveUsers: 2, canCreateMore: true });
});
afterEach(cleanup);

it('renders every plan with its limit and prices', async () => {
  renderPage();
  expect(await screen.findByText('Pro')).toBeTruthy();
  expect(screen.getByText('Básico', { selector: 'h3' })).toBeTruthy();
  expect(screen.getByText('Hasta 2 usuarios activos (administrador incluido)')).toBeTruthy();
  expect(screen.getByText('Hasta 6 usuarios activos (administrador incluido)')).toBeTruthy();
  expect(screen.getByText('$49.900')).toBeTruthy();
  expect(screen.getByText('Gratis')).toBeTruthy();
});

it('shows "Precio por definir" for a null price and no suffix next to it', async () => {
  renderPage();
  await screen.findByText('Pro');
  expect(screen.getAllByText('Precio por definir')).toHaveLength(2);
  expect(screen.getAllByText('/mes')).toHaveLength(1);
  expect(screen.queryAllByText('/año')).toHaveLength(0);
});

it('marks only the current plan with "Plan actual"', async () => {
  renderPage();
  await screen.findByText('Pro');
  expect(screen.getAllByText('Plan actual')).toHaveLength(1);
  expect(screen.getByText('Básico', { selector: 'h3' }).closest('div[data-plan]').getAttribute('data-plan')).toBe('BASIC');
  expect(screen.getByText('Plan actual').closest('div[data-plan]').getAttribute('data-plan')).toBe('BASIC');
});

it('shows the usage from the quota', async () => {
  renderPage();
  expect(await screen.findByText('1 de 2 usuarios activos')).toBeTruthy();
});

it('still renders the catalog when the store has no quota', async () => {
  getUserQuotaApi.mockResolvedValue(null);
  renderPage();
  expect(await screen.findByText('Pro')).toBeTruthy();
  expect(screen.queryByText(/usuarios activos$/)).toBeNull();
});

it('still renders the catalog when the quota call fails', async () => {
  getUserQuotaApi.mockRejectedValue(new Error('boom'));
  renderPage();
  expect(await screen.findByText('Pro')).toBeTruthy();
});

it('shows the error when the catalog cannot be loaded', async () => {
  getMyPlansApi.mockRejectedValue(new Error('Sin acceso'));
  renderPage();
  expect(await screen.findByText('Sin acceso')).toBeTruthy();
});
