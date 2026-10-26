import { useEffect, useState } from 'react';

/** Devuelve `value` pero solo después de `delayMs` sin cambios: para buscar en el servidor sin una consulta por tecla. */
export default function useDebouncedValue(value, delayMs = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);
  return debounced;
}
