import React from 'react';
import { cx } from './cx';

export const inputClass =
  'w-full px-3.5 py-2.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink ' +
  'placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink disabled:opacity-60';

/** Etiqueta + control + mensaje de error. Pasa el control como children, o usa `Input` / `Select`. */
export function Field({ label, hint, error, className, children }) {
  return (
    <label className={cx('block space-y-1.5', className)}>
      {label && <span className="block text-[11px] font-bold uppercase tracking-wider text-ink-soft">{label}</span>}
      {children}
      {error
        ? <span className="block text-[11px] font-semibold text-red-500">{error}</span>
        : hint && <span className="block text-[11px] text-ink-muted">{hint}</span>}
    </label>
  );
}

export function Input({ label, hint, error, className, ...rest }) {
  return (
    <Field label={label} hint={hint} error={error}>
      <input className={cx(inputClass, error && 'border-red-400', className)} {...rest} />
    </Field>
  );
}

export function Select({ label, hint, error, className, children, ...rest }) {
  return (
    <Field label={label} hint={hint} error={error}>
      <select className={cx(inputClass, className)} {...rest}>{children}</select>
    </Field>
  );
}
