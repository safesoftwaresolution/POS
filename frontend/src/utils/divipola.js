// Búsquedas sobre el catálogo DIVIPOLA del DANE (departamentos y municipios) que sirve
// GET /api/v1/billing/catalogs/divipola. Funciones puras: el hook useDivipola las usa con el catálogo cargado.

/** Texto para comparar: sin tildes, en minúsculas y sin espacios en los extremos. */
export function normalizeText(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

/** Código DANE de 5 dígitos. Recupera el cero inicial que se pierde al guardarlo como número (5001 → '05001'). */
export function toDaneCode(value) {
  const digits = String(value ?? '').replace(/\D/g, '');
  if (!digits) return '';
  return digits.padStart(5, '0');
}

const byName = (a, b) => a.name.localeCompare(b.name, 'es');

/** Prepara el catálogo para buscar rápido: departamentos ordenados, municipios por código y por departamento. */
export function buildDivipolaIndex(catalog) {
  const departments = [...(catalog?.departments || [])].sort(byName);
  const departmentsByCode = new Map(departments.map((d) => [d.code, d]));
  const municipalities = (catalog?.municipalities || []).map((m) => {
    const department = departmentsByCode.get(m.departmentCode) || null;
    return {
      ...m,
      department,
      searchText: normalizeText(`${m.name} ${department?.name || ''} ${m.code}`),
      nameText: normalizeText(m.name),
    };
  });
  const byCode = new Map(municipalities.map((m) => [m.code, m]));
  const byDepartment = new Map();
  for (const m of municipalities) {
    if (!byDepartment.has(m.departmentCode)) byDepartment.set(m.departmentCode, []);
    byDepartment.get(m.departmentCode).push(m);
  }
  for (const list of byDepartment.values()) list.sort(byName);
  return { departments, departmentsByCode, municipalities, byCode, byDepartment };
}

/** Municipios del departamento, ordenados por nombre. */
export function municipalitiesOf(index, departmentCode) {
  return index.byDepartment.get(departmentCode) || [];
}

/** Municipio (con su `department`) por código DANE, o null. */
export function findMunicipality(index, code) {
  const dane = toDaneCode(code);
  return (dane && index.byCode.get(dane)) || null;
}

/**
 * Municipios cuyo nombre, departamento o código contiene el texto (sin importar tildes ni mayúsculas). Primero los
 * que empiezan por el texto; luego por nombre. Sin texto no devuelve nada.
 */
export function searchMunicipalities(index, text, limit = 20) {
  const query = normalizeText(text);
  if (!query) return [];
  return index.municipalities
    .filter((m) => m.searchText.includes(query))
    .map((m) => ({ m, starts: m.nameText.startsWith(query) ? 0 : 1 }))
    .sort((a, b) => a.starts - b.starts || byName(a.m, b.m))
    .slice(0, limit)
    .map(({ m }) => m);
}
