import React, { useState, useEffect, useRef } from 'react';
import { Save, Store, Key, AlertCircle, ImagePlus, Mail, Phone, MapPin, Hash, Globe, IdCard } from 'lucide-react';
import { getSettingsApi, updateSettingsApi } from '../services/settingsApi';
import { getOwnStoreApi, updateOwnStoreApi } from '../services/storesApi';
import { uploadFileApi } from '../services/http';

const EMPTY_CONFIG = { businessName: '', taxId: '', address: '', phone: '', email: '', logoUrl: '' };
const EMPTY_STORE_FORM = { name: '', phone: '', website: '', address: '', taxId: '' };

const STORE_STATUS_LABELS = {
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo',
  PENDING_VERIFICATION: 'Pend. Verificación',
  SUSPENDED: 'Suspendido',
  REJECTED: 'Rechazado',
};

const STORE_STATUS_STYLES = {
  ACTIVE: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  INACTIVE: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300',
  PENDING_VERIFICATION: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  SUSPENDED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
};

export default function SettingsBento() {
  const [saved, setSaved] = useState(false);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [uploadingLogo, setUploadingLogo] = useState(false);
  const [config, setConfig] = useState(EMPTY_CONFIG);
  const logoInputRef = useRef(null);

  const [storeForm, setStoreForm] = useState(EMPTY_STORE_FORM);
  const [storeStatus, setStoreStatus] = useState(null);
  const [storeSaved, setStoreSaved] = useState(false);
  const [storeErrorMsg, setStoreErrorMsg] = useState('');

  useEffect(() => {
    async function loadSettings() {
      setLoading(true);
      try {
        const data = await getSettingsApi();
        if (data && data.businessName) {
          setConfig({
            businessName: data.businessName || '',
            taxId: data.taxId || '',
            address: data.address || '',
            phone: data.phone || '',
            email: data.email || '',
            logoUrl: data.logoUrl || ''
          });
        }
      } catch (e) {
        console.warn('Cargando valores por defecto de configuración:', e);
      } finally {
        setLoading(false);
      }
    }
    async function loadStore() {
      try {
        const store = await getOwnStoreApi();
        setStoreForm({
          name: store.name || '',
          phone: store.phone || '',
          website: store.website || '',
          address: store.address || '',
          taxId: store.taxId || ''
        });
        setStoreStatus(store.status);
      } catch (e) {
        console.warn('Error cargando el registro legal del local:', e);
      }
    }
    loadSettings();
    loadStore();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    try {
      await updateSettingsApi(config);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      setErrorMsg(err.message || 'Error guardando la configuración. Intenta nuevamente.');
    }
  };

  const handleStoreSubmit = async (e) => {
    e.preventDefault();
    setStoreErrorMsg('');
    try {
      const updated = await updateOwnStoreApi(storeForm);
      setStoreStatus(updated.status);
      setStoreSaved(true);
      setTimeout(() => setStoreSaved(false), 3000);
    } catch (err) {
      setStoreErrorMsg(err.message || 'Error guardando el registro legal del local. Intenta nuevamente.');
    }
  };

  const handleLogoChange = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setErrorMsg('');
    setUploadingLogo(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('folder', 'imagenes-logos');
      const result = await uploadFileApi('/storage/upload', formData);
      setConfig(prev => ({ ...prev, logoUrl: result.url }));
    } catch (err) {
      setErrorMsg(err.message || 'Error al subir el logo. Intenta nuevamente.');
    } finally {
      setUploadingLogo(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-black text-[#0A0A0A] dark:text-[#F4F4F6]">Configuración</h1>
        <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Nombre, logo y datos de contacto de tu negocio</p>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs font-bold text-gray-400 dark:text-gray-500">
          Consultando configuración...
        </div>
      ) : (
        <div className="space-y-8">
          {/* Registro legal del local */}
          <form onSubmit={handleStoreSubmit} className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="font-black text-sm text-[#0A0A0A] dark:text-[#F4F4F6] uppercase tracking-wide">Registro Legal del Local</h2>
              <div className="flex items-center gap-2">
                {storeStatus && (
                  <span className={`text-[10px] px-2.5 py-1 rounded-full font-extrabold ${STORE_STATUS_STYLES[storeStatus] || ''}`}>
                    {STORE_STATUS_LABELS[storeStatus] || storeStatus}
                  </span>
                )}
                {storeSaved && (
                  <span className="px-3 py-1 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 rounded-2xl text-xs font-extrabold border border-emerald-500/20">
                    ¡Guardado!
                  </span>
                )}
              </div>
            </div>

            {storeErrorMsg && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-2xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{storeErrorMsg}</span>
              </div>
            )}

            <div className="bento-card p-6 space-y-4">
              <p className="text-[11px] text-gray-400 dark:text-gray-500 font-semibold">
                Estos son los datos con los que tu local quedó registrado. El correo y el estado de verificación solo puede cambiarlos nuestro equipo.
              </p>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre del Local:</label>
                  <input
                    type="text"
                    value={storeForm.name}
                    onChange={e => setStoreForm({ ...storeForm, name: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                    required
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 flex items-center gap-1.5">
                    <Phone className="w-3.5 h-3.5" /> Teléfono:
                  </label>
                  <input
                    type="text"
                    value={storeForm.phone}
                    onChange={e => setStoreForm({ ...storeForm, phone: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                  />
                </div>
                <div className="md:col-span-2">
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 flex items-center gap-1.5">
                    <MapPin className="w-3.5 h-3.5" /> Dirección:
                  </label>
                  <input
                    type="text"
                    value={storeForm.address}
                    onChange={e => setStoreForm({ ...storeForm, address: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 flex items-center gap-1.5">
                    <IdCard className="w-3.5 h-3.5" /> NIT / Tax ID:
                  </label>
                  <input
                    type="text"
                    value={storeForm.taxId}
                    onChange={e => setStoreForm({ ...storeForm, taxId: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-mono font-bold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 flex items-center gap-1.5">
                    <Globe className="w-3.5 h-3.5" /> Sitio Web:
                  </label>
                  <input
                    type="text"
                    value={storeForm.website}
                    onChange={e => setStoreForm({ ...storeForm, website: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                  />
                </div>
              </div>

              <button
                type="submit"
                className="w-full py-3 bg-[#0A0A0A] hover:bg-black dark:bg-[#272730] dark:hover:bg-[#2C2C36] text-white font-extrabold text-xs rounded-2xl flex items-center justify-center gap-2 cursor-pointer transition-colors"
              >
                <Save className="w-4 h-4" /> Guardar Registro Legal
              </button>
            </div>
          </form>

          {/* Ajustes operativos / de facturación */}
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="font-black text-sm text-[#0A0A0A] dark:text-[#F4F4F6] uppercase tracking-wide">Ajustes de Facturación</h2>
              {saved && (
                <span className="px-3.5 py-1.5 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 rounded-2xl text-xs font-extrabold border border-emerald-500/20 animate-in fade-in">
                  ¡Configuración guardada!
                </span>
              )}
            </div>

            {errorMsg && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-2xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            {/* Identidad del negocio */}
            <div className="bento-card p-6 space-y-4">
              <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
                <Store className="w-4 h-4 text-[#FF5520] dark:text-[#FF5520]" /> Identidad del Negocio
              </h3>

              <div className="flex items-center gap-4">
                <div className="w-16 h-16 rounded-2xl bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] flex items-center justify-center overflow-hidden shrink-0">
                  {config.logoUrl ? (
                    <img src={config.logoUrl} alt="Logo del negocio" className="w-full h-full object-cover" />
                  ) : (
                    <ImagePlus className="w-6 h-6 text-gray-300 dark:text-gray-600" />
                  )}
                </div>
                <div className="space-y-1.5">
                  <button
                    type="button"
                    disabled={uploadingLogo}
                    onClick={() => logoInputRef.current?.click()}
                    className="px-3.5 py-2 bg-gray-100 dark:bg-white/10 hover:bg-gray-200 dark:hover:bg-white/20 disabled:opacity-50 text-gray-700 dark:text-gray-200 rounded-2xl text-xs font-bold cursor-pointer"
                  >
                    {uploadingLogo ? 'Subiendo...' : config.logoUrl ? 'Cambiar logo' : 'Subir logo'}
                  </button>
                  <p className="text-[10px] text-gray-400 dark:text-gray-500 font-semibold">PNG o JPG, se usa en facturas y reportes.</p>
                  <input ref={logoInputRef} type="file" accept="image/*" className="hidden" onChange={handleLogoChange} />
                </div>
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 text-xs">Nombre Comercial:</label>
                <input
                  type="text"
                  value={config.businessName}
                  onChange={e => setConfig({ ...config, businessName: e.target.value })}
                  className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-xs text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                  required
                />
              </div>
            </div>

            {/* Contacto y ubicación */}
            <div className="bento-card p-6 space-y-4">
              <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
                <MapPin className="w-4 h-4 text-[#FF5520] dark:text-[#FF5520]" /> Contacto y Ubicación
              </h3>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 flex items-center gap-1.5">
                    <Mail className="w-3.5 h-3.5" /> Correo Electrónico:
                  </label>
                  <input
                    type="email"
                    value={config.email}
                    onChange={e => setConfig({ ...config, email: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 flex items-center gap-1.5">
                    <Phone className="w-3.5 h-3.5" /> Teléfono:
                  </label>
                  <input
                    type="text"
                    value={config.phone}
                    onChange={e => setConfig({ ...config, phone: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                    required
                  />
                </div>
                <div className="md:col-span-2">
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1 flex items-center gap-1.5">
                    <MapPin className="w-3.5 h-3.5" /> Dirección Física:
                  </label>
                  <input
                    type="text"
                    value={config.address}
                    onChange={e => setConfig({ ...config, address: e.target.value })}
                    className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                    required
                  />
                </div>
              </div>
            </div>

            {/* Datos fiscales */}
            <div className="bento-card p-6 space-y-4">
              <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
                <Hash className="w-4 h-4 text-[#FF5520] dark:text-[#FF5520]" /> Datos Fiscales
              </h3>
              <div className="text-xs">
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">NIT / Cédula Fiscal:</label>
                <input
                  type="text"
                  value={config.taxId}
                  onChange={e => setConfig({ ...config, taxId: e.target.value })}
                  className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl font-mono font-bold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                  required
                />
              </div>
            </div>

            {/* Facturación electrónica: las credenciales de Factus las carga el SUPER_ADMIN por local
                (ver backend/docs/modules/billing/spec.md, RN-BILL-005) — el admin del local nunca las
                ve ni las envía. El estado de habilitación y los rangos DIAN se gestionan en Facturación DIAN. */}
            <div className="bento-card p-6 space-y-3">
              <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-[#F4F4F6] flex items-center gap-2">
                <Key className="w-4 h-4 text-[#FF5520] dark:text-[#FF5520]" /> Facturación Electrónica Factus DIAN
              </h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">
                El estado de habilitación y los rangos de numeración DIAN de tu local se gestionan desde{' '}
                <a href="/invoicing" className="text-[#FF5520] font-bold underline">Facturación DIAN → Habilitación DIAN</a>.
              </p>
            </div>

            <button
              type="submit"
              className="w-full py-3.5 bg-[#0A0A0A] hover:bg-[#202020] text-white font-extrabold text-xs rounded-2xl flex items-center justify-center gap-2 shadow-lg shadow-[#0A0A0A]/10 cursor-pointer transition-all active:scale-[0.99]"
            >
              <Save className="w-4 h-4" /> Guardar Ajustes de Facturación
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
