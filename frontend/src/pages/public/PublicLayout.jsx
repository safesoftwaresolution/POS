import React from 'react';
import { Link } from 'react-router-dom';
import BentoIcon from '../../components/BentoIcon';
import { useAuth } from '../../context/AuthContext';

const LEGAL_LINKS = [
  { slug: 'terms-and-conditions', label: 'Términos y Condiciones' },
  { slug: 'privacy-policy', label: 'Política de Privacidad' },
  { slug: 'cookies-policy', label: 'Cookies' },
];

/** Marco común de las páginas públicas (legal y soporte): cabecera de marca y pie con enlaces. */
export default function PublicLayout({ children }) {
  const { isAuthenticated } = useAuth();
  return (
    <div className="min-h-screen bg-page text-ink flex flex-col">
      <header className="border-b border-line bg-card">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2.5">
            <BentoIcon size="w-9 h-9" rounded="rounded-xl" />
            <span className="font-black text-lg tracking-tight">
              Bento<span className="text-brand">POS</span>
            </span>
          </Link>
          <Link to={isAuthenticated ? '/' : '/login'} className="text-xs font-bold text-ink-soft hover:text-ink">
            {isAuthenticated ? 'Volver al sistema' : 'Iniciar sesión'}
          </Link>
        </div>
      </header>

      <main className="flex-1 w-full max-w-3xl mx-auto px-4 py-8">{children}</main>

      <footer className="border-t border-line">
        <nav className="max-w-3xl mx-auto px-4 py-4 flex flex-wrap items-center justify-center gap-x-5 gap-y-1 text-[11px] font-semibold text-ink-muted">
          {LEGAL_LINKS.map(l => (
            <Link key={l.slug} to={`/legal/${l.slug}`} className="hover:text-ink">{l.label}</Link>
          ))}
          <Link to="/support" className="hover:text-ink">Soporte</Link>
        </nav>
      </footer>
    </div>
  );
}
