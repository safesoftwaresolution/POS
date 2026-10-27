import React, { useRef, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  ShoppingCart,
  Package,
  Tag,
  Users,
  FileText,
  FileCheck2,
  Receipt,
  Wallet,
  Settings,
  UserCog,
  HelpCircle,
  LogOut,
  ChevronLeft,
  ChevronRight,
  X,
  Moon,
  Bell,
  Camera
} from 'lucide-react';
import useAuth from '../../hooks/useAuth';
import useTheme from '../../hooks/useTheme';
import { getProductsApi } from '../../services/productsApi';
import useLoad from '../../hooks/useLoad';
import useUnreadNotifications from '../../hooks/useUnreadNotifications';
import asList from '../../utils/asList';
import { ROLE_LABELS } from '../../constants/roles';
import BentoIcon from '../BentoIcon';
import BrandLogo from '../BrandLogo';
import Avatar from '../ui/Avatar';
import { prepareAvatarForUpload } from '../../utils/image';
import { uploadMyAvatarApi, removeMyAvatarApi } from '../../services/usersApi';

export default function Sidebar({ isOpen, onClose, isCollapsed, onToggleCollapse }) {
  const { logout, user, updateUser } = useAuth();
  const { darkMode, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const [profileOpen, setProfileOpen] = useState(false);
  const [unreadCount] = useUnreadNotifications();
  const { data: lowStockCount } = useLoad(
    async () => {
      const products = await asList(getProductsApi());
      return products.filter((p) => (p.quantityAvailable ?? p.stock ?? 0) <= (p.minStock || 5)).length;
    },
    [],
    { initial: 0 },
  );

  const avatarInputRef = useRef(null);
  const [avatarBusy, setAvatarBusy] = useState(false);
  const [avatarError, setAvatarError] = useState('');

  const handleAvatarFile = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setAvatarError('');
    setAvatarBusy(true);
    try {
      const updated = await uploadMyAvatarApi(await prepareAvatarForUpload(file));
      updateUser({ avatarUrl: updated.avatarUrl ?? null });
    } catch (err) {
      setAvatarError(err.message || 'No se pudo subir la foto.');
    } finally {
      setAvatarBusy(false);
    }
  };

  const handleAvatarRemove = async () => {
    setAvatarError('');
    setAvatarBusy(true);
    try {
      await removeMyAvatarApi();
      updateUser({ avatarUrl: null });
    } catch (err) {
      setAvatarError(err.message || 'No se pudo quitar la foto.');
    } finally {
      setAvatarBusy(false);
    }
  };

  // Agrupado por área de trabajo para que el menú sea más fácil de escanear.
  const navGroups = [
    {
      title: 'Principal',
      items: [
        { path: '/', label: 'Dashboard', icon: LayoutDashboard },
      ],
    },
    {
      title: 'Operación',
      items: [
        { path: '/pos', label: 'Punto de Venta', icon: ShoppingCart },
        { path: '/sales', label: 'Ventas', icon: Receipt },
        ...(user?.role === 'ADMINISTRATOR' ? [{ path: '/cash', label: 'Caja', icon: Wallet }] : []),
        { path: '/products', label: 'Productos', icon: Tag },
        { path: '/inventory', label: 'Inventario', icon: Package, badge: lowStockCount > 0 ? lowStockCount : undefined },
      ],
    },
    {
      title: 'Clientes y Facturación',
      items: [
        { path: '/customers', label: 'Clientes', icon: Users },
        { path: '/invoicing', label: 'Facturación DIAN', icon: FileText },
        ...(user?.role === 'ADMINISTRATOR' ? [{ path: '/documents', label: 'Documentos', icon: FileCheck2 }] : []),
      ],
    },
    {
      title: 'Sistema',
      items: [
        ...(user?.role === 'ADMINISTRATOR' ? [{ path: '/users', label: 'Usuarios', icon: UserCog }] : []),
        ...(user?.role !== 'SELLER' ? [{ path: '/settings', label: 'Configuración', icon: Settings }] : []),
      ],
    },
  ];

  const handleLogout = async () => {
    await logout();
    if (onClose) onClose();
    navigate('/login');
  };

  const handleNavClick = () => {
    if (onClose) onClose();
  };

  return (
    <>
      {/* Mobile Backdrop Overlay */}
      {isOpen && (
        <div 
          onClick={onClose}
          className="fixed inset-0 bg-black/60 backdrop-blur-xs z-40 lg:hidden transition-opacity"
        />
      )}

      {/* Sidebar - Expandable / Collapsible Sidebar */}
      <aside
        className={`fixed top-0 left-0 h-screen bg-card text-ink z-50 flex flex-col py-6 border-r border-line shadow-xs select-none transition-all duration-300 ${
          isCollapsed ? 'lg:w-20' : 'lg:w-64'
        } ${
          isOpen ? 'translate-x-0 w-64' : '-translate-x-full lg:translate-x-0'
        }`}
      >
        {/* Brand Header */}
        <div className={`shrink-0 flex items-center gap-3 w-full pb-4 border-b border-line ${
          isCollapsed ? 'px-3 justify-center' : 'px-5 justify-between'
        }`}>
          <div className="flex items-center gap-3 min-w-0">
            {(!isCollapsed || isOpen) ? (
              <div className="min-w-0 space-y-1">
                <BrandLogo className="h-10" />
                <p className="text-[10px] text-gray-400 font-semibold tracking-wide truncate">Tu negocio, en equilibrio</p>
              </div>
            ) : (
              <BentoIcon />
            )}
          </div>
        </div>

        {/* Navigation Menu */}
        <nav className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-4 w-full px-3 mt-4">
          {navGroups.map((group, groupIdx) => (
            <div key={group.title} className="flex flex-col gap-1.5 w-full">
              {(!isCollapsed || isOpen) ? (
                <p className="px-3.5 text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">
                  {group.title}
                </p>
              ) : (
                groupIdx > 0 && <div className="h-px w-8 bg-[#EAEAEA] dark:bg-line mx-auto" />
              )}
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    onClick={handleNavClick}
                    title={isCollapsed ? item.label : undefined}
                    className={({ isActive }) =>
                      `relative rounded-2xl flex items-center transition-all duration-200 cursor-pointer ${
                        isActive
                          ? 'bg-solid text-solid-ink shadow-md shadow-[#0A0A0A]/10 font-bold'
                          : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-line hover:text-brand dark:hover:text-brand'
                      } ${
                        isCollapsed && !isOpen
                          ? 'w-11 h-11 justify-center mx-auto'
                          : 'w-full px-3.5 py-3 gap-3 text-xs font-semibold'
                      }`
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <Icon className={`w-5 h-5 shrink-0 ${isActive ? 'text-brand' : ''}`} />
                        {(!isCollapsed || isOpen) ? (
                          <>
                            <span className="truncate">{item.label}</span>
                            {item.badge != null && (
                              <span className={`ml-auto text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                                'bg-brand text-white'
                              }`}>
                                {item.badge}
                              </span>
                            )}
                          </>
                        ) : (
                          item.badge != null && (
                            <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-brand ring-2 ring-white dark:ring-card" />
                          )
                        )}
                      </>
                    )}
                  </NavLink>
                );
              })}
            </div>
          ))}
        </nav>

        {/* Bottom Tools & Collapse Toggle */}
        <div className="shrink-0 flex flex-col gap-2 w-full px-3 pt-3 border-t border-line">
          {/* User Card (opens account menu) */}
          <button
            onClick={() => setProfileOpen(true)}
            className={`flex items-center gap-2.5 p-2.5 rounded-xl hover:bg-gray-100 dark:hover:bg-line transition-colors cursor-pointer text-left ${isCollapsed && !isOpen ? 'justify-center' : ''}`}
          >
            <div className="relative shrink-0">
              <Avatar
                name={user?.fullName || user?.username}
                src={user?.avatarUrl}
                className="w-9 h-9 rounded-full text-xs ring-1 ring-[#EAEAEA] dark:ring-line"
              />
              <span className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-brand ring-2 ring-white dark:ring-card" />
            </div>
            {(!isCollapsed || isOpen) && (
              <>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-bold text-ink truncate">{user?.fullName || user?.username}</p>
                  <p className="text-[11px] text-ink-muted truncate">{ROLE_LABELS[user?.role] || user?.role}</p>
                </div>
                <ChevronRight className="w-4 h-4 text-gray-300 dark:text-gray-600 shrink-0" />
              </>
            )}
          </button>

          {/* Collapse/Expand Desktop Button */}
          <button
            onClick={onToggleCollapse}
            className={`hidden lg:flex items-center gap-3 p-2.5 rounded-2xl text-xs font-bold text-ink-soft hover:bg-gray-100 dark:hover:bg-line transition-colors cursor-pointer ${
              isCollapsed ? 'justify-center' : 'px-3'
            }`}
            title={isCollapsed ? 'Expandir Menú' : 'Contraer Menú'}
          >
            {isCollapsed ? (
              <ChevronRight className="w-5 h-5 text-brand dark:text-brand" />
            ) : (
              <>
                <ChevronLeft className="w-5 h-5 text-brand dark:text-brand" />
                <span>Contraer Menú</span>
              </>
            )}
          </button>
        </div>
      </aside>

      {/* Account Menu Modal */}
      {profileOpen && (
        <div
          onClick={() => setProfileOpen(false)}
          className="fixed inset-0 bg-black/50 backdrop-blur-xs z-[60] flex items-center justify-center p-4"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-sm bg-card rounded-3xl shadow-2xl border border-line overflow-hidden animate-in fade-in zoom-in duration-150"
          >
            {/* Header */}
            <div className="flex items-center justify-between px-5 pt-5 pb-4 border-b border-line">
              <div className="flex items-center gap-4 min-w-0">
                <div className="relative shrink-0">
                  <Avatar
                    name={user?.fullName || user?.username}
                    src={user?.avatarUrl}
                    className={`w-14 h-14 rounded-2xl text-lg ring-2 ring-[#EAEAEA] dark:ring-line ${avatarBusy ? 'opacity-50' : ''}`}
                  />
                  <button
                    type="button"
                    onClick={() => avatarInputRef.current?.click()}
                    disabled={avatarBusy}
                    aria-label="Cambiar foto de perfil"
                    title="Cambiar foto de perfil"
                    className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full bg-brand text-white flex items-center justify-center ring-2 ring-white dark:ring-card cursor-pointer disabled:cursor-wait"
                  >
                    <Camera className="w-3 h-3" />
                  </button>
                  <input
                    ref={avatarInputRef}
                    type="file"
                    accept="image/png,image/jpeg"
                    className="hidden"
                    onChange={handleAvatarFile}
                  />
                </div>
                <div className="min-w-0">
                  <p className="text-base font-extrabold text-ink truncate leading-tight">
                    {user?.fullName || user?.username}
                  </p>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-sm text-ink-soft truncate">
                      {ROLE_LABELS[user?.role] || user?.role}
                    </span>
                    <span className="shrink-0 px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#FFF0EB] text-brand border border-[#FFD5C8]">
                      PosBento Pro
                    </span>
                  </div>
                </div>
              </div>
              <button
                onClick={() => setProfileOpen(false)}
                className="p-1.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 cursor-pointer shrink-0"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {(avatarError || user?.avatarUrl) && (
              <div className="px-5 pt-3 flex items-center justify-between gap-3 text-[11px]">
                <span className="text-red-600 dark:text-red-400 font-semibold">{avatarError}</span>
                {user?.avatarUrl && (
                  <button
                    type="button"
                    onClick={handleAvatarRemove}
                    disabled={avatarBusy}
                    className="shrink-0 font-bold text-ink-soft hover:text-red-600 cursor-pointer disabled:opacity-50"
                  >
                    Quitar foto
                  </button>
                )}
              </div>
            )}

            {/* Menu Items */}
            <div className="px-3 py-3 flex flex-col gap-1">
              {/* Dark Mode Toggle */}
              <div className="flex items-center justify-between pl-[30px] pr-4 py-3.5 rounded-xl">
                <div className="flex items-center gap-3 text-sm font-medium text-gray-600 dark:text-gray-300">
                  <Moon className="w-4 h-4 text-gray-400" />
                  Modo oscuro
                </div>
                <button
                  onClick={toggleTheme}
                  className={`w-10 h-[22px] rounded-full transition-colors relative cursor-pointer ${darkMode ? 'bg-brand' : 'bg-gray-300 dark:bg-gray-600'}`}
                >
                  <span className={`absolute top-[3px] w-4 h-4 rounded-full bg-white shadow-sm transition-all duration-200 ${darkMode ? 'left-[22px]' : 'left-[3px]'}`} />
                </button>
              </div>

              {/* Notifications */}
              <div className="flex items-center justify-between pl-[30px] pr-4 py-3.5 rounded-xl">
                <div className="flex items-center gap-3 text-sm font-medium text-gray-600 dark:text-gray-300">
                  <Bell className="w-4 h-4 text-gray-400" />
                  Notificaciones
                </div>
                {unreadCount > 0 && (
                  <span className="min-w-[22px] h-[22px] px-1.5 bg-brand rounded-full text-[11px] font-extrabold text-white flex items-center justify-center">
                    {unreadCount > 99 ? '99+' : unreadCount}
                  </span>
                )}
              </div>

              {/* Settings */}
              <button
                onClick={() => { setProfileOpen(false); navigate('/settings'); }}
                className="w-full flex items-center gap-3 pl-[30px] pr-4 py-3.5 rounded-xl text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-line cursor-pointer text-left transition-colors"
              >
                <Settings className="w-4 h-4 text-gray-400 shrink-0" />
                Configuración
              </button>
            </div>

            <div className="ml-[60px] mr-4 border-t border-line" />

            {/* Manage Account */}
            <div className="px-3 py-3">
              <button
                onClick={() => { if (onClose) onClose(); navigate('/support'); }}
                className="w-full flex items-center justify-between pl-[30px] pr-4 py-3.5 rounded-xl text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-line cursor-pointer transition-colors"
              >
                <span className="flex items-center gap-3">
                  <HelpCircle className="w-4 h-4 text-gray-400" />
                  Ayuda y soporte
                </span>
                <ChevronRight className="w-4 h-4 text-ink-muted" />
              </button>
            </div>

            <div className="ml-[60px] mr-4 border-t border-line" />

            {/* Logout */}
            <div className="px-3 py-3">
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-3 pl-[30px] pr-4 py-3.5 rounded-xl text-sm font-semibold text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 cursor-pointer transition-colors"
              >
                <LogOut className="w-4 h-4 shrink-0" />
                Cerrar sesión
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

