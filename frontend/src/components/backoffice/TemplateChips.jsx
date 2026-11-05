import React from 'react';
import { storeTemplateLabel } from '../../constants/storeTemplates';
import { templatesOf } from '../../utils/templateSelection';

/** Etiquetas con las plantillas de una categoría o de un local. */
export default function TemplateChips({ entity }) {
  return (
    <span className="inline-flex flex-wrap gap-1">
      {templatesOf(entity).map(t => (
        <span key={t} className="text-[10px] px-2 py-0.5 rounded-full bg-brand/10 text-brand border border-brand/20 font-extrabold">
          {storeTemplateLabel(t)}
        </span>
      ))}
    </span>
  );
}
