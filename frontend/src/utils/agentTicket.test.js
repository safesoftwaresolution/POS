import { describe, expect, it } from 'vitest';
import { applyAgentTicket, ticketSignature, toAgentTicket } from './agentTicket';

const CAFE = { id: 11, name: 'Café grande', salePrice: 3500, quantityAvailable: 40, imageUrl: 'cafe.png' };
const CORTE = { id: 31, itemType: 'SERVICE', name: 'Corte', price: 15000 };
const GENERAL = { id: 1, fullName: 'Cliente General' };
const LUIS = { id: 43, fullName: 'Luis Gómez' };

const cartLine = (item, quantity, price) => ({ ...item, price, stock: 10, quantity });

const serverTicket = (overrides = {}) => ({
  lines: [], customer: { id: 1, name: 'Cliente General' }, paymentMethod: null, cashReceived: null,
  subtotal: 0, confirmationRequested: false, ...overrides,
});

describe('toAgentTicket', () => {
  it('sends only kind, id and quantity per line, never names or prices', () => {
    const ticket = toAgentTicket({
      cart: [cartLine(CAFE, 2, 3500), cartLine(CORTE, 1, 15000)],
      customer: LUIS, paymentMethod: 'NEQUI', cashTendered: '',
    });

    expect(ticket.lines).toEqual([
      { kind: 'PRODUCT', id: 11, quantity: 2 },
      { kind: 'SERVICE', id: 31, quantity: 1 },
    ]);
    expect(ticket.customerId).toBe(43);
    expect(ticket.paymentMethodCode).toBe('NEQUI');
    expect(JSON.stringify(ticket)).not.toMatch(/Café|3500|15000/);
  });

  it('sends cash only for cash payments with a real amount', () => {
    const base = { cart: [], customer: null };
    expect(toAgentTicket({ ...base, paymentMethod: 'CASH', cashTendered: '50000' }).cashReceived).toBe(50000);
    expect(toAgentTicket({ ...base, paymentMethod: 'CASH', cashTendered: '' }).cashReceived).toBeNull();
    expect(toAgentTicket({ ...base, paymentMethod: 'CASH', cashTendered: '0' }).cashReceived).toBeNull();
    expect(toAgentTicket({ ...base, paymentMethod: 'NEQUI', cashTendered: '7000' }).cashReceived).toBeNull();
    expect(toAgentTicket({ ...base, paymentMethod: null, cashTendered: '' }).paymentMethodCode).toBeNull();
    expect(toAgentTicket({ ...base, paymentMethod: 'CASH', cashTendered: '' }).customerId).toBeNull();
  });
});

describe('applyAgentTicket', () => {
  const lines = [
    { kind: 'PRODUCT', id: 11, code: 'P-11', name: 'Café grande', unitPrice: 3600, quantity: 2 },
    { kind: 'SERVICE', id: 31, code: 'S-31', name: 'Corte', unitPrice: 15000, quantity: 1 },
  ];

  it('keeps the local catalog object and takes the price and quantity the server says', () => {
    const result = applyAgentTicket({
      ticket: serverTicket({ lines }), productsList: [CAFE, CORTE], customersList: [GENERAL, LUIS],
    });

    expect(result.cart).toHaveLength(2);
    expect(result.cart[0]).toMatchObject({ id: 11, name: 'Café grande', imageUrl: 'cafe.png', price: 3600, quantity: 2, stock: 40 });
    expect(result.cart[1]).toMatchObject({ id: 31, itemType: 'SERVICE', price: 15000, quantity: 1, stock: Infinity });
    expect(result.total).toBe(2 * 3600 + 15000);
  });

  it('builds a minimal line when the local catalog does not have the item yet', () => {
    const result = applyAgentTicket({ ticket: serverTicket({ lines: [lines[0]] }), productsList: [], customersList: [] });

    expect(result.cart[0]).toMatchObject({ id: 11, name: 'Café grande', price: 3600, quantity: 2, stock: 2 });
  });

  it('does not mix a product and a service that share an id', () => {
    const sameId = { id: 11, itemType: 'SERVICE', name: 'Servicio 11', price: 9 };
    const result = applyAgentTicket({
      ticket: serverTicket({ lines: [lines[0]] }), productsList: [sameId, CAFE], customersList: [],
    });

    expect(result.cart[0].name).toBe('Café grande');
  });

  it('resolves the customer in the local list, or null so the POS keeps its own', () => {
    const named = serverTicket({ customer: { id: 43, name: 'Luis Gómez' } });
    expect(applyAgentTicket({ ticket: named, customersList: [GENERAL, LUIS] }).customer).toBe(LUIS);
    expect(applyAgentTicket({ ticket: named, customersList: [GENERAL] }).customer).toBeNull();
    expect(applyAgentTicket({ ticket: serverTicket({ customer: null }), customersList: [GENERAL] }).customer).toBeNull();
  });

  it('uses the dictated payment method, else the one the POS already had', () => {
    expect(applyAgentTicket({ ticket: serverTicket({ paymentMethod: { code: 'NEQUI', label: 'Nequi' } }) }).paymentMethod).toBe('NEQUI');
    expect(applyAgentTicket({ ticket: serverTicket(), currentPaymentMethod: 'CARD' }).paymentMethod).toBe('CARD');
    expect(applyAgentTicket({ ticket: serverTicket() }).paymentMethod).toBe('CASH');
  });

  it('pays exactly the total unless cash was dictated for a cash payment', () => {
    const paid = (overrides) => applyAgentTicket({
      ticket: serverTicket({ lines: [lines[0]], ...overrides }), productsList: [CAFE],
    }).cashTendered;

    expect(paid({})).toBe('7200');
    expect(paid({ paymentMethod: { code: 'CASH' }, cashReceived: 50000 })).toBe('50000');
    expect(paid({ paymentMethod: { code: 'NEQUI' }, cashReceived: 50000 })).toBe('7200');
    expect(paid({ paymentMethod: { code: 'CREDIT' } })).toBe('7200');
  });

  it('passes the confirmation request through as a boolean', () => {
    expect(applyAgentTicket({ ticket: serverTicket({ confirmationRequested: true }) }).confirmationRequested).toBe(true);
    expect(applyAgentTicket({ ticket: serverTicket() }).confirmationRequested).toBe(false);
  });
});

describe('ticketSignature', () => {
  const base = { cart: [cartLine(CAFE, 2, 3500)], customer: GENERAL, paymentMethod: 'CASH', cashTendered: '7000' };

  it('is stable for the same ticket and changes with any visible part of it', () => {
    expect(ticketSignature(base)).toBe(ticketSignature({ ...base, cart: [cartLine(CAFE, 2, 3500)] }));
    expect(ticketSignature(base)).not.toBe(ticketSignature({ ...base, cart: [cartLine(CAFE, 3, 3500)] }));
    expect(ticketSignature(base)).not.toBe(ticketSignature({ ...base, cart: [cartLine(CAFE, 2, 4000)] }));
    expect(ticketSignature(base)).not.toBe(ticketSignature({ ...base, customer: LUIS }));
    expect(ticketSignature(base)).not.toBe(ticketSignature({ ...base, paymentMethod: 'CARD' }));
    expect(ticketSignature(base)).not.toBe(ticketSignature({ ...base, cashTendered: '8000' }));
    expect(ticketSignature(base)).not.toBe(ticketSignature({ ...base, cart: [] }));
  });

  it('distinguishes a product from a service with the same id', () => {
    const asService = { ...base, cart: [cartLine({ ...CAFE, itemType: 'SERVICE' }, 2, 3500)] };
    expect(ticketSignature(base)).not.toBe(ticketSignature(asService));
  });
});
