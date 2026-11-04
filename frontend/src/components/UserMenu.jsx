import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import useAuth from '../hooks/useAuth';
import { roleLabel } from '../constants/roles';
import Avatar from './ui/Avatar';

/**
 * Avatar del encabezado: al hacer clic abre la ficha de la persona con su correo, su rol, los accesos que
 * cada encabezado quiera sumar (`items`: `{ label, icon, to }`) y el cierre de sesión.
 */
export default function UserMenu({ items = [] }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const handleEscape = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [open]);

  const handleLogout = async () => {
    setOpen(false);
    await logout();
    navigate('/login');
  };

  const goTo = (path) => {
    setOpen(false);
    navigate(path);
  };

  const name = user?.fullName || user?.username;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        title={name}
        className="rounded-full cursor-pointer transition-transform hover:scale-105 focus-visible:outline-2 focus-visible:outline-brand"
      >
        <Avatar
          name={name}
          src={user?.avatarUrl}
          className="w-10 h-10 rounded-full text-xs shadow-sm border-2 border-white dark:border-line-strong"
        />
      </button>

      {open && (
        <div role="menu" className="absolute right-0 mt-2 w-64 bg-card border border-line rounded-2xl shadow-xl z-50 overflow-hidden">
          <div className="flex items-center gap-3 p-4 border-b border-line">
            <Avatar name={name} src={user?.avatarUrl} className="w-11 h-11 rounded-full text-sm" />
            <div className="min-w-0">
              <p className="text-sm font-extrabold text-ink truncate">{name}</p>
              {user?.email && <p className="text-[11px] text-ink-muted truncate">{user.email}</p>}
              <p className="text-[11px] font-bold text-brand">{roleLabel(user?.role)}</p>
            </div>
          </div>
          <div className="p-2 flex flex-col gap-0.5">
            {items.map(({ label, icon: Icon, to }) => (
              <button
                key={to}
                type="button"
                role="menuitem"
                onClick={() => goTo(to)}
                className="w-full flex items-center gap-3 px-3 py-2.5 text-ink-soft hover:bg-page rounded-xl transition-colors cursor-pointer text-xs font-bold"
              >
                <Icon className="w-4 h-4 shrink-0 text-ink-muted" />
                <span>{label}</span>
              </button>
            ))}
            <button
              type="button"
              role="menuitem"
              onClick={handleLogout}
              className="w-full flex items-center gap-3 px-3 py-2.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-xl transition-colors cursor-pointer text-xs font-extrabold"
            >
              <LogOut className="w-4 h-4 shrink-0" />
              <span>Cerrar Sesión</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
