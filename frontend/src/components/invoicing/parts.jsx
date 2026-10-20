import React from 'react';

export function TabButton({ active, onClick, icon: Icon, label, count }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`pb-3 text-sm font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
        active
          ? 'border-brand text-brand'
          : 'border-transparent text-ink-soft hover:text-ink'
      }`}
    >
      <Icon className="w-4 h-4" />
      <span>{label}</span>
      {count != null && (
        <span className="ml-0.5 px-2 py-0.5 rounded-full text-[10px] font-bold tabular-nums bg-gray-100 dark:bg-white/10 text-ink">
          {count}
        </span>
      )}
    </button>
  );
}

export function KpiCard({ label, value, hint, hintClass = 'text-ink-soft', icon: Icon, accent }) {
  return (
    <div className="bento-card p-5 flex flex-col justify-between gap-3 min-w-0">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-soft">{label}</span>
        <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${accent}`}>
          <Icon className="w-4 h-4" />
        </div>
      </div>
      <div className="min-w-0">
        <div className={`font-black text-ink tabular-nums truncate ${String(value).length > 8 ? 'text-xl leading-[2.25rem]' : 'text-3xl'}`}>{value}</div>
        <div className={`text-[11px] font-semibold mt-0.5 truncate ${hintClass}`}>{hint}</div>
      </div>
    </div>
  );
}
