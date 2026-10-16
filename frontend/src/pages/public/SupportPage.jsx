import React, { useState } from 'react';
import { CheckCircle2, Search, LifeBuoy } from 'lucide-react';
import { createSupportTicketApi, trackSupportTicketApi } from '../../services/publicApi';
import { useAuth } from '../../context/AuthContext';
import { Badge, Button, Card, Input, Select, Field, inputClass } from '../../components/ui';
import PublicLayout from './PublicLayout';

const TYPES = [
  { value: 'PETITION', label: 'Petición' },
  { value: 'COMPLAINT', label: 'Queja' },
  { value: 'CLAIM', label: 'Reclamo' },
  { value: 'SUGGESTION', label: 'Sugerencia' },
  { value: 'BUG_REPORT', label: 'Reporte de error' },
];
const TYPE_LABEL = Object.fromEntries(TYPES.map(t => [t.value, t.label]));

const STATUS = {
  OPEN: { label: 'Abierto', tone: 'info' },
  IN_PROGRESS: { label: 'En proceso', tone: 'warning' },
  RESOLVED: { label: 'Resuelto', tone: 'success' },
  CLOSED: { label: 'Cerrado', tone: 'neutral' },
};

const EMPTY = { type: 'PETITION', title: '', description: '', contactName: '', contactEmail: '', contactPhone: '' };

function NewTicketForm({ onCreated }) {
  const { user } = useAuth();
  const [form, setForm] = useState({ ...EMPTY, contactName: user?.fullName || '' });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const set = (patch) => setForm(prev => ({ ...prev, ...patch }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSaving(true);
    try {
      const ticket = await createSupportTicketApi({
        ...form,
        title: form.title.trim(),
        description: form.description.trim(),
        contactName: form.contactName.trim(),
        contactEmail: form.contactEmail.trim(),
        priority: form.type === 'BUG_REPORT' ? 'HIGH' : 'MEDIUM',
        storeId: user?.storeId,
      });
      onCreated(ticket);
    } catch (err) {
      setError(err.message || 'No se pudo radicar la solicitud. Intenta nuevamente.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Select label="Tipo de solicitud" value={form.type} onChange={e => set({ type: e.target.value })}>
        {TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
      </Select>
      <Input label="Asunto" value={form.title} onChange={e => set({ title: e.target.value })} required maxLength={150} />
      <Field label="Descripción">
        <textarea
          className={`${inputClass} min-h-32 resize-y`}
          value={form.description}
          onChange={e => set({ description: e.target.value })}
          placeholder="Cuéntanos qué ocurrió o qué necesitas, con el mayor detalle posible."
          required
        />
      </Field>
      <div className="grid sm:grid-cols-2 gap-4">
        <Input label="Nombre" value={form.contactName} onChange={e => set({ contactName: e.target.value })} required />
        <Input label="Correo de contacto" type="email" value={form.contactEmail} onChange={e => set({ contactEmail: e.target.value })} required />
      </div>
      <Input label="Teléfono (opcional)" value={form.contactPhone} onChange={e => set({ contactPhone: e.target.value })} />

      {error && <p className="text-xs font-semibold text-red-500">{error}</p>}
      <Button type="submit" loading={saving} className="w-full">Radicar solicitud</Button>
    </form>
  );
}

function TrackTicket({ initialNumber = '' }) {
  const [number, setNumber] = useState(initialNumber);
  const [ticket, setTicket] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setTicket(null);
    setLoading(true);
    try {
      setTicket(await trackSupportTicketApi(number));
    } catch {
      setError('No encontramos ningún radicado con ese número. Revisa que esté completo (ej. PQR-20261016-0001).');
    } finally {
      setLoading(false);
    }
  };

  const status = ticket && (STATUS[ticket.status] || { label: ticket.status, tone: 'neutral' });

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="flex gap-2 items-end">
        <div className="flex-1">
          <Input label="Número de radicado" value={number} onChange={e => setNumber(e.target.value)} placeholder="PQR-20261016-0001" required />
        </div>
        <Button type="submit" icon={Search} loading={loading}>Consultar</Button>
      </form>

      {error && <p className="text-xs font-semibold text-red-500">{error}</p>}

      {ticket && (
        <div className="p-5 rounded-2xl border border-line space-y-3">
          <div className="flex items-center justify-between gap-3">
            <span className="font-mono text-xs font-bold text-ink-soft">{ticket.ticketNumber}</span>
            <Badge tone={status.tone}>{status.label}</Badge>
          </div>
          <div>
            <h3 className="font-extrabold text-sm text-ink">{ticket.title}</h3>
            <p className="text-[11px] text-ink-muted font-semibold">{TYPE_LABEL[ticket.type] || ticket.type}</p>
          </div>
          {ticket.resolutionNotes && (
            <div className="p-3 rounded-xl bg-emerald-50 dark:bg-emerald-500/10 text-xs text-emerald-700 dark:text-emerald-400 whitespace-pre-line">
              <span className="font-extrabold block mb-1">Respuesta del equipo</span>
              {ticket.resolutionNotes}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function SupportPage() {
  const [tab, setTab] = useState('new'); // new | track
  const [created, setCreated] = useState(null);

  return (
    <PublicLayout>
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-2xl bg-brand/10 border border-brand/25 flex items-center justify-center text-brand">
            <LifeBuoy className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-2xl font-black text-ink">Soporte</h1>
            <p className="text-xs text-ink-soft font-medium">Peticiones, quejas, reclamos, sugerencias y reportes de error</p>
          </div>
        </div>

        <div className="flex gap-2">
          {[['new', 'Nueva solicitud'], ['track', 'Consultar radicado']].map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              className={`px-4 py-2 rounded-full text-xs font-bold cursor-pointer transition-colors ${
                tab === id ? 'bg-solid text-white' : 'text-ink-soft border border-line hover:border-line-strong'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <Card>
          {tab === 'track' ? (
            <TrackTicket />
          ) : created ? (
            <div className="text-center space-y-3 py-6">
              <CheckCircle2 className="w-12 h-12 text-emerald-500 mx-auto" />
              <h2 className="font-extrabold text-lg text-ink">Solicitud radicada</h2>
              <p className="text-xs text-ink-soft">Guarda este número para consultar el estado de tu solicitud:</p>
              <p className="font-mono font-black text-xl text-brand select-all">{created.ticketNumber}</p>
              <Button variant="soft" onClick={() => setCreated(null)}>Radicar otra solicitud</Button>
            </div>
          ) : (
            <NewTicketForm onCreated={setCreated} />
          )}
        </Card>
      </div>
    </PublicLayout>
  );
}
