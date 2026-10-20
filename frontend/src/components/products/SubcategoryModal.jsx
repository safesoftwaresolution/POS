import React from 'react';
import { AlertCircle } from 'lucide-react';
import { Button, Input, Modal, Select } from '../ui';

/** Alta / edición de una subcategoría de la tienda (la categoría padre no se puede cambiar al editar). */
export default function SubcategoryModal({ form, setForm, categories, errorMsg, onSubmit, onClose }) {
  const set = (patch) => setForm({ ...form, ...patch });

  return (
    <Modal title={form.id ? 'Editar Subcategoría' : 'Nueva Subcategoría'} onClose={onClose} dismissible={false}>
      <form onSubmit={onSubmit} className="space-y-4">
        {errorMsg && (
          <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        <Select label="Categoría" value={form.categoryId} onChange={e => set({ categoryId: e.target.value })} disabled={!!form.id} required>
          <option value="" disabled>Selecciona una categoría...</option>
          {categories.map(cat => <option key={cat.id} value={cat.id}>{cat.name}</option>)}
        </Select>
        <Input label="Nombre de la subcategoría" value={form.name} onChange={e => set({ name: e.target.value })} placeholder="Ej. Gaseosas" required />
        <Input label="Descripción (opcional)" value={form.description} onChange={e => set({ description: e.target.value })} placeholder="Bebidas carbonatadas" />

        <div className="flex gap-2 pt-2">
          <Button variant="soft" className="w-1/2" onClick={onClose}>Cancelar</Button>
          <Button type="submit" className="w-1/2">{form.id ? 'Guardar Cambios' : 'Crear Subcategoría'}</Button>
        </div>
      </form>
    </Modal>
  );
}
