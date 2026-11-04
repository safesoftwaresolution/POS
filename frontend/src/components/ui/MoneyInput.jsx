import React from 'react';
import { Field, inputClass } from './Field';
import { cx } from './cx';
import { formatCop } from '../../utils/format';

/**
 * Campo de dinero en pesos colombianos: se ve "10.000" mientras se escribe, pero `value` y `onChange`
 * manejan solo los dígitos ("10000"), que es lo que se envía al backend. `onChange` recibe el string
 * ya limpio, no el evento.
 */
export default function MoneyInput({ label, hint, error, className, value, onChange, ...rest }) {
  const control = (
    <input
      {...rest}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      value={formatCop(value)}
      onChange={(e) => onChange(e.target.value.replace(/\D/g, ''))}
      className={cx(inputClass, error && 'border-red-400', className)}
    />
  );
  if (!label && !hint && !error) return control;
  return <Field label={label} hint={hint} error={error}>{control}</Field>;
}
