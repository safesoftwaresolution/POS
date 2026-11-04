// Preguntas frecuentes: las usan la seccion visible (Faq.astro) y el JSON-LD de SEO (index.astro).
// Solo afirmaciones respaldadas por el sistema (README / DESIGN.md / specs de modulos).
export const faq = [
  {
    q: '¿Tengo que instalar algo?',
    a: 'No. PosBento funciona desde el navegador, en computador, tablet o celular. Si quieres, puedes instalarlo como aplicación en el celular desde el mismo navegador.',
  },
  {
    q: '¿Puedo usar un lector de código de barras?',
    a: 'Sí. Escanea con un lector en el mostrador o con la cámara del celular, y el producto se agrega al ticket al instante. También puedes generar e imprimir las etiquetas de tus productos (Code 128).',
  },
  {
    q: '¿Cómo funciona la facturación electrónica?',
    a: 'Al cobrar, marcas que la venta lleva factura electrónica. La venta se cierra en el momento y la factura se emite en segundo plano ante la DIAN a través de un proveedor tecnológico. Ves su estado (pendiente, validada, rechazada o con error), puedes reintentar el envío y descargar el XML y el PDF. Para emitir, tu local debe estar habilitado: lo gestionas desde el sistema.',
  },
  {
    q: '¿Qué pasa si falla el envío de una factura?',
    a: 'Tu venta no se detiene. La factura queda marcada con su estado y puedes reintentar el envío cuando quieras, sin volver a cobrar.',
  },
  {
    q: '¿Puedo manejar varios locales o empleados?',
    a: 'Sí. Cada local trabaja con sus propios datos, aislados de los demás, y cada persona entra con un rol: Administrador, Supervisor o Vendedor, con los permisos que le corresponden.',
  },
  {
    q: '¿Mis datos están seguros?',
    a: 'Cada local solo ve su información. La sesión usa cookies protegidas (HttpOnly) y tokens de corta duración, el acceso depende del rol y las credenciales de facturación de cada local se guardan cifradas.',
  },
  {
    q: '¿Funciona sin internet?',
    a: 'No. PosBento trabaja en línea para mantener tus ventas, tu stock y tus facturas al día en tiempo real, así que necesitas conexión a internet para usarlo.',
  },
];
