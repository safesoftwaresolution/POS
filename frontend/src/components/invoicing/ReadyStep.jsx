import React from 'react';
import { Link } from 'react-router-dom';
import { PartyPopper, ShoppingCart } from 'lucide-react';
import StepMessage from './StepMessage';
import ImportResult from './ImportResult';
import { ENVIRONMENT_LABELS } from './setupSteps';

/** Paso 4: la facturación quedó activa (o suspendida, con su motivo). */
export default function ReadyStep({ setup, step, importResult }) {
  if (step.state !== 'DONE') return <StepMessage step={step} />;
  const sandbox = setup.environment === 'SANDBOX';
  return (
    <div className="space-y-3">
      <div className="p-5 rounded-3xl border border-emerald-200 bg-emerald-50 dark:border-emerald-500/20 dark:bg-emerald-500/10 flex flex-col sm:flex-row sm:items-center gap-4">
        <div className="w-12 h-12 rounded-2xl bg-emerald-500 text-white flex items-center justify-center shrink-0">
          <PartyPopper className="w-6 h-6" />
        </div>
        <div className="flex-1 min-w-0 space-y-1">
          <p className="text-sm font-extrabold text-ink">¡Tu facturación electrónica está activa!</p>
          <p className="text-xs text-ink-soft">
            {step.message} Facturas en el entorno de <strong>{ENVIRONMENT_LABELS[setup.environment] || setup.environment}</strong>
            {sandbox ? ': son facturas de prueba, sin validez ante la DIAN.' : '.'}
          </p>
        </div>
        <Link
          to="/pos"
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl text-xs font-bold bg-solid hover:bg-solid-hover text-solid-ink shrink-0"
        >
          <ShoppingCart className="w-4 h-4" /> Ir al punto de venta
        </Link>
      </div>
      {importResult && <ImportResult result={importResult} />}
    </div>
  );
}
