import React, { useState, useEffect } from 'react';
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
import { getProductsApi } from '../../services/productsApi';
import BentoIcon from '../BentoIcon';

const ROLE_LABELS = {
  ADMINISTRATOR: 'Administrador',
  SUPERVISOR: 'Supervisor',
  SELLER: 'Vendedor',
};

export default function Sidebar({ isOpen, onClose, isCollapsed, onToggleCollapse }) {
  const { logout, user } = useAuth();
  const navigate = useNavigate();
  const [lowStockCount, setLowStockCount] = useState(0);

  useEffect(() => {
    getProductsApi()
      .then(data => {
        const products = Array.isArray(data) ? data : [];
        setLowStockCount(products.filter(p => (p.quantityAvailable ?? p.stock ?? 0) <= (p.minStock || 5)).length);
      })
      .catch(() => setLowStockCount(0));
  }, []);

  const getInitials = (name) => {
    if (!name) return 'US';
    return name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
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
        { path: '/products', label: 'Productos', icon: Tag },
        { path: '/inventory', label: 'Inventario', icon: Package, badge: lowStockCount > 0 ? lowStockCount : undefined },
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
        className={`fixed top-0 left-0 h-screen bg-white dark:bg-[#17171B] text-[#0A0A0A] dark:text-[#F4F4F6] z-50 flex flex-col py-6 border-r border-[#EAEAEA] dark:border-[#272730] shadow-xs select-none transition-all duration-300 ${
          isCollapsed ? 'lg:w-20' : 'lg:w-64'
        } ${
          isOpen ? 'translate-x-0 w-64' : '-translate-x-full lg:translate-x-0'
        }`}
      >
        {/* Brand Header */}
        <div className={`shrink-0 flex items-center gap-3 w-full pb-4 border-b border-[#EAEAEA] dark:border-[#272730] ${
          isCollapsed ? 'px-3 justify-center' : 'px-5 justify-between'
        }`}>
          <div className="flex items-center gap-3 min-w-0">
            <BentoIcon />
            {(!isCollapsed || isOpen) && (
              <div className="truncate">
                <h1 className="font-black text-lg leading-tight tracking-tight text-[#0A0A0A] dark:text-white truncate flex items-center gap-1">
                  <span>Bento</span><span className="text-[#FF5520]">POS</span>
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
                groupIdx > 0 && <div className="h-px w-8 bg-[#EAEAEA] dark:bg-[#272730] mx-auto" />
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
                          ? 'bg-[#0A0A0A] text-white shadow-md shadow-[#0A0A0A]/10 font-bold'
                          : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-[#272730] hover:text-[#FF5520] dark:hover:text-[#FF5520]'
                      } ${
                        isCollapsed && !isOpen
                          ? 'w-11 h-11 justify-center mx-auto'
                          : 'w-full px-3.5 py-3 gap-3 text-xs font-semibold'
                      }`
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <Icon className={`w-5 h-5 shrink-0 ${isActive ? 'text-[#FF5520]' : ''}`} />
                        {(!isCollapsed || isOpen) ? (
                          <>
                            <span className="truncate">{item.label}</span>
                            {item.badge != null && (
                              <span className={`ml-auto text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                                isActive ? 'bg-white/20 text-white' : 'bg-[#FF5520] text-white'
                              }`}>
                                {item.badge}
                              </span>
                            )}
                          </>
                        ) : (
                          item.badge != null && (
                            <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-[#FF5520] ring-2 ring-white dark:ring-[#17171B]" />
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
        <div className="shrink-0 flex flex-col gap-2 w-full px-3 pt-3 border-t border-[#EAEAEA] dark:border-[#272730]">
          {/* User Card */}
          <div className={`flex items-center gap-2.5 p-2.5 rounded-xl ${isCollapsed && !isOpen ? 'justify-center' : ''}`}>
            <div className="w-9 h-9 rounded-full bg-[#0A0A0A] text-white flex items-center justify-center font-bold text-xs shrink-0 ring-1 ring-[#EAEAEA] dark:ring-[#272730]">
              {getInitials(user?.fullName || user?.username)}
            </div>
            {(!isCollapsed || isOpen) && (
              <div className="min-w-0 flex-1">
                <p className="text-xs font-bold text-[#0A0A0A] dark:text-white truncate">{user?.fullName || user?.username}</p>
                <p className="text-[11px] text-gray-400 dark:text-gray-500 truncate">{ROLE_LABELS[user?.role] || user?.role}</p>
              </div>
            )}
          </div>

          {/* Collapse/Expand Desktop Button */}
          <button
            onClick={onToggleCollapse}
            className={`hidden lg:flex items-center gap-3 p-2.5 rounded-2xl text-xs font-bold text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-[#272730] transition-colors cursor-pointer ${
              isCollapsed ? 'justify-center' : 'px-3'
            }`}
            title={isCollapsed ? 'Expandir Menú' : 'Contraer Menú'}
          >
            {isCollapsed ? (
              <ChevronRight className="w-5 h-5 text-[#FF5520] dark:text-[#FF5520]" />
            ) : (
              <>
                <ChevronLeft className="w-5 h-5 text-[#FF5520] dark:text-[#FF5520]" />
                <span>Contraer Menú</span>
              </>
            )}
          </button>

          {/* Help Button */}
          <button 
            title="Soporte"
            className={`flex items-center gap-3 p-2.5 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-[#272730] hover:text-[#FF5520] dark:hover:text-[#FF5520] rounded-2xl transition-all cursor-pointer text-xs font-semibold ${
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
