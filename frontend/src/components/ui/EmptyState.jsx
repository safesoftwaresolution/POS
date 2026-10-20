import React from 'react';

export default function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-line">
      {Icon && <Icon className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />}
      <div>
        <p className="text-xs font-bold text-ink">{title}</p>
        {description && <p className="text-[11px] text-ink-muted">{description}</p>}
      </div>
      {action}
    </div>
  );
}
