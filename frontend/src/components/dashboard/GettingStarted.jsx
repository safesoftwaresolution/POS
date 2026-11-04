import React from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Circle, Rocket } from 'lucide-react';

/**
 * Guía de primer uso: una franja delgada que aparece mientras falte crear un producto o hacer la primera venta
 * y se oculta sola al completarse. `hasSales` cuenta las ventas de toda la vida, no las del rango del Dashboard.
 * `canCreateProducts` oculta el paso 1 a quien no puede crearlos.
 */
export default function GettingStarted({ hasProducts, hasSales, canCreateProducts }) {
  const navigate = useNavigate();
  const steps = [
    { done: hasProducts, label: 'Crea tu primer producto', hint: 'Nombre, precio y cantidad en inventario.', to: '/products', show: canCreateProducts },
    { done: hasSales, label: 'Haz tu primera venta', hint: 'Entra al Punto de Venta, elige productos y cobra.', to: '/pos', show: true },
  ].filter((s) => s.show);

  if (steps.every((s) => s.done)) return null;

  const next = steps.find((s) => !s.done);
  const completed = steps.filter((s) => s.done).length;

  return (
    <div className="bento-card hover:transform-none p-4 sm:px-6 border-brand/30 flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-6">
      <div className="flex items-center gap-3 min-w-0 flex-1">
        <div className="w-10 h-10 rounded-2xl bg-brand/10 text-brand flex items-center justify-center shrink-0"><Rocket className="w-5 h-5" /></div>
        <div className="min-w-0">
          <p className="text-sm font-black text-ink">Empieza aquí · {completed} de {steps.length}</p>
          <p className="text-xs text-ink-soft font-medium truncate"><span className="font-bold text-ink">{next.label}.</span> {next.hint}</p>
        </div>
      </div>
      <ul className="flex items-center gap-1.5" aria-label="Progreso">
        {steps.map((s) => (
          <li key={s.to} title={s.label}>
            {s.done ? <CheckCircle2 className="w-5 h-5 text-emerald-500" /> : <Circle className="w-5 h-5 text-ink-muted" />}
          </li>
        ))}
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
