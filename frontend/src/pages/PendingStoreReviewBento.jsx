import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, XCircle, LogOut } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const STATUS_COPY = {
  PENDING_VERIFICATION: {
    icon: Clock,
    iconClass: 'text-amber-500',
    title: 'Tu local está en revisión',
    message: 'Ya registramos los datos de tu local. Un administrador de la plataforma lo revisará pronto; te avisaremos por correo en cuanto quede activo.',
  },
  REJECTED: {
    icon: XCircle,
    iconClass: 'text-red-500',
    title: 'Tu local fue rechazado',
    message: 'La plataforma no aprobó el registro de tu local.',
  },
};

export default function PendingStoreReviewBento({ store }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const copy = STATUS_COPY[store?.status] || STATUS_COPY.PENDING_VERIFICATION;
  const Icon = copy.icon;

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-[#F7F7F5] dark:bg-[#0E0E12] text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center justify-center p-4 select-none">
      <div className="max-w-md w-full bento-card p-8 rounded-3xl shadow-xl space-y-5 text-center">
        <div className={`w-16 h-16 rounded-2xl bg-gray-100 dark:bg-white/10 flex items-center justify-center mx-auto ${copy.iconClass}`}>
          <Icon className="w-9 h-9" />
        </div>
        <h1 className="text-lg font-black">{copy.title}</h1>
        <p className="text-xs text-gray-500 dark:text-gray-400 font-semibold leading-relaxed">
          {copy.message}
        </p>
        {store?.status === 'REJECTED' && store?.rejectionReason && (
          <div className="p-3.5 bg-red-100 dark:bg-red-500/10 border border-red-300 dark:border-red-500/30 rounded-2xl text-xs font-bold text-red-600 dark:text-red-300 text-left">
            Motivo: {store.rejectionReason}
          </div>
        )}
        <button
          type="button"
          onClick={handleLogout}
          className="w-full py-3 bg-gray-100 dark:bg-white/10 hover:bg-gray-200 dark:hover:bg-white/20 text-[#17171B] dark:text-white font-black text-xs rounded-2xl flex items-center justify-center gap-2 transition-all cursor-pointer"
        >
          <LogOut className="w-4 h-4" />
          <span>Cerrar sesión</span>
        </button>
      </div>
    </div>
  );
}
