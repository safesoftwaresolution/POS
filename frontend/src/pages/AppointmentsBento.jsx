import React, { useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Plus, Clock, X, MoveRight } from 'lucide-react';
import {
  getAppointmentsApi, createAppointmentApi, rescheduleAppointmentApi, cancelAppointmentApi,
} from '../services/appointmentsApi';
import { getCustomersApi } from '../services/customersApi';
import { getServicesApi } from '../services/servicesApi';
import AppointmentFormModal from '../components/appointments/AppointmentFormModal';
import { Badge, Button, Card, EmptyState, PageHeader, SegmentedControl } from '../components/ui';
import useModal from '../hooks/useModal';
import useLoad from '../hooks/useLoad';
import useNow from '../hooks/useNow';
import asList from '../utils/asList';
import { addDays, daysOf, groupByDay, rangeFor, timeLabel, toLocalIso } from '../utils/calendar';

const VIEWS = [
  { id: 'day', label: 'Día' },
  { id: 'week', label: 'Semana' },
];

const dayTitle = (day) => day.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
const rangeTitle = (days) => (days.length === 1
  ? dayTitle(days[0])
  : `${days[0].toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })} – ${days[6].toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })}`);

/** Primera hora libre razonable para una cita nueva: hoy a las 09:00 o el día que se está mirando. */
const defaultStart = (day) => toLocalIso(new Date(day.getFullYear(), day.getMonth(), day.getDate(), 9, 0, 0));

/** Calendario de citas (solo locales con plantilla de citas): ver por día o semana, agendar, mover y cancelar. */
export default function AppointmentsBento() {
  const { confirm, notify } = useModal();
  const now = useNow();
  const [view, setView] = useState('week');
  const [cursor, setCursor] = useState(() => new Date());
  const [form, setForm] = useState(null); // { appointment?, initialStart? }
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const days = daysOf(view, cursor);
  const { from, to } = rangeFor(view, cursor);

  const { data: appointments, loading, reload } = useLoad(
    () => asList(getAppointmentsApi(from, to)),
    [from, to],
    { initial: [], autoRefresh: true },
  );
  // Se cargan una vez; solo el formulario los usa.
  const { data: customers } = useLoad(() => asList(getCustomersApi()), [], { initial: [] });
  const { data: services } = useLoad(() => asList(getServicesApi('', null, 0, 100, true)), [], { initial: [] });

  const step = view === 'day' ? 1 : 7;
  const grouped = groupByDay(appointments, days);
  const isToday = (day) => toLocalIso(day).slice(0, 10) === toLocalIso(now).slice(0, 10);

  const openCreate = (day = cursor) => { setForm({ initialStart: defaultStart(day) }); setErrorMsg(''); };
  const openMove = (appointment) => { setForm({ appointment }); setErrorMsg(''); };

  const handleSubmit = async (values) => {
    setSaving(true);
    setErrorMsg('');
    try {
      if (form.appointment) {
        await rescheduleAppointmentApi(form.appointment.id, values.startAt);
      } else {
        await createAppointmentApi(values);
      }
      setForm(null);
      reload();
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo guardar la cita.');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = async (appointment) => {
    const ok = await confirm(
      `¿Cancelar la cita de ${appointment.customerName} (${appointment.serviceName})?`,
      { title: 'Cancelar cita' },
    );
    if (!ok) return;
    try {
      await cancelAppointmentApi(appointment.id);
      reload();
    } catch (err) {
      notify('Error cancelando la cita: ' + err.message);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Citas"
        subtitle="Calendario y agendamiento de citas del local"
        actions={<Button icon={Plus} onClick={() => openCreate()}>Nueva cita</Button>}
      />

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" aria-label="Anterior" onClick={() => setCursor(addDays(cursor, -step))}>
            <ChevronLeft className="w-4 h-4" />
          </Button>
          <Button variant="outline" size="sm" onClick={() => setCursor(new Date())}>Hoy</Button>
          <Button variant="outline" size="sm" aria-label="Siguiente" onClick={() => setCursor(addDays(cursor, step))}>
            <ChevronRight className="w-4 h-4" />
          </Button>
          <h2 className="text-sm font-extrabold text-ink capitalize ml-2">{rangeTitle(days)}</h2>
        </div>
        <SegmentedControl options={VIEWS} value={view} onChange={setView} ariaLabel="Vista del calendario" />
      </div>

      {loading ? (
        <Card className="py-12 text-center text-xs font-bold text-ink-muted">Consultando citas...</Card>
      ) : (
        <div className={view === 'week' ? 'grid grid-cols-1 md:grid-cols-2 xl:grid-cols-7 gap-3' : 'grid grid-cols-1 gap-3'}>
          {grouped.map(({ day, appointments: dayAppointments }) => (
            <Card key={toLocalIso(day)} className="p-4 space-y-3 min-w-0" data-testid="day-column">
              <div className="flex items-center justify-between gap-2">
                <p className={`text-xs font-extrabold capitalize ${isToday(day) ? 'text-brand' : 'text-ink'}`}>{dayTitle(day)}</p>
                <button
                  type="button"
                  onClick={() => openCreate(day)}
                  aria-label={`Agendar el ${dayTitle(day)}`}
                  className="p-1 rounded-lg text-ink-muted hover:text-ink hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                >
                  <Plus className="w-4 h-4" />
                </button>
              </div>

              {dayAppointments.length === 0 ? (
                <p className="text-[11px] text-ink-muted">Sin citas</p>
              ) : (
                <ul className="space-y-2">
                  {dayAppointments.map(a => {
                    const cancelled = a.status === 'CANCELLED';
                    return (
                      <li key={a.id} className={`p-3 rounded-2xl border border-line bg-gray-50/60 dark:bg-white/5 space-y-1.5 ${cancelled ? 'opacity-60' : ''}`}>
                        <div className="flex items-center justify-between gap-2">
                          <span className="flex items-center gap-1 text-[11px] font-extrabold text-ink">
                            <Clock className="w-3 h-3" /> {timeLabel(a)}
                          </span>
                          {cancelled && <Badge tone="danger">Cancelada</Badge>}
                        </div>
                        <p className={`text-xs font-bold text-ink break-words ${cancelled ? 'line-through' : ''}`}>{a.customerName}</p>
                        <p className="text-[11px] text-ink-soft break-words">{a.serviceName}</p>
                        {a.notes && <p className="text-[11px] text-ink-muted break-words">{a.notes}</p>}
                        {!cancelled && (
                          <div className="flex gap-1 pt-1">
                            <Button variant="ghost" size="sm" icon={MoveRight} onClick={() => openMove(a)}>Mover</Button>
                            <Button variant="ghost" size="sm" icon={X} onClick={() => handleCancel(a)}>Cancelar</Button>
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </Card>
          ))}
        </div>
      )}

      {!loading && appointments.length === 0 && (
        <EmptyState
          icon={CalendarDays}
          title="No hay citas en este periodo"
          description="Agenda la primera con el botón «Nueva cita»"
        />
      )}

      {form && (
        <AppointmentFormModal
          appointment={form.appointment}
          initialStart={form.initialStart}
          customers={customers}
          services={services}
          saving={saving}
          error={errorMsg}
          onSubmit={handleSubmit}
          onClose={() => setForm(null)}
        />
      )}
    </div>
  );
}
