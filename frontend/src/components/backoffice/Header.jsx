import React from 'react';
import { Menu, Sun, Moon } from 'lucide-react';
import useAuth from '../../hooks/useAuth';
import Avatar from '../ui/Avatar';
import useTheme from '../../hooks/useTheme';

export default function BackofficeHeader({ onToggleMobileMenu }) {
  const { user } = useAuth();
  const { darkMode, toggleTheme } = useTheme();

  return (
    <header className="bg-transparent flex justify-between items-center px-4 sm:px-8 py-4 h-20 w-full z-40 select-none">
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleMobileMenu}
          className="lg:hidden p-2 text-gray-700 dark:text-gray-200 hover:bg-gray-200 dark:hover:bg-line rounded-xl transition-colors cursor-pointer"
        >
          <Menu className="w-5 h-5 text-brand dark:text-brand" />
        </button>
        <span className="text-xs font-extrabold text-ink-muted uppercase tracking-wider">
          Panel de Super Administración
        </span>
      </div>

      <div className="flex items-center gap-3 sm:gap-4">
        <button
          onClick={toggleTheme}
          className="w-10 h-10 text-gray-600 dark:text-amber-400 bg-card border border-line hover:bg-[#F5F5F3] dark:hover:bg-line rounded-xl transition-all flex items-center justify-center cursor-pointer"
          title={darkMode ? 'Cambiar a Modo Claro' : 'Cambiar a Modo Oscuro'}
        >
          {darkMode ? (
            <Sun className="w-4.5 h-4.5 text-amber-400 fill-amber-400/20" />
          ) : (
            <Moon className="w-4.5 h-4.5 text-slate-700" />
          )}
        </button>

        <div title={user?.fullName || user?.username}>
          <Avatar
            name={user?.fullName || user?.username}
            src={user?.avatarUrl}
            className="w-10 h-10 rounded-full text-xs shadow-sm border-2 border-white dark:border-line-strong"
          />
        </div>
      </div>
    </header>
  );
}
