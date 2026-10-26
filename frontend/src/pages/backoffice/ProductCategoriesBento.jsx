import React, { useState } from 'react';
import { Package, Edit3, Trash2, AlertCircle, Plus, X } from 'lucide-react';
import {
  getCategoriesApi,
  createCategoryApi,
  updateCategoryApi,
  deleteCategoryApi
} from '../../services/categoriesApi';
import useModal from '../../hooks/useModal';
import useLoad from '../../hooks/useLoad';
import asList from '../../utils/asList';

const EMPTY_FORM = { id: null, name: '', description: '' };

export default function ProductCategoriesBento() {
  const { confirm, notify } = useModal();
  const { data: categories, loading, reload } = useLoad(() => asList(getCategoriesApi()), [], { initial: [], autoRefresh: true });
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [showForm, setShowForm] = useState(false);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setError('');
    setShowForm(true);
  };

  const openEdit = (cat) => {
    setForm({ id: cat.id, name: cat.name, description: cat.description || '' });
    setError('');
    setShowForm(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (form.id) {
        await updateCategoryApi(form.id, form.name, form.description);
      } else {
        await createCategoryApi(form.name, form.description);
      }
      setShowForm(false);
      setForm(EMPTY_FORM);
      reload();
    } catch (err) {
      setError(err.message || 'Error al guardar la categoría.');
    }
  };

  const handleDelete = async (cat) => {
    const ok = await confirm(`¿Eliminar la categoría "${cat.name}"?`, { title: 'Eliminar categoría' });
    if (!ok) return;
    try {
      await deleteCategoryApi(cat.id);
      reload();
    } catch (err) {
      notify('Error eliminando categoría: ' + err.message);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#17171B] dark:text-white">Categorías de Producto</h1>
          <p className="text-xs text-ink-soft font-medium">
            Catálogo global de categorías que los locales usan para clasificar sus productos
          </p>
        </div>

        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
        >
          <Plus className="w-4 h-4" /> Nueva Categoría
        </button>
      </div>

      <div className="bento-card p-6 space-y-4">
        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">
            Consultando categorías...
          </div>
        ) : categories.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Package className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay categorías registradas</p>
              <p className="text-[11px] text-ink-muted">
                Los locales no podrán crear productos hasta que exista al menos una categoría
              </p>
            </div>
            <button
              onClick={openCreate}
              className="px-4 py-2 bg-solid text-white rounded-xl text-xs font-extrabold shadow-sm hover:bg-solid-hover cursor-pointer"
            >
              + Crear Primera Categoría
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {categories.map(cat => (
              <div key={cat.id} className="p-5 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-line space-y-3 flex flex-col justify-between hover:bg-white dark:hover:bg-solid hover:border-line-strong transition-all shadow-xs">
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 font-extrabold">
                      {cat.productCount} {cat.productCount === 1 ? 'producto' : 'productos'}
                    </span>
                  </div>
                  <h3 className="font-extrabold text-sm text-ink mt-2">{cat.name}</h3>
                  <p className="text-[11px] text-ink-soft mt-0.5">{cat.description || 'Sin descripción'}</p>
                </div>

                <div className="pt-3 border-t border-line flex items-center justify-end gap-1">
                  <button
                    onClick={() => openEdit(cat)}
                    title="Editar categoría"
                    className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(cat)}
                    title="Eliminar categoría"
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

      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmit} className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-ink">
                {form.id ? 'Editar Categoría' : 'Nueva Categoría'}
              </h3>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {error && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre de la categoría:</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })}
                  placeholder="Ej. Abarrotes"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Descripción (opcional):</label>
                <input
                  type="text"
                  value={form.description}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  placeholder="Productos alimenticios y comestibles"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
              >
                {form.id ? 'Guardar Cambios' : 'Crear Categoría'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
