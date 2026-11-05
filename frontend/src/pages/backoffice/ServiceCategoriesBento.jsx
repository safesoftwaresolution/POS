import React, { useState } from 'react';
import { Sparkles, Edit3, Ban, AlertCircle, Plus, X } from 'lucide-react';
import {
  getBackofficeServiceCategoriesApi,
  createBackofficeServiceCategoryApi,
  updateBackofficeServiceCategoryApi,
  deactivateBackofficeServiceCategoryApi
} from '../../services/backoffice/serviceCategoriesApi';
import { getBackofficeStoreCategoriesApi } from '../../services/backoffice/storesApi';
import useModal from '../../hooks/useModal';
import useLoad from '../../hooks/useLoad';
import asList from '../../utils/asList';
import { offeredToLabel, toggleId } from '../../utils/serviceCategories';

const EMPTY_FORM = { id: null, name: '', description: '', storeCategoryIds: [] };

/** Catálogo global de categorías de servicios (maquillaje, corte…), cada una ofrecida a ciertos tipos de local. */
export default function ServiceCategoriesBento() {
  const { confirm, notify } = useModal();
  const { data: categories, loading, reload } = useLoad(() => asList(getBackofficeServiceCategoriesApi()), [], { initial: [], autoRefresh: true });
  const { data: storeCategories } = useLoad(() => asList(getBackofficeStoreCategoriesApi()), [], { initial: [] });
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [showForm, setShowForm] = useState(false);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setError('');
    setShowForm(true);
  };

  const openEdit = (cat) => {
    setForm({ id: cat.id, name: cat.name, description: cat.description || '', storeCategoryIds: cat.storeCategoryIds || [] });
    setError('');
    setShowForm(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (form.id) {
        await updateBackofficeServiceCategoryApi(form.id, form.name, form.description, form.storeCategoryIds);
      } else {
        await createBackofficeServiceCategoryApi(form.name, form.description, form.storeCategoryIds);
      }
      setShowForm(false);
      setForm(EMPTY_FORM);
      reload();
    } catch (err) {
      setError(err.message || 'Error al guardar la categoría.');
    }
  };

  const handleDeactivate = async (cat) => {
    const ok = await confirm(`¿Desactivar la categoría "${cat.name}"?`, { title: 'Desactivar categoría' });
    if (!ok) return;
    try {
      await deactivateBackofficeServiceCategoryApi(cat.id);
      reload();
    } catch (err) {
      notify('Error desactivando categoría: ' + err.message);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-ink">Categorías de Servicios</h1>
          <p className="text-xs text-ink-soft font-medium">Cada categoría se ofrece solo a los tipos de local que elijas</p>
        </div>

        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-solid-ink rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
        >
          <Plus className="w-4 h-4" /> Nueva Categoría
        </button>
      </div>

      <div className="bento-card p-6 space-y-4">
        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Consultando categorías...</div>
        ) : categories.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Sparkles className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay categorías de servicios</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {categories.map(cat => (
              <div key={cat.id} className="p-5 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-line space-y-3 flex flex-col justify-between hover:bg-white dark:hover:bg-white/10 hover:border-line-strong transition-all shadow-xs">
                <div>
                  <h3 className="font-extrabold text-sm text-ink">{cat.name}</h3>
                  <p className="text-[11px] text-ink-soft mt-0.5">{cat.description || 'Sin descripción'}</p>
                  <p className="text-[11px] text-ink-muted mt-2">
                    <span className="font-bold">Se ofrece a:</span> {offeredToLabel(cat, storeCategories)}
                  </p>
                </div>

                <div className="pt-3 border-t border-line flex items-center justify-end gap-1">
                  <button
                    onClick={() => openEdit(cat)}
                    title="Editar categoría"
                    className="p-1.5 text-ink-muted hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDeactivate(cat)}
                    title="Desactivar categoría"
                    className="p-1.5 text-ink-muted hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <Ban className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmit} className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-ink">{form.id ? 'Editar Categoría' : 'Nueva Categoría'}</h3>
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
                <label htmlFor="service-category-name" className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre de la categoría:</label>
                <input
                  id="service-category-name"
                  type="text"
                  value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })}
                  placeholder="Ej. Maquillaje"
                  maxLength={100}
                  className="w-full p-2.5 bg-page border border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
              <div>
                <label htmlFor="service-category-description" className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Descripción (opcional):</label>
                <input
                  id="service-category-description"
                  type="text"
                  value={form.description}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  maxLength={255}
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                />
              </div>
              <fieldset className="space-y-1.5">
                <legend className="font-bold text-gray-700 dark:text-gray-300 mb-1">Se ofrece a estos tipos de local:</legend>
                {storeCategories.map(sc => (
                  <label key={sc.id} className="flex items-center gap-2 p-2.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink cursor-pointer">
                    <input
                      type="checkbox"
                      checked={form.storeCategoryIds.includes(sc.id)}
                      onChange={() => setForm({ ...form, storeCategoryIds: toggleId(form.storeCategoryIds, sc.id) })}
                      className="accent-brand w-4 h-4"
                    />
                    <span>{sc.name}</span>
                  </label>
                ))}
                <p className="text-[11px] text-ink-muted">Si no marcas ninguno, la categoría se ofrece a todos los locales.</p>
              </fieldset>
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
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-solid-ink rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
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
