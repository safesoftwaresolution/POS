import { describe, expect, it } from 'vitest';
import { cartKey } from './cartItem';
import {
  MAX_LINE_QUANTITY,
  buildTicketFromDraft,
  candidateKey,
  hasUndecided,
  initialDecisions,
  parseQuantity,
} from './voiceDraft';

// ---- catálogo del POS (lo que carga POSBento) ----
const CAFE = { id: 7, itemType: 'PRODUCT', name: 'Café grande', salePrice: 4500, quantityAvailable: 10 };
const AGUA = { id: 8, itemType: 'PRODUCT', name: 'Botella de agua', price: 2000, stock: 3 };
const AGOTADO = { id: 9, itemType: 'PRODUCT', name: 'Galleta', salePrice: 1000, quantityAvailable: 0 };
const CORTE = { id: 7, itemType: 'SERVICE', name: 'Corte', price: 15000, code: 'SRV-1' }; // mismo id que CAFE
const productsList = [CAFE, AGUA, AGOTADO, CORTE];

const GENERAL = { id: 1, fullName: 'Cliente General' };
const ANA_14 = { id: 14, fullName: 'Ana Pérez' };
const ANA_22 = { id: 22, fullName: 'Ana Pérez' };
const customersList = [GENERAL, ANA_14, ANA_22];

const paymentMethods = [
  { code: 'CASH', label: 'Efectivo', active: true },
  { code: 'CARD', label: 'Tarjeta', active: true },
];

// ---- borradores (forma de la respuesta del backend) ----
const candidate = (kind, id, label, code = null) => ({ kind, id, code, label });
const reference = (mention, status, candidates = []) => ({ mention, status, candidates });
const product = (id) => candidate('PRODUCT', id, `Producto ${id}`);
const NOT_PROVIDED = reference(null, 'NOT_PROVIDED');
const CASH_REF = reference('efectivo', 'RESOLVED', [candidate('PAYMENT_METHOD', 3, 'Efectivo', 'CASH')]);

function draftOf(overrides = {}) {
  return {
    items: [],
    customer: NOT_PROVIDED,
    paymentMethod: NOT_PROVIDED,
    cashReceived: null,
    pendingFields: [],
    ...overrides,
  };
}

// «Dos cafés grandes y una botella de agua para Ana Pérez. Paga en efectivo con cincuenta mil»
function exampleDraft() {
  return draftOf({
    items: [
      { item: reference('cafés grandes', 'RESOLVED', [product(7)]), quantity: 2 },
      { item: reference('botella de agua', 'RESOLVED', [product(8)]), quantity: 1 },
    ],
    customer: reference('Ana Pérez', 'AMBIGUOUS', [
      candidate('CUSTOMER', 14, 'Ana Pérez (••••1234; #14)'),
      candidate('CUSTOMER', 22, 'Ana Pérez (••••6789; #22)'),
    ]),
    paymentMethod: CASH_REF,
    cashReceived: 50000,
    pendingFields: ['customer'],
  });
}

const context = { paymentMethods };
const build = (draft, decisions, extra = {}) => buildTicketFromDraft({
  draft, decisions, productsList, customersList, paymentMethods,
  cart: [], customer: GENERAL, paymentMethod: 'CASH', ...extra,
});
const codes = (ticket) => ticket.warnings.map((w) => w.code);

describe('parseQuantity', () => {
  it('accepts whole numbers from 1 up to the contract maximum', () => {
    expect(parseQuantity('3')).toBe(3);
    expect(parseQuantity(' 12 ')).toBe(12);
    expect(parseQuantity(String(MAX_LINE_QUANTITY))).toBe(MAX_LINE_QUANTITY);
  });

  it.each(['', '0', '-1', '1.5', 'dos', String(MAX_LINE_QUANTITY + 1), null, undefined])('rejects %j', (value) => {
    expect(parseQuantity(value)).toBeNull();
  });
});

describe('initialDecisions', () => {
  it('pre-selects RESOLVED items and keeps the dictated quantity as text', () => {
    const decisions = initialDecisions(exampleDraft(), context);
    expect(decisions.items).toEqual([
      { choice: candidateKey(product(7)), quantity: '2', dropped: false },
      { choice: candidateKey(product(8)), quantity: '1', dropped: false },
    ]);
  });

  it('leaves AMBIGUOUS and NEEDS_REVIEW items without a choice and drops NOT_FOUND ones', () => {
    const draft = draftOf({
      items: [
        { item: reference('café', 'AMBIGUOUS', [product(7), product(8)]), quantity: 1 },
        { item: reference('algo', 'NEEDS_REVIEW', [product(8)]), quantity: 1 },
        { item: reference('pizza', 'NOT_FOUND'), quantity: 1 },
      ],
    });
    const { items } = initialDecisions(draft, context);
    expect(items[0]).toEqual({ choice: null, quantity: '1', dropped: false });
    expect(items[1]).toEqual({ choice: null, quantity: '1', dropped: false });
    expect(items[2].dropped).toBe(true);
    expect(items[2].choice).toBeNull();
  });

  it('never turns a missing quantity into one', () => {
    const draft = draftOf({ items: [{ item: reference('café', 'RESOLVED', [product(7)]), quantity: null }] });
    expect(initialDecisions(draft, context).items[0].quantity).toBe('');
  });

  it('only picks a customer when RESOLVED; AMBIGUOUS and NEEDS_REVIEW stay pending, the rest keeps the ticket customer', () => {
    const ana = candidate('CUSTOMER', 14, 'Ana');
    const decide = (customer) => initialDecisions(draftOf({ customer }), context).customer;
    expect(decide(reference('Ana', 'RESOLVED', [ana]))).toEqual({ mode: 'pick', choice: candidateKey(ana) });
    expect(decide(reference('Ana', 'AMBIGUOUS', [ana, candidate('CUSTOMER', 22, 'Ana 2')])).mode).toBe('pending');
    expect(decide(reference('Ana', 'NEEDS_REVIEW', [ana])).mode).toBe('pending');
    expect(decide(reference('Ana', 'NOT_FOUND')).mode).toBe('keep');
    expect(decide(NOT_PROVIDED)).toEqual({ mode: 'keep', choice: null });
  });

  it('only picks a payment method whose code is in the active list', () => {
    const decide = (paymentMethod) => initialDecisions(draftOf({ paymentMethod }), context).paymentMethod;
    expect(decide(CASH_REF)).toEqual({ mode: 'pick', choice: candidateKey(CASH_REF.candidates[0]) });
    const nequi = reference('nequi', 'RESOLVED', [candidate('PAYMENT_METHOD', 5, 'Nequi', 'NEQUI')]);
    expect(decide(nequi)).toEqual({ mode: 'keep', choice: null });
    const either = reference('tarjeta', 'AMBIGUOUS', [
      candidate('PAYMENT_METHOD', 4, 'Tarjeta', 'CARD'),
      candidate('PAYMENT_METHOD', 5, 'Nequi', 'NEQUI'),
    ]);
    expect(decide(either).mode).toBe('pending');
    expect(decide(NOT_PROVIDED).mode).toBe('keep');
  });

  it('prefills the cash field with whole pesos and leaves it empty when nothing was dictated', () => {
    expect(initialDecisions(draftOf({ cashReceived: 50000 }), context).cashReceived).toBe('50000');
    expect(initialDecisions(draftOf({ cashReceived: 50000.75 }), context).cashReceived).toBe('50000');
    expect(initialDecisions(draftOf({ cashReceived: null }), context).cashReceived).toBe('');
    expect(initialDecisions(draftOf({ cashReceived: 0 }), context).cashReceived).toBe('');
  });
});

describe('hasUndecided', () => {
  it('is true while the customer is ambiguous and false once the cashier picks one', () => {
    const draft = exampleDraft();
    const decisions = initialDecisions(draft, context);
    expect(hasUndecided(draft, decisions)).toBe(true);

    const picked = { ...decisions, customer: { mode: 'pick', choice: candidateKey(draft.customer.candidates[1]) } };
    expect(hasUndecided(draft, picked)).toBe(false);
    const kept = { ...decisions, customer: { mode: 'keep', choice: null } };
    expect(hasUndecided(draft, kept)).toBe(false);
  });

  it('is true for an ambiguous item or a missing quantity until it is chosen, typed or dropped', () => {
    const draft = draftOf({
      items: [
        { item: reference('café', 'AMBIGUOUS', [product(7), product(8)]), quantity: null },
      ],
    });
    const base = initialDecisions(draft, context);
    expect(hasUndecided(draft, base)).toBe(true);

    const chosenNoQty = { ...base, items: [{ ...base.items[0], choice: candidateKey(product(7)) }] };
    expect(hasUndecided(draft, chosenNoQty)).toBe(true);
    const chosenZero = { ...base, items: [{ ...base.items[0], choice: candidateKey(product(7)), quantity: '0' }] };
    expect(hasUndecided(draft, chosenZero)).toBe(true);
    const complete = { ...base, items: [{ ...base.items[0], choice: candidateKey(product(7)), quantity: '2' }] };
    expect(hasUndecided(draft, complete)).toBe(false);
    const dropped = { ...base, items: [{ ...base.items[0], dropped: true }] };
    expect(hasUndecided(draft, dropped)).toBe(false);
  });

  it('is true while the payment method is pending', () => {
    const draft = draftOf({
      paymentMethod: reference('tarjeta', 'NEEDS_REVIEW', [candidate('PAYMENT_METHOD', 4, 'Tarjeta', 'CARD')]),
    });
    expect(hasUndecided(draft, initialDecisions(draft, context))).toBe(true);
  });

  it('is false for a draft that is fully resolved', () => {
    const draft = draftOf({
      items: [{ item: reference('café', 'RESOLVED', [product(7)]), quantity: 2 }],
      customer: reference('Ana', 'RESOLVED', [candidate('CUSTOMER', 14, 'Ana')]),
      paymentMethod: CASH_REF,
    });
    expect(hasUndecided(draft, initialDecisions(draft, context))).toBe(false);
  });
});

describe('buildTicketFromDraft', () => {
  it('turns the spec example into cart lines, the chosen Ana, cash payment and the dictated cash', () => {
    const draft = exampleDraft();
    const decisions = initialDecisions(draft, context);
    decisions.customer = { mode: 'pick', choice: candidateKey(draft.customer.candidates[1]) };

    const ticket = build(draft, decisions);

    expect(ticket.cart).toEqual([
      { ...CAFE, price: 4500, stock: 10, quantity: 2 },
      { ...AGUA, price: 2000, stock: 3, quantity: 1 },
    ]);
    expect(ticket.customer).toBe(ANA_22);
    expect(ticket.paymentMethod).toBe('CASH');
    expect(ticket.cashTendered).toBe('50000');
    expect(ticket.warnings).toEqual([]);
    expect(ticket.isEmpty).toBe(false);
  });

  it('never picks an ambiguous customer on its own, nor swaps Ana for the general customer', () => {
    const draft = exampleDraft();
    const decisions = initialDecisions(draft, context); // cliente pendiente

    expect(build(draft, decisions).customer).toBeNull();
    const kept = { ...decisions, customer: { mode: 'keep', choice: null } };
    expect(build(draft, kept).customer).toBeNull(); // null = el ticket conserva su cliente actual
  });

  it('warns when a mentioned customer is not found and keeps the current one', () => {
    const draft = draftOf({ customer: reference('Luis Gómez', 'NOT_FOUND') });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.customer).toBeNull();
    expect(codes(ticket)).toEqual(['CUSTOMER_NOT_FOUND']);
    expect(ticket.warnings[0].message).toContain('Luis Gómez');
    expect(ticket.warnings[0].message).toContain('Cliente General');
  });

  it('warns when the chosen customer is no longer in the loaded list', () => {
    const draft = draftOf({ customer: reference('Eva', 'RESOLVED', [candidate('CUSTOMER', 99, 'Eva')]) });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.customer).toBeNull();
    expect(codes(ticket)).toEqual(['CUSTOMER_NOT_AVAILABLE']);
  });

  it('drops NOT_FOUND items with a warning that names what was said', () => {
    const draft = draftOf({
      items: [
        { item: reference('café', 'RESOLVED', [product(7)]), quantity: 1 },
        { item: reference('pizza hawaiana', 'NOT_FOUND'), quantity: 2 },
      ],
    });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.cart.map(cartKey)).toEqual(['PRODUCT-7']);
    expect(codes(ticket)).toEqual(['ITEM_NOT_FOUND']);
    expect(ticket.warnings[0].message).toContain('pizza hawaiana');
  });

  it('does not warn about an item the cashier dropped on purpose', () => {
    const draft = draftOf({ items: [{ item: reference('café', 'AMBIGUOUS', [product(7), product(8)]), quantity: 1 }] });
    const decisions = initialDecisions(draft, context);
    decisions.items[0].dropped = true;

    const ticket = build(draft, decisions);

    expect(ticket.cart).toEqual([]);
    expect(ticket.warnings).toEqual([]);
    expect(ticket.isEmpty).toBe(true);
  });

  it('skips lines that are still undecided and uses the quantity the cashier typed', () => {
    const draft = draftOf({ items: [{ item: reference('café', 'RESOLVED', [product(7)]), quantity: null }] });
    const decisions = initialDecisions(draft, context);

    expect(build(draft, decisions).cart).toEqual([]); // sin cantidad no hay línea: nunca "1"

    decisions.items[0].quantity = '3';
    expect(build(draft, decisions).cart).toEqual([{ ...CAFE, price: 4500, stock: 10, quantity: 3 }]);
  });

  it('caps a quantity above the available stock and warns', () => {
    const draft = draftOf({ items: [{ item: reference('agua', 'RESOLVED', [product(8)]), quantity: 5 }] });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.cart[0].quantity).toBe(3);
    expect(codes(ticket)).toEqual(['STOCK_CAPPED']);
    expect(ticket.warnings[0].message).toContain('Botella de agua');
    expect(ticket.warnings[0].message).toContain('3');
  });

  it('adds to a line already in the ticket without exceeding the stock, and never mutates the input cart', () => {
    const existing = [{ ...AGUA, price: 2000, stock: 3, quantity: 2 }, { ...CAFE, price: 4500, stock: 10, quantity: 1 }];
    const snapshot = JSON.parse(JSON.stringify(existing));
    const draft = draftOf({ items: [{ item: reference('agua', 'RESOLVED', [product(8)]), quantity: 2 }] });

    const ticket = build(draft, initialDecisions(draft, context), { cart: existing });

    expect(ticket.cart.find((l) => cartKey(l) === 'PRODUCT-8').quantity).toBe(3);
    expect(ticket.cart.find((l) => cartKey(l) === 'PRODUCT-7').quantity).toBe(1);
    expect(codes(ticket)).toEqual(['STOCK_CAPPED']);
    expect(existing).toEqual(snapshot);
  });

  it('adds up quantities when two dictated lines resolve to the same product', () => {
    const draft = draftOf({
      items: [
        { item: reference('café', 'RESOLVED', [product(7)]), quantity: 2 },
        { item: reference('otro café', 'RESOLVED', [product(7)]), quantity: 1 },
      ],
    });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.cart).toHaveLength(1);
    expect(ticket.cart[0].quantity).toBe(3);
  });

  it('does not add a chosen item without stock', () => {
    const draft = draftOf({ items: [{ item: reference('galleta', 'RESOLVED', [product(9)]), quantity: 1 }] });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.cart).toEqual([]);
    expect(codes(ticket)).toEqual(['OUT_OF_STOCK']);
  });

  it('does not add an item that is not in the loaded catalog', () => {
    const draft = draftOf({ items: [{ item: reference('té', 'RESOLVED', [product(404)]), quantity: 1 }] });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.cart).toEqual([]);
    expect(codes(ticket)).toEqual(['ITEM_NOT_IN_CATALOG']);
  });

  it('treats services as unlimited and keeps them apart from a product with the same id', () => {
    const draft = draftOf({
      items: [
        { item: reference('café', 'RESOLVED', [product(7)]), quantity: 1 },
        { item: reference('corte', 'RESOLVED', [candidate('SERVICE', 7, 'Corte', 'SRV-1')]), quantity: 50 },
      ],
    });
    const ticket = build(draft, initialDecisions(draft, context));

    expect(ticket.cart.map(cartKey)).toEqual(['PRODUCT-7', 'SERVICE-7']);
    expect(ticket.cart[1]).toMatchObject({ price: 15000, stock: Infinity, quantity: 50 });
    expect(ticket.warnings).toEqual([]);
  });

  it('only applies a payment method that is active; otherwise it keeps the ticket one and warns', () => {
    const nequi = reference('nequi', 'RESOLVED', [candidate('PAYMENT_METHOD', 5, 'Nequi', 'NEQUI')]);
    const draft = draftOf({ paymentMethod: nequi });
    const ticket = build(draft, initialDecisions(draft, context), { paymentMethod: 'CARD' });

    expect(ticket.paymentMethod).toBeNull();
    expect(codes(ticket)).toEqual(['PAYMENT_NOT_AVAILABLE']);
    expect(ticket.warnings[0].message).toContain('nequi');

    // Aunque alguien fuerce la decisión, el helper no aplica un código inactivo.
    const forced = { ...initialDecisions(draft, context), paymentMethod: { mode: 'pick', choice: candidateKey(nequi.candidates[0]) } };
    expect(build(draft, forced).paymentMethod).toBeNull();
  });

  describe('cash received', () => {
    const itemsFor = (quantity) => [{ item: reference('café', 'RESOLVED', [product(7)]), quantity }];

    it('prefills the dictated cash when the chosen payment is cash', () => {
      const draft = draftOf({ items: itemsFor(2), paymentMethod: CASH_REF, cashReceived: 50000 });
      const ticket = build(draft, initialDecisions(draft, context), { paymentMethod: 'CARD' });

      expect(ticket.paymentMethod).toBe('CASH');
      expect(ticket.cashTendered).toBe('50000');
    });

    it('does not prefill cash for another method: it sets the total, as the checkout does', () => {
      const card = reference('tarjeta', 'RESOLVED', [candidate('PAYMENT_METHOD', 4, 'Tarjeta', 'CARD')]);
      const draft = draftOf({ items: itemsFor(2), paymentMethod: card, cashReceived: 50000 });
      const ticket = build(draft, initialDecisions(draft, context));

      expect(ticket.paymentMethod).toBe('CARD');
      expect(ticket.cashTendered).toBe('9000');
    });

    it('uses the ticket payment when none was dictated: cash fills, card does not', () => {
      const draft = draftOf({ items: itemsFor(1), cashReceived: 50000 });
      const decisions = initialDecisions(draft, context);

      expect(build(draft, decisions, { paymentMethod: 'CASH' }).cashTendered).toBe('50000');
      expect(build(draft, decisions, { paymentMethod: 'CARD' }).cashTendered).toBe('4500');
    });

    it('takes the amount the cashier edited and leaves the field alone when nothing is dictated', () => {
      const draft = draftOf({ items: itemsFor(1), paymentMethod: CASH_REF, cashReceived: 50000 });
      const edited = { ...initialDecisions(draft, context), cashReceived: '60000' };
      expect(build(draft, edited).cashTendered).toBe('60000');

      const silent = draftOf({ items: itemsFor(1) });
      expect(build(silent, initialDecisions(silent, context), { paymentMethod: 'CASH' }).cashTendered).toBeNull();
    });

    it('clears the field when switching to cash without a dictated amount', () => {
      const draft = draftOf({ items: itemsFor(1), paymentMethod: CASH_REF });
      expect(build(draft, initialDecisions(draft, context), { paymentMethod: 'CARD' }).cashTendered).toBe('');
    });
  });

  it('reports an empty result when nothing would change, and not when only the customer would', () => {
    const empty = draftOf();
    expect(build(empty, initialDecisions(empty, context)).isEmpty).toBe(true);

    const onlyCustomer = draftOf({ customer: reference('Ana', 'RESOLVED', [candidate('CUSTOMER', 14, 'Ana')]) });
    const ticket = build(onlyCustomer, initialDecisions(onlyCustomer, context));
    expect(ticket.customer).toBe(ANA_14);
    expect(ticket.isEmpty).toBe(false);
  });
});
