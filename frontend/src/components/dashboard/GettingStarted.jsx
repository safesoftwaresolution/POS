import React from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Circle, Rocket } from 'lucide-react';

/**
 * Guía de primer uso: aparece mientras el local no tenga productos o ventas y se oculta sola al completarse.
 * `hasProducts`/`hasSales` vienen del Dashboard; `canCreateProducts` oculta el paso 1 a quien no puede crearlos.
 */
export default function GettingStarted({ hasProducts, hasSales, canCreateProducts }) {
  const navigate = useNavigate();
  const steps = [
    { done: hasProducts, label: 'Crea tu primer producto', hint: 'Nombre, precio y cantidad en inventario.', to: '/products', show: canCreateProducts },
    { done: hasSales, label: 'Haz tu primera venta', hint: 'Entra al Punto de Venta, elige productos y cobra.', to: '/pos', show: true },
  ].filter((s) => s.show);

  if (steps.every((s) => s.done)) return null;

  return (
    <div className="bento-card p-5 space-y-3 border-brand/30">
      <h2 className="flex items-center gap-2 font-black text-sm text-ink">
        <Rocket className="w-4 h-4 text-brand" /> Empieza aquí
      </h2>
      <ul className="space-y-2">
        {steps.map((s) => (
          <li key={s.to} className="flex items-center gap-3">
            {s.done
              ? <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0" />
              : <Circle className="w-5 h-5 text-ink-muted shrink-0" />}
            <div className="flex-1 min-w-0">
              <p className={`text-xs font-bold ${s.done ? 'text-ink-muted line-through' : 'text-ink'}`}>{s.label}</p>
              {!s.done && <p className="text-[11px] text-ink-soft font-medium">{s.hint}</p>}
            </div>
            {!s.done && (
              <button
                type="button"
                onClick={() => navigate(s.to)}
                className="px-3 py-1.5 bg-brand text-white text-xs font-extrabold rounded-full cursor-pointer shrink-0"
              >
                Ir
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
