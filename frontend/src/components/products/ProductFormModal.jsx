import React, { useRef } from 'react';
import { AlertCircle, ImagePlus, Plus } from 'lucide-react';
import { Button, Field, Input, Modal, Select, inputClass } from '../ui';

/**
 * Alta / edición de un producto. El estado vive en la página (`product`, `setProduct`);
 * este componente solo pinta el formulario y delega las acciones en los callbacks.
 */
export default function ProductFormModal({
  product, setProduct, categories, subcategories, errorMsg,
  uploadingImage, onImageChange,
  creatingSubcategory, setCreatingSubcategory, newSubcategoryName, setNewSubcategoryName, onCreateSubcategory,
  onSubmit, onClose,
}) {
  const imageInputRef = useRef(null);
  const isEdit = !!product.id;
  const set = (patch) => setProduct({ ...product, ...patch });
  const noCategories = categories.length === 0;

  return (
    <Modal title={isEdit ? 'Editar Producto' : 'Crear Nuevo Producto'} onClose={onClose} dismissible={false}>
      <form onSubmit={onSubmit} className="space-y-4">
        {errorMsg && (
          <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        {noCategories && (
          <div className="p-3 bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/30 rounded-xl text-xs font-bold flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>Aún no hay categorías de producto. Pide a un administrador que cree una desde el backoffice antes de registrar productos.</span>
          </div>
        )}

        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-gray-50 dark:bg-page border border-line flex items-center justify-center overflow-hidden shrink-0">
            {product.imageUrl ? (
              <img src={product.imageUrl} alt="Imagen del producto" className="w-full h-full object-cover" />
            ) : (
              <ImagePlus className="w-6 h-6 text-gray-300 dark:text-gray-600" />
            )}
          </div>
          <div className="space-y-1.5">
            <Button variant="soft" disabled={uploadingImage} onClick={() => imageInputRef.current?.click()}>
              {uploadingImage ? 'Subiendo...' : product.imageUrl ? 'Cambiar imagen' : 'Subir imagen'}
            </Button>
            <input ref={imageInputRef} type="file" accept="image/*" className="hidden" onChange={onImageChange} />
          </div>
        </div>

        <Select
          label="Categoría"
          value={product.categoryId}
          onChange={e => { set({ categoryId: e.target.value, subcategoryId: '' }); setCreatingSubcategory(false); }}
          required
          disabled={noCategories}
        >
          <option value="" disabled>Selecciona una categoría...</option>
          {categories.map(cat => <option key={cat.id} value={cat.id}>{cat.name}</option>)}
        </Select>

        {product.categoryId && (
          <Field label="Subcategoría (opcional)" hint="Las subcategorías son propias de tu tienda — créalas aquí mismo.">
            {!creatingSubcategory ? (
              <div className="flex items-center gap-2">
                <select className={inputClass} value={product.subcategoryId} onChange={e => set({ subcategoryId: e.target.value })}>
                  <option value="">Sin subcategoría</option>
                  {subcategories.map(sub => <option key={sub.id} value={sub.id}>{sub.name}</option>)}
                </select>
                <button
                  type="button"
                  onClick={() => setCreatingSubcategory(true)}
                  title="Crear nueva subcategoría"
                  className="shrink-0 w-9 h-9 rounded-2xl bg-brand/10 dark:bg-brand/20 text-brand hover:bg-solid hover:text-white flex items-center justify-center cursor-pointer transition-colors"
                >
                  <Plus className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  autoFocus
                  className={inputClass}
                  value={newSubcategoryName}
                  onChange={e => setNewSubcategoryName(e.target.value)}
                  placeholder="Ej. Gaseosas"
                />
                <Button size="sm" className="shrink-0" onClick={onCreateSubcategory}>Crear</Button>
                <Button size="sm" variant="soft" className="shrink-0" onClick={() => { setCreatingSubcategory(false); setNewSubcategoryName(''); }}>
                  Cancelar
                </Button>
              </div>
            )}
          </Field>
        )}

        <Input label="Nombre Comercial del Producto" value={product.name} onChange={e => set({ name: e.target.value })} placeholder="Ej. Arroz 1kg" required />

        <div className="grid grid-cols-2 gap-3">
          <Input label="Código de Barras" className="font-mono" value={product.barcode} onChange={e => set({ barcode: e.target.value })} placeholder="770..." />
          <Input label="Código Interno" className="font-mono" value={product.internalCode} onChange={e => set({ internalCode: e.target.value })} placeholder="PROD-101" disabled={isEdit} />
        </div>

        <div className={isEdit ? '' : 'grid grid-cols-2 gap-3'}>
          <Input label="Precio Venta ($ COP)" type="number" value={product.price} onChange={e => set({ price: e.target.value })} placeholder="12000" required />
          {!isEdit && (
            <Input label="Stock Inicial" type="number" value={product.stock} onChange={e => set({ stock: e.target.value })} placeholder="50" />
          )}
        </div>
        {isEdit && <p className="text-[10px] text-ink-muted">Para ajustar el stock ve al módulo de Inventario.</p>}

        <div className="flex gap-2 pt-2">
          <Button variant="soft" className="w-1/2" onClick={onClose}>Cancelar</Button>
          <Button type="submit" className="w-1/2" disabled={noCategories}>{isEdit ? 'Guardar Cambios' : 'Guardar Producto'}</Button>
        </div>
      </form>
    </Modal>
  );
}
