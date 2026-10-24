import React, { useState, useEffect } from 'react';
import { Tags, Plus, Search, Pencil, Trash2, Package, AlertCircle } from 'lucide-react';
import { getCategoriesApi, createCategoryApi, updateCategoryApi, deleteCategoryApi } from '../services/api';

export default function CategoriesBento() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [form, setForm] = useState({ name: '', description: '' });

  const loadCategories = async () => {
    setLoading(true);
    try {
      const data = await getCategoriesApi();
      setCategories(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Error cargando categorías:', e);
      setCategories([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCategories();
  }, []);

  const openCreateModal = () => {
    setEditingCategory(null);
    setForm({ name: '', description: '' });
    setErrorMsg('');
    setShowModal(true);
  };

  const openEditModal = (category) => {
    setEditingCategory(category);
    setForm({ name: category.name, description: category.description || '' });
    setErrorMsg('');
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    try {
      if (editingCategory) {
        await updateCategoryApi(editingCategory.id, form.name, form.description);
      } else {
        await createCategoryApi(form.name, form.description);
      }
      setShowModal(false);
      await loadCategories();
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar la categoría.');
    }
  };

  const handleDelete = async (category) => {
    if (category.productCount > 0) {
      alert(`No puedes eliminar "${category.name}": tiene ${category.productCount} producto(s) asociado(s).`);
      return;
    }
    if (!window.confirm(`¿Seguro que deseas eliminar la categoría "${category.name}"?`)) return;
    try {
      await deleteCategoryApi(category.id);
      await loadCategories();
    } catch (e) {
      alert('Error eliminando categoría: ' + e.message);
    }
  };

  const filtered = categories.filter(c =>
    c.name && c.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#191c1e] dark:text-white">Categorías de Productos</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Clasificación del catálogo para organizar tus productos</p>
        </div>

        <button
          onClick={openCreateModal}
          className="px-4 py-2.5 bg-[#006d3c] hover:bg-[#00522c] text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#006d3c]/20 cursor-pointer"
        >
          <Plus className="w-4 h-4" /> Nueva Categoría
        </button>
      </div>

      <div className="bento-card p-6 space-y-4">
        <div className="flex items-center justify-between">
          <div className="relative w-72">
            <Search className="w-4 h-4 text-gray-400 dark:text-gray-500 absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar categoría..."
              className="w-full pl-10 pr-3 py-1.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-[#e0e3e6] dark:border-[#1d332c] rounded-2xl text-xs font-semibold text-[#191c1e] dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-[#006d3c] dark:focus:border-[#12b76a]"
            />
          </div>
          <span className="text-xs font-bold text-gray-400 dark:text-gray-500">{filtered.length} categorías registradas</span>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-gray-400 dark:text-gray-500">
            Consultando categorías...
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Tags className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay categorías registradas aún</p>
              <p className="text-[11px] text-gray-400 dark:text-gray-500">Crea tu primera categoría para poder registrar productos</p>
            </div>
            <button
              onClick={openCreateModal}
              className="px-4 py-2 bg-[#006d3c] text-white rounded-xl text-xs font-extrabold shadow-sm hover:bg-[#00522c]"
            >
              + Crear Primera Categoría
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map(c => (
              <div key={c.id} className="p-5 bg-gray-50/60 dark:bg-white/5 rounded-2xl border border-gray-200 dark:border-gray-700 space-y-3 flex flex-col justify-between hover:bg-white dark:hover:bg-[#14231e] hover:border-[#12b76a] transition-all shadow-xs">
                <div>
                  <div className="flex items-center justify-between">
                    <div className="w-9 h-9 rounded-xl bg-emerald-50 dark:bg-[#12b76a]/10 text-[#006d3c] dark:text-[#12b76a] flex items-center justify-center">
                      <Tags className="w-4.5 h-4.5" />
                    </div>
                    <span className="text-[10px] px-2.5 py-1 rounded-full font-extrabold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 flex items-center gap-1">
                      <Package className="w-3 h-3" /> {c.productCount} producto{c.productCount === 1 ? '' : 's'}
                    </span>
                  </div>
                  <h3 className="font-extrabold text-sm text-[#191c1e] dark:text-white mt-2.5">{c.name}</h3>
                  <p className="text-[11px] text-gray-500 dark:text-gray-400 mt-0.5">
                    {c.description || 'Sin descripción'}
                  </p>
                </div>

                <div className="pt-3 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-1">
                  <button
                    onClick={() => openEditModal(c)}
                    title="Editar categoría"
                    className="p-1.5 text-gray-400 dark:text-gray-500 hover:text-[#006d3c] dark:hover:text-[#12b76a] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <Pencil className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(c)}
                    title="Eliminar categoría"
                    className="p-1.5 text-gray-400 dark:text-gray-500 hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Create / Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-navy-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmit} className="bento-card max-w-md w-full bg-white dark:bg-[#14231e] p-6 rounded-3xl shadow-2xl space-y-4">
            <h3 className="font-extrabold text-base text-[#191c1e] dark:text-white">
              {editingCategory ? 'Editar Categoría' : 'Nueva Categoría'}
            </h3>

            {errorMsg && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre de la Categoría:</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })}
                  placeholder="Ej. Abarrotes"
                  className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl font-semibold text-[#191c1e] dark:text-white"
                  required
                />
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Descripción (opcional):</label>
                <input
                  type="text"
                  value={form.description}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  placeholder="Ej. Productos de despensa y consumo general"
                  className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl text-[#191c1e] dark:text-white"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#1e293b] hover:bg-gray-200 dark:hover:bg-[#334155] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-[#006d3c] hover:bg-[#00522c] text-white rounded-2xl text-xs font-extrabold"
              >
                {editingCategory ? 'Guardar Cambios' : 'Crear Categoría'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
