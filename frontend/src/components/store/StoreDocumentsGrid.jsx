import React, { useRef } from 'react';
import { Upload, AlertCircle, CheckCircle2, Clock, XCircle, ExternalLink } from 'lucide-react';
import { DOCUMENT_TYPES, DOC_TYPE_LABELS, DOC_STATUS_LABELS, DOC_STATUS_STYLES } from '../../constants/storeDocuments';
import useStoreDocuments from '../../hooks/useStoreDocuments';

const DOC_STATUS_ICONS = {
  PENDING: Clock,
  APPROVED: CheckCircle2,
  REJECTED: XCircle,
};

export function DocumentCard({ type, latestDoc, uploading, onUpload }) {
  const inputRef = useRef(null);
  const StatusIcon = latestDoc ? DOC_STATUS_ICONS[latestDoc.status] : null;

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (file) onUpload(type, file);
  };

  return (
    <div className="bento-card p-5 space-y-3 text-left">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h3 className="font-extrabold text-sm text-[#17171B] dark:text-white">{DOC_TYPE_LABELS[type]}</h3>
          {latestDoc ? (
            <p className="text-[10px] text-ink-muted font-semibold">
              Subido {new Date(latestDoc.uploadedAt).toLocaleDateString('es-CO')}
            </p>
          ) : (
            <p className="text-[10px] text-ink-muted font-semibold">Aún no se ha subido</p>
          )}
        </div>
        {latestDoc && (
          <span className={`shrink-0 inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full font-extrabold ${DOC_STATUS_STYLES[latestDoc.status] || ''}`}>
            {StatusIcon && <StatusIcon className="w-3 h-3" />}
            {DOC_STATUS_LABELS[latestDoc.status] || latestDoc.status}
          </span>
        )}
      </div>

      {latestDoc?.status === 'REJECTED' && latestDoc.rejectionReason && (
        <p className="text-[11px] text-red-500 dark:text-red-400 font-semibold">
          Motivo de rechazo: {latestDoc.rejectionReason}
        </p>
      )}

      <div className="flex items-center gap-2">
        {latestDoc && (
          <a
            href={latestDoc.documentUrl}
            target="_blank"
            rel="noreferrer"
            className="flex-1 py-2 bg-gray-100 dark:bg-white/10 hover:bg-gray-200 dark:hover:bg-white/20 text-gray-700 dark:text-gray-200 rounded-2xl text-[11px] font-bold flex items-center justify-center gap-1.5 cursor-pointer"
          >
            <ExternalLink className="w-3.5 h-3.5" /> Ver documento
          </a>
        )}
        <button
          type="button"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
          className="flex-1 py-2 bg-solid hover:bg-solid-hover disabled:opacity-50 text-solid-ink rounded-2xl text-[11px] font-extrabold flex items-center justify-center gap-1.5 cursor-pointer shadow-sm shadow-[#0A0A0A]/10"
        >
          <Upload className="w-3.5 h-3.5" /> {uploading ? 'Subiendo...' : latestDoc ? 'Reemplazar' : 'Subir'}
        </button>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png"
          className="hidden"
          onChange={handleFileChange}
        />
      </div>
    </div>
  );
}

/** Tarjetas de los documentos KYC del local (uno por tipo) con su estado y el botón para subir o reemplazar. */
export default function StoreDocumentsGrid({ className = 'grid grid-cols-1 md:grid-cols-2 gap-4' }) {
  const { loading, error, uploadingType, upload, latestByType } = useStoreDocuments();

  return (
    <>
      {error && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-2xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="py-12 text-center text-xs font-bold text-ink-muted">
          Consultando documentos...
        </div>
      ) : (
        <div className={className}>
          {DOCUMENT_TYPES.map(type => (
            <DocumentCard
              key={type}
              type={type}
              latestDoc={latestByType(type)}
              uploading={uploadingType === type}
              onUpload={upload}
            />
          ))}
        </div>
      )}
    </>
  );
}
