import React, { useState } from 'react';
import { ChevronDown, Download, RefreshCw } from 'lucide-react';
import { Button } from '../ui';
import StepMessage from './StepMessage';
import GuideList from './GuideList';
import ManualRangeForm from './ManualRangeForm';
import ImportResult from './ImportResult';
import { importNumberingApi } from '../../services/billingApi';

const GUIDE = [
  <>Entra a los servicios en línea de la DIAN (<strong>MUISCA</strong>) con el usuario de tu empresa.</>,
  <>Abre <strong>«Numeración de facturación»</strong> y solicita una numeración de tipo <strong>«Facturación electrónica de venta»</strong>. Te sugerimos el prefijo <strong>FE</strong>.</>,
  <>Firma la solicitud con tu <strong>clave dinámica</strong>.</>,
  <>Luego, en <strong>«Rangos de numeración»</strong>, asocia ese prefijo a <strong>Alegra</strong>.</>,
  <>La DIAN puede tardar hasta una hora en reflejarlo. Después pulsa <strong>«Traer mis rangos de la DIAN»</strong>.</>,
];

/** Paso 3: la resolución de numeración. Se trae de la DIAN; si no, se puede registrar a mano. */
export default function NumberingStep({ step, canEdit, onSaved, onRangesChanged, importResult, onImported }) {
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState('');
  const [manualOpen, setManualOpen] = useState(false);

  const pending = step.state === 'PENDING';

  const handleImport = async () => {
    setImporting(true);
    setError('');
    onImported?.(null);
    try {
      const imported = await importNumberingApi();
      onImported?.(imported);
      onSaved(imported.setup);
    } catch (err) {
      setError(err.message);
    } finally {
      setImporting(false);
    }
  };

  return (
    <div className="space-y-5">
      <StepMessage step={step} />

      {canEdit && !pending && (
        <>
          {step.state !== 'DONE' && (
            <GuideList items={GUIDE} />
          )}

          <div className="flex justify-end">
            <Button icon={importing ? RefreshCw : Download} loading={importing} onClick={handleImport}>
              Traer mis rangos de la DIAN
            </Button>
          </div>

          {error && (
            <p role="alert" className="p-3 rounded-2xl text-xs font-semibold text-red-600 bg-red-50 border border-red-200 dark:text-red-400 dark:bg-red-500/20 dark:border-red-500/20">{error}</p>
          )}
          {importResult && <ImportResult result={importResult} />}

          <div className="rounded-2xl border border-line">
            <button
              type="button"
              onClick={() => setManualOpen((v) => !v)}
              aria-expanded={manualOpen}
              className="w-full flex items-center justify-between gap-2 px-4 py-3 text-xs font-bold text-ink-soft hover:text-ink cursor-pointer"
            >
              ¿Prefieres registrarlo a mano?
              <ChevronDown className={`w-4 h-4 transition-transform ${manualOpen ? 'rotate-180' : ''}`} />
            </button>
            {manualOpen && (
              <div className="px-4 pb-4">
                <ManualRangeForm
                  onCreated={() => { setManualOpen(false); onRangesChanged?.(); }}
                  onCancel={() => setManualOpen(false)}
                />
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
