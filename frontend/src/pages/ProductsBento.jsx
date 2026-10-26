import React, { useState } from 'react';
import { Plus, Search, Package, Layers, FileSpreadsheet } from 'lucide-react';
import { getProductsApi, createProductApi, updateProductApi, deleteProductApi, changeProductStatusApi } from '../services/productsApi';
import { getCategoriesApi } from '../services/categoriesApi';
import {
  getSubcategoriesApi,
  createSubcategoryApi,
  updateSubcategoryApi,
  deleteSubcategoryApi
} from '../services/subcategoriesApi';
import { createInventoryMovementApi } from '../services/inventoryApi';
import useModal from '../hooks/useModal';
import useAuth from '../hooks/useAuth';
import { uploadFileApi } from '../services/http';
import BarcodeModal from '../components/BarcodeModal';
import ProductTable from '../components/products/ProductTable';
import ProductFormModal from '../components/products/ProductFormModal';
import ProductImportModal from '../components/products/ProductImportModal';
import SubcategoriesPanel from '../components/products/SubcategoriesPanel';
import SubcategoryModal from '../components/products/SubcategoryModal';
import { Button, Card, PageHeader } from '../components/ui';
import useLoad from '../hooks/useLoad';
import asList from '../utils/asList';

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
  // Mismo criterio que el backend: crear/importar productos y gestionar subcategorías es de ADMINISTRATOR y SUPERVISOR.
  const canManageProducts = user?.role === 'ADMINISTRATOR' || user?.role === 'SUPERVISOR';
  const canManageSubcategories = canManageProducts;
  const [activeTab, setActiveTab] = useState('catalog');
  const [showImportModal, setShowImportModal] = useState(false);
  const [search, setSearch] = useState('');
  const [showInactive, setShowInactive] = useState(false);
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

  const { data: products, loading, reload: reloadProducts } = useLoad(
    () => asList(getProductsApi('', null, 0, 50, !showInactive)),
    [showInactive],
    { initial: [], autoRefresh: true },
  );
  const { data: categories } = useLoad(() => asList(getCategoriesApi()), [], { initial: [], autoRefresh: true });
  const { data: subcategories, reload: reloadSubcategories } = useLoad(
    () => asList(getSubcategoriesApi()),
    [],
    { initial: [], autoRefresh: true },
  );

  const subcategoriesForSelectedCategory = subcategories.filter(
    sub => String(sub.categoryId) === String(newProduct.categoryId)
  );

  const handleCreateSubcategory = async () => {
    if (!newSubcategoryName.trim() || !newProduct.categoryId) return;
    try {
      const created = await createSubcategoryApi(Number(newProduct.categoryId), newSubcategoryName.trim());
      reloadSubcategories();
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
      reloadSubcategories();
    } catch (err) {
      setSubErrorMsg(err.message || 'Error al guardar la subcategoría. Intenta nuevamente.');
    }
  };

  const handleDeleteSubcategory = async (sub) => {
    const ok = await confirm(`¿Eliminar la subcategoría "${sub.name}"?`, { title: 'Eliminar subcategoría' });
    if (!ok) return;
    try {
      await deleteSubcategoryApi(sub.id);
      reloadSubcategories();
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
      reloadProducts();
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

  const toggleInactive = () => setShowInactive((v) => !v);

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
      reloadProducts();
    } catch (e) {
      notify('No se pudo cambiar el estado del producto: ' + e.message);
    }
  };

  const handleDelete = async (id) => {
    const ok = await confirm('¿Seguro que deseas eliminar este producto?', { title: 'Eliminar producto' });
    if (!ok) return;
    try {
      await deleteProductApi(id);
      reloadProducts();
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
          activeTab === 'catalog' && canManageProducts ? (
            <>
              <Button variant="outline" icon={FileSpreadsheet} onClick={() => setShowImportModal(true)}>Importar Excel</Button>
              <Button icon={Plus} onClick={openCreateModal}>Crear Nuevo Producto</Button>
            </>
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
            onCreate={canManageProducts ? openCreateModal : null}
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
      )}

      {showImportModal && (
        <ProductImportModal
          onClose={() => setShowImportModal(false)}
          onImported={() => { reloadProducts(); reloadSubcategories(); }}
        />
      )}

      <BarcodeModal product={barcodeProduct} onClose={() => setBarcodeProduct(null)} />
    </div>
  );
}
