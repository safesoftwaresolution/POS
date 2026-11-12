import { STORE_TEMPLATE_OPTIONS } from '../constants/storeTemplates';

const ORDER = STORE_TEMPLATE_OPTIONS.map(o => o.value);
const inOrder = (keys) => ORDER.filter(k => keys.includes(k));

/** Marca o desmarca una plantilla en una lista, devolviendo una lista nueva en el orden canónico. */
export function toggleTemplate(list, key) {
  return inOrder(list.includes(key) ? list.filter(k => k !== key) : [...list, key]);
}

/** Plantillas de una categoría o local tal como las manda el backend; cae a la principal (`template`) de una respuesta vieja. */
export function templatesOf(entity) {
  if (entity?.templates?.length) return entity.templates;
  return entity?.template ? [entity.template] : [];
}
