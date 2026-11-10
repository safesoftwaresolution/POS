/** Marca o desmarca un id en una lista de ids, devolviendo una lista nueva sin duplicados. */
export function toggleId(list, id) {
  return list.includes(id) ? list.filter(x => x !== id) : [...list, id];
}

/** Texto con los tipos de local donde se ofrece una categoría de servicios; vacío = todos. */
export function offeredToLabel(category, storeCategories) {
  const ids = category?.storeCategoryIds ?? [];
  if (ids.length === 0) return 'Todos los locales';
  const names = ids.map(id => storeCategories.find(c => c.id === id)?.name).filter(Boolean);
  return names.join(', ');
}

/**
 * Opciones del select de categoría de un servicio: las disponibles para el local y, al editar, la que el servicio ya
 * tiene aunque ya no se ofrezca (para no mostrar el campo vacío).
 */
export function categoryOptionsFor(available, current) {
  if (!current?.id || available.some(c => c.id === current.id)) return available;
  return [...available, { id: current.id, name: current.name }];
}
