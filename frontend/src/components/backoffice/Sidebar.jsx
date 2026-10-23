import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Store,
  Tags,
  Package,
  Puzzle,
  ScrollText,
  LifeBuoy,
  UserCog,
  Clock,
  History,
  Users,
  BarChart3,
  Receipt,
  LogOut,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import BentoIcon from '../BentoIcon';

export default function BackofficeSidebar({ isOpen, onClose, isCollapsed, onToggleCollapse }) {
  const { logout, user } = useAuth();
  const navigate = useNavigate();

  const getInitials = (name) => {
    if (!name) return 'SA';
    return name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
  };

  const navGroups = [
    {
      title: 'Locales',
      items: [
        { path: '/backoffice/stats', label: 'Estadísticas', icon: BarChart3 },
        { path: '/backoffice', label: 'Locales', icon: Store, end: true },
        { path: '/backoffice/onboarding', label: 'Pendientes', icon: Clock },
        { path: '/backoffice/users', label: 'Usuarios', icon: Users },
        { path: '/backoffice/audit', label: 'Auditoría', icon: History },
        { path: '/backoffice/billing', label: 'Facturación DIAN', icon: Receipt },
      ],
    },
    {
      title: 'Catálogos',
      items: [
        { path: '/backoffice/categories', label: 'Categorías de Locales', icon: Tags },
        { path: '/backoffice/product-categories', label: 'Categorías de Producto', icon: Package },
        { path: '/backoffice/parameters', label: 'Parámetros y Catálogos', icon: Puzzle },
        { path: '/backoffice/legal-documents', label: 'Textos Legales', icon: ScrollText },
      ],
    },
    {
      title: 'Soporte y Equipo',
      items: [
        { path: '/backoffice/support', label: 'Soporte y PQRs', icon: LifeBuoy },
        { path: '/backoffice/staff', label: 'Operadores', icon: UserCog },
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
      {isOpen && (
        <div 
          onClick={onClose}
          className="fixed inset-0 bg-black/60 backdrop-blur-xs z-40 lg:hidden transition-opacity"
        />
      )}

      <aside
        className={`fixed top-0 left-0 h-screen bg-card text-ink z-50 flex flex-col py-6 border-r border-line shadow-xs select-none transition-all duration-300 ${
          isCollapsed ? 'lg:w-20' : 'lg:w-64'
        } ${
          isOpen ? 'translate-x-0 w-64' : '-translate-x-full lg:translate-x-0'
        }`}
      >
        <div className={`shrink-0 flex items-center gap-3 w-full pb-4 border-b border-line ${
          isCollapsed ? 'px-3 justify-center' : 'px-5 justify-between'
        }`}>
          <div className="flex items-center gap-3 min-w-0">
            <BentoIcon />
            {(!isCollapsed || isOpen) && (
              <div className="truncate">
                <h1 className="font-black text-lg leading-tight tracking-tight text-ink truncate flex items-center gap-1">
                  <span>Bento</span><span className="text-brand">POS</span>
                </h1>
                <p className="text-[10px] text-gray-400 font-semibold tracking-wide">Backoffice SaaS</p>
              </div>
            )}
          </div>
        </div>

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
                    end={item.end}
                    onClick={handleNavClick}
                    title={isCollapsed ? item.label : undefined}
                    className={({ isActive }) =>
                      `rounded-2xl flex items-center transition-all duration-200 cursor-pointer ${
                        isActive
                          ? 'bg-solid text-white shadow-md shadow-[#0A0A0A]/10 font-bold'
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
                        {(!isCollapsed || isOpen) && <span className="truncate">{item.label}</span>}
                      </>
                    )}
                  </NavLink>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="shrink-0 flex flex-col gap-2 w-full px-3 pt-3 border-t border-line">
          {/* User Card */}
          <div className={`flex items-center gap-2.5 p-2.5 rounded-xl ${isCollapsed && !isOpen ? 'justify-center' : ''}`}>
            <div className="w-9 h-9 rounded-full bg-solid text-white flex items-center justify-center font-bold text-xs shrink-0 ring-1 ring-[#EAEAEA] dark:ring-line">
              {getInitials(user?.fullName || user?.username)}
            </div>
            {(!isCollapsed || isOpen) && (
              <div className="min-w-0 flex-1">
                <p className="text-xs font-bold text-ink truncate">{user?.fullName || user?.username}</p>
                <p className="text-[11px] text-ink-muted truncate">Super Admin</p>
              </div>
            )}
          </div>

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

          <button
            onClick={handleLogout}
            title="Cerrar Sesión"
            className={`flex items-center gap-3 p-2.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-2xl transition-all cursor-pointer text-xs font-extrabold ${
              isCollapsed && !isOpen ? 'justify-center' : 'px-3'
            }`}
          >
            <LogOut className="w-5 h-5 shrink-0" />
            {(!isCollapsed || isOpen) && <span>Cerrar Sesión</span>}
          </button>
        </div>
      </aside>
    </>
  );
}
