import { useCallback, useEffect, useRef, useState } from 'react';
import useAutoRefresh from './useAutoRefresh';

const sameDeps = (a, b) => a.length === b.length && a.every((v, i) => Object.is(v, b[i]));

/**
 * Carga de datos de una pantalla: reemplaza el trío `loading` + `error` + `useEffect` que se repetía en cada página.
 *
 *   const { data, loading, error, reload } = useLoad(
 *     () => searchSalesApi({ dateFrom, dateTo, page }),
 *     [dateFrom, dateTo, page],
 *     { initial: EMPTY_PAGE, autoRefresh: true },
 *   );
 *
 * - `fetcher` devuelve una promesa (para varias llamadas: `() => Promise.all([...])`). Recibe como argumento lo que
 *   había cargado antes, por si quiere conservar un bloque cuya consulta falló.
 * - `deps` son las variables de las que depende la consulta; al cambiar, vuelve a cargar y `loading` pasa a true.
 * - `reload()` vuelve a consultar sin "Cargando...": conserva lo que ya se ve, y si falla lo ignora.
 *   `reload({ silent: false })` sí muestra el estado de carga (botones "Actualizar").
 * - Si llega una respuesta vieja (cambiaste de página antes de que terminara), se descarta.
 * - `autoRefresh` repite `reload` cada 15 s mientras la pestaña está visible (ver `useAutoRefresh`); un número
 *   (`autoRefresh: 45000`) cambia el intervalo en milisegundos.
 * - `enabled: false` pospone la carga (p. ej. hasta conocer el rol del usuario).
 *
 * `loading` se deriva al renderizar (sin `setState` dentro del efecto), por eso no depende de ningún truco de lint.
 */
export default function useLoad(fetcher, deps = [], {
  initial = null,
  autoRefresh = false,
  enabled = true,
  errorMessage = 'No se pudo cargar la información.',
} = {}) {
  const [result, setResult] = useState({ data: initial, error: '', settled: false });
  const [version, setVersion] = useState(0);
  const [prevDeps, setPrevDeps] = useState(deps);
  const fetcherRef = useRef(fetcher);
  const dataRef = useRef(result.data);

  // Cambiaron las dependencias: lo que hay en pantalla ya no corresponde, así que vuelve a "cargando".
  // Ajustar estado durante el render es el patrón que React recomienda para derivarlo de props.
  if (!sameDeps(prevDeps, deps)) {
    setPrevDeps(deps);
    setResult((r) => ({ ...r, error: '', settled: false }));
  }

  useEffect(() => { fetcherRef.current = fetcher; }, [fetcher]);
  useEffect(() => { dataRef.current = result.data; }, [result.data]);

  useEffect(() => {
    if (!enabled) return undefined;
    let stale = false;
    Promise.resolve()
      .then(() => fetcherRef.current(dataRef.current))
      .then((data) => {
        if (!stale) setResult({ data, error: '', settled: true });
      })
      .catch((e) => {
        if (stale) return;
        // Un reload silencioso que falla deja la pantalla como estaba; una carga nueva sí muestra el error.
        setResult((r) => (r.settled ? r : { data: initial, error: e?.message || errorMessage, settled: true }));
      });
    return () => { stale = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `deps` es la lista del llamador; `initial`/`errorMessage` son constantes de la llamada
  }, [...deps, version, enabled]);

  // `reload()` es silencioso; `reload({ silent: false })` (botón "Actualizar") muestra "Cargando..." de nuevo.
  const reload = useCallback(({ silent = true } = {}) => {
    if (!silent) setResult((r) => ({ ...r, error: '', settled: false }));
    setVersion((v) => v + 1);
  }, []);
  useAutoRefresh(reload, typeof autoRefresh === 'number' ? autoRefresh : 15000, Boolean(autoRefresh) && enabled);

  // Mientras `enabled` es false no hay nada que esperar; el spinner solo aplica a una carga en curso.
  const setData = useCallback(
    (updater) => setResult((r) => ({ ...r, data: typeof updater === 'function' ? updater(r.data) : updater })),
    [],
  );

  return { data: result.data, loading: enabled && !result.settled, error: result.error, reload, setData };
}
