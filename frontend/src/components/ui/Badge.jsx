import React from 'react';
import { cx } from './cx';

// Colores semánticos de DESIGN.md §2.4
const TONES = {
  success: 'text-emerald-600 bg-emerald-50 border-emerald-200 dark:text-emerald-400 dark:bg-emerald-500/10 dark:border-emerald-500/20',
  warning: 'text-amber-700 bg-amber-50 border-amber-200 dark:text-amber-400 dark:bg-amber-500/20 dark:border-amber-500/20',
  danger: 'text-red-600 bg-red-50 border-red-200 dark:text-red-400 dark:bg-red-500/20 dark:border-red-500/20',
  info: 'text-blue-700 bg-blue-50 border-blue-200 dark:text-blue-400 dark:bg-blue-500/20 dark:border-blue-500/20',
  brand: 'text-brand bg-brand/10 border-brand/30',
  neutral: 'text-ink-soft bg-gray-100 border-line dark:bg-white/5',
};

export default function Badge({ tone = 'neutral', className, children }) {
  return (
    <span className={cx(
      'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border text-[10px] font-extrabold',
      TONES[tone], className,
    )}>
      {children}
    </span>
  );
}
