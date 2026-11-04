import React from 'react';
import { ArrowRight, History } from 'lucide-react';
import { Badge, EmptyState, Modal } from '../ui';
import useLoad from '../../hooks/useLoad';
import { getAuditHistoryApi } from '../../services/auditApi';
import { AUDIT_ACTION_LABELS, fieldLabel, formatAuditValue } from '../../utils/auditLabels';
import { formatDateTime } from '../../utils/format';

const ACTION_TONES = { CREATE: 'success', UPDATE: 'info', DELETE: 'danger' };

/** Historial de cambios de un cliente: quién lo creó, editó o eliminó, y qué campos cambiaron. Solo ADMINISTRATOR. */
export default function CustomerHistoryModal({ customer, onClose }) {
  const { data, loading, error } = useLoad(
    () => getAuditHistoryApi('CUSTOMER', customer.id),
    [customer.id],
    { initial: { content: [] }, errorMessage: 'No se pudo cargar el historial.' },
  );
  const entries = data.content || [];

  return (
    <Modal title="Historial del cliente" subtitle={customer.fullName || customer.name} size="lg" onClose={onClose}>
      <div className="px-6 pb-6 space-y-3">
        {error && <div className="p-3 rounded-xl bg-red-50 dark:bg-red-500/10 text-xs font-bold text-red-600">{error}</div>}

        {loading ? (
          <div className="py-10 text-center text-xs font-bold text-ink-muted">Cargando historial...</div>
        ) : entries.length === 0 ? (
          <EmptyState
            icon={History}
            title="Sin cambios registrados"
            description="Los clientes anteriores a la auditoría no tienen historial hasta que se editen"
          />
        ) : (
          entries.map((entry) => {
            const fields = Object.entries(entry.changes || {});
            return (
              <div key={entry.id} className="p-3.5 rounded-2xl border border-line bg-page space-y-2">
                <div className="flex items-center justify-between gap-3 text-xs">
                  <div className="flex items-center gap-2 min-w-0">
                    <Badge tone={ACTION_TONES[entry.action] || 'neutral'}>{AUDIT_ACTION_LABELS[entry.action] || entry.action}</Badge>
                    <span className="font-bold text-ink truncate">{entry.username || 'Usuario desconocido'}</span>
                  </div>
                  <span className="text-ink-muted font-semibold shrink-0">{formatDateTime(entry.createdAt)}</span>
                </div>
                {fields.length > 0 && (
                  <ul className="space-y-1 text-[11px] text-ink-soft font-medium">
                    {fields.map(([field, change]) => (
                      <li key={field} className="flex flex-wrap items-center gap-1.5">
                        <span className="font-bold text-ink">{fieldLabel(field)}:</span>
                        {entry.action !== 'CREATE' && (
                          <>
                            <span className="line-through opacity-70">{formatAuditValue(change.from)}</span>
                            <ArrowRight className="w-3 h-3 text-ink-muted" />
                          </>
                        )}
                        <span>{formatAuditValue(change.to)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })
        )}
      </div>
    </Modal>
  );
}
