/** Convierte un mapa `{ etiqueta: monto }` en entradas ordenadas de mayor a menor. */
export default function sortedEntries(map, labelFn = (k) => k) {
  return Object.entries(map || {})
    .map(([label, value]) => ({ label: labelFn(label), value: Number(value) }))
    .sort((a, b) => b.value - a.value);
}
