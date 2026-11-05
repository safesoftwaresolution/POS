// Plantillas funcionales de un local. La plantilla de cada local la deriva el backend de su
// categoría; aquí solo viven las claves y sus etiquetas para mostrarlas.
export const STORE_TEMPLATES = {
  RETAIL: 'RETAIL',
  FOOD: 'FOOD',
  APPOINTMENTS: 'APPOINTMENTS',
};

export const DEFAULT_STORE_TEMPLATE = STORE_TEMPLATES.RETAIL;

export const STORE_TEMPLATE_LABELS = {
  RETAIL: 'Comercio / Tienda',
  FOOD: 'Comida y bebidas',
  APPOINTMENTS: 'Citas y agenda',
};

export const STORE_TEMPLATE_OPTIONS = Object.entries(STORE_TEMPLATE_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export const storeTemplateLabel = (template) =>
  STORE_TEMPLATE_LABELS[template] ?? STORE_TEMPLATE_LABELS[DEFAULT_STORE_TEMPLATE];
