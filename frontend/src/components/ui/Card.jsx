import React from 'react';
import { cx } from './cx';

/** Tarjeta Bento (24px de radio, tokens de tema). */
export default function Card({ as: Tag = 'div', padded = true, className, children, ...rest }) {
  return (
    <Tag className={cx('bento-card', padded && 'p-6', className)} {...rest}>
      {children}
    </Tag>
  );
}
