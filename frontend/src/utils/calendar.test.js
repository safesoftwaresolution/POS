import { describe, expect, it } from 'vitest';
import {
  addDays, startOfWeek, rangeFor, daysOf, toLocalIso, toInputValue, groupByDay, timeLabel,
} from './calendar';

// Los tests construyen fechas locales (año, mes, día) para no depender de la zona horaria de la máquina.
const d = (y, m, day, h = 0, min = 0) => new Date(y, m - 1, day, h, min, 0, 0);

describe('startOfWeek', () => {
  it('la semana empieza el lunes', () => {
    expect(toLocalIso(startOfWeek(d(2026, 11, 11, 15)))).toBe('2026-11-09T00:00:00'); // miércoles
    expect(toLocalIso(startOfWeek(d(2026, 11, 9)))).toBe('2026-11-09T00:00:00'); // lunes
    expect(toLocalIso(startOfWeek(d(2026, 11, 15, 23)))).toBe('2026-11-09T00:00:00'); // domingo
  });
});

describe('rangeFor', () => {
  it('día: de las 00:00 al día siguiente, el fin es exclusivo', () => {
    expect(rangeFor('day', d(2026, 11, 11, 15))).toEqual({ from: '2026-11-11T00:00:00', to: '2026-11-12T00:00:00' });
  });

  it('semana: de lunes a lunes siguiente', () => {
    expect(rangeFor('week', d(2026, 11, 11))).toEqual({ from: '2026-11-09T00:00:00', to: '2026-11-16T00:00:00' });
  });
});

describe('daysOf', () => {
  it('devuelve 1 día en vista día y 7 en vista semana', () => {
    expect(daysOf('day', d(2026, 11, 11)).map(toLocalIso)).toEqual(['2026-11-11T00:00:00']);
    const week = daysOf('week', d(2026, 11, 11));
    expect(week).toHaveLength(7);
    expect(toLocalIso(week[0])).toBe('2026-11-09T00:00:00');
    expect(toLocalIso(week[6])).toBe('2026-11-15T00:00:00');
  });
});

describe('addDays', () => {
  it('cruza el cambio de mes sin mutar la fecha original', () => {
    const base = d(2026, 11, 30);
    expect(toLocalIso(addDays(base, 2))).toBe('2026-12-02T00:00:00');
    expect(toLocalIso(base)).toBe('2026-11-30T00:00:00');
  });
});

describe('toInputValue', () => {
  it('formatea para <input type="datetime-local"> y lee el ISO del backend', () => {
    expect(toInputValue('2026-11-10T15:05:00')).toBe('2026-11-10T15:05');
    expect(toInputValue(null)).toBe('');
  });
});

describe('groupByDay', () => {
  const apps = [
    { id: 1, startAt: '2026-11-10T15:00:00' },
    { id: 2, startAt: '2026-11-10T09:30:00' },
    { id: 3, startAt: '2026-11-12T10:00:00' },
  ];

  it('agrupa por día, ordena por hora y deja vacíos los días sin citas', () => {
    const days = daysOf('week', d(2026, 11, 10));
    const grouped = groupByDay(apps, days);
    expect(grouped).toHaveLength(7);
    expect(grouped[1].appointments.map(a => a.id)).toEqual([2, 1]); // martes 10
    expect(grouped[3].appointments.map(a => a.id)).toEqual([3]); // jueves 12
    expect(grouped[0].appointments).toEqual([]);
  });
});

describe('timeLabel', () => {
  it('muestra la hora de inicio y fin', () => {
    expect(timeLabel({ startAt: '2026-11-10T09:30:00', endAt: '2026-11-10T10:15:00' })).toBe('09:30 – 10:15');
  });
});
