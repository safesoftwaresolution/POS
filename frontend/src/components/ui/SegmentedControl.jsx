import React, { useEffect, useLayoutEffect, useRef } from 'react';
import { cx } from './cx';

const DRAG_THRESHOLD_PX = 6;

/**
 * Selector de una opcion entre varias, con una pastilla que se desliza hacia la opcion elegida.
 * Con `swipe` (por defecto) tambien se elige arrastrando el dedo o el mouse por encima de las opciones; si el
 * contenedor puede desplazarse (`scrollable`) el arrastre se deja al scroll nativo.
 * En modo oscuro la pastilla es clara: `bg-solid` se confunde con el fondo del contenedor.
 */
export default function SegmentedControl({
  options, value, onChange, ariaLabel, pill = false, swipe = true, scrollable = false, equal = true, className,
}) {
  const containerRef = useRef(null);
  const indicatorRef = useRef(null);
  const itemRefs = useRef({});
  const drag = useRef({ startX: 0, active: false, moved: false });
  const placed = useRef(false);

  // La posicion se escribe directo en el DOM (no en estado): evita un render extra por cada cambio de opcion.
  const place = () => {
    const el = itemRefs.current[value];
    const indicator = indicatorRef.current;
    if (!el || !indicator) return;
    indicator.style.width = `${el.offsetWidth}px`;
    indicator.style.height = `${el.offsetHeight}px`;
    indicator.style.transform = `translate(${el.offsetLeft}px, ${el.offsetTop}px)`;
    if (scrollable && containerRef.current) {
      const box = containerRef.current;
      const left = el.offsetLeft - (box.clientWidth - el.offsetWidth) / 2;
      box.scrollTo({ left: Math.max(0, left), behavior: placed.current ? 'smooth' : 'auto' });
    }
  };

  useLayoutEffect(() => {
    place();
    if (!placed.current) {
      // La primera colocacion no se anima: la pastilla aparece ya en su sitio.
      const raf = requestAnimationFrame(() => { placed.current = true; });
      return () => cancelAnimationFrame(raf);
    }
  });

  useEffect(() => {
    const box = containerRef.current;
    if (!box || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(() => place());
    observer.observe(box);
    return () => observer.disconnect();
  });

  const idAt = (clientX) => {
    let nearest = null;
    let best = Infinity;
    for (const option of options) {
      const rect = itemRefs.current[option.id]?.getBoundingClientRect();
      if (!rect) continue;
      if (clientX >= rect.left && clientX <= rect.right) return option.id;
      const distance = Math.min(Math.abs(clientX - rect.left), Math.abs(clientX - rect.right));
      if (distance < best) { best = distance; nearest = option.id; }
    }
    return nearest;
  };

  const onPointerDown = (e) => {
    if (!swipe || scrollable || (e.pointerType === 'mouse' && e.button !== 0)) return;
    drag.current = { startX: e.clientX, active: true, moved: false };
  };

  const onPointerMove = (e) => {
    const state = drag.current;
    if (!state.active) return;
    if (!state.moved && Math.abs(e.clientX - state.startX) < DRAG_THRESHOLD_PX) return;
    if (!state.moved) {
      state.moved = true;
      containerRef.current?.setPointerCapture?.(e.pointerId);
    }
    const id = idAt(e.clientX);
    if (id != null && id !== value) onChange(id);
  };

  const endDrag = (e) => {
    if (drag.current.moved) containerRef.current?.releasePointerCapture?.(e.pointerId);
    drag.current = { startX: 0, active: false, moved: false };
  };

  const onKeyDown = (e) => {
    const step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
    if (!step) return;
    e.preventDefault();
    const index = options.findIndex((o) => o.id === value);
    const next = options[(index + step + options.length) % options.length];
    onChange(next.id);
    itemRefs.current[next.id]?.focus();
  };

  const shape = pill ? 'rounded-full' : 'rounded-xl';

  return (
    <div
      ref={containerRef}
      role="tablist"
      aria-label={ariaLabel}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onKeyDown={onKeyDown}
      className={cx(
        'relative flex p-1 bg-gray-100 dark:bg-line border border-gray-200 dark:border-gray-700 max-w-full select-none',
        pill ? 'rounded-full' : 'rounded-2xl',
        scrollable ? 'overflow-x-auto' : 'touch-pan-y',
        className,
      )}
    >
      <span
        ref={indicatorRef}
        aria-hidden="true"
        className={cx(
          'absolute left-0 top-0 bg-solid dark:bg-white shadow-sm pointer-events-none',
          'transition-[transform,width,height] duration-300 ease-[cubic-bezier(0.34,1.2,0.4,1)] motion-reduce:transition-none',
          shape,
        )}
      />
      {options.map((option) => {
        const Icon = option.icon;
        const selected = option.id === value;
        return (
          <button
            key={option.id}
            ref={(el) => { itemRefs.current[option.id] = el; }}
            type="button"
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(option.id)}
            className={cx(
              'relative z-10 px-3 py-2 text-xs font-bold cursor-pointer transition-colors duration-200 flex items-center justify-center gap-1.5',
              shape,
              equal ? 'flex-1 basis-0 text-center' : 'shrink-0',
              selected ? 'text-white dark:text-gray-950' : 'text-ink-soft hover:text-ink',
            )}
          >
            {Icon && <Icon className="w-4 h-4 shrink-0" />}
            <span className="leading-tight">{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}
