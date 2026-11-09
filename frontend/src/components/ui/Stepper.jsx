import React from 'react';
import { AlertTriangle, Check } from 'lucide-react';
import { cx } from './cx';

const STATE_TEXT = {
  done: 'completado',
  current: 'paso actual',
  pending: 'pendiente',
  error: 'requiere atención',
};

const BUBBLE = {
  done: 'bg-emerald-500 text-white border-emerald-500',
  current: 'bg-brand text-white border-brand shadow-md shadow-brand/20',
  pending: 'bg-page text-ink-muted border-line',
  error: 'bg-red-500 text-white border-red-500',
};

const LABEL = {
  done: 'text-ink',
  current: 'text-ink',
  pending: 'text-ink-muted',
  error: 'text-red-600 dark:text-red-400',
};

/**
 * Pasos de un proceso: vertical en móvil, horizontal desde `md`. Cada paso es
 * `{ key, label, state: 'done' | 'current' | 'pending' | 'error', description? }`.
 * Con `onStepClick` los pasos que no están pendientes se pueden abrir; `selectedKey` marca el que se está viendo.
 */
export default function Stepper({ steps, selectedKey, onStepClick, ariaLabel = 'Pasos', className }) {
  return (
    <nav aria-label={ariaLabel} className={className}>
      <ol className="flex flex-col md:flex-row gap-3 md:gap-0">
        {steps.map((step, i) => {
          const state = STATE_TEXT[step.state] ? step.state : 'pending';
          const clickable = Boolean(onStepClick) && state !== 'pending';
          const selected = selectedKey === step.key;
          const last = i === steps.length - 1;
          const content = (
            <>
              <span
                aria-hidden="true"
                className={cx(
                  'w-8 h-8 rounded-full border-2 flex items-center justify-center text-xs font-black shrink-0 transition-all',
                  BUBBLE[state],
                  selected && 'ring-2 ring-offset-2 ring-brand/40 ring-offset-card',
                )}
              >
                {state === 'done' ? <Check className="w-4 h-4" /> : state === 'error' ? <AlertTriangle className="w-4 h-4" /> : i + 1}
              </span>
              <span className="min-w-0 text-left">
                <span className={cx('block text-xs font-extrabold', LABEL[state])}>{step.label}</span>
                <span className="sr-only"> ({STATE_TEXT[state]})</span>
                {step.description && (
                  <span className="block text-[11px] font-medium text-ink-soft md:hidden">{step.description}</span>
                )}
              </span>
            </>
          );
          return (
            <li
              key={step.key}
              aria-current={state === 'current' ? 'step' : undefined}
              className="flex md:flex-1 md:items-center gap-3 min-w-0"
            >
              {clickable ? (
                <button
                  type="button"
                  onClick={() => onStepClick(step.key)}
                  aria-pressed={selected}
                  className="flex items-center gap-3 min-w-0 rounded-2xl p-1 -m-1 cursor-pointer hover:bg-gray-100 dark:hover:bg-white/5"
                >
                  {content}
                </button>
              ) : (
                <div className="flex items-center gap-3 min-w-0">{content}</div>
              )}
              {!last && (
                <span
                  aria-hidden="true"
                  className={cx(
                    'hidden md:block flex-1 h-0.5 mx-3 rounded-full',
                    state === 'done' ? 'bg-emerald-500' : 'bg-line',
                  )}
                />
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
