import React from 'react';

const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/** Qué trajo la importación de rangos de la DIAN y qué no, con su motivo. */
export default function ImportResult({ result }) {
  const nothing = result.importedCount === 0 && result.noteRangesCreated === 0;
  return (
    <div role="status" className="p-4 rounded-2xl border border-line bg-page text-xs space-y-2">
      <p className="font-bold text-ink">
        {nothing
          ? 'No encontramos rangos nuevos para traer.'
          : `Trajimos ${plural(result.importedCount, 'resolución de facturas', 'resoluciones de facturas')}`
            + (result.noteRangesCreated > 0 ? ` y creamos ${plural(result.noteRangesCreated, 'rango de notas', 'rangos de notas')}.` : '.')}
      </p>
      {result.skipped?.length > 0 && (
        <div className="space-y-1">
          <p className="font-semibold text-ink-soft">No se trajeron:</p>
          <ul className="list-disc pl-5 space-y-0.5 text-ink-soft">
            {result.skipped.map((reason) => <li key={reason}>{reason}</li>)}
          </ul>
        </div>
      )}
    </div>
  );
}
