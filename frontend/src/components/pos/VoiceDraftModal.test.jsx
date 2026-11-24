import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import VoiceDraftModal from './VoiceDraftModal';
import VoiceDictationButton from './VoiceDictationButton';
import useVoiceRecorder from '../../hooks/useVoiceRecorder';
import { createSaleApi } from '../../services/salesApi';
import { isVoiceSalesDisabled, previewVoiceSaleApi } from '../../services/voiceSalesApi';

vi.mock('../../services/salesApi', () => ({ createSaleApi: vi.fn() }));
vi.mock('../../services/voiceSalesApi', () => ({ previewVoiceSaleApi: vi.fn(), isVoiceSalesDisabled: vi.fn(() => false) }));
vi.mock('../../hooks/useVoiceRecorder', () => ({ default: vi.fn() }));

// ---- contexto del POS ----
const CAFE = { id: 7, itemType: 'PRODUCT', name: 'Café grande', salePrice: 4500, quantityAvailable: 10 };
const LATTE = { id: 12, itemType: 'PRODUCT', name: 'Café latte', salePrice: 6000, quantityAvailable: 4 };
const AGUA = { id: 8, itemType: 'PRODUCT', name: 'Botella de agua', price: 2000, stock: 3 };
const productsList = [CAFE, LATTE, AGUA];
const GENERAL = { id: 1, fullName: 'Cliente General' };
const customersList = [GENERAL, { id: 14, fullName: 'Ana Pérez' }, { id: 22, fullName: 'Ana Pérez' }];
const paymentMethods = [
  { code: 'CASH', label: 'Efectivo', active: true },
  { code: 'CARD', label: 'Tarjeta', active: true },
];

const candidate = (kind, id, label, code = null) => ({ kind, id, code, label });
const reference = (mention, status, candidates = []) => ({ mention, status, candidates });
const NOT_PROVIDED = reference(null, 'NOT_PROVIDED');
const CASH_REF = reference('efectivo', 'RESOLVED', [candidate('PAYMENT_METHOD', 3, 'Efectivo', 'CASH')]);
const ANA = reference('Ana Pérez', 'AMBIGUOUS', [
  candidate('CUSTOMER', 14, 'Ana Pérez (••••1234; #14)'),
  candidate('CUSTOMER', 22, 'Ana Pérez (••••6789; #22)'),
]);

const draftOf = (overrides = {}) => ({
  items: [], customer: NOT_PROVIDED, paymentMethod: NOT_PROVIDED, cashReceived: null, pendingFields: [], ...overrides,
});

// «Dos cafés grandes y una botella de agua para Ana Pérez. Paga en efectivo con cincuenta mil»
const exampleDraft = () => draftOf({
  items: [
    { item: reference('cafés grandes', 'RESOLVED', [candidate('PRODUCT', 7, 'Café grande', 'CAF-G')]), quantity: 2 },
    { item: reference('botella de agua', 'RESOLVED', [candidate('PRODUCT', 8, 'Botella de agua', 'AGU')]), quantity: 1 },
  ],
  customer: ANA,
  paymentMethod: CASH_REF,
  cashReceived: 50000,
  pendingFields: ['customer'],
});

function renderModal(draft, overrides = {}) {
  const onApply = vi.fn();
  const onClose = vi.fn();
  render(
    <VoiceDraftModal
      draft={draft}
      productsList={productsList}
      customersList={customersList}
      paymentMethods={paymentMethods}
      cart={[]}
      customer={GENERAL}
      paymentMethod="CASH"
      onApply={onApply}
      onClose={onClose}
      {...overrides}
    />,
  );
  return { onApply, onClose };
}

const applyButton = () => screen.getByRole('button', { name: /aplicar al ticket/i });
const customerSelect = () => screen.getByRole('combobox', { name: /cliente de la venta/i });
const pickCustomer = (value) => fireEvent.change(customerSelect(), { target: { value } });

let fetchMock;
beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  vi.clearAllMocks();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('VoiceDraftModal', () => {
  it('shows what was heard, both Ana options and a pending customer; Apply stays disabled', () => {
    renderModal(exampleDraft());

    expect(screen.getByText(/cafés grandes/)).toBeTruthy();
    expect(screen.getByText(/Ana Pérez \(••••1234; #14\)/)).toBeTruthy();
    expect(screen.getByText(/Ana Pérez \(••••6789; #22\)/)).toBeTruthy();
    expect(customerSelect().value).toBe(''); // nunca se elige a una Ana por el cajero
    expect(applyButton().disabled).toBe(true);
    expect(screen.getByText(/no se registra ninguna venta/i)).toBeTruthy();
    expect(screen.getByRole('status').textContent).toMatch(/resuelve/i);
  });

  it('enables Apply after the cashier picks one Ana and hands the ticket to onApply without selling', () => {
    const { onApply } = renderModal(exampleDraft());

    pickCustomer('CUSTOMER-22');
    expect(applyButton().disabled).toBe(false);
    fireEvent.click(applyButton());

    expect(onApply).toHaveBeenCalledTimes(1);
    const ticket = onApply.mock.calls[0][0];
    expect(ticket.customer).toEqual({ id: 22, fullName: 'Ana Pérez' });
    expect(ticket.cart.map((l) => [l.id, l.quantity])).toEqual([[7, 2], [8, 1]]);
    expect(ticket.paymentMethod).toBe('CASH');
    expect(ticket.cashTendered).toBe('50000');
    // La venta solo la crea el cobro normal del POS.
    expect(createSaleApi).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('lets the cashier keep the current customer instead of choosing an Ana', () => {
    const { onApply } = renderModal(exampleDraft());

    pickCustomer('keep');
    fireEvent.click(applyButton());

    expect(onApply.mock.calls[0][0].customer).toBeNull();
  });

  it('keeps Apply disabled while an ambiguous item has no choice, then enables it once chosen', () => {
    const draft = draftOf({
      items: [{
        item: reference('café', 'AMBIGUOUS', [candidate('PRODUCT', 7, 'Café grande'), candidate('PRODUCT', 12, 'Café latte')]),
        quantity: 2,
      }],
    });
    const { onApply } = renderModal(draft);
    expect(applyButton().disabled).toBe(true);

    fireEvent.change(screen.getByRole('combobox', { name: /producto para «café»/i }), { target: { value: 'PRODUCT-12' } });

    expect(applyButton().disabled).toBe(false);
    fireEvent.click(applyButton());
    expect(onApply.mock.calls[0][0].cart).toEqual([{ ...LATTE, price: 6000, stock: 4, quantity: 2 }]);
  });

  it('requires the cashier to type a missing quantity and never assumes one', () => {
    const draft = draftOf({ items: [{ item: reference('café', 'RESOLVED', [candidate('PRODUCT', 7, 'Café grande')]), quantity: null }] });
    const { onApply } = renderModal(draft);
    const quantity = screen.getByRole('textbox', { name: /cantidad de «café»/i });
    expect(quantity.value).toBe('');
    expect(applyButton().disabled).toBe(true);

    fireEvent.change(quantity, { target: { value: '0' } });
    expect(applyButton().disabled).toBe(true);
    fireEvent.change(quantity, { target: { value: '3' } });
    expect(applyButton().disabled).toBe(false);

    fireEvent.click(applyButton());
    expect(onApply.mock.calls[0][0].cart[0].quantity).toBe(3);
  });

  it('shows a not-found item as dropped, with no way to pick it, and warns what was said', () => {
    const draft = draftOf({
      items: [
        { item: reference('café', 'RESOLVED', [candidate('PRODUCT', 7, 'Café grande')]), quantity: 1 },
        { item: reference('pizza hawaiana', 'NOT_FOUND'), quantity: 2 },
      ],
    });
    const { onApply } = renderModal(draft);

    expect(screen.getByText(/no encontrado/i)).toBeTruthy();
    expect(screen.queryByRole('combobox', { name: /producto para «pizza hawaiana»/i })).toBeNull();
    expect(screen.getByText(/No se encontró «pizza hawaiana»/)).toBeTruthy();
    expect(applyButton().disabled).toBe(false);

    fireEvent.click(applyButton());
    expect(onApply.mock.calls[0][0].cart.map((l) => l.id)).toEqual([7]);
  });

  it('drops and restores a line, and Apply follows the decision', () => {
    const draft = draftOf({
      items: [{
        item: reference('café', 'AMBIGUOUS', [candidate('PRODUCT', 7, 'Café grande'), candidate('PRODUCT', 12, 'Café latte')]),
        quantity: 1,
      }],
      paymentMethod: CASH_REF,
    });
    renderModal(draft);
    expect(applyButton().disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: /quitar «café»/i }));
    expect(applyButton().disabled).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: /restaurar «café»/i }));
    expect(applyButton().disabled).toBe(true);
  });

  it('keeps the ticket customer when none was mentioned or the one mentioned was not found, and says so', () => {
    renderModal(draftOf({
      items: [{ item: reference('café', 'RESOLVED', [candidate('PRODUCT', 7, 'Café grande')]), quantity: 1 }],
      customer: reference('Luis Gómez', 'NOT_FOUND'),
    }));

    expect(screen.queryByRole('combobox', { name: /cliente de la venta/i })).toBeNull();
    expect(screen.getByText(/No se encontró al cliente «Luis Gómez»; se mantiene Cliente General/)).toBeTruthy();
    expect(applyButton().disabled).toBe(false);
  });

  it('warns visibly when a dictated quantity is above the stock and applies the capped quantity', () => {
    const draft = draftOf({ items: [{ item: reference('agua', 'RESOLVED', [candidate('PRODUCT', 8, 'Botella de agua')]), quantity: 5 }] });
    const { onApply } = renderModal(draft);

    expect(screen.getByText(/Botella de agua: solo hay 3 disponibles; la cantidad quedó en 3/)).toBeTruthy();
    fireEvent.click(applyButton());
    expect(onApply.mock.calls[0][0].cart[0].quantity).toBe(3);
  });

  it('prefills the cash field only for cash and passes the edited amount on', () => {
    const { onApply } = renderModal(exampleDraft());
    pickCustomer('keep');

    const cash = screen.getByRole('textbox', { name: /efectivo recibido/i });
    expect(cash.value).toBe('50.000');
    fireEvent.change(cash, { target: { value: '60.000' } });
    fireEvent.click(applyButton());

    expect(onApply.mock.calls[0][0].cashTendered).toBe('60000');
  });

  it('shows no cash field when the dictated payment is not cash', () => {
    const card = reference('tarjeta', 'RESOLVED', [candidate('PAYMENT_METHOD', 4, 'Tarjeta', 'CARD')]);
    const { onApply } = renderModal(draftOf({
      items: [{ item: reference('café', 'RESOLVED', [candidate('PRODUCT', 7, 'Café grande')]), quantity: 2 }],
      paymentMethod: card,
      cashReceived: 50000,
    }));

    expect(screen.queryByRole('textbox', { name: /efectivo recibido/i })).toBeNull();
    fireEvent.click(applyButton());
    const ticket = onApply.mock.calls[0][0];
    expect(ticket.paymentMethod).toBe('CARD');
    expect(ticket.cashTendered).toBe('9000');
  });

  it('does not offer a payment method that is not active and keeps the ticket one', () => {
    const nequi = reference('nequi', 'RESOLVED', [candidate('PAYMENT_METHOD', 5, 'Nequi', 'NEQUI')]);
    const { onApply } = renderModal(draftOf({
      items: [{ item: reference('café', 'RESOLVED', [candidate('PRODUCT', 7, 'Café grande')]), quantity: 1 }],
      paymentMethod: nequi,
    }));

    expect(screen.queryByRole('combobox', { name: /medio de pago/i })).toBeNull();
    expect(screen.getByText(/El medio de pago «nequi» no está disponible; se mantiene Efectivo/)).toBeTruthy();
    fireEvent.click(applyButton());
    expect(onApply.mock.calls[0][0].paymentMethod).toBeNull();
  });

  it('cancels without applying, and Escape does not discard the draft', () => {
    const { onApply, onClose } = renderModal(exampleDraft());

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /cancelar/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onApply).not.toHaveBeenCalled();
    expect(createSaleApi).not.toHaveBeenCalled();
  });
});

// ---- botón de dictado: une la grabadora, la API y el borrador (la grabadora y la API ya tienen sus pruebas) ----
describe('VoiceDictationButton', () => {
  let onRecorded;
  let recorder;
  const blob = new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' });

  const mountButton = () => {
    const onDraft = vi.fn();
    render(<VoiceDictationButton onDraft={onDraft} />);
    return onDraft;
  };
  const record = (audio = blob) => act(async () => { await onRecorded(audio); });
  const alert = () => screen.queryByRole('alert');

  beforeEach(() => {
    recorder = {
      supported: true, unsupportedMessage: '', status: 'idle', elapsed: 0, error: '',
      start: vi.fn(), stop: vi.fn(), clearError: vi.fn(),
    };
    useVoiceRecorder.mockImplementation((options) => {
      onRecorded = options.onRecorded;
      return recorder;
    });
    isVoiceSalesDisabled.mockReturnValue(false);
    previewVoiceSaleApi.mockReset();
  });

  it('starts recording on the first tap and stops on the second', () => {
    mountButton();
    fireEvent.click(screen.getByRole('button', { name: /dictar venta/i }));
    expect(recorder.start).toHaveBeenCalledTimes(1);

    cleanup();
    recorder.status = 'recording';
    recorder.elapsed = 12;
    mountButton();
    fireEvent.click(screen.getByRole('button', { name: /detener dictado, 0:12/i }));
    expect(recorder.stop).toHaveBeenCalledTimes(1);
  });

  it('sends the recording to the API and hands the draft over, without selling anything', async () => {
    const draft = draftOf();
    previewVoiceSaleApi.mockResolvedValue(draft);
    const onDraft = mountButton();

    await record();

    expect(previewVoiceSaleApi).toHaveBeenCalledWith(blob);
    expect(onDraft).toHaveBeenCalledWith(draft);
    expect(alert()).toBeNull();
    expect(createSaleApi).not.toHaveBeenCalled();
  });

  it('shows the API message on a 429 or 503 and keeps the button usable for manual sales', async () => {
    previewVoiceSaleApi.mockRejectedValue(Object.assign(new Error('Se alcanzó el límite de dictados por minuto.'), { kind: 'quota', status: 429 }));
    const onDraft = mountButton();

    await record();

    expect(alert().textContent).toMatch(/límite de dictados/);
    expect(onDraft).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /dictar venta/i }).disabled).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: /cerrar aviso/i }));
    expect(alert()).toBeNull();
    expect(recorder.clearError).toHaveBeenCalled();
  });

  it('on a 404 explains it, disables the button and stops offering it once dismissed', async () => {
    previewVoiceSaleApi.mockRejectedValue(Object.assign(new Error('El dictado por voz no está habilitado en este servidor.'), { kind: 'disabled', status: 404 }));
    mountButton();

    await record();

    expect(alert().textContent).toMatch(/no está habilitado en este servidor/);
    expect(screen.getByRole('button', { name: /dictar venta/i }).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: /cerrar aviso/i }));
    expect(screen.queryByRole('button', { name: /dictar venta/i })).toBeNull();
  });

  it('is not offered when the server already answered 404 earlier in the session', () => {
    isVoiceSalesDisabled.mockReturnValue(true);
    mountButton();
    expect(screen.queryByRole('button', { name: /dictar venta/i })).toBeNull();
  });

  it('shows why the browser cannot record, and marks the button as unavailable', () => {
    recorder.supported = false;
    recorder.unsupportedMessage = 'Este navegador no puede grabar en formato WebM.';
    recorder.error = 'Este navegador no puede grabar en formato WebM.';
    mountButton();

    const button = screen.getByRole('button', { name: /dictar venta/i });
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.title).toMatch(/webm/i);
    expect(alert().textContent).toMatch(/webm/i);
  });
});
