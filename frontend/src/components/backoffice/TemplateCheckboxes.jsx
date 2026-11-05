import React from 'react';
import { STORE_TEMPLATE_OPTIONS } from '../../constants/storeTemplates';
import { toggleTemplate } from '../../utils/templateSelection';

/**
 * Casillas de plantillas (selección múltiple). `locked` son las que no se pueden quitar aquí (por ejemplo, las que
 * ya aporta la categoría del local) y se muestran marcadas con `lockedHint`.
 */
export default function TemplateCheckboxes({ legend, value, onChange, locked = [], lockedHint = '' }) {
  return (
    <fieldset className="space-y-1.5">
      <legend className="font-bold text-gray-700 dark:text-gray-300 mb-1">{legend}</legend>
      {STORE_TEMPLATE_OPTIONS.map(opt => {
        const isLocked = locked.includes(opt.value);
        return (
          <label key={opt.value} className="flex items-center gap-2 p-2.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink cursor-pointer">
            <input
              type="checkbox"
              checked={isLocked || value.includes(opt.value)}
              disabled={isLocked}
              onChange={() => onChange(toggleTemplate(value, opt.value))}
              className="accent-brand w-4 h-4"
            />
            <span>{opt.label}</span>
            {isLocked && lockedHint && <span className="ml-auto text-[10px] text-ink-muted font-bold">{lockedHint}</span>}
          </label>
        );
      })}
    </fieldset>
  );
}
