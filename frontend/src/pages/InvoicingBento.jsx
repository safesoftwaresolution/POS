import React, { useState, useEffect, useCallback } from 'react';
import { FileText, RefreshCw, ShieldCheck, ShieldAlert, Hash, CheckCircle2, AlertTriangle, Building2 } from 'lucide-react';
import {
  listInvoicesApi,
  getBillingActivationStatusApi,
  listNumberingRangesApi
} from '../services/billingApi';
import { useAuth } from '../context/AuthContext';
import { INVOICE_STATUSES, ACTIVATION_LABELS } from '../components/invoicing/constants';
import { TabButton, KpiCard } from '../components/invoicing/parts';

import InvoicesTab from '../components/invoicing/InvoicesTab';
import ActivationTab from '../components/invoicing/ActivationTab';
import useAutoRefresh from '../hooks/useAutoRefresh';

export default function InvoicingBento() {
  const { user } = useAuth();
  const role = user?.role;
  const canSeeActivation = role === 'ADMINISTRATOR' || role === 'SUPERVISOR';
  const [tab, setTab] = useState('invoices');
  const [refreshKey, setRefreshKey] = useState(0);

  const [totals, setTotals] = useState({ VALIDATED: null, PENDING: null, ERROR: null, REJECTED: null });
  const [activation, setActivation] = useState(null);
  const [ranges, setRanges] = useState([]);
  const [rangesLoading, setRangesLoading] = useState(false);

  const loadTotals = useCallback(async () => {
    const entries = await Promise.all(
      INVOICE_STATUSES.map(async (s) => {
        try {
          const page = await listInvoicesApi(s, 0, 1);
          return [s, page.totalElements ?? (page.content || []).length];
        } catch {
          return [s, null];
        }
      })
    );
    setTotals(Object.fromEntries(entries));
  }, []);

  const loadBillingState = useCallback(async ({ silent = false } = {}) => {
    if (!canSeeActivation) return;
    if (!silent) setRangesLoading(true);
    try {
      const [act, rgs] = await Promise.all([
        getBillingActivationStatusApi().catch(() => null),
        listNumberingRangesApi().catch(() => [])
      ]);
      setActivation(act);
      setRanges(Array.isArray(rgs) ? rgs : []);
    } finally {
      if (!silent) setRangesLoading(false);
    }
  }, [canSeeActivation]);

  useEffect(() => { loadTotals(); loadBillingState(); }, [loadTotals, loadBillingState, refreshKey]);
  useAutoRefresh(() => Promise.all([loadTotals(), loadBillingState({ silent: true })]));

  const activeRangeId = activation?.environment === 'PRODUCTION' ? activation?.productionNumberingRangeId : activation?.sandboxNumberingRangeId;
  const activeRange = ranges.find(r => r.id === activeRangeId);
  const remaining = activeRange && activeRange.to != null && activeRange.current != null ? activeRange.to - activeRange.current + 1 : null;

  const incidents = ['PENDING', 'ERROR', 'REJECTED'].reduce((sum, s) => sum + (totals[s] || 0), 0);
  const totalsLoaded = Object.values(totals).every(v => v != null);
  const totalInvoices = INVOICE_STATUSES.reduce((sum, s) => sum + (totals[s] || 0), 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-2xl font-black text-ink tracking-tight">Facturación Electrónica DIAN</h1>
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-brand text-white">Factus</span>
          </div>
          <p className="text-xs text-ink-soft font-medium mt-1">
            Seguimiento de facturas, CUFE y numeración oficial de tu local ante la DIAN. Factus es el proveedor tecnológico autorizado que las transmite.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setRefreshKey(k => k + 1)}
          className="h-10 px-4 rounded-2xl border border-line bg-card text-gray-600 dark:text-gray-300 hover:text-ink text-xs font-bold flex items-center gap-2 cursor-pointer self-start md:self-auto"
        >
          <RefreshCw className="w-3.5 h-3.5" /> Sincronizar con la DIAN
        </button>
      </div>

      <div className={`grid grid-cols-1 sm:grid-cols-2 ${canSeeActivation ? 'lg:grid-cols-4' : 'lg:grid-cols-2'} gap-4`}>
        <KpiCard
          label="Facturas validadas"
          value={totals.VALIDATED ?? '—'}
          hint={totalsLoaded && totalInvoices > 0 ? `${totals.VALIDATED} de ${totalInvoices} emitidas` : 'Aceptadas por la DIAN'}
          hintClass="text-emerald-600 dark:text-emerald-400"
          icon={CheckCircle2}
          accent="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
        />
        <KpiCard
          label="Pendientes y errores"
          value={totalsLoaded ? incidents : '—'}
          hint={totalsLoaded ? (incidents > 0 ? 'Requieren revisión o reintento' : 'Sin incidencias en cola') : 'Consultando...'}
          hintClass={incidents > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-ink-soft'}
          icon={AlertTriangle}
          accent="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400"
        />
        {canSeeActivation && (
          <>
            <KpiCard
              label="Consecutivos disponibles"
              value={remaining ?? '—'}
              hint={activeRange ? `Prefijo ${activeRange.prefix} · hasta ${activeRange.to}` : 'Sin rango elegido para facturar'}
              icon={Hash}
              accent="bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400"
            />
            <KpiCard
              label="Habilitación del local"
              value={activation ? (ACTIVATION_LABELS[activation.status] || activation.status) : '—'}
              hint={activation ? `Entorno ${activation.environment}` : 'Consultando...'}
              hintClass={activation?.status === 'ACTIVE' ? 'text-emerald-600 dark:text-emerald-400' : 'text-ink-soft'}
              icon={activation?.status === 'ACTIVE' ? ShieldCheck : ShieldAlert}
              accent={activation?.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'}
            />
          </>
        )}
      </div>

      {canSeeActivation && (
        <div className="border-b border-line flex items-center gap-6 overflow-x-auto">
          <TabButton active={tab === 'invoices'} onClick={() => setTab('invoices')} icon={FileText} label="Facturas emitidas" count={totalsLoaded ? totalInvoices : null} />
          <TabButton active={tab === 'activation'} onClick={() => setTab('activation')} icon={Building2} label="Habilitación y rangos" count={ranges.length || null} />
        </div>
      )}

      {tab === 'invoices' || !canSeeActivation ? (
        <InvoicesTab role={role} refreshKey={refreshKey} onChanged={loadTotals} />
      ) : (
        <ActivationTab
          role={role}
          activation={activation}
          ranges={ranges}
          rangesLoading={rangesLoading}
          activeRangeId={activeRangeId}
          onReload={loadBillingState}
        />
      )}
    </div>
  );
}
