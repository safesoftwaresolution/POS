import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import POSBento from './POSBento';
import { getAllProductsApi } from '../services/productsApi';
import { getCustomersApi } from '../services/customersApi';
import { createSaleApi } from '../services/salesApi';
import { getBillingSummaryApi } from '../services/billingApi';
import { agentTurnApi } from '../services/voiceSalesApi';

vi.mock('../services/productsApi', () => ({ getAllProductsApi: vi.fn() }));
vi.mock('../services/servicesApi', () => ({ getServicesApi: vi.fn(async () => []) }));
vi.mock('../services/customersApi', () => ({ getCustomersApi: vi.fn() }));
vi.mock('../services/categoriesApi', () => ({ getCategoriesApi: vi.fn(async () => []) }));
vi.mock('../services/parametersApi', () => ({
  getParameterValuesApi: vi.fn(async () => [
    { code: 'CASH', label: 'Efectivo', active: true },
    { code: 'NEQUI', label: 'Nequi', active: true },
  ]),
}));
vi.mock('../services/billingApi', () => ({ getBillingSummaryApi: vi.fn(), getSaleInvoiceApi: vi.fn() }));
vi.mock('../services/salesApi', () => ({ createSaleApi: vi.fn() }));
vi.mock('../services/voiceSalesApi', () => ({
  agentTurnApi: vi.fn(), transcribeVoiceApi: vi.fn(), isVoiceSalesDisabled: vi.fn(() => false),
}));
vi.mock('../hooks/useModal', () => ({ default: () => ({ notify: vi.fn(), confirm: vi.fn() }) }));

const CAFE = { id: 11, name: 'Café grande', internalCode: 'CAFE', salePrice: 3500, quantityAvailable: 40, categoryName: 'Bebidas' };
const GENERAL = { id: 1, fullName: 'Cliente General', identification: '9999999999' };
const LUIS = { id: 43, fullName: 'Luis Gómez', identification: '5555555555' };

const turn = (ticketOverrides = {}) => ({
  reply: 'Listo: 2 cafés grandes, pago con Nequi. ¿Confirmas?',
  warnings: [],
  ticket: {
    lines: [{ kind: 'PRODUCT', id: 11, code: 'CAFE', name: 'Café grande', unitPrice: 3500, quantity: 2 }],
    customer: { id: 1, name: 'Cliente General' },
    paymentMethod: { code: 'NEQUI', label: 'Nequi' },
    cashReceived: null, subtotal: 7000, confirmationRequested: true,
    ...ticketOverrides,
  },
});

beforeEach(() => {
  vi.mocked(getAllProductsApi).mockResolvedValue([CAFE]);
  vi.mocked(getCustomersApi).mockResolvedValue([GENERAL, LUIS]);
  vi.mocked(getBillingSummaryApi).mockResolvedValue({ active: true }); // la tienda SÍ factura electrónicamente
  vi.mocked(createSaleApi).mockResolvedValue({ id: 99, invoiceNumber: 'V-99' });
  vi.mocked(agentTurnApi).mockResolvedValue(turn());
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const assistantInput = () => screen.getByLabelText(/escríbele al asistente/i);
const confirmation = () => screen.queryByRole('group', { name: /confirmar venta/i });

async function dictate(text = 'dos cafés grandes, paga con nequi y cobra') {
  render(<POSBento />);
  await screen.findAllByText('Café grande'); // el catálogo ya cargó
  fireEvent.change(assistantInput(), { target: { value: text } });
  fireEvent.click(screen.getByRole('button', { name: /enviar al asistente/i }));
  await screen.findByRole('group', { name: /confirmar venta/i });
}

describe('POS with the voice assistant', () => {
  it('fills the ticket panel in place and asks for confirmation inline, without registering anything', async () => {
    await dictate();

    const ticket = screen.getByText('Ticket de Venta').closest('div.flex-col');
    expect(ticket.textContent).toContain('Café grande');
    expect(ticket.textContent).toContain('$7.000');
    expect(confirmation().textContent).toContain('Nequi');
    expect(confirmation().textContent).toContain('Cliente General');
    expect(screen.getByText(/listo: 2 cafés grandes/i)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /cobrar \[f4\]/i })).toBeNull(); // la confirmación ocupa su lugar
    expect(createSaleApi).not.toHaveBeenCalled();
    // El servidor recibió un ticket vacío, con el cliente general por defecto y solo ids.
    expect(agentTurnApi).toHaveBeenCalledWith(expect.objectContaining({
      text: 'dos cafés grandes, paga con nequi y cobra',
      ticket: { lines: [], customerId: 1, paymentMethodCode: 'CASH', cashReceived: null },
    }));
  });

  it('registers a normal sale on confirm: never electronic, even if the store invoices electronically', async () => {
    await dictate();

    fireEvent.click(screen.getByRole('button', { name: /confirmar y registrar/i }));

    await waitFor(() => expect(createSaleApi).toHaveBeenCalledTimes(1));
    const [command, idempotencyKey] = vi.mocked(createSaleApi).mock.calls[0];
    expect(command).toMatchObject({
      customerId: 1,
      items: [{ productId: 11, serviceId: null, quantity: 2, unitPrice: 3500 }],
      cashReceived: 7000,
      totalAmount: 7000,
      sendElectronicInvoice: false,
      paymentMethod: 'NEQUI',
    });
    expect(idempotencyKey).toBeTruthy();
    await waitFor(() => expect(confirmation()).toBeNull());
    expect(screen.queryByText(/ítems? agregados?/i)?.textContent).toMatch(/^0 /);
  });

  it('withdraws the confirmation as soon as the ticket changes', async () => {
    await dictate();

    fireEvent.click(screen.getByRole('button', { name: /vaciar/i }));

    await waitFor(() => expect(confirmation()).toBeNull());
    expect(createSaleApi).not.toHaveBeenCalled();
  });

  it('goes back to editing without registering when the cashier cancels', async () => {
    await dictate();

    fireEvent.click(screen.getByRole('button', { name: /seguir editando/i }));

    await waitFor(() => expect(confirmation()).toBeNull());
    expect(screen.getByRole('button', { name: /cobrar \[f4\]/i })).toBeTruthy();
    expect(screen.getByText('Ticket de Venta').closest('div.flex-col').textContent).toContain('Café grande'); // el ticket queda como estaba
    expect(createSaleApi).not.toHaveBeenCalled();
  });

  it('uses the dictated customer when it is in the local list', async () => {
    vi.mocked(agentTurnApi).mockResolvedValue(turn({ customer: { id: 43, name: 'Luis Gómez' } }));
    await dictate('dos cafés para Luis Gómez');

    expect(confirmation().textContent).toContain('Luis Gómez');
    fireEvent.click(screen.getByRole('button', { name: /confirmar y registrar/i }));
    await waitFor(() => expect(createSaleApi).toHaveBeenCalled());
    expect(vi.mocked(createSaleApi).mock.calls[0][0].customerId).toBe(43);
  });

  it('does not allow confirming a cash payment that does not cover the total', async () => {
    vi.mocked(agentTurnApi).mockResolvedValue(
      turn({ paymentMethod: { code: 'CASH', label: 'Efectivo' }, cashReceived: 5000 }),
    );
    await dictate('dos cafés, efectivo, recibo cinco mil');

    const button = screen.getByRole('button', { name: /confirmar y registrar/i });
    expect(button.disabled).toBe(true);
    expect(confirmation().textContent).toMatch(/no cubre el total/i);
    fireEvent.click(button);
    expect(createSaleApi).not.toHaveBeenCalled();
  });

  it('keeps the manual flow intact when the assistant fails', async () => {
    vi.mocked(agentTurnApi).mockRejectedValue(Object.assign(new Error('El asistente no está disponible ahora.'), { kind: 'unavailable' }));
    render(<POSBento />);
    await screen.findAllByText('Café grande');

    fireEvent.change(assistantInput(), { target: { value: 'hola' } });
    fireEvent.click(screen.getByRole('button', { name: /enviar al asistente/i }));

    await screen.findByRole('alert');
    expect(screen.getByRole('alert').textContent).toMatch(/no está disponible/i);
    expect(screen.getByRole('button', { name: /cobrar \[f4\]/i })).toBeTruthy();
    expect(confirmation()).toBeNull();
  });
});
