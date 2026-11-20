import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import BranchSwitcher from './BranchSwitcher';
import { switchStoreApi } from '../../services/businessApi';

const state = vi.hoisted(() => ({ business: null, activeStoreId: 1 }));
const setSessionFromResponse = vi.hoisted(() => vi.fn());
const notify = vi.hoisted(() => vi.fn());

vi.mock('../../services/businessApi', () => ({ switchStoreApi: vi.fn() }));
vi.mock('../../hooks/useBusiness', () => ({ default: () => ({ business: state.business, activeStoreId: state.activeStoreId }) }));
vi.mock('../../hooks/useAuth', () => ({ default: () => ({ setSessionFromResponse }) }));
vi.mock('../../hooks/useModal', () => ({ default: () => ({ notify }) }));

const branches = [
  { storeId: 1, name: 'Principal', status: 'ACTIVE', main: true },
  { storeId: 2, name: 'Norte', status: 'PENDING_VERIFICATION', main: false },
];

beforeEach(() => {
  vi.clearAllMocks();
  state.business = { id: 7, name: 'Mi Negocio', branches };
  state.activeStoreId = 1;
  // jsdom no permite reemplazar location.assign: se sustituye por un espía.
  Object.defineProperty(window, 'location', { value: { assign: vi.fn() }, writable: true, configurable: true });
});
afterEach(cleanup);

it('renders nothing for a store without a business or with a single branch', () => {
  state.business = null;
  const { container, rerender } = render(<BranchSwitcher />);
  expect(container.firstChild).toBeNull();

  state.business = { id: 7, name: 'Solo', branches: [branches[0]] };
  rerender(<BranchSwitcher />);
  expect(container.firstChild).toBeNull();
});

it('shows the active branch and lists all of them with the status of those that do not operate', () => {
  render(<BranchSwitcher />);
  expect(screen.getByRole('button', { name: /Principal/ })).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: /Principal/ }));
  expect(screen.getAllByRole('option')).toHaveLength(2);
  expect(screen.getByText('En revisión')).toBeTruthy();
});

it('switches branch: applies the new session and reloads from the start', async () => {
  const session = { token: 't', storeId: 2, username: 'dueno', role: 'ADMINISTRATOR' };
  switchStoreApi.mockResolvedValue(session);
  render(<BranchSwitcher />);

  fireEvent.click(screen.getByRole('button', { name: /Principal/ }));
  fireEvent.click(screen.getByText('Norte'));

  await waitFor(() => expect(window.location.assign).toHaveBeenCalledWith('/'));
  expect(switchStoreApi).toHaveBeenCalledWith(2);
  expect(setSessionFromResponse).toHaveBeenCalledWith(session);
});

it('choosing the branch you are already on does nothing', () => {
  render(<BranchSwitcher />);
  fireEvent.click(screen.getByRole('button', { name: /Principal/ }));
  fireEvent.click(screen.getAllByText('Principal')[1]);

  expect(switchStoreApi).not.toHaveBeenCalled();
  expect(screen.queryByRole('listbox')).toBeNull();
});

it('keeps the current session and tells the user when the switch is refused', async () => {
  switchStoreApi.mockRejectedValue(new Error('No tienes acceso a esa sucursal.'));
  render(<BranchSwitcher />);

  fireEvent.click(screen.getByRole('button', { name: /Principal/ }));
  fireEvent.click(screen.getByText('Norte'));

  await waitFor(() => expect(notify).toHaveBeenCalledWith('No tienes acceso a esa sucursal.'));
  expect(setSessionFromResponse).not.toHaveBeenCalled();
  expect(window.location.assign).not.toHaveBeenCalled();
});
