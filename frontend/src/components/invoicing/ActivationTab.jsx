import React, { useState } from 'react';
import { RefreshCw, Eye, X, Plus, Hash, CheckCircle2, Ban, PauseCircle, Star, Building2 } from 'lucide-react';
import {
  requestBillingActivationApi,
  getDianNumberingRangesApi,
  createNumberingRangeApi,
  deleteNumberingRangeApi,
  toggleNumberingRangeStatusApi,
  updateNumberingRangeCurrentApi,
  setDefaultNumberingRangeApi
} from '../../services/billingApi';
import useModal from '../../hooks/useModal';
import { ACTIVATION_LABELS, ACTIVATION_STYLES, EMPTY_RANGE_FORM, FIELD_CLASS, formatDateTime } from './constants';

export default function ActivationTab({ role, activation, ranges, rangesLoading, activeRangeId, onReload }) {
  const { notify, confirm } = useModal();
  const isAdmin = role === 'ADMINISTRATOR';
  const [requesting, setRequesting] = useState(false);
  const [dianRanges, setDianRanges] = useState(null);
  const [dianLoading, setDianLoading] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_RANGE_FORM);
  const [creating, setCreating] = useState(false);
  const [editingCurrent, setEditingCurrent] = useState(null);
  const [currentValue, setCurrentValue] = useState('');

  const canRequest = isAdmin && (activation?.status === 'NOT_REQUESTED' || activation?.status === 'REJECTED');

  const handleRequestActivation = async () => {
    setRequesting(true);
    try {
      await requestBillingActivationApi();
      await onReload();
    } catch (err) {
      notify('Error solicitando la activación: ' + err.message);
    } finally {
      setRequesting(false);
    }
  };

  const loadDianRanges = async () => {
    setDianLoading(true);
    try {
      setDianRanges(await getDianNumberingRangesApi());
    } catch (err) {
      notify('Error consultando resoluciones DIAN: ' + err.message);
      setDianRanges([]);
    } finally {
      setDianLoading(false);
    }
  };

  const handleCreateRange = async (e) => {
    e.preventDefault();
    setCreating(true);
    try {
      await createNumberingRangeApi({
        document: createForm.document.trim(),
        prefix: createForm.prefix.trim(),
        resolutionNumber: createForm.resolutionNumber.trim(),
        current: Number(createForm.current)
      });
      setShowCreateForm(false);
      setCreateForm(EMPTY_RANGE_FORM);
      await onReload();
    } catch (err) {
      notify('Error registrando el rango: ' + err.message);
    } finally {
      setCreating(false);
    }
  };

  const handleToggleStatus = async (range) => {
    try {
      await toggleNumberingRangeStatusApi(range.id);
      await onReload();
    } catch (err) {
      notify('Error cambiando el estado del rango: ' + err.message);
    }
  };

  const handleDelete = async (range) => {
    const ok = await confirm(`¿Eliminar el rango ${range.prefix} (resolución ${range.resolutionNumber})?`);
    if (!ok) return;
    try {
      await deleteNumberingRangeApi(range.id);
      await onReload();
    } catch (err) {
      notify('Error eliminando el rango: ' + err.message);
    }
  };

  const handleSetDefault = async (range) => {
    try {
      await setDefaultNumberingRangeApi(range.id);
      await onReload();
    } catch (err) {
      notify('Error eligiendo el rango: ' + err.message);
    }
  };

  const handleSaveCurrent = async (range) => {
    try {
      await updateNumberingRangeCurrentApi(range.id, Number(currentValue));
      setEditingCurrent(null);
      await onReload();
    } catch (err) {
      notify('Error actualizando el consecutivo: ' + err.message);
    }
  };

  const tile = 'p-3 rounded-2xl bg-page border border-line';

  return (
    <div className="space-y-6">
      <div className="bento-card p-6 space-y-4">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-solid text-brand flex items-center justify-center shrink-0">
              <Building2 className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-sm text-ink">Estado de habilitación ante Factus y la DIAN</h3>
              <p className="text-[11px] text-ink-soft font-medium">
                {activation ? `Entorno activo: ${activation.environment}` : 'Consultando...'}
              </p>
            </div>
          </div>
          {activation && (
            <span className={`text-[11px] px-3 py-1 rounded-full font-extrabold ${ACTIVATION_STYLES[activation.status] || ''}`}>
              {ACTIVATION_LABELS[activation.status] || activation.status}
            </span>
          )}
        </div>

        {activation?.statusReason && (
          <p className="text-[11px] text-red-500 dark:text-red-400 font-semibold">Motivo: {activation.statusReason}</p>
        )}

        {activation && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
            <div className={tile}>
              <span className="text-[10px] font-extrabold uppercase text-ink-muted">Sandbox</span>
              <p className="font-bold text-ink mt-0.5 flex items-center gap-1.5">
                {activation.sandboxConfigured ? <><CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Configurado</> : 'Sin configurar'}
              </p>
            </div>
            <div className={tile}>
              <span className="text-[10px] font-extrabold uppercase text-ink-muted">Producción</span>
              <p className="font-bold text-ink mt-0.5 flex items-center gap-1.5">
                {activation.productionConfigured ? <><CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Configurada</> : 'Pendiente de paso a producción'}
              </p>
            </div>
            <div className={tile}>
              <span className="text-[10px] font-extrabold uppercase text-ink-muted">Última actualización</span>
              <p className="font-bold text-ink mt-0.5">{formatDateTime(activation.updatedAt)}</p>
            </div>
          </div>
        )}

        {canRequest && (
          <div className="space-y-2">
            <button
              type="button"
              onClick={handleRequestActivation}
              disabled={requesting}
              className="px-4 py-2.5 bg-solid hover:bg-solid-hover disabled:opacity-50 text-solid-ink rounded-2xl text-xs font-bold cursor-pointer"
            >
              {requesting ? 'Enviando...' : 'Solicitar activación'}
            </button>
            <p className="text-[11px] text-ink-muted">
              Necesitas al menos un documento (RUT, cámara de comercio, etc.) cargado en <a href="/documents" className="text-brand underline">Documentos</a> antes de solicitar.
            </p>
          </div>
        )}
      </div>

      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <h3 className="font-extrabold text-sm text-ink flex items-center gap-2">
            <Hash className="w-4 h-4 text-brand" /> Resoluciones y rangos de numeración
          </h3>
          <div className="flex items-center gap-3">
            <button type="button" onClick={loadDianRanges} className="text-xs font-bold text-ink-soft flex items-center gap-1.5 hover:underline cursor-pointer">
              {dianLoading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Eye className="w-3.5 h-3.5" />} Consultar resoluciones en la DIAN
            </button>
            {isAdmin && (
              <button
                type="button"
                onClick={() => setShowCreateForm(v => !v)}
                className="px-3.5 py-2 bg-solid hover:bg-solid-hover text-solid-ink rounded-xl text-[11px] font-bold flex items-center gap-1.5 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" /> Registrar rango
              </button>
            )}
          </div>
        </div>

        {dianRanges && (
          <div className="p-4 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-line text-[11px] space-y-1.5">
            <p className="font-extrabold text-gray-600 dark:text-gray-300">Resoluciones autorizadas por la DIAN para este local:</p>
            {dianRanges.length === 0 ? (
              <p className="text-gray-400">No hay resoluciones asociadas todavía.</p>
            ) : dianRanges.map((r, idx) => (
              <p key={idx} className="font-mono text-ink-soft">
                {r.prefix} · Res. {r.resolutionNumber} · {r.startDate} a {r.endDate}
              </p>
            ))}
          </div>
        )}

        {isAdmin && showCreateForm && (
          <form onSubmit={handleCreateRange} className="bento-card p-5 space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              {[
                ['range-document', 'Código de documento:', 'document', 'text', '01'],
                ['range-prefix', 'Prefijo:', 'prefix', 'text', 'SETP'],
                ['range-resolution', 'Número de resolución:', 'resolutionNumber', 'text', ''],
                ['range-current', 'Consecutivo actual:', 'current', 'number', '1']
              ].map(([id, label, key, type, ph]) => (
                <div key={id}>
                  <label htmlFor={id} className="font-bold text-gray-700 dark:text-gray-300 block mb-1">{label}</label>
                  <input
                    id={id}
                    required
                    type={type}
                    min={type === 'number' ? 1 : undefined}
                    placeholder={ph}
                    value={createForm[key]}
                    onChange={e => setCreateForm({ ...createForm, [key]: e.target.value })}
                    className={FIELD_CLASS}
                  />
                </div>
              ))}
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setShowCreateForm(false)} className="flex-1 py-2.5 bg-gray-100 dark:bg-line text-gray-700 dark:text-gray-300 rounded-xl text-[11px] font-bold cursor-pointer">Cancelar</button>
              <button type="submit" disabled={creating} className="flex-1 py-2.5 bg-solid hover:bg-solid-hover disabled:opacity-50 text-solid-ink rounded-xl text-[11px] font-extrabold cursor-pointer">
                {creating ? 'Registrando...' : 'Registrar'}
              </button>
            </div>
          </form>
        )}

        {rangesLoading && ranges.length === 0 ? (
          <p className="text-xs font-bold text-ink-muted py-6 text-center">Consultando rangos...</p>
        ) : ranges.length === 0 ? (
          <div className="py-10 text-center space-y-1 bg-gray-50/50 dark:bg-white/5 rounded-3xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-gray-700 dark:text-gray-300">Sin rangos de numeración registrados</p>
            {isAdmin && <p className="text-[11px] text-ink-muted">Registra la resolución que la DIAN te asignó para empezar a facturar.</p>}
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
                        {!isDefault && (
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
    </div>
  );
}
