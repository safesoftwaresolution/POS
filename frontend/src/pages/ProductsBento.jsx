import React, { useState, useEffect } from 'react';
import { Plus, Search, Package, Layers } from 'lucide-react';
import { getProductsApi, createProductApi, updateProductApi, deleteProductApi, changeProductStatusApi } from '../services/productsApi';
import { getCategoriesApi } from '../services/categoriesApi';
import {
<<<<<<< HEAD
  Tag,
  Plus,
  Search,
  Edit3,
  Trash2,
  Barcode,
  Package,
  Check,
  AlertCircle
} from 'lucide-react';
import { getProductsApi, createProductApi, deleteProductApi, getCategoriesApi } from '../services/api';
=======
  getSubcategoriesApi,
  createSubcategoryApi,
  updateSubcategoryApi,
  deleteSubcategoryApi
} from '../services/subcategoriesApi';
import { createInventoryMovementApi } from '../services/inventoryApi';
import { useModal } from '../context/ModalContext';
import { useAuth } from '../context/AuthContext';
import { uploadFileApi } from '../services/http';
>>>>>>> develop
import BarcodeModal from '../components/BarcodeModal';
import ProductTable from '../components/products/ProductTable';
import ProductFormModal from '../components/products/ProductFormModal';
import SubcategoriesPanel from '../components/products/SubcategoriesPanel';
import SubcategoryModal from '../components/products/SubcategoryModal';
import { Button, Card, PageHeader } from '../components/ui';

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
<<<<<<< HEAD
=======
  const [subcategories, setSubcategories] = useState([]);
>>>>>>> develop
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showInactive, setShowInactive] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [barcodeProduct, setBarcodeProduct] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');
<<<<<<< HEAD
  const [newProduct, setNewProduct] = useState({
    name: '',
    barcode: '',
    internalCode: '',
    categoryId: '',
    price: '',
    stock: '',
    minStock: 5
  });
=======
  const [newProduct, setNewProduct] = useState(EMPTY_PRODUCT);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [showNewSubcategory, setShowNewSubcategory] = useState(false);
  const [newSubcategoryName, setNewSubcategoryName] = useState('');
  const [subSearch, setSubSearch] = useState('');
  const [showSubModal, setShowSubModal] = useState(false);
  const [subForm, setSubForm] = useState(EMPTY_SUBCATEGORY);
  const [subErrorMsg, setSubErrorMsg] = useState('');
>>>>>>> develop

  const loadProducts = async (inactive = showInactive) => {
    setLoading(true);
    try {
      const data = await getProductsApi('', null, 0, 50, !inactive);
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
<<<<<<< HEAD
      const data = await getCategoriesApi();
      setCategories(Array.isArray(data) ? data : []);
=======
      setCategories(await getCategoriesApi());
>>>>>>> develop
    } catch (e) {
      console.warn('Error cargando categorías:', e);
      setCategories([]);
    }
  };

<<<<<<< HEAD
  useEffect(() => {
    loadProducts();
    loadCategories();
=======
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
>>>>>>> develop
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
<<<<<<< HEAD
      await createProductApi({
        name: newProduct.name,
        barcode: newProduct.barcode || `${Math.floor(1000000000000 + Math.random() * 9000000000000)}`,
        internalCode: newProduct.internalCode || `PROD-00${products.length + 1}`,
        categoryId: newProduct.categoryId,
        price: parseFloat(newProduct.price),
        stock: parseInt(newProduct.stock) || 0,
        minStock: parseInt(newProduct.minStock) || 5
      });
      setShowAddModal(false);
      setNewProduct({ name: '', barcode: '', internalCode: '', categoryId: '', price: '', stock: '', minStock: 5 });
=======
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
>>>>>>> develop
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

  const toggleInactive = () => {
    const next = !showInactive;
    setShowInactive(next);
    loadProducts(next);
  };

  const handleToggleStatus = async (p) => {
    const activate = p.active === false;
    if (!activate) {
      const ok = await confirm(`"${p.name}" dejará de aparecer en el punto de venta. Podrás reactivarlo cuando quieras.`, {
        title: 'Desactivar producto', confirmText: 'Desactivar', danger: false,
      });
      if (!ok) return;
    }
    try {
      await changeProductStatusApi(p.id, activate);
      await loadProducts();
    } catch (e) {
      notify('No se pudo cambiar el estado del producto: ' + e.message);
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
      <PageHeader
        title="Productos"
        subtitle="Catálogo y subcategorías de tu tienda en BentoPOS"
        actions={
          activeTab === 'catalog' ? (
            <Button icon={Plus} onClick={openCreateModal}>Crear Nuevo Producto</Button>
          ) : activeTab === 'subcategories' && canManageSubcategories ? (
            <Button icon={Plus} onClick={openCreateSubModal}>Nueva Subcategoría</Button>
          ) : null
        }
      />

      <div className="flex bg-gray-100 dark:bg-line rounded-full p-1 border border-line w-fit">
        {TABS.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-2 rounded-full text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                activeTab === tab.id ? 'bg-solid text-white shadow-xs' : 'text-ink-soft hover:text-brand'
              }`}
            >
              <Icon className="w-4 h-4" /> {tab.label}
            </button>
          );
        })}
      </div>

      {activeTab === 'catalog' && (
        <Card className="space-y-4">
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
            <div className="flex items-center gap-3">
              <button
                onClick={toggleInactive}
                className={`px-3 py-1.5 rounded-full text-[11px] font-bold cursor-pointer border transition-colors ${
                  showInactive ? 'bg-solid text-white border-transparent' : 'border-line text-ink-soft hover:border-line-strong'
                }`}
              >
                {showInactive ? 'Viendo inactivos' : 'Ver inactivos'}
              </button>
              <span className="text-xs font-bold text-ink-muted">
                {filtered.length} {showInactive ? 'productos inactivos' : 'productos registrados'}
              </span>
            </div>
          </div>

          <ProductTable
            products={filtered}
            loading={loading}
            canToggleStatus={canManageSubcategories}
            onCreate={openCreateModal}
            onEdit={openEditModal}
            onBarcode={setBarcodeProduct}
            onToggleStatus={handleToggleStatus}
            onDelete={handleDelete}
          />
        </Card>
      )}

      {activeTab === 'subcategories' && (
        <SubcategoriesPanel
          categories={categories}
          subcategories={subcategories}
          filtered={filteredSubcategories}
          search={subSearch}
          onSearchChange={setSubSearch}
          canManage={canManageSubcategories}
          onCreate={openCreateSubModal}
          onEdit={openEditSubModal}
          onDelete={handleDeleteSubcategory}
        />
      )}

      {showSubModal && (
        <SubcategoryModal
          form={subForm}
          setForm={setSubForm}
          categories={categories}
          errorMsg={subErrorMsg}
          onSubmit={handleSubmitSubcategory}
          onClose={() => setShowSubModal(false)}
        />
      )}

      {showAddModal && (
<<<<<<< HEAD
        <div className="fixed inset-0 bg-navy-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleAddProduct} className="bento-card max-w-md w-full bg-white dark:bg-[#14231e] p-6 rounded-3xl shadow-2xl space-y-4">
            <h3 className="font-extrabold text-base text-[#191c1e] dark:text-white">Crear Nuevo Producto</h3>

            {errorMsg && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre Comercial del Producto:</label>
                <input
                  type="text"
                  value={newProduct.name}
                  onChange={e => setNewProduct({ ...newProduct, name: e.target.value })}
                  placeholder="Ej. Arroz 1kg"
                  className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl font-semibold text-[#191c1e] dark:text-white"
                  required
                />
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Categoría:</label>
                {categories.length === 0 ? (
                  <p className="text-[11px] text-amber-700 dark:text-amber-400 font-semibold bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 rounded-2xl p-2.5">
                    No hay categorías creadas. Ve a <span className="font-extrabold">Categorías</span> en el menú y crea una primero.
                  </p>
                ) : (
                  <select
                    value={newProduct.categoryId}
                    onChange={e => setNewProduct({ ...newProduct, categoryId: e.target.value })}
                    className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl font-semibold text-[#191c1e] dark:text-white"
                    required
                  >
                    <option value="">Selecciona una categoría...</option>
                    {categories.map(c => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                )}
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Código de Barras:</label>
                  <input
                    type="text"
                    value={newProduct.barcode}
                    onChange={e => setNewProduct({ ...newProduct, barcode: e.target.value })}
                    placeholder="770..."
                    className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl font-mono text-xs text-[#191c1e] dark:text-white"
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Código Interno:</label>
                  <input
                    type="text"
                    value={newProduct.internalCode}
                    onChange={e => setNewProduct({ ...newProduct, internalCode: e.target.value })}
                    placeholder="PROD-101"
                    className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl font-mono text-xs text-[#191c1e] dark:text-white"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Precio Venta ($ COP):</label>
                  <input
                    type="number"
                    value={newProduct.price}
                    onChange={e => setNewProduct({ ...newProduct, price: e.target.value })}
                    placeholder="12000"
                    className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl font-bold text-[#191c1e] dark:text-white"
                    required
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Stock Inicial:</label>
                  <input
                    type="number"
                    value={newProduct.stock}
                    onChange={e => setNewProduct({ ...newProduct, stock: e.target.value })}
                    placeholder="50"
                    className="w-full p-2.5 bg-[#f7f9fc] dark:bg-[#0b1411] border border-gray-300 dark:border-gray-700 rounded-2xl font-bold text-[#191c1e] dark:text-white"
                  />
                </div>
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowAddModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#1e293b] hover:bg-gray-200 dark:hover:bg-[#334155] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-[#006d3c] hover:bg-[#00522c] text-white rounded-2xl text-xs font-extrabold"
              >
                Guardar Producto
              </button>
            </div>
          </form>
        </div>
=======
        <ProductFormModal
          product={newProduct}
          setProduct={setNewProduct}
          categories={categories}
          subcategories={subcategoriesForSelectedCategory}
          errorMsg={errorMsg}
          uploadingImage={uploadingImage}
          onImageChange={handleImageChange}
          creatingSubcategory={showNewSubcategory}
          setCreatingSubcategory={setShowNewSubcategory}
          newSubcategoryName={newSubcategoryName}
          setNewSubcategoryName={setNewSubcategoryName}
          onCreateSubcategory={handleCreateSubcategory}
          onSubmit={handleSubmitProduct}
          onClose={() => setShowAddModal(false)}
        />
>>>>>>> develop
      )}

      <BarcodeModal product={barcodeProduct} onClose={() => setBarcodeProduct(null)} />
    </div>
  );
}
