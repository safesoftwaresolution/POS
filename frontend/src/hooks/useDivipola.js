import { useCallback, useMemo } from 'react';
import useLoad from './useLoad';
import { getDivipolaApi } from '../services/billingApi';
import {
  buildDivipolaIndex,
  findMunicipality as findIn,
  municipalitiesOf as municipalitiesIn,
  searchMunicipalities,
} from '../utils/divipola';

// El catálogo (1.122 municipios) no cambia durante la sesión: se pide una sola vez y lo comparten todos los
// formularios. Si la consulta falla se olvida la promesa para reintentar la próxima vez.
let cached = null;
let pending = null;

function loadDivipola() {
  if (cached) return Promise.resolve(cached);
  if (!pending) {
    pending = getDivipolaApi()
      .then((catalog) => {
        cached = buildDivipolaIndex(catalog);
        return cached;
      })
      .finally(() => { pending = null; });
  }
  return pending;
}

const EMPTY_INDEX = buildDivipolaIndex(null);

/**
 * Departamentos y municipios de Colombia (DIVIPOLA del DANE) para elegir el municipio por nombre:
 *
 *   const { departments, municipalitiesOf, findMunicipality, search, loading, error } = useDivipola();
 *
 * - `municipalitiesOf(departmentCode)`: municipios del departamento, por nombre.
 * - `findMunicipality(code)`: municipio (con `department`) por código DANE; acepta el número sin el cero inicial.
 * - `search(text)`: búsqueda sin tildes por nombre, departamento o código (máx. 20).
 */
export default function useDivipola() {
  const { data, loading, error } = useLoad(loadDivipola, [], {
    initial: cached || EMPTY_INDEX,
    errorMessage: 'No se pudo cargar la lista de municipios.',
  });
  const index = data || EMPTY_INDEX;

  const municipalitiesOf = useCallback((departmentCode) => municipalitiesIn(index, departmentCode), [index]);
  const findMunicipality = useCallback((code) => findIn(index, code), [index]);
  const search = useCallback((text, limit) => searchMunicipalities(index, text, limit), [index]);

  return useMemo(() => ({
    departments: index.departments,
    municipalitiesOf,
    findMunicipality,
    search,
    loading,
    error,
  }), [index, municipalitiesOf, findMunicipality, search, loading, error]);
}
