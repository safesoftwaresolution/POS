import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import { cx } from './cx';

const WIDTHS = { sm: 'max-w-sm', md: 'max-w-md', lg: 'max-w-2xl', xl: 'max-w-4xl' };

/** Diálogo modal base: overlay, cierre con Escape / clic fuera y cabecera opcional. */
// `dismissible={false}` desactiva el cierre con Escape / clic fuera (útil en formularios, para no perder lo escrito).
export default function Modal({ title, subtitle, onClose, size = 'md', footer, dismissible = true, children }) {
  useEffect(() => {
    if (!dismissible) return undefined;
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose, dismissible]);

  return (
    <div
      className="fixed inset-0 bg-black/60 backdrop-blur-xs z-100 flex items-center justify-center p-4"
      onMouseDown={(e) => dismissible && e.target === e.currentTarget && onClose?.()}
      role="dialog"
      aria-modal="true"
    >
      <div className={cx('bento-card w-full max-h-[90vh] flex flex-col shadow-2xl', WIDTHS[size])}>
        {(title || onClose) && (
          <div className="flex items-start justify-between gap-4 p-6 pb-4">
            <div className="min-w-0">
              {title && <h3 className="font-extrabold text-base text-ink">{title}</h3>}
              {subtitle && <p className="text-xs text-ink-soft mt-0.5">{subtitle}</p>}
            </div>
            {onClose && (
              <button
                type="button"
                onClick={onClose}
                aria-label="Cerrar"
                className="p-1.5 rounded-xl text-ink-muted hover:text-ink hover:bg-gray-100 dark:hover:bg-white/5 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
        )}
        <div className="px-6 pb-6 overflow-y-auto space-y-4">{children}</div>
        {footer && <div className="px-6 py-4 border-t border-line flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}
