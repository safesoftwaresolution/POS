import React from 'react';
import { Trophy } from 'lucide-react';

// Rankings compartidos por los dashboards (local y backoffice).

const initials = (name = '') => name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || '?';

/** Ranking con avatar, barra de avance y participación sobre el total. `entries`: `[{ label, value }]` ya ordenado. */
export function Ranking({ entries, formatValue, avatar = false, startAt = 1, shareTotal, emptyLabel }) {
  if (entries.length === 0) {
    return <p className="text-xs text-ink-muted font-semibold py-10 text-center">{emptyLabel}</p>;
  }
  const max = Math.max(...entries.map((e) => e.value), 1);
  const total = shareTotal ?? entries.reduce((sum, e) => sum + e.value, 0);
  return (
    <ol className="space-y-4">
      {entries.map((e, i) => (
        <li key={e.label} className="flex items-center gap-3">
          {avatar ? (
            <span className="w-9 h-9 rounded-full bg-brand/10 text-brand text-[11px] font-black flex items-center justify-center shrink-0">{initials(e.label)}</span>
          ) : (
            <span className="w-6 text-center text-xs font-black text-ink-muted shrink-0 tabular-nums">{startAt + i}</span>
          )}
          <div className="flex-1 min-w-0">
            <div className="flex items-baseline justify-between gap-3 text-xs">
              <span className="font-bold text-ink truncate">{e.label}</span>
              <span className="font-black text-ink tabular-nums shrink-0">
                {formatValue(e.value)}
                {total > 0 && <span className="ml-1.5 font-semibold text-ink-muted">{Math.round((e.value / total) * 100)}%</span>}
              </span>
            </div>
            <div className="mt-1.5 h-2 bg-page rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full bar-grow-x ${i === 0 ? 'bg-brand' : 'bg-brand/40'}`}
                style={{ width: `${Math.max((e.value / max) * 100, 3)}%`, animationDelay: `${i * 60}ms` }}
              />
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}

/** Podio de los tres primeros (2.º, 1.º, 3.º) y el resto como ranking compacto. */
export function Podium({ entries, formatValue }) {
  if (entries.length < 3) return <Ranking entries={entries} formatValue={formatValue} emptyLabel="Sin datos en este período" />;
  const [first, second, third, ...rest] = entries;
  const columns = [
    { entry: second, rank: 2, height: 'h-24', tone: 'bg-brand/35' },
    { entry: first, rank: 1, height: 'h-36', tone: 'bg-gradient-to-t from-brand to-orange-400' },
    { entry: third, rank: 3, height: 'h-16', tone: 'bg-brand/20' },
  ];
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-3 gap-3 items-end">
        {columns.map(({ entry, rank, height, tone }, i) => (
          <div key={entry.label} className="flex flex-col items-center min-w-0">
            <span className="text-[11px] font-black text-ink tabular-nums mb-1.5">{formatValue(entry.value)}</span>
            <div className={`w-full ${height} ${tone} rounded-t-2xl bar-grow-y flex items-start justify-center pt-2`} style={{ animationDelay: `${i * 90}ms` }}>
              {rank === 1 ? <Trophy className="w-5 h-5 text-white" /> : <span className="text-sm font-black text-brand">{rank}</span>}
            </div>
            <span className="mt-2 text-[11px] font-bold text-ink text-center leading-tight line-clamp-2">{entry.label}</span>
          </div>
        ))}
      </div>
      {rest.length > 0 && <Ranking entries={rest} formatValue={formatValue} startAt={4} shareTotal={entries.reduce((sum, e) => sum + e.value, 0)} emptyLabel="" />}
    </div>
  );
}

