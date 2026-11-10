import React from 'react';
import { FileCheck2 } from 'lucide-react';
import StoreDocumentsGrid from '../components/store/StoreDocumentsGrid';

export default function DocumentsBento() {
  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-black text-ink flex items-center gap-2">
          <FileCheck2 className="w-6 h-6 text-brand dark:text-brand" /> Documentos Legales
        </h1>
        <p className="text-xs text-ink-soft font-medium">
          Sube los documentos de verificación de tu local. Nuestro equipo los revisará y los verás aprobados o rechazados aquí.
        </p>
      </div>

      <StoreDocumentsGrid />
    </div>
  );
}
