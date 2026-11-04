import { useEffect, useRef, useState } from 'react';

const prefersReducedMotion = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** Anima un número desde su valor anterior hasta `target` (sube al cargar, como en un tablero). Con "reducir movimiento" no anima. */
export default function useCountUp(target, duration = 800) {
  const [value, setValue] = useState(0);
  const from = useRef(0);
  const reduced = prefersReducedMotion();

  useEffect(() => {
    if (reduced) return undefined;
    const begin = from.current;
    const startedAt = performance.now();
    let raf = 0;
    const tick = (now) => {
      const t = Math.min((now - startedAt) / duration, 1);
      const eased = 1 - (1 - t) ** 3;
      from.current = begin + (target - begin) * eased;
      setValue(from.current);
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, duration, reduced]);

  return reduced ? target : value;
}
