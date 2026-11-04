import React, { useState, useEffect, lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import useAutoRefresh from './hooks/useAutoRefresh';
import { AuthProvider } from './context/AuthContext';
import useAuth from './hooks/useAuth';
import { ThemeProvider } from './context/ThemeContext';
import { ModalProvider } from './context/ModalContext';
import { getOwnStoreApi } from './services/storesApi';

import Sidebar from './components/layout/Sidebar';
import Header from './components/layout/Header';
import BackofficeSidebar from './components/backoffice/Sidebar';
import BackofficeHeader from './components/backoffice/Header';

import LoginBento from './pages/LoginBento';
import RegisterBento from './pages/RegisterBento';
import VerifyEmailBento from './pages/VerifyEmailBento';
import ForgotPasswordBento from './pages/ForgotPasswordBento';
import ResetPasswordBento from './pages/ResetPasswordBento';
import PendingStoreReviewBento from './pages/PendingStoreReviewBento';

const StoreOnboardingBento = lazy(() => import('./pages/onboarding/StoreOnboardingBento'));
const DashboardBento = lazy(() => import('./pages/DashboardBento'));
const POSBento = lazy(() => import('./pages/POSBento'));
const InventoryBento = lazy(() => import('./pages/InventoryBento'));
const ProductsBento = lazy(() => import('./pages/ProductsBento'));
const ServicesBento = lazy(() => import('./pages/ServicesBento'));
const CustomersBento = lazy(() => import('./pages/CustomersBento'));
const CreditBento = lazy(() => import('./pages/CreditBento'));
const InvoicingBento = lazy(() => import('./pages/InvoicingBento'));
const SalesBento = lazy(() => import('./pages/SalesBento'));
const CashBento = lazy(() => import('./pages/CashBento'));
const SettingsBento = lazy(() => import('./pages/SettingsBento'));
const UsersBento = lazy(() => import('./pages/UsersBento'));
const LegalPage = lazy(() => import('./pages/public/LegalPage'));
const SupportPage = lazy(() => import('./pages/public/SupportPage'));
const DocumentsBento = lazy(() => import('./pages/DocumentsBento'));
const StoresBento = lazy(() => import('./pages/backoffice/StoresBento'));
const BillingBento = lazy(() => import('./pages/backoffice/BillingBento'));
const CategoriesBento = lazy(() => import('./pages/backoffice/CategoriesBento'));
const ProductCategoriesBento = lazy(() => import('./pages/backoffice/ProductCategoriesBento'));
const ParametersBento = lazy(() => import('./pages/backoffice/ParametersBento'));
const LegalDocumentsBento = lazy(() => import('./pages/backoffice/LegalDocumentsBento'));
const SupportTicketsBento = lazy(() => import('./pages/backoffice/SupportTicketsBento'));
const StaffBento = lazy(() => import('./pages/backoffice/StaffBento'));
const OnboardingBento = lazy(() => import('./pages/backoffice/OnboardingBento'));
const StoreAuditBento = lazy(() => import('./pages/backoffice/StoreAuditBento'));
const UsersDirectoryBento = lazy(() => import('./pages/backoffice/UsersDirectoryBento'));
const StatsBento = lazy(() => import('./pages/backoffice/StatsBento'));

// Las páginas se cargan bajo demanda: el bundle inicial solo trae login, registro y los layouts.
function PageFallback() {
  return <div className="py-24 text-center text-xs font-bold text-ink-muted">Cargando...</div>;
}

/**
 * Restringe una ruta a ciertos roles. El backend ya responde 403, pero sin esto el usuario
 * llegaba a ver la pantalla vacía escribiendo la URL a mano.
 */
function RequireRole({ roles, children }) {
  const { user } = useAuth();
  return roles.includes(user?.role) ? children : <Navigate to="/" replace />;
}

/** Mientras el ADMINISTRATOR no tenga un local ACTIVE, bloquea el POS y muestra el estado. */
function RequireActiveStore({ children }) {
  const [store, setStore] = useState(undefined); // undefined = cargando

  useEffect(() => {
    let cancelled = false;
    getOwnStoreApi()
      .then(data => { if (!cancelled) setStore(data); })
      .catch(() => { if (!cancelled) setStore(null); });
    return () => { cancelled = true; };
  }, []);

  // Mientras el local sigue en revisión, consulta su estado cada cierto tiempo: al aprobarse, el POS se
  // abre solo sin que la persona tenga que recargar.
  useAutoRefresh(() => {
    if (store && store.status === 'ACTIVE') return;
    getOwnStoreApi().then(setStore).catch(() => {});
  });

  if (store === undefined) {
    return null;
  }
  if (!store || store.status !== 'ACTIVE') {
    return <PendingStoreReviewBento store={store} />;
  }
  return children;
}

function ProtectedLayout() {
  const { isAuthenticated, user } = useAuth();
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    return localStorage.getItem('pos_sidebar_collapsed') === 'true';
  });

  const toggleSidebarCollapse = () => {
    setSidebarCollapsed(prev => {
      const next = !prev;
      localStorage.setItem('pos_sidebar_collapsed', next ? 'true' : 'false');
      return next;
    });
  };

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role === 'SUPER_ADMIN') {
    return <Navigate to="/backoffice" replace />;
  }

  if (user?.role === 'ADMINISTRATOR' && !user?.storeId) {
    return <Navigate to="/onboarding/store" replace />;
  }

  if (user?.role === 'ADMINISTRATOR') {
    return (
      <RequireActiveStore>
        <ProtectedLayoutContent
          mobileSidebarOpen={mobileSidebarOpen}
          setMobileSidebarOpen={setMobileSidebarOpen}
          sidebarCollapsed={sidebarCollapsed}
          toggleSidebarCollapse={toggleSidebarCollapse}
        />
      </RequireActiveStore>
    );
  }

  return (
    <ProtectedLayoutContent
      mobileSidebarOpen={mobileSidebarOpen}
      setMobileSidebarOpen={setMobileSidebarOpen}
      sidebarCollapsed={sidebarCollapsed}
      toggleSidebarCollapse={toggleSidebarCollapse}
    />
  );
}

function ProtectedLayoutContent({ mobileSidebarOpen, setMobileSidebarOpen, sidebarCollapsed, toggleSidebarCollapse }) {
  return (
    <div className="min-h-screen bg-page text-ink flex transition-colors duration-300">
      {/* Expandable / Collapsible Sidebar */}
      <Sidebar
        isOpen={mobileSidebarOpen}
        onClose={() => setMobileSidebarOpen(false)}
        isCollapsed={sidebarCollapsed}
        onToggleCollapse={toggleSidebarCollapse}
      />

      {/* Main Workspace Area with Dynamic Padding (lg:pl-20 or lg:pl-64) */}
      <div className={`pl-0 transition-all duration-300 flex-1 flex flex-col min-w-0 ${
        sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-64'
      }`}>
        <Header
          onToggleMobileMenu={() => setMobileSidebarOpen(prev => !prev)}
        />

        <main className="p-4 sm:p-6 lg:p-8 flex-1">
          <Suspense fallback={<PageFallback />}>
          <Routes>
            <Route path="/" element={<DashboardBento />} />
            <Route path="/pos" element={<POSBento />} />
            <Route path="/inventory" element={<InventoryBento />} />
            <Route path="/suppliers" element={<Navigate to="/inventory?tab=suppliers" replace />} />
            <Route path="/products" element={<ProductsBento />} />
            {/* Todos los roles de tienda ven el catálogo (GET /services); crear y editar lo limita la propia pantalla. */}
            <Route path="/services" element={<ServicesBento />} />
            <Route path="/categories" element={<CategoriesBento />} />
            <Route path="/customers" element={<CustomersBento />} />
            <Route path="/credit" element={<CreditBento />} />
            <Route path="/invoicing" element={<InvoicingBento />} />
            <Route path="/sales" element={<SalesBento />} />
            <Route path="/cash" element={<RequireRole roles={['ADMINISTRATOR']}><CashBento /></RequireRole>} />
            <Route path="/reports" element={<Navigate to="/" replace />} />
            <Route path="/returns" element={<Navigate to="/inventory?tab=returns" replace />} />
            <Route path="/documents" element={<RequireRole roles={['ADMINISTRATOR']}><DocumentsBento /></RequireRole>} />
            <Route path="/users" element={<RequireRole roles={['ADMINISTRATOR']}><UsersBento /></RequireRole>} />
            <Route path="/settings" element={<RequireRole roles={['ADMINISTRATOR', 'SUPERVISOR']}><SettingsBento /></RequireRole>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  );
}

function BackofficeProtectedLayout() {
  const { isAuthenticated, user } = useAuth();
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    return localStorage.getItem('pos_backoffice_sidebar_collapsed') === 'true';
  });

  const toggleSidebarCollapse = () => {
    setSidebarCollapsed(prev => {
      const next = !prev;
      localStorage.setItem('pos_backoffice_sidebar_collapsed', next ? 'true' : 'false');
      return next;
    });
  };

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== 'SUPER_ADMIN') {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="min-h-screen bg-page text-ink flex transition-colors duration-300">
      <BackofficeSidebar
        isOpen={mobileSidebarOpen}
        onClose={() => setMobileSidebarOpen(false)}
        isCollapsed={sidebarCollapsed}
        onToggleCollapse={toggleSidebarCollapse}
      />

      <div className={`pl-0 transition-all duration-300 flex-1 flex flex-col min-w-0 ${
        sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-64'
      }`}>
        <BackofficeHeader
          onToggleMobileMenu={() => setMobileSidebarOpen(prev => !prev)}
        />

        <main className="p-4 sm:p-6 lg:p-8 flex-1">
          <Suspense fallback={<PageFallback />}>
          <Routes>
            <Route path="/" element={<StoresBento />} />
            <Route path="/billing" element={<BillingBento />} />
            <Route path="/categories" element={<CategoriesBento />} />
            <Route path="/product-categories" element={<ProductCategoriesBento />} />
            <Route path="/parameters" element={<ParametersBento />} />
            <Route path="/legal-documents" element={<LegalDocumentsBento />} />
            <Route path="/support" element={<SupportTicketsBento />} />
            <Route path="/staff" element={<StaffBento />} />
            <Route path="/onboarding" element={<OnboardingBento />} />
            <Route path="/audit" element={<StoreAuditBento />} />
            <Route path="/users" element={<UsersDirectoryBento />} />
            <Route path="/stats" element={<StatsBento />} />
            <Route path="*" element={<Navigate to="/backoffice" replace />} />
          </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <ModalProvider>
          <AuthProvider>
            <Suspense fallback={<PageFallback />}>
            <Routes>
              <Route path="/login" element={<LoginBento />} />
              <Route path="/register" element={<RegisterBento />} />
              <Route path="/verify-email" element={<VerifyEmailBento />} />
              <Route path="/forgot-password" element={<ForgotPasswordBento />} />
              <Route path="/reset-password" element={<ResetPasswordBento />} />
              <Route path="/legal/:slug" element={<LegalPage />} />
              <Route path="/support" element={<SupportPage />} />
              <Route path="/onboarding/store" element={<StoreOnboardingBento />} />
              <Route path="/backoffice/*" element={<BackofficeProtectedLayout />} />
              <Route path="/*" element={<ProtectedLayout />} />
            </Routes>
            </Suspense>
          </AuthProvider>
        </ModalProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
