import React from 'react';
import { Menu, Sun, Moon } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';

export default function BackofficeHeader({ onToggleMobileMenu }) {
  const { user } = useAuth();
  const { darkMode, toggleTheme } = useTheme();

  const getInitials = (name) => {
    if (!name) return 'SA';
    return name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
  };

  return (
    <header className="bg-transparent flex justify-between items-center px-4 sm:px-8 py-4 h-20 w-full z-40 select-none">
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleMobileMenu}
          className="lg:hidden p-2 text-gray-700 dark:text-gray-200 hover:bg-gray-200 dark:hover:bg-[#262f38] rounded-xl transition-colors cursor-pointer"
        >
          <Menu className="w-5 h-5 text-[#067A57] dark:text-[#00D47E]" />
        </button>
        <span className="text-xs font-extrabold text-gray-400 dark:text-gray-500 uppercase tracking-wider">
          Panel de Super Administración
        </span>
      </div>

      <div className="flex items-center gap-3 sm:gap-4">
        <button
          onClick={toggleTheme}
          className="p-2.5 text-gray-600 dark:text-amber-400 bg-white dark:bg-[#161b22] border border-[#e2e8f0] dark:border-[#262f38] hover:bg-[#f2f4f7] dark:hover:bg-[#262f38] rounded-full transition-all flex items-center justify-center cursor-pointer shadow-xs"
          title={darkMode ? 'Cambiar a Modo Claro' : 'Cambiar a Modo Oscuro'}
        >
          {darkMode ? (
            <Sun className="w-4.5 h-4.5 text-amber-400 fill-amber-400/20" />
          ) : (
            <Moon className="w-4.5 h-4.5 text-slate-700" />
          )}
        </button>

        <div
          className="w-10 h-10 rounded-full bg-[#161b22] dark:bg-[#262f38] text-white flex items-center justify-center font-bold text-xs shadow-sm border-2 border-white dark:border-[#38434f] cursor-pointer"
          title={user?.fullName || user?.username}
        >
          {getInitials(user?.fullName || user?.username)}
        </div>
      </div>
    </header>
  );
}
