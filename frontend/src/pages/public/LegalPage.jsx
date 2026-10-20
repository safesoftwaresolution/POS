import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { FileText } from 'lucide-react';
import { getLegalDocumentApi } from '../../services/publicApi';
import { Card, EmptyState } from '../../components/ui';
import PublicLayout from './PublicLayout';

const formatDate = (iso) =>
  iso ? new Date(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' }) : null;

export default function LegalPage() {
  const { slug } = useParams();
  const [doc, setDoc] = useState(null);
  const [state, setState] = useState('loading'); // loading | ready | missing

  useEffect(() => {
    let cancelled = false;
    getLegalDocumentApi(slug)
      .then(data => { if (!cancelled) { setDoc(data); setState('ready'); } })
      .catch(() => { if (!cancelled) setState('missing'); });
    return () => { cancelled = true; };
  }, [slug]);

  return (
    <PublicLayout>
      {state === 'loading' && <p className="py-16 text-center text-xs font-bold text-ink-muted">Cargando documento...</p>}

      {state === 'missing' && (
        <EmptyState
          icon={FileText}
          title="Documento no disponible"
          description="Este texto legal no existe o todavía no ha sido publicado."
        />
      )}

      {state === 'ready' && doc && (
        <Card className="space-y-4">
          <div>
            <h1 className="text-2xl font-black text-ink">{doc.title}</h1>
            <p className="text-[11px] font-semibold text-ink-muted mt-1">
              {doc.version && `Versión ${doc.version}`}
              {doc.version && doc.publishedAt && ' · '}
              {doc.publishedAt && `Publicado el ${formatDate(doc.publishedAt)}`}
            </p>
          </div>
          {/* El contenido se muestra como texto plano: nunca se inyecta como HTML. */}
          <div className="text-sm text-ink-soft leading-relaxed whitespace-pre-line">{doc.content}</div>
        </Card>
      )}
    </PublicLayout>
  );
}
