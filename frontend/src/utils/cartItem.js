// Una línea del ticket es un producto o un servicio. Los ids de ambos catálogos son independientes
// (un producto 3 y un servicio 3 pueden convivir), así que la línea se identifica por tipo + id.

export const ITEM_PRODUCT = 'PRODUCT';
export const ITEM_SERVICE = 'SERVICE';

export const isService = (item) => item?.itemType === ITEM_SERVICE;

export const cartKey = (item) => `${isService(item) ? ITEM_SERVICE : ITEM_PRODUCT}-${item.id}`;

/** Línea del request de venta: exactamente uno de `productId` / `serviceId`. */
export function toSaleLine(item) {
  return {
    productId: isService(item) ? null : item.id,
    serviceId: isService(item) ? item.id : null,
    quantity: item.quantity,
    unitPrice: item.price,
  };
}
