import React, { useEffect, useRef } from 'react';
import { BENTO_PATH, POS_PATH } from '../../constants/brandWordmark';

const ORANGE = '#FF5520';
const SPLASH_MS = 2800;
const REDUCED_MS = 900;

// Los cuatro bloques del isotipo con el vector de donde llegan volando (--fx/--fy) y su orden de entrada (--i).
const BLOCKS = [
  { x: 21, y: 21, fill: ORANGE, fx: '-70px', fy: '-70px' },
  { x: 51, y: 21, fill: '#DADADA', fx: '70px', fy: '-70px' },
  { x: 21, y: 51, fill: '#DADADA', fx: '-70px', fy: '70px' },
  { x: 51, y: 51, fill: '#532011', fx: '70px', fy: '70px' },
];

/**
 * Bienvenida tras iniciar sesión: el isotipo de PosBento se arma con sus cuatro bloques, aparece el punto de marca y se
 * revela el logotipo con el saludo. Llama a `onDone` al terminar (~2,8 s), o antes si el usuario hace clic o pulsa
 * Enter/Escape; con "reducir movimiento" no anima y dura menos.
 */
export default function WelcomeSplash({ greeting, onDone }) {
  const doneRef = useRef(onDone);
  useEffect(() => { doneRef.current = onDone; });

  useEffect(() => {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    const timer = setTimeout(() => doneRef.current(), reduced ? REDUCED_MS : SPLASH_MS);
    const onKey = (e) => { if (e.key === 'Enter' || e.key === 'Escape') doneRef.current(); };
    window.addEventListener('keydown', onKey);
    return () => { clearTimeout(timer); window.removeEventListener('keydown', onKey); };
  }, []);

  return (
    <div
      className="welcome-splash fixed inset-0 z-[100] bg-[#0E0E12] flex flex-col items-center justify-center gap-6 cursor-pointer select-none"
      role="status"
      aria-live="polite"
      onClick={() => doneRef.current()}
    >
      <svg viewBox="-6 -6 353.86 108" aria-hidden="true" className="w-[min(86vw,520px)] h-auto overflow-visible">
        <defs>
          <radialGradient id="ws-glow-grad">
            <stop offset="0" stopColor={ORANGE} stopOpacity="1" />
            <stop offset="1" stopColor={ORANGE} stopOpacity="0" />
          </radialGradient>
          <clipPath id="ws-word-clip">
            <rect className="ws-word-clip" x="120" y="0" width="240" height="100" style={{ transformBox: 'fill-box', transformOrigin: 'left' }} />
          </clipPath>
        </defs>

        <circle className="ws-glow" cx="48" cy="48" r="90" fill="url(#ws-glow-grad)" />

        <g>
          <rect className="ws-box ws-tile" width="96" height="96" rx="32" fill="#0A0A0A" stroke="#272730" strokeWidth="1.5" />
          {BLOCKS.map((b, i) => (
            <rect
              key={i}
              className="ws-box ws-sq"
              x={b.x} y={b.y} width="24" height="24" rx="6" fill={b.fill}
              style={{ '--i': i, '--fx': b.fx, '--fy': b.fy }}
            />
          ))}
          <circle className="ws-box ws-ripple" cx="91.5" cy="4.5" r="7.5" fill="none" stroke={ORANGE} strokeWidth="2" />
          <g className="ws-box ws-ring">
            <circle cx="91.5" cy="4.5" r="10.5" fill="#FFFFFF" />
            <circle cx="91.5" cy="4.5" r="7.5" fill={ORANGE} />
          </g>
        </g>

        <g clipPath="url(#ws-word-clip)">
          <g className="ws-word">
            <path d={POS_PATH} fill={ORANGE} />
            <path d={BENTO_PATH} fill="#FFFFFF" />
          </g>
        </g>
      </svg>

      <p className="ws-greeting text-base sm:text-lg font-bold text-white/90 text-center px-6">{greeting}</p>

      <div className="absolute bottom-0 left-0 right-0 h-1 bg-white/5" aria-hidden="true">
        <div className="ws-bar h-full bg-[#FF5520]" />
      </div>
    </div>
  );
}
