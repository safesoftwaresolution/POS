import { DEFAULT_STORE_TEMPLATE, STORE_TEMPLATES } from '../constants/storeTemplates';

/**
 * Qué módulos específicos habilita cada plantilla. Es solo presentación: el backend sigue siendo
 * quien decide qué puede hacer cada local. Las funciones comunes (venta, inventario, clientes) no
 * dependen de la plantilla.
 */
export function templateFeaturesFor(template) {
  const t = Object.values(STORE_TEMPLATES).includes(template) ? template : DEFAULT_STORE_TEMPLATE;
  return {
    template: t,
    /** Calendario y agendamiento de citas (barberías, peluquerías, uñas, boutiques). */
    appointments: t === STORE_TEMPLATES.APPOINTMENTS,
  };
}
