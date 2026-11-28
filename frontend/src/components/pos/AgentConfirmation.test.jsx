import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import AgentConfirmation from './AgentConfirmation';

afterEach(cleanup);

const summary = (overrides = {}) => ({
  itemCount: 3, total: 9000, customerName: 'Cliente General', paymentLabel: 'Nequi', onCredit: false,
  cashReceived: null, change: 0, cashShort: false, ...overrides,
});

const setup = (props = {}) => {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(<AgentConfirmation summary={summary()} submitting={false} onConfirm={onConfirm} onCancel={onCancel} {...props} />);
  return { onConfirm, onCancel };
};

const group = () => screen.getByRole('group', { name: /confirmar venta/i }).textContent;
const confirmButton = () => screen.getByRole('button', { name: /confirmar y registrar/i });

describe('AgentConfirmation', () => {
  it('repeats what is about to be registered and says nothing is registered yet', () => {
    setup();

    expect(group()).toContain('¿Registro esta venta?');
    expect(group()).toMatch(/hasta que confirmes no se registra nada/i);
    expect(group()).toContain('Cliente General');
    expect(group()).toContain('Nequi');
    expect(group()).toContain('$9.000 COP');
    expect(group()).not.toMatch(/efectivo recibido/i);
  });

  it('confirms or goes back to editing with the two buttons', () => {
    const { onConfirm, onCancel } = setup();

    fireEvent.click(confirmButton());
    fireEvent.click(screen.getByRole('button', { name: /seguir editando/i }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('shows the cash received and the change for a cash payment', () => {
    setup({ summary: summary({ paymentLabel: 'Efectivo', cashReceived: 50000, change: 41000, total: 9000 }) });

    expect(group()).toContain('$50.000');
    expect(group()).toContain('Cambio');
    expect(group()).toContain('$41.000');
  });

  it('blocks the confirmation and explains why when the cash does not cover the total', () => {
    const { onConfirm } = setup({ summary: summary({ paymentLabel: 'Efectivo', cashReceived: 5000, cashShort: true }) });

    expect(screen.getByRole('alert').textContent).toMatch(/no cubre el total/i);
    expect(confirmButton().disabled).toBe(true);
    fireEvent.click(confirmButton());
    expect(onConfirm).not.toHaveBeenCalled();
    expect(group()).not.toContain('Cambio');
  });

  it('labels a credit sale and disables both buttons while the sale is being registered', () => {
    setup({ summary: summary({ paymentLabel: 'Crédito', onCredit: true }), submitting: true });

    expect(group()).toContain('Crédito (a crédito)');
    expect(confirmButton().disabled).toBe(true);
    expect(screen.getByRole('button', { name: /seguir editando/i }).disabled).toBe(true);
  });
});
