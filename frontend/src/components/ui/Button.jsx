import React from 'react';
import { cx } from './cx';

const VARIANTS = {
  solid: 'bg-solid hover:bg-solid-hover text-solid-ink shadow-md shadow-[#0A0A0A]/10',
  brand: 'bg-brand hover:bg-brand-hover text-white shadow-md shadow-brand/20',
  soft: 'bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300',
  outline: 'border border-line hover:border-line-strong text-ink bg-card',
  danger: 'bg-red-500 hover:bg-red-600 text-white shadow-md shadow-red-500/20',
  ghost: 'text-ink-soft hover:text-ink hover:bg-gray-100 dark:hover:bg-white/5',
};

const SIZES = {
  sm: 'px-3 py-1.5 text-[11px] rounded-xl',
  md: 'px-4 py-2.5 text-xs rounded-2xl',
  lg: 'px-5 py-3 text-sm rounded-2xl',
};

/** Botón estándar. `icon` es un componente de lucide-react. */
export default function Button({
  variant = 'solid', size = 'md', icon: Icon, loading = false, className, children, disabled, type = 'button', ...rest
}) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cx(
        'inline-flex items-center justify-center gap-2 font-bold cursor-pointer transition-all',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        VARIANTS[variant], SIZES[size], className,
      )}
      {...rest}
    >
      {Icon && <Icon className="w-4 h-4" />}
      {loading ? 'Procesando...' : children}
    </button>
  );
}
