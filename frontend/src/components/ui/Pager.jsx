import { ChevronLeft, ChevronRight } from 'lucide-react';

/** Paginación de los listados que vienen paginados del servidor (`page` empieza en 0). No se muestra con una sola página. */
export default function Pager({ page, totalPages, totalElements, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between pt-2 border-t border-line">
      <p className="text-[11px] font-bold text-ink-muted">
        {totalElements} registros · página {page + 1} de {totalPages}
      </p>
      <div className="flex gap-2">
        <button
          onClick={() => onChange(Math.max(0, page - 1))}
          disabled={page === 0}
          className="p-2 rounded-xl bg-gray-100 dark:bg-line disabled:opacity-40 cursor-pointer"
          aria-label="Página anterior"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>
        <button
          onClick={() => onChange(Math.min(totalPages - 1, page + 1))}
          disabled={page >= totalPages - 1}
          className="p-2 rounded-xl bg-gray-100 dark:bg-line disabled:opacity-40 cursor-pointer"
          aria-label="Página siguiente"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
