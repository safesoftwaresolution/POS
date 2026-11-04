/**
 * Espera una petición que debería devolver una lista y garantiza un arreglo: algunos endpoints responden `null`
 * o un objeto cuando no hay datos, y las pantallas hacen `.map`/`.filter` sin revisar.
 */
export default async function asList(request) {
  const data = await request;
  return Array.isArray(data) ? data : [];
}
