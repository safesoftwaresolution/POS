import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Calendar,
  Bell,
  Menu,
  Sun,
  Moon,
  CheckCheck,
  Package,
  FileCheck2,
  FileX2,
  ShoppingBag
} from 'lucide-react';
import { useNavigate, useLocation } from 'react-router-dom';
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

const MONTHS_ES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

function formatHeaderDate(date) {
  return `${date.getDate()} ${MONTHS_ES[date.getMonth()]}, ${date.getFullYear()}`;
}

const PAGE_TITLES = {
  '/': { title: 'Dashboard', subtitle: 'Resumen en tiempo real de tu negocio' },
  '/pos': { title: 'Punto de Venta', subtitle: 'Registra ventas, escanea productos y cobra' },
  '/products': { title: 'Productos', subtitle: 'Catálogo, precios y subcategorías' },
  '/inventory': { title: 'Inventario', subtitle: 'Stock, devoluciones y proveedores' },
  '/customers': { title: 'Clientes', subtitle: 'Base de datos de clientes frecuentes' },
  '/invoicing': { title: 'Facturación DIAN', subtitle: 'Facturas electrónicas y su estado ante la DIAN' },
  '/documents': { title: 'Documentos', subtitle: 'Documentos legales de tu tienda' },
  '/reports': { title: 'Reportes', subtitle: 'Rendimiento de ventas y compras' },
  '/users': { title: 'Usuarios', subtitle: 'Personal con acceso a tu local y sus permisos' },
  '/settings': { title: 'Configuración', subtitle: 'Preferencias y datos de tu negocio' },
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
  const location = useLocation();
  const { user } = useAuth();
  const { darkMode, toggleTheme } = useTheme();
  const pageInfo = PAGE_TITLES[location.pathname] || { title: 'BentoPOS', subtitle: 'Tu negocio, en equilibrio' };
  const isOnPos = location.pathname === '/pos';

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
    <header className="bg-white dark:bg-page flex justify-between items-center px-4 sm:px-8 py-4 h-20 w-full z-40 select-none border-b border-line">

      {/* Page Title & Subtitle */}
      <div className="flex items-center gap-4 min-w-0">
        <button
          onClick={onToggleMobileMenu}
          className="lg:hidden p-2 text-gray-700 dark:text-gray-200 hover:bg-gray-200 dark:hover:bg-line rounded-xl transition-colors cursor-pointer shrink-0"
        >
          <Menu className="w-5 h-5 text-brand" />
        </button>

        <div className="min-w-0">
          <div className="flex items-center gap-2.5">
            <h1 className="text-xl font-bold text-ink tracking-tight truncate">
              {pageInfo.title}
            </h1>
            {isOnPos && (
              <span className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#FFF0EB] text-brand border border-[#FFD5C8] shrink-0">
                <span className="w-1.5 h-1.5 rounded-full bg-brand"></span>
                Modo Rápido
              </span>
            )}
          </div>
          <p className="text-xs text-ink-soft truncate hidden sm:block">{pageInfo.subtitle}</p>
        </div>
      </div>

      {/* Right Action Tools: Date, Dark/Light Mode, Bell, Terminal, Profile, + Nueva Venta */}
      <div className="flex items-center gap-2 sm:gap-3 shrink-0">
        {/* Current Date */}
        <div className="hidden md:flex items-center gap-2 h-10 px-3.5 text-gray-600 dark:text-gray-300 bg-card border border-line rounded-xl text-xs font-semibold">
          <Calendar className="w-4 h-4" />
          <span>{formatHeaderDate(new Date())}</span>
        </div>

        {/* Dark / Light Mode Toggle Button */}
        <button
          onClick={toggleTheme}
          className="group w-10 h-10 text-gray-600 dark:text-amber-400 bg-card border border-line hover:bg-[#F5F5F3] dark:hover:bg-line rounded-xl transition-all flex items-center justify-center cursor-pointer"
          title={darkMode ? 'Cambiar a Modo Claro' : 'Cambiar a Modo Oscuro'}
        >
          {darkMode ? (
            <Sun className="w-4.5 h-4.5 text-amber-400 fill-amber-400/20 group-hover:text-brand group-hover:fill-brand/20 transition-colors" />
          ) : (
            <Moon className="w-4.5 h-4.5 text-slate-700 group-hover:text-brand transition-colors" />
          )}
        </button>

        {/* Notificaciones */}
        <div className="relative" ref={notifRef}>
          <button
            onClick={handleToggleNotifications}
            className="group w-10 h-10 text-gray-600 dark:text-gray-300 bg-card border border-line hover:bg-[#F5F5F3] dark:hover:bg-line rounded-xl transition-all flex items-center justify-center relative cursor-pointer"
          >
            <Bell className="w-4.5 h-4.5 group-hover:text-brand transition-colors" />
            {unreadCount > 0 && (
              <span className="absolute top-1.5 right-1.5 min-w-[16px] h-4 px-1 bg-brand rounded-full border border-white dark:border-page text-[9px] font-extrabold text-white flex items-center justify-center">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          {notifOpen && (
            <div className="absolute right-0 mt-2 w-80 max-h-96 overflow-y-auto bg-card border border-line rounded-2xl shadow-xl z-50">
              <div className="flex items-center justify-between px-4 py-3 border-b border-line">
                <span className="font-extrabold text-xs text-ink">Notificaciones</span>
                {notifications.some(n => !n.read) && (
                  <button
                    onClick={handleMarkAllRead}
                    className="flex items-center gap-1 text-[10px] font-bold text-brand dark:text-brand hover:underline cursor-pointer"
                  >
                    <CheckCheck className="w-3.5 h-3.5" /> Marcar todas
                  </button>
                )}
              </div>

              {notifications.length === 0 ? (
                <div className="py-8 text-center text-xs font-semibold text-ink-muted">
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
                        className={`w-full text-left px-4 py-3 border-b border-line last:border-b-0 flex gap-3 hover:bg-gray-50 dark:hover:bg-white/5 cursor-pointer ${!notif.read ? 'bg-brand/5 dark:bg-brand/10' : ''}`}
                      >
                        <div className="w-8 h-8 rounded-xl bg-gray-100 dark:bg-white/10 flex items-center justify-center shrink-0">
                          <Icon className="w-4 h-4 text-gray-500 dark:text-gray-300" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-bold text-ink truncate">{notif.title}</p>
                          <p className="text-[11px] text-ink-soft line-clamp-2">{notif.message}</p>
                          <p className="text-[10px] text-ink-muted font-semibold mt-0.5">{formatRelativeTime(notif.createdAt)}</p>
                        </div>
                        {!notif.read && <span className="w-2 h-2 rounded-full bg-solid dark:bg-brand shrink-0 mt-1.5" />}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Quick POS Action (hidden while already on POS) */}
        {!isOnPos && (
          <button
            onClick={() => navigate('/pos')}
            className="flex items-center gap-1.5 bg-solid hover:bg-solid-hover text-white font-bold text-xs rounded-xl px-4 py-2.5 transition-all shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
          >
            <ShoppingBag className="w-4 h-4 text-brand" />
            <span className="hidden sm:inline">Nueva Venta</span>
          </button>
        )}

        {/* User Profile Avatar */}
        <div className="w-10 h-10 rounded-full bg-solid dark:bg-line text-white flex items-center justify-center font-bold text-xs shadow-sm border-2 border-white dark:border-line-strong cursor-pointer" title={user?.fullName || user?.username}>
          {getInitials(user?.fullName || user?.username)}
        </div>
      </div>

    </header>
  );
}
