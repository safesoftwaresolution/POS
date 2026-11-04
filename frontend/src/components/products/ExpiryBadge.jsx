import React from 'react';
import { CalendarClock } from 'lucide-react';
import { Badge } from '../ui';
import { EXPIRY_WINDOW_DAYS, daysUntil, expiryLabel, expiryStatus, formatExpiryDate } from '../../utils/expiry';

const TONES = { expired: 'danger', today: 'danger', soon: 'warning', ok: 'neutral' };

/**
 * Etiqueta del vencimiento de un producto: "Vence en 12 días" (ámbar), "Vence hoy" / "Vencido hace 3 días" (rojo)
 * o la fecha en neutro si aún falta mucho. No pinta nada si el producto no tiene fecha.
 */
export default function ExpiryBadge({ date, windowDays = EXPIRY_WINDOW_DAYS, className }) {
  const status = expiryStatus(date, windowDays);
  if (status === 'none') return null;

  const label = status === 'ok' ? `Vence ${formatExpiryDate(date)}` : expiryLabel(daysUntil(date));
  return (
    <Badge tone={TONES[status]} className={className}>
      <span title={`Vencimiento: ${formatExpiryDate(date)}`} className="inline-flex items-center gap-1">
        <CalendarClock className="w-3 h-3 shrink-0" />
        {label}
      </span>
    </Badge>
  );
}
