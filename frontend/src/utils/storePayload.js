/**
 * Cuerpo de `PUT /backoffice/stores/{id}` para cambiar solo la categoría de un local desde la tabla: el
 * backend reemplaza todos los campos, así que se reenvían los actuales. Igual que el formulario de edición,
 * los campos vacíos viajan como cadena vacía y una categoría vacía como `null` (sin categoría).
 */
export function storeUpdatePayload(store, categoryId) {
  return {
    name: store.name || '',
    storeCategoryId: categoryId ? Number(categoryId) : null,
    phone: store.phone || '',
    email: store.email || '',
    website: store.website || '',
    address: store.address || '',
    taxId: store.taxId || '',
  };
}
