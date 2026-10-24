import { useEffect, useRef } from 'react';

/**
 * Vuelve a ejecutar `refresh` cada `intervalMs` mientras la pestaña está visible y justo al volver a ella.
 * Pensado para paneles que deben reflejar cambios de otros usuarios (p. ej. nuevos registros en el
 * backoffice) sin recargar: `refresh` debe ser silencioso (sin pantalla de "Cargando...") y tolerar fallos.
 */
export default function useAutoRefresh(refresh, intervalMs = 15000) {
  const refreshRef = useRef(refresh);

  useEffect(() => { refreshRef.current = refresh; }, [refresh]);

  useEffect(() => {
    const tick = () => {
      if (document.visibilityState === 'visible') refreshRef.current();
    };
    const id = setInterval(tick, intervalMs);
    document.addEventListener('visibilitychange', tick);
    return () => {
      clearInterval(id);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [intervalMs]);
}
