import React, { useState } from 'react';
import { Button, Input, Modal, Select, inputClass } from '../ui';
import { Field } from '../ui/Field';
import { toInputValue } from '../../utils/calendar';

/**
 * Agendar una cita nueva (sin `appointment`) o mover una existente (con `appointment`: solo cambia la hora).
 * `customers` y `services` llegan ya cargados por la página.
 */
export default function AppointmentFormModal({ appointment, initialStart, customers, services, saving, error, onSubmit, onClose }) {
  const moving = Boolean(appointment);
  const [form, setForm] = useState({
    customerId: '',
    serviceId: '',
    startAt: toInputValue(appointment?.startAt ?? initialStart),
    notes: '',
  });
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const canSubmit = Boolean(form.startAt) && (moving || (form.customerId && form.serviceId));

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    onSubmit(moving
      ? { startAt: form.startAt }
      : { customerId: Number(form.customerId), serviceId: Number(form.serviceId), startAt: form.startAt, notes: form.notes.trim() });
  };

  return (
    <Modal
      title={moving ? 'Mover cita' : 'Nueva cita'}
      subtitle={moving ? `${appointment.customerName} · ${appointment.serviceName}` : undefined}
      onClose={onClose}
      dismissible={false}
      footer={(
        <>
          <Button variant="soft" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="appointment-form" loading={saving} disabled={!canSubmit}>
            {moving ? 'Mover cita' : 'Agendar cita'}
          </Button>
        </>
      )}
    >
      <form id="appointment-form" onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div role="alert" className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold">
            {error}
          </div>
        )}

        {!moving && (
          <>
            <Select label="Cliente" value={form.customerId} onChange={set('customerId')} required>
              <option value="">Selecciona un cliente</option>
              {customers.map(c => <option key={c.id} value={c.id}>{c.fullName}</option>)}
            </Select>
            <Select label="Servicio" value={form.serviceId} onChange={set('serviceId')} required>
              <option value="">Selecciona un servicio</option>
              {services.map(s => (
                <option key={s.id} value={s.id}>
                  {s.name}{s.durationMinutes ? ` · ${s.durationMinutes} min` : ''}
                </option>
              ))}
            </Select>
          </>
        )}

        <Input
          label="Fecha y hora"
          type="datetime-local"
          value={form.startAt}
          onChange={set('startAt')}
          required
          hint={moving ? 'La duración de la cita se conserva.' : 'La cita dura lo que el servicio (30 min si no tiene duración).'}
        />

        {!moving && (
          <Field label="Notas (opcional)">
            <textarea
              value={form.notes}
              onChange={set('notes')}
              maxLength={255}
              rows={2}
              className={inputClass}
              placeholder="Preferencias, recordatorios…"
            />
          </Field>
        )}
      </form>
    </Modal>
  );
}
