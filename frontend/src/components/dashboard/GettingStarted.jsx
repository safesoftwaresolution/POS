import React from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Circle, CircleDashed, Rocket } from 'lucide-react';
import useLoad from '../../hooks/useLoad';
import { getSettingsApi } from '../../services/settingsApi';
import { getBillingActivationStatusApi } from '../../services/billingApi';
import { gettingStartedSteps, isBusinessProfileComplete } from '../../utils/gettingStarted';

// Cada consulta falla por separado: si no se sabe el estado de un paso, ese paso simplemente no se muestra.
const loadAdminState = async () => {
  const [settings, activation] = await Promise.all([
    getSettingsApi().catch(() => undefined),
    getBillingActivationStatusApi().catch(() => undefined),
  ]);
  return {
    profileComplete: settings === undefined ? null : isBusinessProfileComplete(settings),
    billingActive: activation === undefined ? null : activation?.status === 'ACTIVE',
  };
};

/**
 * Guía de primer uso: una franja delgada que aparece mientras falte un paso obligatorio (datos del negocio, primer
 * producto, primera venta) y se oculta sola al completarlos; la facturación electrónica es opcional y no la retiene.
 * `hasSales` cuenta las ventas de toda la vida, no las del rango del Dashboard. `canCreateProducts` oculta el paso del
 * producto a quien no puede crearlos; los pasos del negocio y la facturación son solo del ADMINISTRATOR.
 */
export default function GettingStarted({ hasProducts, hasSales, canCreateProducts, isAdmin = false }) {
  const navigate = useNavigate();
  const { data: adminState, loading } = useLoad(loadAdminState, [], {
    initial: { profileComplete: null, billingActive: null },
    enabled: isAdmin,
  });

  if (loading) return null;

  const steps = gettingStartedSteps({ hasProducts, hasSales, canCreateProducts, isAdmin, ...adminState });
  const required = steps.filter((s) => !s.optional);
  if (required.every((s) => s.done)) return null;

  const next = required.find((s) => !s.done);
  const completed = required.filter((s) => s.done).length;

  return (
    <div className="bento-card hover:transform-none p-4 sm:px-6 border-brand/30 flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-6">
      <div className="flex items-center gap-3 min-w-0 flex-1">
        <div className="w-10 h-10 rounded-2xl bg-brand/10 text-brand flex items-center justify-center shrink-0"><Rocket className="w-5 h-5" /></div>
        <div className="min-w-0">
          <p className="text-sm font-black text-ink">Empieza aquí · {completed} de {required.length}</p>
          <p className="text-xs text-ink-soft font-medium truncate"><span className="font-bold text-ink">{next.label}.</span> {next.hint}</p>
        </div>
      </div>
      <ul className="flex items-center gap-1.5" aria-label="Progreso">
        {steps.map((s) => {
          const Icon = s.done ? CheckCircle2 : (s.optional ? CircleDashed : Circle);
          return (
            <li key={s.to}>
              <button
                type="button"
                title={s.label}
                aria-label={s.label}
                onClick={() => navigate(s.to)}
                className="block cursor-pointer"
              >
                <Icon className={`w-5 h-5 ${s.done ? 'text-emerald-500' : 'text-ink-muted'}`} />
              </button>
            </li>
          );
        })}
      </ul>
      <button
        type="button"
        onClick={() => navigate(next.to)}
        className="px-4 py-2 bg-brand text-white text-xs font-extrabold rounded-full cursor-pointer shrink-0"
      >
        Continuar
      </button>
    </div>
  );
}
