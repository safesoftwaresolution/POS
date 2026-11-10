// Utilidades de fecha del calendario de citas. Trabajan con fechas locales y con el ISO sin zona
// (YYYY-MM-DDTHH:mm:ss) que usa el backend (LocalDateTime), nunca con UTC.

const pad = (n) => String(n).padStart(2, '0');

export const addDays = (date, days) => {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
};

const midnight = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate());

/** Lunes de la semana de `date`, a las 00:00. */
export function startOfWeek(date) {
  const day = midnight(date);
  const offset = (day.getDay() + 6) % 7; // lunes = 0 … domingo = 6
  return addDays(day, -offset);
}

/** Fecha local -> 'YYYY-MM-DDTHH:mm:ss' (lo que espera el backend). */
export const toLocalIso = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
  `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;

/** ISO del backend -> valor de un <input type="datetime-local"> ('YYYY-MM-DDTHH:mm'). */
export const toInputValue = (iso) => (iso ? iso.slice(0, 16) : '');

/** Días que muestra la vista: uno en 'day', lunes a domingo en 'week'. */
export function daysOf(view, date) {
  if (view === 'day') return [midnight(date)];
  const monday = startOfWeek(date);
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

/** Rango [from, to) que se pide al backend para la vista; `to` es exclusivo. */
export function rangeFor(view, date) {
  const days = daysOf(view, date);
  return { from: toLocalIso(days[0]), to: toLocalIso(addDays(days[days.length - 1], 1)) };
}

/** Citas agrupadas por día (en el orden de `days`), cada grupo ordenado por hora de inicio. */
export function groupByDay(appointments, days) {
  return days.map((day) => {
    const key = toLocalIso(day).slice(0, 10);
    const appointmentsOfDay = appointments
      .filter((a) => a.startAt.slice(0, 10) === key)
      .sort((a, b) => a.startAt.localeCompare(b.startAt));
    return { day, appointments: appointmentsOfDay };
  });
}

/** '09:30 – 10:15' */
export const timeLabel = (appointment) => `${appointment.startAt.slice(11, 16)} – ${appointment.endAt.slice(11, 16)}`;
