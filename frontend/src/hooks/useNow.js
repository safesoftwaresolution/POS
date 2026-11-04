import { useEffect, useState } from 'react';

/** La fecha y hora actuales, actualizadas cada `intervalMs` (por defecto 1 min): para mostrar "hoy" sin llamar a `new Date()` al renderizar. */
export default function useNow(intervalMs = 60000) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
