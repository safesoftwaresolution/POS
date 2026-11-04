import React, { useEffect, useRef, useState } from 'react';
import { Search, X } from 'lucide-react';
import { getProductsApi } from '../../services/productsApi';
import { Badge, inputClass } from '../ui';

const RESULT_LIMIT = 20;

/**
 * Selector de producto con búsqueda en el servidor (nombre, código o código de barras). A diferencia de cargar
 * la primera página del catálogo, llega a cualquier producto del local, también a los desactivados con stock.
 * `value` es el producto elegido (o null); `onChange(product | null)` recibe la elección o el borrado.
 */
export default function ProductPicker({ value, onChange, placeholder = 'Buscar por nombre o código...', activeOnly = false }) {
  const [term, setTerm] = useState('');
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const requestRef = useRef(0);

  useEffect(() => {
    if (!open) return undefined;
    // Cada búsqueda lleva un número: una respuesta lenta de una búsqueda anterior no pisa la actual.
    const requestId = ++requestRef.current;
    const timer = setTimeout(async () => {
      setLoading(true);
      setFailed(false);
      try {
        const list = await getProductsApi(term.trim(), null, 0, RESULT_LIMIT, activeOnly ? true : null);
        if (requestId === requestRef.current) setResults(Array.isArray(list) ? list : []);
      } catch {
        if (requestId === requestRef.current) { setResults([]); setFailed(true); }
      } finally {
        if (requestId === requestRef.current) setLoading(false);
      }
    }, term ? 250 : 0);
    return () => clearTimeout(timer);
  }, [term, open, activeOnly]);

  if (value) {
    return (
      <div className="flex items-center justify-between gap-3 p-3 bg-page border border-line rounded-2xl">
        <div className="min-w-0">
          <p className="text-xs font-bold text-ink truncate">
            {value.name}
            {value.active === false && <Badge tone="warning" className="ml-2 align-middle">Inactivo</Badge>}
          </p>
          <p className="text-[11px] text-ink-muted">
            {value.internalCode} · Stock actual: <span className="font-bold text-ink-soft tabular-nums">{value.quantityAvailable ?? 0}</span>
          </p>
        </div>
        <button
          type="button"
          onClick={() => { onChange(null); setTerm(''); }}
          aria-label="Cambiar producto"
          className="p-1.5 rounded-xl text-ink-muted hover:text-ink hover:bg-gray-100 dark:hover:bg-white/5 cursor-pointer shrink-0"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    );
  }

  const choose = (product) => {
    setOpen(false);
    setTerm('');
    onChange(product);
  };

  return (
    <div
      className="space-y-2"
      onBlur={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) setOpen(false); }}
    >
      <div className="relative">
        <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={term}
          onChange={(e) => { setTerm(e.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          onKeyDown={(e) => { if (e.key === 'Escape' && open) { e.stopPropagation(); setOpen(false); } }}
          placeholder={placeholder}
          aria-label="Buscar producto"
          autoComplete="off"
          className={`${inputClass} pl-10`}
        />
      </div>

      {open && (
        <div className="border border-line rounded-2xl bg-card max-h-56 overflow-y-auto divide-y divide-line">
          {loading && results.length === 0 ? (
            <p className="p-3 text-[11px] font-semibold text-ink-muted">Buscando...</p>
          ) : failed ? (
            <p className="p-3 text-[11px] font-semibold text-red-500">No se pudo buscar productos.</p>
          ) : results.length === 0 ? (
            <p className="p-3 text-[11px] font-semibold text-ink-muted">No hay productos que coincidan.</p>
          ) : (
            results.map((p) => (
              <button
                key={p.id}
                type="button"
                // Sin esto el input pierde el foco al pulsar (Safari no enfoca botones) y la lista se cierra antes del clic.
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(p)}
                className="w-full text-left px-3 py-2.5 hover:bg-gray-50 dark:hover:bg-white/5 cursor-pointer flex items-center justify-between gap-3"
              >
                <span className="min-w-0">
                  <span className="block text-xs font-bold text-ink truncate">{p.name}</span>
                  <span className="block text-[11px] text-ink-muted">{p.internalCode}</span>
                </span>
                <span className="flex items-center gap-2 shrink-0">
                  {p.active === false && <Badge tone="warning">Inactivo</Badge>}
                  <span className="text-xs font-black text-ink-soft tabular-nums">{p.quantityAvailable ?? 0}</span>
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
