import React from 'react';
import { CheckCircle, XCircle } from 'lucide-react';

/** Toasts de escaneo: no bloqueantes, se autodescartan y se apilan si llegan varios escaneos seguidos. */
export default function ScanToasts({ toasts }) {
  return (
    <div className="fixed top-4 right-4 z-[60] flex flex-col gap-2 items-end pointer-events-none">
      {toasts.map(toast => (
        <div
          key={toast.id}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-2xl shadow-xl text-xs font-bold text-white animate-in fade-in slide-in-from-top-2 duration-150 ${
            toast.type === 'success' ? 'bg-solid' : 'bg-red-600'
          }`}
        >
          {toast.type === 'success' ? <CheckCircle className="w-4 h-4 shrink-0" /> : <XCircle className="w-4 h-4 shrink-0" />}
          <span>{toast.message}</span>
        </div>
      ))}
    </div>
  );
}
