import React, { useState, useEffect, useRef } from 'react';
import {
  Plus,
  Search,
  Trash2,
  Edit3,
  Barcode,
  Package,
  Layers,
  AlertCircle,
  ImagePlus
} from 'lucide-react';
import { getProductsApi, createProductApi, updateProductApi, deleteProductApi } from '../services/productsApi';
import { getCategoriesApi } from '../services/categoriesApi';
import {
  getSubcategoriesApi,
  createSubcategoryApi,
  updateSubcategoryApi,
  deleteSubcategoryApi
} from '../services/subcategoriesApi';
import { createInventoryMovementApi } from '../services/inventoryApi';
import { useModal } from '../context/ModalContext';
import { useAuth } from '../context/AuthContext';
import { uploadFileApi } from '../services/http';
import BarcodeModal from '../components/BarcodeModal';

const EMPTY_PRODUCT = {
  id: null,
  name: '',
  barcode: '',
  internalCode: '',
  categoryId: '',
  subcategoryId: '',
  purchasePrice: null,
  price: '',
  stock: '',
  minStock: 5,
  imageUrl: ''
};

const EMPTY_SUBCATEGORY = { id: null, categoryId: '', name: '', description: '' };

const TABS = [
  { id: 'catalog', label: 'Catálogo', icon: Package },
  { id: 'subcategories', label: 'Subcategorías', icon: Layers },
];

export default function ProductsBento() {
  const { confirm, notify } = useModal();
  const { user } = useAuth();
  const canManageSubcategories = user?.role === 'ADMINISTRATOR' || user?.role === 'SUPERVISOR';
  const [activeTab, setActiveTab] = useState('catalog');
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [subcategories, setSubcategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showAddModal, setShowAddModal] = useState(false);
  const [barcodeProduct, setBarcodeProduct] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [newProduct, setNewProduct] = useState(EMPTY_PRODUCT);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [showNewSubcategory, setShowNewSubcategory] = useState(false);
  const [newSubcategoryName, setNewSubcategoryName] = useState('');
  const [subSearch, setSubSearch] = useState('');
  const [showSubModal, setShowSubModal] = useState(false);
  const [subForm, setSubForm] = useState(EMPTY_SUBCATEGORY);
  const [subErrorMsg, setSubErrorMsg] = useState('');
  const imageInputRef = useRef(null);

  const loadProducts = async () => {
    setLoading(true);
    try {
      const data = await getProductsApi();
      setProducts(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Error cargando productos:', e);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  const loadCategories = async () => {
    try {
      setCategories(await getCategoriesApi());
    } catch (e) {
      console.warn('Error cargando categorías:', e);
      setCategories([]);
    }
  };

  const loadSubcategories = async () => {
    try {
      setSubcategories(await getSubcategoriesApi());
    } catch (e) {
      console.warn('Error cargando subcategorías:', e);
      setSubcategories([]);
    }
  };

  useEffect(() => {
    loadProducts();
    loadCategories();
    loadSubcategories();
  }, []);

  const subcategoriesForSelectedCategory = subcategories.filter(
    sub => String(sub.categoryId) === String(newProduct.categoryId)
  );

  const handleCreateSubcategory = async () => {
    if (!newSubcategoryName.trim() || !newProduct.categoryId) return;
    try {
      const created = await createSubcategoryApi(Number(newProduct.categoryId), newSubcategoryName.trim());
      await loadSubcategories();
      setNewProduct(prev => ({ ...prev, subcategoryId: String(created.id) }));
      setNewSubcategoryName('');
      setShowNewSubcategory(false);
    } catch (err) {
      notify('Error creando subcategoría: ' + err.message);
    }
  };

  const openCreateSubModal = () => {
    setSubForm(EMPTY_SUBCATEGORY);
    setSubErrorMsg('');
    setShowSubModal(true);
  };

  const openEditSubModal = (sub) => {
    setSubForm({ id: sub.id, categoryId: String(sub.categoryId), name: sub.name, description: sub.description || '' });
    setSubErrorMsg('');
    setShowSubModal(true);
  };

  const handleSubmitSubcategory = async (e) => {
    e.preventDefault();
    setSubErrorMsg('');
    if (!subForm.name || !subForm.categoryId) return;
    try {
      if (subForm.id) {
        await updateSubcategoryApi(subForm.id, subForm.name, subForm.description);
      } else {
        await createSubcategoryApi(Number(subForm.categoryId), subForm.name, subForm.description);
      }
      setShowSubModal(false);
      setSubForm(EMPTY_SUBCATEGORY);
      await loadSubcategories();
    } catch (err) {
      setSubErrorMsg(err.message || 'Error al guardar la subcategoría. Intenta nuevamente.');
    }
  };

  const handleDeleteSubcategory = async (sub) => {
    const ok = await confirm(`¿Eliminar la subcategoría "${sub.name}"?`, { title: 'Eliminar subcategoría' });
    if (!ok) return;
    try {
      await deleteSubcategoryApi(sub.id);
      await loadSubcategories();
    } catch (err) {
      notify('Error eliminando subcategoría: ' + err.message);
    }
  };

  const openCreateModal = () => {
    setNewProduct(EMPTY_PRODUCT);
    setErrorMsg('');
    setShowAddModal(true);
  };

  const openEditModal = (p) => {
    const category = categories.find(c => c.name === p.categoryName);
    setNewProduct({
      id: p.id,
      name: p.name || '',
      barcode: p.barcode || '',
      internalCode: p.internalCode || '',
      categoryId: category ? String(category.id) : '',
      subcategoryId: p.subcategoryId ? String(p.subcategoryId) : '',
      purchasePrice: p.purchasePrice ?? null,
      price: p.salePrice ?? p.price ?? '',
      stock: '',
      minStock: p.minStock ?? 5,
      imageUrl: p.imageUrl || ''
    });
    setErrorMsg('');
    setShowAddModal(true);
  };

  const handleSubmitProduct = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    if (!newProduct.name || !newProduct.price || !newProduct.categoryId) return;

    try {
      if (newProduct.id) {
        await updateProductApi(newProduct.id, {
          name: newProduct.name,
          description: newProduct.name,
          categoryId: Number(newProduct.categoryId),
          subcategoryId: newProduct.subcategoryId ? Number(newProduct.subcategoryId) : null,
          purchasePrice: parseFloat(newProduct.purchasePrice ?? newProduct.price * 0.7),
          salePrice: parseFloat(newProduct.price),
          barcode: newProduct.barcode,
          minStock: parseInt(newProduct.minStock) || 5,
          imageUrl: newProduct.imageUrl || null
        });
      } else {
        const created = await createProductApi({
          name: newProduct.name,
          barcode: newProduct.barcode || `${Math.floor(1000000000000 + Math.random() * 9000000000000)}`,
          internalCode: newProduct.internalCode || `PROD-00${products.length + 1}`,
          categoryId: Number(newProduct.categoryId),
          subcategoryId: newProduct.subcategoryId ? Number(newProduct.subcategoryId) : null,
          price: parseFloat(newProduct.price),
          minStock: parseInt(newProduct.minStock) || 5,
          imageUrl: newProduct.imageUrl || null
        });

        const initialStock = parseInt(newProduct.stock) || 0;
        if (initialStock > 0) {
          await createInventoryMovementApi(created.id, 'ENTRY', initialStock, 'Stock inicial al crear el producto');
        }
      }

      setShowAddModal(false);
      setNewProduct(EMPTY_PRODUCT);
      await loadProducts();
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar el producto. Intenta nuevamente.');
    }
  };

  const handleImageChange = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setErrorMsg('');
    setUploadingImage(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('folder', 'imagenes-productos');
      const result = await uploadFileApi('/storage/upload', formData);
      setNewProduct(prev => ({ ...prev, imageUrl: result.url }));
    } catch (err) {
      setErrorMsg(err.message || 'Error al subir la imagen. Intenta nuevamente.');
    } finally {
      setUploadingImage(false);
    }
  };

  const handleDelete = async (id) => {
    const ok = await confirm('¿Seguro que deseas eliminar este producto?', { title: 'Eliminar producto' });
    if (!ok) return;
    try {
      await deleteProductApi(id);
      await loadProducts();
    } catch (e) {
      notify('Error eliminando producto: ' + e.message);
    }
  };

  const filtered = products.filter(p =>
    (p.name && p.name.toLowerCase().includes(search.toLowerCase())) ||
    (p.barcode && p.barcode.includes(search)) ||
    (p.internalCode && p.internalCode.toLowerCase().includes(search.toLowerCase()))
  );

  const filteredSubcategories = subcategories.filter(s =>
    s.name.toLowerCase().includes(subSearch.toLowerCase()) ||
    (s.categoryName && s.categoryName.toLowerCase().includes(subSearch.toLowerCase()))
  );

  return (
    <div className="space-y-6 select-none">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-ink">Productos</h1>
          <p className="text-xs text-ink-soft font-medium">Catálogo y subcategorías de tu tienda en BentoPOS</p>
        </div>

        {activeTab === 'catalog' && (
          <button
            onClick={openCreateModal}
            className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer transition-all"
          >
            <Plus className="w-4 h-4" /> Crear Nuevo Producto
          </button>
        )}
        {activeTab === 'subcategories' && canManageSubcategories && (
          <button
            onClick={openCreateSubModal}
            className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer transition-all"
          >
            <Plus className="w-4 h-4" /> Nueva Subcategoría
          </button>
        )}
      </div>

      {/* Tab Switcher */}
      <div className="flex bg-gray-100 dark:bg-[#272730] rounded-full p-1 border border-gray-200 dark:border-gray-700 w-fit">
        {TABS.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-2 rounded-full text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                activeTab === tab.id
                  ? 'bg-solid text-white shadow-xs'
                  : 'text-ink-soft hover:text-brand dark:hover:text-brand'
              }`}
            >
              <Icon className="w-4 h-4" /> {tab.label}
            </button>
          );
        })}
      </div>

      {activeTab === 'catalog' && (
      <div className="bento-card p-6 space-y-4">
        <div className="flex items-center justify-between">
          <div className="relative w-72">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filtrar por nombre o código de barras..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
            />
          </div>
          <span className="text-xs font-bold text-ink-muted">{filtered.length} productos registrados</span>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">
            Consultando catálogo de productos...
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Package className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No se encontraron productos</p>
              <p className="text-[11px] text-ink-muted">Crea tu primer producto para empezar a registrar ventas</p>
            </div>
            <button
              onClick={openCreateModal}
              className="px-4 py-2 bg-solid text-white rounded-xl text-xs font-extrabold shadow-sm hover:bg-solid-hover cursor-pointer"
            >
              + Registrar Primer Producto
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto -mx-6">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-line bg-gray-50/50 dark:bg-[#0E0E12] text-ink-muted uppercase text-[10px] tracking-wider font-extrabold">
                  <th className="py-3 px-6">Producto</th>
                  <th className="py-3 px-3">Código</th>
                  <th className="py-3 px-3">Categoría</th>
                  <th className="py-3 px-3 text-right">Precio</th>
                  <th className="py-3 px-3">Stock</th>
                  <th className="py-3 px-6 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-line">
                {filtered.map(p => {
                  const stockVal = p.quantityAvailable ?? p.stock ?? 0;
                  const isLow = stockVal <= (p.minStock || 5);
                  return (
                    <tr
                      key={p.id}
                      className="hover:bg-gray-50 dark:hover:bg-white/5 transition-colors cursor-pointer group"
                      onClick={() => openEditModal(p)}
                    >
                      <td className="py-3 px-6">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-xl bg-gray-50 dark:bg-[#0E0E12] border border-line flex items-center justify-center overflow-hidden shrink-0">
                            {p.imageUrl ? (
                              <img src={p.imageUrl} alt={p.name} className="w-full h-full object-cover" />
                            ) : (
                              <Package className="w-4 h-4 text-gray-300 dark:text-gray-600" />
                            )}
                          </div>
                          <div className="min-w-0">
                            <span className="font-bold text-sm text-ink block truncate group-hover:text-brand dark:group-hover:text-brand transition-colors">
                              {p.name}
                            </span>
                            <span className="text-[11px] text-ink-soft font-mono flex items-center gap-1">
                              <Barcode className="w-3 h-3 shrink-0" /> {p.barcode || 'Sin código'}
                            </span>
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-3 font-mono font-semibold text-ink-soft">{p.internalCode}</td>
                      <td className="py-3 px-3">
                        <div className="flex items-center gap-1 flex-wrap">
                          <span className="text-[10px] px-2 py-0.5 rounded-full bg-brand/10 dark:bg-brand/20 text-brand font-extrabold">
                            {p.categoryName || 'General'}
                          </span>
                          {p.subcategoryName && (
                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-[#272730] text-gray-600 dark:text-gray-300 font-extrabold">
                              {p.subcategoryName}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-3 px-3 text-right font-black text-sm text-ink tabular-nums">
                        ${(p.salePrice || p.price || 0).toLocaleString('es-CO')}
                      </td>
                      <td className="py-3 px-3">
                        <span className={`font-bold tabular-nums ${isLow ? 'text-amber-600 dark:text-amber-400' : 'text-ink'}`}>
                          {stockVal} {isLow && '(Bajo)'}
                        </span>
                      </td>
                      <td className="py-3 px-6 text-right" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => setBarcodeProduct(p)}
                            title="Ver código de barras"
                            className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <Barcode className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => openEditModal(p)}
                            title="Editar producto"
                            className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDelete(p.id)}
                            title="Eliminar producto"
                            className="p-1.5 text-ink-muted hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
      )}

      {activeTab === 'subcategories' && (
        <div className="space-y-6">
          {/* Global categories overview */}
          <div className="bento-card p-6 space-y-3">
            <div>
              <h2 className="text-sm font-extrabold text-ink">Categorías Globales</h2>
              <p className="text-[11px] text-ink-soft font-medium">
                Catálogo compartido por todas las tiendas — cada subcategoría tuya vive dentro de una de estas
              </p>
            </div>
            {categories.length === 0 ? (
              <p className="text-xs font-bold text-ink-muted py-2">
                Aún no hay categorías globales. Pide a un administrador que cree una desde el backoffice.
              </p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {categories.map(cat => (
                  <span
                    key={cat.id}
                    className="text-xs font-bold px-3 py-1.5 rounded-full bg-brand/10 dark:bg-brand/20 text-brand dark:text-brand border border-brand/25"
                  >
                    {cat.name}
                    <span className="ml-1.5 opacity-70 font-semibold">
                      · {subcategories.filter(s => s.categoryId === cat.id).length} subcat.
                    </span>
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Store subcategories */}
          <div className="bento-card p-6 space-y-4">
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="relative w-full sm:w-72">
                <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
                <input
                  type="text"
                  value={subSearch}
                  onChange={(e) => setSubSearch(e.target.value)}
                  placeholder="Buscar subcategoría o categoría..."
                  className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
                />
              </div>
              <span className="text-xs font-bold text-ink-muted">{filteredSubcategories.length} subcategorías</span>
            </div>

            {filteredSubcategories.length === 0 ? (
              <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
                <Layers className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
                <div>
                  <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay subcategorías registradas</p>
                  <p className="text-[11px] text-ink-muted">
                    {canManageSubcategories
                      ? 'Crea la primera para organizar mejor tu catálogo de productos'
                      : 'Pide a un administrador o supervisor que cree la primera'}
                  </p>
                </div>
                {canManageSubcategories && categories.length > 0 && (
                  <button
                    onClick={openCreateSubModal}
                    className="px-4 py-2 bg-solid text-white rounded-xl text-xs font-extrabold shadow-sm hover:bg-solid-hover cursor-pointer"
                  >
                    + Crear Primera Subcategoría
                  </button>
                )}
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {filteredSubcategories.map(sub => (
                  <div key={sub.id} className="p-5 bg-card rounded-2xl border border-line space-y-3 flex flex-col justify-between hover:border-line-strong dark:hover:border-line-strong transition-all shadow-xs group">
                    <div>
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-[#272730] text-gray-600 dark:text-gray-300 font-extrabold truncate">
                          {sub.categoryName}
                        </span>
                        <span className="text-[10px] font-mono text-ink-muted font-bold shrink-0">
                          {sub.productCount} {sub.productCount === 1 ? 'producto' : 'productos'}
                        </span>
                      </div>
                      <h3 className="font-extrabold text-sm text-ink mt-2 group-hover:text-brand dark:group-hover:text-brand transition-colors">
                        {sub.name}
                      </h3>
                      <p className="text-[11px] text-ink-soft mt-0.5">{sub.description || 'Sin descripción'}</p>
                    </div>

                    {canManageSubcategories && (
                      <div className="pt-3 border-t border-line flex items-center justify-end gap-1">
                        <button
                          onClick={() => openEditSubModal(sub)}
                          title="Editar subcategoría"
                          className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                        >
                          <Edit3 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleDeleteSubcategory(sub)}
                          title="Eliminar subcategoría"
                          className="p-1.5 text-ink-muted hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Create/Edit Subcategory Modal */}
      {showSubModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmitSubcategory} className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 border border-line">
            <h3 className="font-extrabold text-base text-ink">
              {subForm.id ? 'Editar Subcategoría' : 'Nueva Subcategoría'}
            </h3>

            {subErrorMsg && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{subErrorMsg}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Categoría:</label>
                <select
                  value={subForm.categoryId}
                  onChange={e => setSubForm({ ...subForm, categoryId: e.target.value })}
                  disabled={!!subForm.id}
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-semibold text-ink disabled:opacity-60 focus:outline-none focus:border-ink"
                  required
                >
                  <option value="" disabled>Selecciona una categoría...</option>
                  {categories.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre de la subcategoría:</label>
                <input
                  type="text"
                  value={subForm.name}
                  onChange={e => setSubForm({ ...subForm, name: e.target.value })}
                  placeholder="Ej. Gaseosas"
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink"
                  required
                />
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Descripción (opcional):</label>
                <input
                  type="text"
                  value={subForm.description}
                  onChange={e => setSubForm({ ...subForm, description: e.target.value })}
                  placeholder="Bebidas carbonatadas"
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowSubModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
              >
                {subForm.id ? 'Guardar Cambios' : 'Crear Subcategoría'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Add Product Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmitProduct} className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 border border-line">
            <h3 className="font-extrabold text-base text-ink">
              {newProduct.id ? 'Editar Producto' : 'Crear Nuevo Producto'}
            </h3>

            {errorMsg && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            {categories.length === 0 && (
              <div className="p-3 bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/30 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>Aún no hay categorías de producto. Pide a un administrador que cree una desde el backoffice antes de registrar productos.</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div className="flex items-center gap-4">
                <div className="w-16 h-16 rounded-2xl bg-gray-50 dark:bg-[#0E0E12] border border-line flex items-center justify-center overflow-hidden shrink-0">
                  {newProduct.imageUrl ? (
                    <img src={newProduct.imageUrl} alt="Imagen del producto" className="w-full h-full object-cover" />
                  ) : (
                    <ImagePlus className="w-6 h-6 text-gray-300 dark:text-gray-600" />
                  )}
                </div>
                <div className="space-y-1.5">
                  <button
                    type="button"
                    disabled={uploadingImage}
                    onClick={() => imageInputRef.current?.click()}
                    className="px-3.5 py-2 bg-gray-100 dark:bg-white/10 hover:bg-gray-200 dark:hover:bg-white/20 disabled:opacity-50 text-gray-700 dark:text-gray-200 rounded-2xl text-xs font-bold cursor-pointer"
                  >
                    {uploadingImage ? 'Subiendo...' : newProduct.imageUrl ? 'Cambiar imagen' : 'Subir imagen'}
                  </button>
                  <input ref={imageInputRef} type="file" accept="image/*" className="hidden" onChange={handleImageChange} />
                </div>
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Categoría:</label>
                <select
                  value={newProduct.categoryId}
                  onChange={e => {
                    setNewProduct({ ...newProduct, categoryId: e.target.value, subcategoryId: '' });
                    setShowNewSubcategory(false);
                  }}
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink"
                  required
                  disabled={categories.length === 0}
                >
                  <option value="" disabled>Selecciona una categoría...</option>
                  {categories.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </select>
              </div>

              {newProduct.categoryId && (
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Subcategoría (opcional):</label>
                  {!showNewSubcategory ? (
                    <div className="flex items-center gap-2">
                      <select
                        value={newProduct.subcategoryId}
                        onChange={e => setNewProduct({ ...newProduct, subcategoryId: e.target.value })}
                        className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink"
                      >
                        <option value="">Sin subcategoría</option>
                        {subcategoriesForSelectedCategory.map(sub => (
                          <option key={sub.id} value={sub.id}>{sub.name}</option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => setShowNewSubcategory(true)}
                        title="Crear nueva subcategoría"
                        className="shrink-0 w-9 h-9 rounded-2xl bg-brand/10 dark:bg-brand/20 text-brand dark:text-brand hover:bg-solid hover:text-white flex items-center justify-center cursor-pointer transition-colors"
                      >
                        <Plus className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        autoFocus
                        value={newSubcategoryName}
                        onChange={e => setNewSubcategoryName(e.target.value)}
                        placeholder="Ej. Gaseosas"
                        className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink"
                      />
                      <button
                        type="button"
                        onClick={handleCreateSubcategory}
                        className="shrink-0 px-3 py-2 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold cursor-pointer"
                      >
                        Crear
                      </button>
                      <button
                        type="button"
                        onClick={() => { setShowNewSubcategory(false); setNewSubcategoryName(''); }}
                        className="shrink-0 px-3 py-2 bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
                      >
                        Cancelar
                      </button>
                    </div>
                  )}
                  <p className="text-[10px] text-ink-muted mt-1">
                    Las subcategorías son propias de tu tienda — créalas aquí mismo.
                  </p>
                </div>
              )}

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre Comercial del Producto:</label>
                <input
                  type="text"
                  value={newProduct.name}
                  onChange={e => setNewProduct({ ...newProduct, name: e.target.value })}
                  placeholder="Ej. Arroz 1kg"
                  className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Código de Barras:</label>
                  <input
                    type="text"
                    value={newProduct.barcode}
                    onChange={e => setNewProduct({ ...newProduct, barcode: e.target.value })}
                    placeholder="770..."
                    className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-mono text-xs text-ink focus:outline-none focus:border-ink"
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Código Interno:</label>
                  <input
                    type="text"
                    value={newProduct.internalCode}
                    onChange={e => setNewProduct({ ...newProduct, internalCode: e.target.value })}
                    placeholder="PROD-101"
                    disabled={!!newProduct.id}
                    className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-mono text-xs text-ink disabled:opacity-60 focus:outline-none focus:border-ink"
                  />
                </div>
              </div>

              <div className={newProduct.id ? '' : 'grid grid-cols-2 gap-2'}>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Precio Venta ($ COP):</label>
                  <input
                    type="number"
                    value={newProduct.price}
                    onChange={e => setNewProduct({ ...newProduct, price: e.target.value })}
                    placeholder="12000"
                    className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-bold text-ink focus:outline-none focus:border-ink"
                    required
                  />
                </div>
                {!newProduct.id && (
                  <div>
                    <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Stock Inicial:</label>
                    <input
                      type="number"
                      value={newProduct.stock}
                      onChange={e => setNewProduct({ ...newProduct, stock: e.target.value })}
                      placeholder="50"
                      className="w-full p-2.5 bg-page border border-gray-300 dark:border-line rounded-2xl font-bold text-ink focus:outline-none focus:border-ink"
                    />
                  </div>
                )}
              </div>
              {newProduct.id && (
                <p className="text-[10px] text-ink-muted">
                  Para ajustar el stock ve al módulo de Inventario.
                </p>
              )}
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowAddModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={categories.length === 0}
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-2xl text-xs font-extrabold shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
              >
                {newProduct.id ? 'Guardar Cambios' : 'Guardar Producto'}
              </button>
            </div>
          </form>
        </div>
      )}

      <BarcodeModal product={barcodeProduct} onClose={() => setBarcodeProduct(null)} />
    </div>
  );
}
