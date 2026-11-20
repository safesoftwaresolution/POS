import React, { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown, Store } from 'lucide-react';
import useBusiness from '../../hooks/useBusiness';
import useAuth from '../../hooks/useAuth';
import useModal from '../../hooks/useModal';
import { Badge } from '../ui';
import { switchStoreApi } from '../../services/businessApi';
import { branchStatusLabel, branchStatusTone } from '../../utils/business';

/**
 * Cambia la sucursal en la que trabaja el dueño de un negocio. Solo aparece si el negocio tiene más de una sucursal.
 * Al cambiar, la sesión recibe un token de esa sucursal y la app se recarga desde el inicio: todo lo que había en
 * pantalla (productos, ventas, caja) era de la sucursal anterior.
 */
export default function BranchSwitcher({ align = 'right' }) {
  const { business, activeStoreId } = useBusiness();
  const { setSessionFromResponse } = useAuth();
  const { notify } = useModal();
  const [open, setOpen] = useState(false);
  const [switching, setSwitching] = useState(false);
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  if (!business || business.branches.length < 2) return null;

  const current = business.branches.find((b) => b.storeId === activeStoreId) || business.branches[0];

  const choose = async (branch) => {
    if (branch.storeId === activeStoreId || switching) {
      setOpen(false);
      return;
    }
    setSwitching(true);
    try {
      const data = await switchStoreApi(branch.storeId);
      setSessionFromResponse(data);
      window.location.assign('/');
    } catch (e) {
      setSwitching(false);
      setOpen(false);
      notify(e?.message || 'No se pudo cambiar de sucursal.');
    }
  };

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        disabled={switching}
        aria-haspopup="listbox"
        aria-expanded={open}
        title="Cambiar de sucursal"
        className="flex items-center gap-2 h-10 max-w-[14rem] px-3 text-ink bg-card border border-line hover:bg-[#F5F5F3] dark:hover:bg-line rounded-xl text-xs font-bold transition-all cursor-pointer disabled:opacity-60"
      >
        <Store className="w-4 h-4 text-brand shrink-0" />
        <span className="truncate">{switching ? 'Cambiando...' : current.name}</span>
        <ChevronDown className="w-3.5 h-3.5 text-ink-muted shrink-0" />
      </button>

      {open && (
        <ul
          role="listbox"
          className={`absolute ${align === 'left' ? 'left-0' : 'right-0'} mt-2 w-72 max-h-80 overflow-y-auto bg-card border border-line rounded-2xl shadow-xl z-50 py-1`}
        >
          <li className="px-4 pt-2 pb-1 text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">
            {business.name}
          </li>
          {business.branches.map((branch) => {
            const isCurrent = branch.storeId === current.storeId;
            return (
              <li key={branch.storeId} role="option" aria-selected={isCurrent}>
                <button
                  type="button"
                  onClick={() => choose(branch)}
                  className={`w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-gray-50 dark:hover:bg-white/5 cursor-pointer ${isCurrent ? 'bg-brand/5' : ''}`}
                >
                  <span className="min-w-0 flex-1">
                    <span className="block text-xs font-bold text-ink truncate">{branch.name}</span>
                    <span className="block text-[10px] font-semibold text-ink-muted">{branch.main ? 'Principal' : 'Sucursal'}</span>
                  </span>
                  {branch.status !== 'ACTIVE' && (
                    <Badge tone={branchStatusTone(branch.status)}>{branchStatusLabel(branch.status)}</Badge>
                  )}
                  {isCurrent && <Check className="w-4 h-4 text-brand shrink-0" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
