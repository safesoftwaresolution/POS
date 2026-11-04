/** El Cliente General (consumidor final) no puede tener fiado: no hay a quién cobrarle. */
export function isGeneralCustomer(customer) {
  if (!customer) return true;
  const name = (customer.fullName || customer.name || '').trim().toLowerCase();
  return String(customer.identification || '').trim() === '222222222222' || name === 'cliente general';
}

/** Fecha por defecto del fiado (hoy + 30 días) en `yyyy-MM-dd`, igual que aplica el backend si no se indica. */
export function defaultCreditDueDate(from = new Date()) {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate() + 30);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/**
 * ¿Se puede fiar `total` a esta cuenta? Sin cupo (null) no hay límite. Devuelve el motivo si no se puede, o null.
 * `account` es el `CreditAccountDto` del cliente.
 */
export function creditBlockReason(customer, account, total) {
  if (isGeneralCustomer(customer)) return 'Elige un cliente registrado: al Cliente General no se le puede fiar.';
  if (account && account.availableCredit != null && total > Number(account.availableCredit)) {
    return 'Supera el cupo disponible del cliente.';
  }
  return null;
}
