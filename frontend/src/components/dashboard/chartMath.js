// Geometría de las gráficas SVG del Dashboard. Todas trabajan en un lienzo de 100 x 100 que el SVG estira
// (preserveAspectRatio="none"); los textos y puntos van en HTML para que no se deformen.

const clampY = (y) => Math.min(100, Math.max(0, y));

/** Puntos `{ x, y }` (0-100) de una serie; `y` crece hacia abajo y deja margen arriba y abajo. */
export function pointsFor(values, max = Math.max(...values, 0)) {
  const count = values.length;
  return values.map((value, i) => ({
    x: count > 1 ? (i / (count - 1)) * 100 : 50,
    y: 92 - (max > 0 ? value / max : 0) * 80,
  }));
}

/** Curva suave (Catmull-Rom convertida a Bezier) que pasa por todos los puntos. */
export function smoothPath(points) {
  if (points.length === 0) return '';
  if (points.length === 1) return `M0 ${points[0].y} L100 ${points[0].y}`;
  let d = `M${points[0].x} ${points[0].y}`;
  for (let i = 0; i < points.length - 1; i += 1) {
    const p0 = points[i - 1] || points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] || p2;
    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c1y = clampY(p1.y + (p2.y - p0.y) / 6);
    const c2x = p2.x - (p3.x - p1.x) / 6;
    const c2y = clampY(p2.y - (p3.y - p1.y) / 6);
    d += ` C${c1x} ${c1y} ${c2x} ${c2y} ${p2.x} ${p2.y}`;
  }
  return d;
}

/** La misma curva cerrada contra la base, para el relleno con degradado. */
export function areaPath(points) {
  if (points.length === 0) return '';
  const first = points.length === 1 ? 0 : points[0].x;
  const last = points.length === 1 ? 100 : points[points.length - 1].x;
  return `${smoothPath(points)} L${last} 100 L${first} 100 Z`;
}

/** Variación porcentual entre dos períodos; `null` si no hay base de comparación. */
export function percentChange(current, previous) {
  if (!(previous > 0)) return null;
  return ((current - previous) / previous) * 100;
}
