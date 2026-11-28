import { ITEM_PRODUCT, ITEM_SERVICE, cartKey, isService } from './cartItem';

// Traduccion entre el ticket del POS y el del asistente (backend/docs/modules/voicesales/spec.md). Al servidor solo
// viajan ids y cantidades: nombres, precios y existencias los vuelve a leer del catalogo de la tienda. El ticket que
// vuelve nunca es una venta registrada.

const kindOf = (item) => (isService(item) ? ITEM_SERVICE : ITEM_PRODUCT);

/** Ticket del request: `{ lines, customerId, paymentMethodCode, cashReceived }`. */
export function toAgentTicket({ cart, customer, paymentMethod, cashTendered }) {
  const cash = Number(cashTendered);
  const cashDictated = paymentMethod === 'CASH' && cashTendered !== '' && Number.isFinite(cash) && cash > 0;
  return {
    lines: cart.map((item) => ({ kind: kindOf(item), id: item.id, quantity: item.quantity })),
    customerId: customer?.id ?? null,
    paymentMethodCode: paymentMethod || null,
    cashReceived: cashDictated ? cash : null,
  };
}

const stockOf = (product) => (isService(product) ? Infinity : (product.quantityAvailable ?? product.stock ?? 0));

/**
 * Lleva la respuesta del asistente al estado del POS.
 * - `cart`: cada linea toma el objeto del catalogo local (para imagen, stock, etc.) con el precio que dijo el servidor;
 *   si el catalogo local aun no la tiene, se arma una linea minima.
 * - `customer`: el cliente del ticket o `null` si no esta en la lista local (el POS conserva el suyo).
 * - `paymentMethod`: el dictado o, si no se dijo, el que ya tenia el POS.
 * - `cashTendered`: lo dictado en efectivo; sin dato (o con otro medio) el pago es exacto, igual que hace el cobro normal.
 */
export function applyAgentTicket({ ticket, productsList = [], customersList = [], currentPaymentMethod = 'CASH' }) {
  const cart = ticket.lines.map((line) => {
    const key = `${line.kind}-${line.id}`;
    const found = productsList.find((product) => cartKey(product) === key);
    const base = found ?? { id: line.id, name: line.name, itemType: line.kind };
    return {
      ...base,
      price: line.unitPrice,
      stock: found ? stockOf(found) : line.quantity,
      quantity: line.quantity,
    };
  });
  const total = cart.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const paymentMethod = ticket.paymentMethod?.code ?? currentPaymentMethod ?? 'CASH';
  const dictatedCash = paymentMethod === 'CASH' && ticket.cashReceived != null ? ticket.cashReceived : null;
  return {
    cart,
    total,
    customer: ticket.customer
      ? customersList.find((candidate) => Number(candidate.id) === Number(ticket.customer.id)) ?? null
      : null,
    paymentMethod,
    cashTendered: String(dictatedCash ?? total),
    confirmationRequested: Boolean(ticket.confirmationRequested),
  };
}

/**
 * Huella del ticket tal como se ve en pantalla. La confirmacion del asistente solo vale mientras la huella sea la de
 * cuando la pidio: cualquier cambio (del cajero o del asistente) la retira sin necesidad de efectos ni temporizadores.
 */
export function ticketSignature({ cart, customer, paymentMethod, cashTendered }) {
  return JSON.stringify([
    cart.map((item) => [cartKey(item), item.quantity, item.price]),
    customer?.id ?? null,
    paymentMethod,
    cashTendered,
  ]);
}
