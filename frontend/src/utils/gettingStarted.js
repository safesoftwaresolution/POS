// Pasos de la guía de primer uso del Dashboard (components/dashboard/GettingStarted).

// Valores que el backend pone cuando el negocio aún no tiene datos (SettingsServiceImpl).
const PLACEHOLDERS = new Set(['mi negocio', 'por definir']);

const isRealValue = (value) => Boolean(value && value.trim()) && !PLACEHOLDERS.has(value.trim().toLowerCase());

/** Los datos del negocio que salen en recibos y facturas ya están completos. */
export function isBusinessProfileComplete(settings) {
  return Boolean(settings) && isRealValue(settings.businessName) && isRealValue(settings.address) && isRealValue(settings.phone);
}

/**
 * Pasos en orden. `profileComplete` y `billingActive` en `null` significan "aún no se sabe": ese paso no se muestra.
 * Los pasos `optional` no impiden dar la guía por terminada.
 */
export function gettingStartedSteps({ hasProducts, hasSales, canCreateProducts, isAdmin, profileComplete = null, billingActive = null }) {
  return [
    { done: profileComplete, label: 'Completa los datos de tu negocio', hint: 'Nombre, dirección y teléfono para tus recibos y facturas.', to: '/settings', show: isAdmin && profileComplete !== null },
    { done: hasProducts, label: 'Crea tu primer producto', hint: 'Nombre, precio y cantidad en inventario.', to: '/products', show: canCreateProducts },
    { done: hasSales, label: 'Haz tu primera venta', hint: 'Entra al Punto de Venta, elige productos y cobra.', to: '/pos', show: true },
    { done: billingActive, label: 'Activa la facturación electrónica (opcional)', hint: 'Emite facturas ante la DIAN desde el POS.', to: '/invoicing?tab=activation', show: isAdmin && billingActive !== null, optional: true },
  ].filter((s) => s.show);
}
