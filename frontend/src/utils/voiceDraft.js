// Borrador de venta dictada → ticket del POS. Todo aqui es puro: no llama al backend ni crea ventas.
// El cobro lo hace el cajero con el flujo normal (CheckoutModal / handleCompleteSale).
//
// Decisiones del cajero sobre un borrador (`decisions`):
//   items:         [{ choice: clave de candidato | null, quantity: texto, dropped: boolean }]  (una por línea)
//   customer:      { mode: 'pending' | 'keep' | 'pick', choice }
//   paymentMethod: { mode: 'pending' | 'keep' | 'pick', choice }
//   cashReceived:  texto con dígitos ('' = sin efectivo)
// `mode: 'pending'` es una decisión sin tomar; 'keep' conserva lo que ya tiene el ticket.

import { cartKey, isService } from './cartItem';
import { paymentLabel, toDigits } from './format';

/** Misma cota que valida el backend en el borrador (1 a 1000 por línea). */
export const MAX_LINE_QUANTITY = 1000;

const ITEM_KINDS = ['PRODUCT', 'SERVICE'];
const CASH = 'CASH';

/** Clave estable de un candidato. Un producto 7 y un servicio 7 conviven, por eso lleva el tipo. */
export const candidateKey = (candidate) => `${candidate.kind}-${candidate.id}`;

const candidatesOf = (reference) => reference?.candidates ?? [];

export const selectableItemCandidates = (reference) =>
  candidatesOf(reference).filter((c) => ITEM_KINDS.includes(c.kind));

export const selectableCustomerCandidates = (reference) =>
  candidatesOf(reference).filter((c) => c.kind === 'CUSTOMER');

/** Solo se pueden aplicar medios de pago cuyo código está en la lista activa del POS. */
export const selectablePaymentCandidates = (reference, paymentMethods = []) =>
  candidatesOf(reference).filter((c) =>
    c.kind === 'PAYMENT_METHOD' && paymentMethods.some((m) => m.code === c.code && m.active !== false));

/** Entero de 1 a MAX_LINE_QUANTITY, o null si el texto no lo es. */
export function parseQuantity(text) {
  const value = String(text ?? '').trim();
  if (!/^\d+$/.test(value)) return null;
  const quantity = Number(value);
  return quantity >= 1 && quantity <= MAX_LINE_QUANTITY ? quantity : null;
}

// RESOLVED con candidato → elegido; AMBIGUOUS / NEEDS_REVIEW → hay que elegir; sin candidatos → nada que elegir.
function initialSingleDecision(reference, selectable) {
  if (selectable.length === 0) return { mode: 'keep', choice: null };
  if (reference.status === 'RESOLVED') return { mode: 'pick', choice: candidateKey(selectable[0]) };
  if (reference.status === 'AMBIGUOUS' || reference.status === 'NEEDS_REVIEW') return { mode: 'pending', choice: null };
  return { mode: 'keep', choice: null };
}

/** Decisiones de partida: solo lo inequívoco viene resuelto; nunca se elige entre alternativas por el cajero. */
export function initialDecisions(draft, { paymentMethods = [] } = {}) {
  return {
    items: draft.items.map(({ item, quantity }) => {
      const selectable = selectableItemCandidates(item);
      const decision = initialSingleDecision(item, selectable);
      return {
        choice: decision.mode === 'pick' ? decision.choice : null,
        quantity: quantity == null ? '' : String(quantity),
        dropped: selectable.length === 0, // NOT_FOUND: solo queda descartarla
      };
    }),
    customer: initialSingleDecision(draft.customer, selectableCustomerCandidates(draft.customer)),
    paymentMethod: initialSingleDecision(
      draft.paymentMethod,
      selectablePaymentCandidates(draft.paymentMethod, paymentMethods),
    ),
    // Se trunca (nunca se redondea hacia arriba): un efectivo mayor al dictado alteraría el cambio.
    cashReceived: draft.cashReceived > 0 ? toDigits(String(draft.cashReceived)) : '',
  };
}

/** Hay algo que el cajero aún debe decidir: un producto sin elegir, una cantidad sin escribir, cliente o pago pendientes. */
export function hasUndecided(draft, decisions) {
  const itemPending = draft.items.some((_, i) => {
    const d = decisions.items[i];
    return !d.dropped && (d.choice == null || parseQuantity(d.quantity) === null);
  });
  return itemPending || decisions.customer.mode === 'pending' || decisions.paymentMethod.mode === 'pending';
}

const warning = (code, message) => ({ code, message });
/** Nombre del cliente del ticket como lo muestra el POS (sin cliente: consumidor final). */
export const customerName = (customer) => customer?.fullName || customer?.name || 'Consumidor final';
const stockOf = (product) => (isService(product) ? Infinity : (product.quantityAvailable ?? product.stock ?? 0));

/**
 * Aplica las decisiones del cajero sobre el ticket actual.
 * Devuelve el ticket resultante sin tocar los argumentos:
 *   cart          carrito completo con las líneas dictadas ya sumadas (misma forma y mismo tope de stock que addToCart)
 *   customer      cliente a asignar, o null para conservar el del ticket
 *   paymentMethod código a asignar, o null para conservar el del ticket
 *   cashTendered  texto para el campo de efectivo, o null para no tocarlo
 *   warnings      [{ code, message }] con lo que no se pudo aplicar tal cual
 *   isEmpty       true si aplicar no cambiaría nada
 */
export function buildTicketFromDraft({
  draft, decisions, productsList = [], customersList = [], paymentMethods = [],
  cart = [], customer = null, paymentMethod = CASH,
}) {
  const warnings = [];
  const nextCart = [...cart];
  let cartChanged = false;

  draft.items.forEach(({ item }, i) => {
    const decision = decisions.items[i];
    const selectable = selectableItemCandidates(item);
    if (!decision || decision.dropped) {
      if (selectable.length === 0) {
        warnings.push(warning('ITEM_NOT_FOUND', `No se encontró «${item.mention}» en el catálogo. Búscalo y agrégalo desde el catálogo.`));
      }
      return;
    }

    const chosen = selectable.find((c) => candidateKey(c) === decision.choice);
    const quantity = parseQuantity(decision.quantity);
    if (!chosen || quantity === null) return; // sin decidir: no se aplica

    const product = productsList.find((p) => cartKey(p) === candidateKey(chosen));
    if (!product) {
      warnings.push(warning('ITEM_NOT_IN_CATALOG', `${chosen.label}: ya no está disponible en el catálogo; no se agregó al ticket.`));
      return;
    }

    const stock = stockOf(product);
    if (stock <= 0) {
      warnings.push(warning('OUT_OF_STOCK', `${product.name}: sin stock disponible; no se agregó al ticket.`));
      return;
    }

    const index = nextCart.findIndex((line) => cartKey(line) === cartKey(product));
    const current = index >= 0 ? nextCart[index].quantity : 0;
    const wanted = current + quantity;
    // Nunca baja una línea que ya tenía el ticket, aunque el stock haya cambiado desde entonces.
    const final = Math.max(current, Math.min(wanted, stock));
    if (final < wanted) {
      warnings.push(warning('STOCK_CAPPED', `${product.name}: solo hay ${stock} disponibles; la cantidad quedó en ${final}.`));
    }
    if (final === current) return;

    cartChanged = true;
    if (index >= 0) {
      nextCart[index] = { ...nextCart[index], quantity: final };
    } else {
      nextCart.push({ ...product, price: product.salePrice || product.price || 0, stock, quantity: final });
    }
  });

  // Cliente: solo se asigna lo que el cajero eligió o lo inequívoco; nunca se sustituye en silencio.
  let nextCustomer = null;
  const customerCandidates = selectableCustomerCandidates(draft.customer);
  if (decisions.customer.mode === 'pick') {
    const chosen = customerCandidates.find((c) => candidateKey(c) === decisions.customer.choice);
    nextCustomer = chosen ? customersList.find((c) => Number(c.id) === Number(chosen.id)) ?? null : null;
    if (chosen && !nextCustomer) {
      warnings.push(warning('CUSTOMER_NOT_AVAILABLE', `El cliente «${chosen.label}» no está en la lista del local; se mantiene ${customerName(customer)}.`));
    }
  } else if (decisions.customer.mode === 'keep' && draft.customer.status !== 'NOT_PROVIDED' && customerCandidates.length === 0) {
    warnings.push(warning('CUSTOMER_NOT_FOUND', `No se encontró al cliente «${draft.customer.mention}»; se mantiene ${customerName(customer)}.`));
  }

  // Medio de pago: solo códigos activos del POS.
  let nextPayment = null;
  const paymentCandidates = selectablePaymentCandidates(draft.paymentMethod, paymentMethods);
  if (decisions.paymentMethod.mode === 'pick') {
    nextPayment = paymentCandidates.find((c) => candidateKey(c) === decisions.paymentMethod.choice)?.code ?? null;
  }
  const paymentMentioned = draft.paymentMethod.status !== 'NOT_PROVIDED';
  if (!nextPayment && paymentMentioned && (decisions.paymentMethod.mode === 'pick' || paymentCandidates.length === 0)) {
    warnings.push(warning('PAYMENT_NOT_AVAILABLE', `El medio de pago «${draft.paymentMethod.mention}» no está disponible; se mantiene ${paymentLabel(paymentMethod)}.`));
  }

  // Efectivo: solo se rellena si el pago resultante es efectivo. Con otro medio el checkout espera el total.
  const effectivePayment = nextPayment ?? paymentMethod;
  let cashTendered = null;
  if (effectivePayment === CASH) {
    const typed = toDigits(decisions.cashReceived);
    if (typed) cashTendered = typed;
    else if (nextPayment && nextPayment !== paymentMethod) cashTendered = '';
  } else if (cartChanged || nextPayment) {
    cashTendered = String(nextCart.reduce((sum, line) => sum + line.price * line.quantity, 0));
  }

  return {
    cart: nextCart,
    customer: nextCustomer,
    paymentMethod: nextPayment,
    cashTendered,
    warnings,
    isEmpty: !cartChanged && !nextCustomer && !nextPayment && cashTendered === null,
  };
}
