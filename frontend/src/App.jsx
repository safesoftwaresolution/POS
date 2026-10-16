import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
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
import PendingStoreReviewBento from './pages/PendingStoreReviewBento';
import StoreOnboardingBento from './pages/onboarding/StoreOnboardingBento';
import DashboardBento from './pages/DashboardBento';
import POSBento from './pages/POSBento';
import InventoryBento from './pages/InventoryBento';
import ProductsBento from './pages/ProductsBento';
import CustomersBento from './pages/CustomersBento';
import InvoicingBento from './pages/InvoicingBento';
import ReportsBento from './pages/ReportsBento';
import SettingsBento from './pages/SettingsBento';
import UsersBento from './pages/UsersBento';
import DocumentsBento from './pages/DocumentsBento';
import StoresBento from './pages/backoffice/StoresBento';
import BillingBento from './pages/backoffice/BillingBento';
import CategoriesBento from './pages/backoffice/CategoriesBento';
import ProductCategoriesBento from './pages/backoffice/ProductCategoriesBento';
import ParametersBento from './pages/backoffice/ParametersBento';
import LegalDocumentsBento from './pages/backoffice/LegalDocumentsBento';
import SupportTicketsBento from './pages/backoffice/SupportTicketsBento';
import StaffBento from './pages/backoffice/StaffBento';
import OnboardingBento from './pages/backoffice/OnboardingBento';

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
          <Routes>
            <Route path="/" element={<DashboardBento />} />
            <Route path="/pos" element={<POSBento />} />
            <Route path="/inventory" element={<InventoryBento />} />
            <Route path="/suppliers" element={<Navigate to="/inventory?tab=suppliers" replace />} />
            <Route path="/products" element={<ProductsBento />} />
            <Route path="/customers" element={<CustomersBento />} />
            <Route path="/invoicing" element={<InvoicingBento />} />
            <Route path="/reports" element={<ReportsBento />} />
            <Route path="/returns" element={<Navigate to="/inventory?tab=returns" replace />} />
            <Route path="/documents" element={<DocumentsBento />} />
            <Route path="/users" element={<UsersBento />} />
            <Route path="/settings" element={<SettingsBento />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
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
            <Route path="*" element={<Navigate to="/backoffice" replace />} />
          </Routes>
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
            <Routes>
              <Route path="/login" element={<LoginBento />} />
              <Route path="/register" element={<RegisterBento />} />
              <Route path="/verify-email" element={<VerifyEmailBento />} />
              <Route path="/onboarding/store" element={<StoreOnboardingBento />} />
              <Route path="/backoffice/*" element={<BackofficeProtectedLayout />} />
              <Route path="/*" element={<ProtectedLayout />} />
            </Routes>
          </AuthProvider>
        </ModalProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
