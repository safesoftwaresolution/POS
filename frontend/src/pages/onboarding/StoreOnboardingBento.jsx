import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, Phone, Mail, MapPin, Globe, FileText, ArrowRight, AlertCircle } from 'lucide-react';
import { registerOwnStoreApi } from '../../services/storesApi';
import { useAuth } from '../../context/AuthContext';

const EMPTY_FORM = { name: '', phone: '', email: '', website: '', address: '', taxId: '' };

export default function StoreOnboardingBento() {
  const navigate = useNavigate();
  const { refreshSession } = useAuth();
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (e) => setForm(prev => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await registerOwnStoreApi({ ...form, storeCategoryId: null });
      await refreshSession();
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message || 'No se pudo registrar el local.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F7F7F5] dark:bg-[#0E0E12] text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center justify-center p-4 select-none">
      <div className="max-w-lg w-full bento-card p-8 rounded-3xl shadow-xl space-y-6">
        <div className="text-center space-y-2">
          <div className="w-14 h-14 rounded-2xl bg-[#0A0A0A] flex items-center justify-center mx-auto shadow-lg shadow-[#0A0A0A]/20">
            <Store className="w-8 h-8 text-[#FF5520]" />
          </div>
          <h1 className="text-xl font-black tracking-tight">Registra tu local</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-semibold">
            Un último paso: cuéntanos sobre tu negocio para activar BentoPOS.
          </p>
        </div>

        {error && (
          <div className="p-3.5 bg-red-100 dark:bg-red-500/10 border border-red-300 dark:border-red-500/30 rounded-2xl text-xs font-bold text-red-600 dark:text-red-300 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 dark:text-gray-400 block">Nombre del local:</label>
            <div className="relative">
              <Store className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
              <input
                type="text" value={form.name} onChange={handleChange('name')}
                placeholder="Mi Tienda" required
                className="w-full pl-10 pr-4 py-2.5 bg-gray-50 dark:bg-[#0E0E12] border border-gray-200 dark:border-[#272730] rounded-2xl text-xs font-semibold focus:outline-none focus:border-[#0A0A0A] transition-all"
              />
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 dark:text-gray-400 block">Correo del local:</label>
            <div className="relative">
              <Mail className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
              <input
                type="email" value={form.email} onChange={handleChange('email')}
                placeholder="contacto@mitienda.com" required
                className="w-full pl-10 pr-4 py-2.5 bg-gray-50 dark:bg-[#0E0E12] border border-gray-200 dark:border-[#272730] rounded-2xl text-xs font-semibold focus:outline-none focus:border-[#0A0A0A] transition-all"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-xs font-bold text-gray-500 dark:text-gray-400 block">Teléfono:</label>
              <div className="relative">
                <Phone className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                <input
                  type="text" value={form.phone} onChange={handleChange('phone')}
                  placeholder="3001234567"
                  className="w-full pl-10 pr-4 py-2.5 bg-gray-50 dark:bg-[#0E0E12] border border-gray-200 dark:border-[#272730] rounded-2xl text-xs font-semibold focus:outline-none focus:border-[#0A0A0A] transition-all"
                />
              </div>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-bold text-gray-500 dark:text-gray-400 block">NIT / Tax ID:</label>
              <div className="relative">
                <FileText className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
                <input
                  type="text" value={form.taxId} onChange={handleChange('taxId')}
                  placeholder="900123456-7"
                  className="w-full pl-10 pr-4 py-2.5 bg-gray-50 dark:bg-[#0E0E12] border border-gray-200 dark:border-[#272730] rounded-2xl text-xs font-semibold focus:outline-none focus:border-[#0A0A0A] transition-all"
                />
              </div>
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 dark:text-gray-400 block">Dirección:</label>
            <div className="relative">
              <MapPin className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
              <input
                type="text" value={form.address} onChange={handleChange('address')}
                placeholder="Calle 123 #45-67"
                className="w-full pl-10 pr-4 py-2.5 bg-gray-50 dark:bg-[#0E0E12] border border-gray-200 dark:border-[#272730] rounded-2xl text-xs font-semibold focus:outline-none focus:border-[#0A0A0A] transition-all"
              />
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 dark:text-gray-400 block">Sitio web (opcional):</label>
            <div className="relative">
              <Globe className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
              <input
                type="text" value={form.website} onChange={handleChange('website')}
                placeholder="https://mitienda.com"
                className="w-full pl-10 pr-4 py-2.5 bg-gray-50 dark:bg-[#0E0E12] border border-gray-200 dark:border-[#272730] rounded-2xl text-xs font-semibold focus:outline-none focus:border-[#0A0A0A] transition-all"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 bg-[#0A0A0A] hover:bg-[#202020] text-white font-black text-xs rounded-2xl flex items-center justify-center gap-2 shadow-lg shadow-[#0A0A0A]/10 active:scale-98 transition-all cursor-pointer disabled:opacity-50"
          >
            <span>{loading ? 'Registrando local...' : 'Registrar mi local'}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>
      </div>
    </div>
  );
}
