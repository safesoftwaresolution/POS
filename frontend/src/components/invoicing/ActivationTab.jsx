import React, { useState } from 'react';
import { X, Plus, Hash, CheckCircle2, Ban, PauseCircle, Star } from 'lucide-react';
import {
  deleteNumberingRangeApi,
  toggleNumberingRangeStatusApi,
  updateNumberingRangeCurrentApi,
  setDefaultNumberingRangeApi
} from '../../services/billingApi';
import useModal from '../../hooks/useModal';
import { Button, Card } from '../ui';
import BillingSetupWizard from './BillingSetupWizard';
import ManualRangeForm from './ManualRangeForm';
import { isInvoiceDocument } from './constants';

/**
 * Habilitación de la facturación electrónica: el asistente paso a paso (el administrador lo recorre, el supervisor
 * ve el avance) y, con la facturación activa, los rangos de numeración del local.
 */
export default function ActivationTab({ role, activation, ranges, rangesLoading, activeRangeId, refreshKey, onReload }) {
  const { notify, confirm } = useModal();
  const isAdmin = role === 'ADMINISTRATOR';
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingCurrent, setEditingCurrent] = useState(null);
  const [currentValue, setCurrentValue] = useState('');

  const handleToggleStatus = async (range) => {
    try {
      await toggleNumberingRangeStatusApi(range.id);
      await onReload();
    } catch (err) {
      notify('No pudimos cambiar el estado del rango: ' + err.message);
    }
  };

  const handleDelete = async (range) => {
    const ok = await confirm(`¿Eliminar el rango ${range.prefix} (resolución ${range.resolutionNumber})?`);
    if (!ok) return;
    try {
      await deleteNumberingRangeApi(range.id);
      await onReload();
    } catch (err) {
      notify('No pudimos eliminar el rango: ' + err.message);
    }
  };

  const handleSetDefault = async (range) => {
    try {
      await setDefaultNumberingRangeApi(range.id);
      await onReload();
    } catch (err) {
      notify('No pudimos elegir el rango: ' + err.message);
    }
  };

  const handleSaveCurrent = async (range) => {
    try {
      await updateNumberingRangeCurrentApi(range.id, Number(currentValue));
      setEditingCurrent(null);
      await onReload();
    } catch (err) {
      notify('No pudimos actualizar el consecutivo: ' + err.message);
    }
  };

  const showRanges = activation?.status === 'ACTIVE';

  return (
    <div className="space-y-6">
      <BillingSetupWizard canEdit={isAdmin} refreshKey={refreshKey} onChanged={onReload} />

      {showRanges && (
      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <h3 className="font-extrabold text-sm text-ink flex items-center gap-2">
            <Hash className="w-4 h-4 text-brand" /> Resoluciones y rangos de numeración
          </h3>
          {isAdmin && (
            <Button size="sm" icon={Plus} onClick={() => setShowCreateForm(v => !v)}>Registrar rango</Button>
          )}
        </div>

        {isAdmin && showCreateForm && (
          <Card>
            <ManualRangeForm
              onCreated={() => { setShowCreateForm(false); onReload(); }}
              onCancel={() => setShowCreateForm(false)}
            />
          </Card>
        )}

        {rangesLoading && ranges.length === 0 ? (
          <p className="text-xs font-bold text-ink-muted py-6 text-center">Consultando rangos...</p>
        ) : ranges.length === 0 ? (
          <div className="py-10 text-center space-y-1 bg-gray-50/50 dark:bg-white/5 rounded-3xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-gray-700 dark:text-gray-300">Sin rangos de numeración registrados</p>
            {isAdmin && <p className="text-[11px] text-ink-muted">Tráelos de la DIAN en el paso «Numeración» o regístralos a mano.</p>}
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {ranges.map(r => {
              const isDefault = activeRangeId === r.id;
              const span = r.to != null && r.from != null ? r.to - r.from + 1 : 0;
              const used = r.current != null && r.from != null ? Math.max(0, r.current - r.from) : 0;
              const pct = span > 0 ? Math.min(100, Math.round((used / span) * 100)) : 0;
              return (
                <div
                  key={r.id}
                  className={`p-5 rounded-3xl bg-card border space-y-4 transition-all ${
                    isDefault ? 'border-brand ring-1 ring-brand shadow-md' : 'border-line'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-mono text-base font-black text-ink">Prefijo {r.prefix}</span>
                        {isDefault && (
                          <span className="px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider bg-brand text-white flex items-center gap-1">
                            <Star className="w-2.5 h-2.5 fill-current" /> Para facturar
                          </span>
                        )}
                        {r.isExpired ? (
                          <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400">Vencido</span>
                        ) : r.isActive ? (
                          <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">Activo</span>
                        ) : (
                          <span className="text-[10px] px-2 py-0.5 rounded-full font-extrabold bg-gray-100 dark:bg-white/10 text-ink-soft">Inactivo</span>
                        )}
                      </div>
                      <p className="text-[11px] text-ink-soft font-medium mt-0.5">{r.documentName || r.document}</p>
                    </div>

                    {isAdmin && (
                      <div className="flex items-center gap-1 shrink-0">
                        {!isDefault && isInvoiceDocument(r.document) && (
                          <button type="button" onClick={() => handleSetDefault(r)} title="Elegir para facturar" aria-label="Elegir para facturar" className="p-1.5 text-gray-400 hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                            <Star className="w-4 h-4" />
                          </button>
                        )}
                        <button type="button" onClick={() => handleToggleStatus(r)} title={r.isActive ? 'Desactivar' : 'Activar'} aria-label={r.isActive ? 'Desactivar' : 'Activar'} className="p-1.5 text-gray-400 hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                          {r.isActive ? <PauseCircle className="w-4 h-4" /> : <CheckCircle2 className="w-4 h-4" />}
                        </button>
                        <button type="button" onClick={() => handleDelete(r)} title="Eliminar" aria-label="Eliminar" className="p-1.5 text-gray-400 hover:text-red-500 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                          <Ban className="w-4 h-4" />
                        </button>
                      </div>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs pt-3 border-t border-line">
                    <div className="min-w-0">
                      <span className="text-[10px] text-ink-muted block">Resolución</span>
                      <span className="font-mono font-bold text-ink block truncate">{r.resolutionNumber || '—'}</span>
                    </div>
                    <div className="min-w-0">
                      <span className="text-[10px] text-ink-muted block">Vigencia</span>
                      <span className="font-semibold text-ink block">{r.startDate && r.endDate ? `${r.startDate} al ${r.endDate}` : '—'}</span>
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between text-[11px] font-semibold gap-2">
                      <span className="text-ink-soft">Consumo del rango</span>
                      {editingCurrent === r.id ? (
                        <span className="flex items-center gap-1">
                          <input
                            type="number"
                            value={currentValue}
                            onChange={e => setCurrentValue(e.target.value)}
                            aria-label="Consecutivo actual"
                            className="w-24 p-1 bg-white dark:bg-page border border-line rounded-lg text-xs font-mono"
                          />
                          <button type="button" onClick={() => handleSaveCurrent(r)} aria-label="Guardar consecutivo" className="text-emerald-500 cursor-pointer"><CheckCircle2 className="w-4 h-4" /></button>
                          <button type="button" onClick={() => setEditingCurrent(null)} aria-label="Cancelar" className="text-gray-400 cursor-pointer"><X className="w-4 h-4" /></button>
                        </span>
                      ) : (
                        <button
                          type="button"
                          disabled={!isAdmin}
                          onClick={() => { setEditingCurrent(r.id); setCurrentValue(String(r.current ?? '')); }}
                          className={`font-mono tabular-nums text-ink ${isAdmin ? 'hover:underline cursor-pointer' : 'cursor-default'}`}
                        >
                          #{r.current ?? '—'} / #{r.to ?? '—'} ({pct}%)
                        </button>
                      )}
                    </div>
                    <div className="w-full h-2 rounded-full bg-gray-100 dark:bg-white/10 overflow-hidden">
                      <div className={`h-full rounded-full ${pct >= 90 ? 'bg-red-500' : 'bg-brand'}`} style={{ width: `${pct}%` }} />
                    </div>
                  </div>

                  {r.technicalKey && (
                    <div className="p-2.5 rounded-xl bg-page border border-line text-[10px]">
                      <span className="text-ink-muted block">Llave técnica DIAN</span>
                      <p className="font-mono text-gray-600 dark:text-gray-300 truncate" title={r.technicalKey}>{r.technicalKey}</p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
      )}
    </div>
  );
}
