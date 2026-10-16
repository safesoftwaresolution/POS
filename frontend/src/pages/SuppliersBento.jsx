import React, { useState, useEffect, useCallback } from 'react';
import { Truck, Plus, Search, Edit3, Trash2, Mail, Phone, AlertCircle } from 'lucide-react';
import {
  getSuppliersApi,
  createSupplierApi,
  updateSupplierApi,
  deleteSupplierApi
} from '../services/suppliersApi';
import { useModal } from '../context/ModalContext';

const EMPTY_FORM = { id: null, companyName: '', taxId: '', contactName: '', email: '', phone: '', address: '' };

export default function SuppliersBento() {
  const { confirm, notify } = useModal();
  const [suppliers, setSuppliers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [errorMsg, setErrorMsg] = useState('');

  const loadSuppliers = useCallback(async () => {
    setLoading(true);
    try {
      setSuppliers(await getSuppliersApi());
    } catch (e) {
      console.warn('Error cargando proveedores:', e);
      setSuppliers([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadSuppliers(); }, [loadSuppliers]);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setErrorMsg('');
    setShowModal(true);
  };

  const openEdit = (s) => {
    setForm({
      id: s.id,
      companyName: s.companyName,
      taxId: s.taxId,
      contactName: s.contactName || '',
      email: s.email || '',
      phone: s.phone || '',
      address: s.address || ''
    });
    setErrorMsg('');
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    try {
      if (form.id) {
        await updateSupplierApi(form.id, {
          companyName: form.companyName,
          contactName: form.contactName || null,
          email: form.email || null,
          phone: form.phone || null,
          address: form.address || null,
        });
      } else {
        await createSupplierApi(form);
      }
      setShowModal(false);
      setForm(EMPTY_FORM);
      await loadSuppliers();
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar el proveedor. Intenta nuevamente.');
    }
  };

  const handleDelete = async (s) => {
    const ok = await confirm(`¿Eliminar al proveedor "${s.companyName}"?`, { title: 'Eliminar proveedor' });
    if (!ok) return;
    try {
      await deleteSupplierApi(s.id);
      await loadSuppliers();
    } catch (err) {
      notify('Error eliminando proveedor: ' + err.message);
    }
  };

  const filtered = suppliers.filter(s =>
    (s.companyName && s.companyName.toLowerCase().includes(search.toLowerCase())) ||
    (s.taxId && s.taxId.includes(search)) ||
    (s.contactName && s.contactName.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="space-y-6 select-none">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-ink">Proveedores</h1>
          <p className="text-xs text-ink-soft font-medium">Directorio de proveedores para registrar compras y cargas de stock en BentoPOS</p>
        </div>

        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer transition-all"
        >
          <Plus className="w-4 h-4" /> Nuevo Proveedor
        </button>
      </div>

      <div className="bento-card p-6 space-y-4 bg-card border-line">
        <div className="flex items-center justify-between">
          <div className="relative w-72">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por nombre, NIT o contacto..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
            />
          </div>
          <span className="text-xs font-bold text-ink-muted">{filtered.length} proveedores registrados</span>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">
            Consultando proveedores...
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Truck className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay proveedores registrados aún</p>
              <p className="text-[11px] text-ink-muted">Crea tu primer proveedor para poder registrar compras y cargas de stock</p>
            </div>
            <button
              onClick={openCreate}
              className="px-4 py-2 bg-solid text-white rounded-xl text-xs font-extrabold shadow-sm hover:bg-solid-hover cursor-pointer"
            >
              + Registrar Primer Proveedor
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map(s => (
              <div key={s.id} className="p-5 bg-card rounded-2xl border border-line space-y-3 flex flex-col justify-between hover:border-line-strong transition-all shadow-xs group">
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono font-bold text-ink-soft">NIT: {s.taxId}</span>
                  </div>
                  <h3 className="font-extrabold text-sm text-ink mt-2 group-hover:text-brand transition-colors">{s.companyName}</h3>
                  {s.contactName && <p className="text-[11px] text-ink-soft mt-0.5">Contacto: {s.contactName}</p>}
                  <div className="mt-2 space-y-1 text-xs text-ink-soft font-medium">
                    <p className="flex items-center gap-1.5"><Mail className="w-3.5 h-3.5 text-ink-muted" /> {s.email || 'Sin correo'}</p>
                    <p className="flex items-center gap-1.5"><Phone className="w-3.5 h-3.5 text-ink-muted" /> {s.phone || 'Sin teléfono'}</p>
                  </div>
                </div>

                <div className="pt-3 border-t border-line flex items-center justify-end gap-1">
                  <button
                    onClick={() => openEdit(s)}
                    title="Editar proveedor"
                    className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(s)}
                    title="Eliminar proveedor"
                    className="p-1.5 text-ink-muted hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmit} className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 border border-line">
            <h3 className="font-extrabold text-base text-ink">
              {form.id ? 'Editar Proveedor' : 'Nuevo Proveedor'}
            </h3>

            {errorMsg && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Razón Social:</label>
                <input
                  type="text"
                  value={form.companyName}
                  onChange={e => setForm({ ...form, companyName: e.target.value })}
                  placeholder="Distribuidora Central S.A.S."
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink"
                  required
                />
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">NIT:</label>
                <input
                  type="text"
                  value={form.taxId}
                  onChange={e => setForm({ ...form, taxId: e.target.value })}
                  placeholder="900123456-7"
                  disabled={!!form.id}
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-mono text-xs text-ink disabled:opacity-60 focus:outline-none focus:border-ink"
                  required
                />
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre del contacto (opcional):</label>
                <input
                  type="text"
                  value={form.contactName}
                  onChange={e => setForm({ ...form, contactName: e.target.value })}
                  placeholder="María Rodríguez"
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Correo:</label>
                  <input
                    type="email"
                    value={form.email}
                    onChange={e => setForm({ ...form, email: e.target.value })}
                    placeholder="contacto@proveedor.com"
                    className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink"
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Teléfono:</label>
                  <input
                    type="text"
                    value={form.phone}
                    onChange={e => setForm({ ...form, phone: e.target.value })}
                    placeholder="3001234567"
                    className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink"
                  />
                </div>
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Dirección (opcional):</label>
                <input
                  type="text"
                  value={form.address}
                  onChange={e => setForm({ ...form, address: e.target.value })}
                  placeholder="Zona Industrial # 15-30, Bogotá"
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
              >
                {form.id ? 'Guardar Cambios' : 'Crear Proveedor'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
