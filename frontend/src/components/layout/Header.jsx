import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Plus,
  Search,
  Bell,
  Menu,
  Sun,
  Moon,
  CheckCheck,
  Package,
  FileCheck2,
  FileX2
} from 'lucide-react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import {
  getNotificationsApi,
  getUnreadNotificationCountApi,
  markNotificationReadApi,
  markAllNotificationsReadApi
} from '../../services/notificationsApi';

const NOTIFICATION_ICONS = {
  LOW_STOCK: Package,
  DOCUMENT_APPROVED: FileCheck2,
  DOCUMENT_REJECTED: FileX2,
};

function formatRelativeTime(isoDate) {
  if (!isoDate) return '';
  const diffMs = Date.now() - new Date(isoDate).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'Ahora';
  if (minutes < 60) return `Hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `Hace ${hours} h`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `Hace ${days} d`;
  return new Date(isoDate).toLocaleDateString('es-CO');
}

export default function Header({ onToggleMobileMenu }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { darkMode, toggleTheme } = useTheme();

  const [notifOpen, setNotifOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const notifRef = useRef(null);

  const getInitials = (name) => {
    if (!name) return 'US';
    return name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
  };

  const refreshUnreadCount = useCallback(async () => {
    try {
      setUnreadCount(await getUnreadNotificationCountApi());
    } catch (e) {
      console.warn('Error consultando notificaciones no leídas:', e);
    }
  }, []);

  useEffect(() => {
    refreshUnreadCount();
    const interval = setInterval(refreshUnreadCount, 45000);
    return () => clearInterval(interval);
  }, [refreshUnreadCount]);

  useEffect(() => {
    function handleClickOutside(e) {
      if (notifRef.current && !notifRef.current.contains(e.target)) {
        setNotifOpen(false);
      }
    }
    if (notifOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [notifOpen]);

  const handleToggleNotifications = async () => {
    const next = !notifOpen;
    setNotifOpen(next);
    if (next) {
      try {
        setNotifications(await getNotificationsApi());
      } catch (e) {
        console.warn('Error cargando notificaciones:', e);
      }
    }
  };

  const handleNotificationClick = async (notif) => {
    if (!notif.read) {
      try {
        await markNotificationReadApi(notif.id);
        setNotifications(prev => prev.map(n => n.id === notif.id ? { ...n, read: true } : n));
        setUnreadCount(prev => Math.max(0, prev - 1));
      } catch (e) {
        console.warn('Error marcando notificación como leída:', e);
      }
    }
    setNotifOpen(false);
    if (notif.linkPath) navigate(notif.linkPath);
  };

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsReadApi();
      setNotifications(prev => prev.map(n => ({ ...n, read: true })));
      setUnreadCount(0);
    } catch (e) {
      console.warn('Error marcando todas como leídas:', e);
    }
  };

  return (
    <header className="bg-transparent flex justify-between items-center px-4 sm:px-8 py-4 h-20 w-full z-40 select-none">

      {/* Left Navigation Tabs */}
      <div className="flex items-center gap-6">
        <button
          onClick={onToggleMobileMenu}
          className="lg:hidden p-2 text-gray-700 dark:text-gray-200 hover:bg-gray-200 dark:hover:bg-[#262f38] rounded-xl transition-colors cursor-pointer"
        >
          <Menu className="w-5 h-5 text-[#067A57] dark:text-[#00D47E]" />
        </button>

        <div className="hidden md:flex gap-6 font-semibold text-sm text-[#161b22] dark:text-gray-200">
          <NavLink
            to="/"
            className={({ isActive }) =>
              isActive ? "text-[#161b22] dark:text-white font-extrabold border-b-2 border-[#067A57] dark:border-[#00D47E] pb-1" : "text-gray-500 dark:text-gray-400 hover:text-[#161b22] dark:hover:text-white transition-colors"
            }
          >
            Dashboard
          </NavLink>
          <NavLink
            to="/pos"
            className={({ isActive }) =>
              isActive ? "text-[#161b22] dark:text-white font-extrabold border-b-2 border-[#067A57] dark:border-[#00D47E] pb-1" : "text-gray-500 dark:text-gray-400 hover:text-[#161b22] dark:hover:text-white transition-colors"
            }
          >
            Punto de Venta
          </NavLink>
          <NavLink
            to="/invoicing"
            className={({ isActive }) =>
              isActive ? "text-[#161b22] dark:text-white font-extrabold border-b-2 border-[#067A57] dark:border-[#00D47E] pb-1" : "text-gray-500 dark:text-gray-400 hover:text-[#161b22] dark:hover:text-white transition-colors"
            }
          >
            Facturas DIAN
          </NavLink>
          <NavLink
            to="/inventory"
            className={({ isActive }) =>
              isActive ? "text-[#161b22] dark:text-white font-extrabold border-b-2 border-[#067A57] dark:border-[#00D47E] pb-1" : "text-gray-500 dark:text-gray-400 hover:text-[#161b22] dark:hover:text-white transition-colors"
            }
          >
            Inventario
          </NavLink>
          <NavLink
            to="/customers"
            className={({ isActive }) =>
              isActive ? "text-[#161b22] dark:text-white font-extrabold border-b-2 border-[#067A57] dark:border-[#00D47E] pb-1" : "text-gray-500 dark:text-gray-400 hover:text-[#161b22] dark:hover:text-white transition-colors"
            }
          >
            Clientes
          </NavLink>
        </div>
      </div>

      {/* Right Action Tools: Dark/Light Mode, Search, Bell, Profile, + Nueva Venta */}
      <div className="flex items-center gap-3 sm:gap-4">
        {/* Dark / Light Mode Toggle Button */}
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

        <button className="p-2.5 text-gray-600 dark:text-gray-300 hover:bg-[#f2f4f7] dark:hover:bg-[#262f38] rounded-full transition-all flex items-center justify-center cursor-pointer">
          <Search className="w-4.5 h-4.5" />
        </button>

        {/* Notificaciones */}
        <div className="relative" ref={notifRef}>
          <button
            onClick={handleToggleNotifications}
            className="p-2.5 text-gray-600 dark:text-gray-300 hover:bg-[#f2f4f7] dark:hover:bg-[#262f38] rounded-full transition-all flex items-center justify-center relative cursor-pointer"
          >
            <Bell className="w-4.5 h-4.5" />
            {unreadCount > 0 && (
              <span className="absolute top-1.5 right-1.5 min-w-[16px] h-4 px-1 bg-[#067A57] rounded-full border border-white dark:border-[#0d1117] text-[9px] font-extrabold text-white flex items-center justify-center">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          {notifOpen && (
            <div className="absolute right-0 mt-2 w-80 max-h-96 overflow-y-auto bg-white dark:bg-[#161b22] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl shadow-xl z-50">
              <div className="flex items-center justify-between px-4 py-3 border-b border-[#e2e8f0] dark:border-[#262f38]">
                <span className="font-extrabold text-xs text-[#161b22] dark:text-white">Notificaciones</span>
                {notifications.some(n => !n.read) && (
                  <button
                    onClick={handleMarkAllRead}
                    className="flex items-center gap-1 text-[10px] font-bold text-[#067A57] dark:text-[#00D47E] hover:underline cursor-pointer"
                  >
                    <CheckCheck className="w-3.5 h-3.5" /> Marcar todas
                  </button>
                )}
              </div>

              {notifications.length === 0 ? (
                <div className="py-8 text-center text-xs font-semibold text-gray-400 dark:text-gray-500">
                  No tienes notificaciones.
                </div>
              ) : (
                <div>
                  {notifications.map(notif => {
                    const Icon = NOTIFICATION_ICONS[notif.type] || Bell;
                    return (
                      <button
                        key={notif.id}
                        onClick={() => handleNotificationClick(notif)}
                        className={`w-full text-left px-4 py-3 border-b border-[#e2e8f0] dark:border-[#262f38] last:border-b-0 flex gap-3 hover:bg-gray-50 dark:hover:bg-white/5 cursor-pointer ${!notif.read ? 'bg-[#067A57]/5 dark:bg-[#067A57]/10' : ''}`}
                      >
                        <div className="w-8 h-8 rounded-xl bg-gray-100 dark:bg-white/10 flex items-center justify-center shrink-0">
                          <Icon className="w-4 h-4 text-gray-500 dark:text-gray-300" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-bold text-[#161b22] dark:text-white truncate">{notif.title}</p>
                          <p className="text-[11px] text-gray-500 dark:text-gray-400 line-clamp-2">{notif.message}</p>
                          <p className="text-[10px] text-gray-400 dark:text-gray-500 font-semibold mt-0.5">{formatRelativeTime(notif.createdAt)}</p>
                        </div>
                        {!notif.read && <span className="w-2 h-2 rounded-full bg-[#067A57] dark:bg-[#00D47E] shrink-0 mt-1.5" />}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        {/* User Profile Avatar */}
        <div className="w-10 h-10 rounded-full bg-[#161b22] dark:bg-[#262f38] text-white flex items-center justify-center font-bold text-xs shadow-sm border-2 border-white dark:border-[#38434f] cursor-pointer" title={user?.fullName || user?.username}>
          {getInitials(user?.fullName || user?.username)}
        </div>

        {/* Action Button "+ Nueva Venta" */}
        <button
          onClick={() => navigate('/pos')}
          className="flex items-center gap-1.5 bg-[#067A57] hover:bg-[#055C43] text-white font-bold text-xs rounded-full px-4 py-2 transition-all shadow-md shadow-[#067A57]/20 cursor-pointer"
        >
          <Plus className="w-4 h-4 text-white" />
          <span className="hidden sm:inline">Nueva Venta</span>
        </button>
      </div>

    </header>
  );
}
