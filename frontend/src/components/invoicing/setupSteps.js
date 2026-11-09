// Pasos del asistente de facturación (BillingSetupDto.steps) con nombres para el usuario.

export const SETUP_STEP_LABELS = {
  FISCAL_DATA: 'Datos de tu RUT',
  DIAN_ENABLEMENT: 'Habilítate en la DIAN',
  NUMBERING: 'Numeración',
  READY: 'Listo',
};

export const ENVIRONMENT_LABELS = {
  PRODUCTION: 'Producción',
  SANDBOX: 'Pruebas',
};

/** `steps` del backend → pasos del `Stepper` (`state` en minúsculas, mensaje como descripción). */
export function toStepperSteps(steps) {
  return (steps || []).map((s) => ({
    key: s.key,
    label: SETUP_STEP_LABELS[s.key] || s.key,
    state: String(s.state || 'PENDING').toLowerCase(),
    description: s.message,
  }));
}

/** El paso que conviene mostrar: el primero con error o en curso; si todo está hecho, el último. */
export function focusStepKey(steps) {
  const list = steps || [];
  const open = list.find((s) => s.state === 'ERROR' || s.state === 'CURRENT');
  return (open || list[list.length - 1])?.key || null;
}
