import React, { useState } from 'react';
import { ExternalLink, RefreshCw, Send } from 'lucide-react';
import { Button, Input } from '../ui';
import StepMessage from './StepMessage';
import GuideList from './GuideList';
import { startDianEnablementApi } from '../../services/billingApi';

const DIAN_PORTAL = 'https://catalogo-vpfe-hab.dian.gov.co/User/CompanyLogin';
const TEST_SET_ID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

const GUIDE = [
  <>Entra al <strong>portal de habilitación de la DIAN</strong> con el usuario de tu empresa.</>,
  <>Abre <strong>«Registro y habilitación»</strong> y luego <strong>«Facturación electrónica»</strong>.</>,
  <>Elige el modo <strong>«Software de un proveedor tecnológico»</strong>.</>,
  <>Como proveedor elige <strong>«Soluciones Alegra S.A.S.»</strong> y como software <strong>«Alegra»</strong>.</>,
  <>Pulsa <strong>«Asociar»</strong>.</>,
  <>Copia el código <strong>«TestSetId»</strong> que te muestra la DIAN y pégalo aquí abajo.</>,
];

/** Paso 2: el dueño asocia a Alegra en el portal de la DIAN y envía aquí el TestSetId. */
export default function DianEnablementStep({ step, canEdit, onSaved, onRefresh, refreshing }) {
  const [testSetId, setTestSetId] = useState('');
  const [error, setError] = useState('');
  const [sending, setSending] = useState(false);

  const done = step.state === 'DONE';
  const pending = step.state === 'PENDING';

  const handleSubmit = async (e) => {
    e.preventDefault();
    const value = testSetId.trim();
    if (!TEST_SET_ID.test(value)) {
      setError('El código no tiene el formato correcto. Se ve así: d7b8f2c4-1a2b-4c3d-9e8f-0a1b2c3d4e5f');
      return;
    }
    setError('');
    setSending(true);
    try {
      onSaved(await startDianEnablementApi(value));
      setTestSetId('');
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-5">
      <StepMessage step={step} />

      {canEdit && !done && !pending && (
        <>
          <div className="space-y-3">
            <GuideList items={GUIDE} />
            <a
              href={DIAN_PORTAL}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-xs font-bold text-brand hover:underline"
            >
              Abrir el portal de habilitación de la DIAN <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </div>

          <form onSubmit={handleSubmit} className="space-y-3" noValidate>
            <Input
              label="Código del set de pruebas (TestSetId)"
              value={testSetId}
              onChange={(e) => setTestSetId(e.target.value)}
              error={error}
              hint="Son 36 caracteres con guiones, por ejemplo d7b8f2c4-1a2b-4c3d-9e8f-0a1b2c3d4e5f."
              autoComplete="off"
              spellCheck={false}
              className="font-mono"
            />
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="outline" icon={RefreshCw} onClick={onRefresh} loading={refreshing}>Revisar estado</Button>
              <Button type="submit" icon={Send} loading={sending}>Enviar a la DIAN</Button>
            </div>
          </form>
        </>
      )}

      {(!canEdit || pending) && !done && (
        <div className="flex justify-end">
          <Button variant="outline" icon={RefreshCw} onClick={onRefresh} loading={refreshing}>Revisar estado</Button>
        </div>
      )}
    </div>
  );
}
