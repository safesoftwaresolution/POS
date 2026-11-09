import React from 'react';
import { AlertTriangle, CheckCircle2, Clock, Info } from 'lucide-react';
import { cx } from '../ui/cx';

const TONES = {
  DONE: { icon: CheckCircle2, className: 'text-emerald-700 bg-emerald-50 border-emerald-200 dark:text-emerald-400 dark:bg-emerald-500/10 dark:border-emerald-500/20' },
  CURRENT: { icon: Info, className: 'text-blue-700 bg-blue-50 border-blue-200 dark:text-blue-400 dark:bg-blue-500/20 dark:border-blue-500/20' },
  PENDING: { icon: Clock, className: 'text-ink-soft bg-page border-line' },
  ERROR: { icon: AlertTriangle, className: 'text-red-600 bg-red-50 border-red-200 dark:text-red-400 dark:bg-red-500/20 dark:border-red-500/20' },
};

/**
 * Mensaje del paso tal como lo redacta el backend. Si la plataforma aún no puede habilitar en producción
 * (`platformWaiting`), muestra un aviso tranquilo en lugar de un error: los datos quedaron guardados.
 */
export default function StepMessage({ step, platformWaiting = false }) {
  if (platformWaiting) {
    return (
      <div role="status" className={cx('p-4 rounded-2xl border text-xs font-semibold flex items-start gap-2.5', TONES.CURRENT.className)}>
        <Clock className="w-4 h-4 shrink-0 mt-0.5" />
        <span>Guardamos tus datos. Te avisaremos cuando la facturación esté disponible.</span>
      </div>
    );
  }
  if (!step?.message) return null;
  const tone = TONES[step.state] || TONES.PENDING;
  const Icon = tone.icon;
  return (
    <div
      role={step.state === 'ERROR' ? 'alert' : 'status'}
      className={cx('p-4 rounded-2xl border text-xs font-semibold flex items-start gap-2.5', tone.className)}
    >
      <Icon className="w-4 h-4 shrink-0 mt-0.5" />
      <span>{step.message}</span>
    </div>
  );
}
