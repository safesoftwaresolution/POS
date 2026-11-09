import React, { useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { Badge, Card, Stepper } from '../ui';
import useLoad from '../../hooks/useLoad';
import { getBillingSetupApi } from '../../services/billingApi';
import FiscalDataStep from './FiscalDataStep';
import DianEnablementStep from './DianEnablementStep';
import NumberingStep from './NumberingStep';
import ReadyStep from './ReadyStep';
import { ACTIVATION_LABELS } from './constants';
import { ENVIRONMENT_LABELS, SETUP_STEP_LABELS, focusStepKey, toStepperSteps } from './setupSteps';

const STATUS_TONES = { NOT_REQUESTED: 'neutral', IN_SETUP: 'warning', ACTIVE: 'success', SUSPENDED: 'danger' };

/**
 * Asistente de habilitación de la facturación electrónica del local, guiado por GET /billing/setup.
 * `canEdit` (administrador) muestra los formularios; el supervisor solo ve el avance.
 * `onChanged` avisa a la página para que recargue el estado y los rangos.
 */
export default function BillingSetupWizard({ canEdit, refreshKey, onChanged }) {
  const { data: setup, loading, error, reload, setData } = useLoad(getBillingSetupApi, [refreshKey], {
    errorMessage: 'No pudimos consultar el avance de tu facturación electrónica.',
  });
  const [selectedKey, setSelectedKey] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  if (loading && !setup) {
    return <Card><p className="text-xs font-bold text-ink-muted text-center py-6">Consultando tu facturación electrónica...</p></Card>;
  }
  if (!setup) {
    return <Card><p role="alert" className="text-xs font-semibold text-red-600 dark:text-red-400 text-center py-6">{error}</p></Card>;
  }

  const steps = setup.steps || [];
  const focusKey = focusStepKey(steps);
  const shownKey = steps.some((s) => s.key === selectedKey) ? selectedKey : focusKey;
  const step = steps.find((s) => s.key === shownKey);

  // Cada acción devuelve el asistente ya actualizado: se muestra y se vuelve al paso que toca.
  const applySetup = (next) => {
    setData(next);
    setSelectedKey(null);
    onChanged?.();
  };

  const refresh = async () => {
    setRefreshing(true);
    try {
      applySetup(await getBillingSetupApi());
    } catch {
      reload();
    } finally {
      setRefreshing(false);
    }
  };

  const onRangesChanged = () => {
    reload();
    onChanged?.();
  };

  return (
    <Card className="space-y-6">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-solid text-brand flex items-center justify-center shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h3 className="font-extrabold text-sm text-ink">Activa tu facturación electrónica</h3>
            <p className="text-[11px] text-ink-soft font-medium">
              Cuatro pasos, a tu ritmo. Guardamos tu avance en cada uno.
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {setup.status === 'ACTIVE' && setup.environment === 'SANDBOX' && <Badge tone="warning">Pruebas</Badge>}
          <Badge tone={STATUS_TONES[setup.status] || 'neutral'}>{ACTIVATION_LABELS[setup.status] || 'Sin empezar'}</Badge>
        </div>
      </div>

      <Stepper
        ariaLabel="Pasos para activar la facturación electrónica"
        steps={toStepperSteps(steps)}
        selectedKey={shownKey}
        onStepClick={setSelectedKey}
      />

      {step && (
        <section aria-labelledby="billing-step-title" className="pt-5 border-t border-line space-y-4">
          <h4 id="billing-step-title" className="font-extrabold text-sm text-ink">{SETUP_STEP_LABELS[step.key]}</h4>
          {step.key === 'FISCAL_DATA' && (
            // `key`: si el perfil cambia en el servidor, el formulario vuelve a partir de él.
            <FiscalDataStep key={JSON.stringify(setup.profile)} setup={setup} step={step} canEdit={canEdit} onSaved={applySetup} />
          )}
          {step.key === 'DIAN_ENABLEMENT' && (
            <DianEnablementStep step={step} canEdit={canEdit} onSaved={applySetup} onRefresh={refresh} refreshing={refreshing} />
          )}
          {step.key === 'NUMBERING' && (
            <NumberingStep step={step} canEdit={canEdit} onSaved={applySetup} onRangesChanged={onRangesChanged} />
          )}
          {step.key === 'READY' && <ReadyStep setup={setup} step={step} />}
        </section>
      )}

      {setup.environment && setup.status !== 'NOT_REQUESTED' && (
        <p className="text-[11px] text-ink-muted">
          Entorno: {ENVIRONMENT_LABELS[setup.environment] || setup.environment}
          {setup.environment === 'SANDBOX' ? ' (las facturas son de prueba)' : ''}.
        </p>
      )}
    </Card>
  );
}
