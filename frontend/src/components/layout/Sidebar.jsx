import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  ShoppingCart,
  Package,
  Tag,
  Users,
  FileText,
  FileCheck2,
  BarChart3,
  Settings,
  HelpCircle,
  LogOut,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export default function Sidebar({ isOpen, onClose, isCollapsed, onToggleCollapse }) {
  const { logout, user } = useAuth();
  const navigate = useNavigate();

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
        { path: '/products', label: 'Productos', icon: Tag },
        { path: '/inventory', label: 'Inventario', icon: Package },
      ],
    },
    {
      title: 'Clientes y Facturación',
      items: [
        { path: '/customers', label: 'Clientes', icon: Users },
        { path: '/invoicing', label: 'Facturación DIAN', icon: FileText },
        { path: '/documents', label: 'Documentos', icon: FileCheck2 },
      ],
    },
    ...(user?.role !== 'SELLER' ? [{
      title: 'Análisis',
      items: [{ path: '/reports', label: 'Reportes', icon: BarChart3 }],
    }] : []),
    {
      title: 'Sistema',
      items: [
        { path: '/settings', label: 'Configuración', icon: Settings },
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
        className={`fixed top-0 left-0 h-screen bg-white dark:bg-[#161b22] text-[#161b22] dark:text-[#f0f6fc] z-50 flex flex-col py-6 border-r border-[#e2e8f0] dark:border-[#262f38] shadow-xs select-none transition-all duration-300 ${
          isCollapsed ? 'lg:w-20' : 'lg:w-64'
        } ${
          isOpen ? 'translate-x-0 w-64' : '-translate-x-full lg:translate-x-0'
        }`}
      >
        {/* Brand Header */}
        <div className={`shrink-0 flex items-center gap-3 w-full pb-4 border-b border-[#e2e8f0] dark:border-[#262f38] ${
          isCollapsed ? 'px-3 justify-center' : 'px-5 justify-between'
        }`}>
          <div className="flex items-center gap-3 min-w-0">
            <img
              src="/logo-icon.png"
              alt="BentoPOS"
              className="w-10 h-10 rounded-2xl object-cover shadow-sm shrink-0 border border-black/5 dark:border-white/10"
            />
            {(!isCollapsed || isOpen) && (
              <div className="truncate">
                <h1 className="font-black text-lg leading-tight tracking-tight text-[#161b22] dark:text-white truncate flex items-center gap-1">
                  <span>Bento</span><span className="text-[#B3654A]">POS</span>
                </h1>
                <p className="text-[10px] text-gray-400 font-semibold tracking-wide">Tu negocio, en equilibrio</p>
              </div>
            )}
          </div>
        </div>

        {/* Navigation Menu */}
        <nav className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-4 w-full px-3 mt-4">
          {navGroups.map((group, groupIdx) => (
            <div key={group.title} className="flex flex-col gap-1.5 w-full">
              {(!isCollapsed || isOpen) ? (
                <p className="px-3.5 text-[10px] font-extrabold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                  {group.title}
                </p>
              ) : (
                groupIdx > 0 && <div className="h-px w-8 bg-[#e2e8f0] dark:bg-[#262f38] mx-auto" />
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
                      `rounded-2xl flex items-center transition-all duration-200 cursor-pointer ${
                        isActive
                          ? 'bg-[#B3654A] text-white shadow-md shadow-[#B3654A]/25 font-bold'
                          : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-[#262f38] hover:text-[#B3654A] dark:hover:text-[#D0906D]'
                      } ${
                        isCollapsed && !isOpen
                          ? 'w-11 h-11 justify-center mx-auto'
                          : 'w-full px-3.5 py-3 gap-3 text-xs font-semibold'
                      }`
                    }
                  >
                    <Icon className="w-5 h-5 shrink-0" />
                    {(!isCollapsed || isOpen) && <span className="truncate">{item.label}</span>}
                  </NavLink>
                );
              })}
            </div>
          ))}
        </nav>

        {/* Bottom Tools & Collapse Toggle */}
        <div className="shrink-0 flex flex-col gap-2 w-full px-3 pt-3 border-t border-[#e2e8f0] dark:border-[#262f38]">
          {/* Collapse/Expand Desktop Button */}
          <button
            onClick={onToggleCollapse}
            className={`hidden lg:flex items-center gap-3 p-2.5 rounded-2xl text-xs font-bold text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-[#262f38] transition-colors cursor-pointer ${
              isCollapsed ? 'justify-center' : 'px-3'
            }`}
            title={isCollapsed ? 'Expandir Menú' : 'Contraer Menú'}
          >
            {isCollapsed ? (
              <ChevronRight className="w-5 h-5 text-[#B3654A] dark:text-[#D0906D]" />
            ) : (
              <>
                <ChevronLeft className="w-5 h-5 text-[#B3654A] dark:text-[#D0906D]" />
                <span>Contraer Menú</span>
              </>
            )}
          </button>

          {/* Help Button */}
          <button 
            title="Soporte"
            className={`flex items-center gap-3 p-2.5 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-[#262f38] hover:text-[#B3654A] dark:hover:text-[#D0906D] rounded-2xl transition-all cursor-pointer text-xs font-semibold ${
              isCollapsed && !isOpen ? 'justify-center' : 'px-3'
            }`}
          >
            <HelpCircle className="w-5 h-5 shrink-0" />
            {(!isCollapsed || isOpen) && <span>Soporte</span>}
          </button>

          {/* Logout Button */}
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
