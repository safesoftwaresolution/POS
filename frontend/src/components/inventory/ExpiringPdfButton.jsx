import React, { useState } from 'react';
import { FileText, Loader2 } from 'lucide-react';
import { downloadExpiringReportPdfApi } from '../../services/reportsApi';
import { deliverBlob } from '../../utils/downloadBlob';
import { EXPIRY_WINDOW_DAYS } from '../../utils/expiry';
import useModal from '../../hooks/useModal';

const WINDOWS = [30, 60, 90];

/** Descarga el informe de vencidos y próximos a vencer (con cantidades) en PDF, con la ventana elegida. */
export default function ExpiringPdfButton() {
  const { notify } = useModal();
  const [days, setDays] = useState(EXPIRY_WINDOW_DAYS);
  const [busy, setBusy] = useState(false);

  const handleDownload = async () => {
    setBusy(true);
    try {
      const blob = await downloadExpiringReportPdfApi(days);
      deliverBlob(blob, 'vencimientos.pdf', { openInTab: true });
    } catch (err) {
      notify('Error generando el PDF de vencimientos: ' + err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex items-center gap-1.5">
      <select
        value={days}
        onChange={(e) => setDays(Number(e.target.value))}
        aria-label="Ventana de vencimiento del PDF"
        className="px-2.5 py-2.5 bg-card border border-line rounded-2xl text-xs font-bold text-ink cursor-pointer focus:outline-none focus:border-ink"
      >
        {WINDOWS.map((d) => <option key={d} value={d}>{d} días</option>)}
      </select>
      <button
        onClick={handleDownload}
        disabled={busy}
        className="px-4 py-2.5 border border-line hover:border-line-strong text-ink bg-card rounded-2xl text-xs font-bold flex items-center gap-2 cursor-pointer transition-all disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />} Vencimientos (PDF)
      </button>
    </div>
  );
}
