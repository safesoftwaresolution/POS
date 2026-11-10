import { DEFAULT_STORE_TEMPLATE, STORE_TEMPLATES } from '../constants/storeTemplates';

// Más específica primero: la plantilla "principal" de un conjunto (igual que en el backend).
const PRIORITY = [STORE_TEMPLATES.APPOINTMENTS, STORE_TEMPLATES.FOOD, STORE_TEMPLATES.RETAIL];

/**
 * Qué módulos específicos habilitan las plantillas de un local. Un local tiene varias (las de su categoría más sus
 * extras): un módulo se habilita si CUALQUIERA de ellas lo habilita. Acepta la lista o, por compatibilidad, una sola.
 * Es solo presentación: el backend sigue siendo quien decide qué puede hacer cada local. Las funciones comunes (venta,
 * inventario, clientes) no dependen de la plantilla.
 */
export function templateFeaturesFor(templates) {
  const known = (Array.isArray(templates) ? templates : [templates])
    .filter(t => Object.values(STORE_TEMPLATES).includes(t));
  const list = known.length ? known : [DEFAULT_STORE_TEMPLATE];
  return {
    templates: list,
    /** La más específica de las plantillas del local. */
    template: PRIORITY.find(t => list.includes(t)),
    /** Calendario y agendamiento de citas (barberías, peluquerías, uñas, boutiques). */
    appointments: list.includes(STORE_TEMPLATES.APPOINTMENTS),
  };
}
