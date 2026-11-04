import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Un aviso que se enciende y se apaga solo ("Guardado", "Copiado"): `const [saved, flash] = useTransientFlag(3000)`.
 * `flash()` lo enciende (o lo reinicia); se limpia el temporizador al desmontar, a diferencia de un `setTimeout` suelto.
 */
export default function useTransientFlag(durationMs = 2000) {
  const [on, setOn] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => clearTimeout(timer.current), []);

  const flash = useCallback(() => {
    clearTimeout(timer.current);
    setOn(true);
    timer.current = setTimeout(() => setOn(false), durationMs);
  }, [durationMs]);

  return [on, flash];
}
