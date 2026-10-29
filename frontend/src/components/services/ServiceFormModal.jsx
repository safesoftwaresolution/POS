import React from 'react';
import { AlertCircle } from 'lucide-react';
import { Button, Input, Modal, MoneyInput, Select } from '../ui';

/** Alta / edición de un servicio. El código no cambia después de creado. */
export default function ServiceFormModal({ form, setForm, categories, errorMsg, saving, onSubmit, onClose }) {
  const set = (patch) => setForm({ ...form, ...patch });

  return (
    <Modal title={form.id ? 'Editar Servicio' : 'Nuevo Servicio'} onClose={onClose} dismissible={false}>
      <form onSubmit={onSubmit} className="space-y-4">
        {errorMsg && (
          <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Input
            label="Código"
            value={form.code}
            onChange={(e) => set({ code: e.target.value })}
            placeholder="SRV-MASAJE"
            disabled={!!form.id}
            required
          />
          <div className="sm:col-span-2">
            <Input label="Nombre" value={form.name} onChange={(e) => set({ name: e.target.value })} placeholder="Masaje relajante 60 min" maxLength={100} required />
          </div>
        </div>

        <Input label="Descripción (opcional)" value={form.description} onChange={(e) => set({ description: e.target.value })} maxLength={255} />

        <Select label="Categoría" value={form.categoryId} onChange={(e) => set({ categoryId: e.target.value })} required>
          <option value="" disabled>Selecciona una categoría...</option>
          {categories.map((cat) => <option key={cat.id} value={cat.id}>{cat.name}</option>)}
        </Select>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="sm:col-span-2">
            <MoneyInput label="Precio de venta (IVA incluido)" value={form.price} onChange={(price) => set({ price })} placeholder="119.000" required />
          </div>
          <Input
            label="IVA %"
            type="number"
            min="0"
            max="100"
            step="0.01"
            value={form.taxRate}
            onChange={(e) => set({ taxRate: e.target.value })}
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Input
            label="Duración (min)"
            type="number"
            min="1"
            value={form.durationMinutes}
            onChange={(e) => set({ durationMinutes: e.target.value })}
            hint="Opcional"
          />
          <Input
            label="Código UNSPSC"
            value={form.unspscCode}
            onChange={(e) => set({ unspscCode: e.target.value })}
            placeholder="90111500"
            hint="Opcional; sin él se factura 999"
          />
          <Input
            label="Unidad DIAN"
            value={form.unitMeasureCode}
            onChange={(e) => set({ unitMeasureCode: e.target.value })}
            hint="94 = unidad"
          />
        </div>

        <div className="flex gap-2 pt-2">
          <Button variant="soft" className="w-1/2" onClick={onClose}>Cancelar</Button>
          <Button type="submit" className="w-1/2" loading={saving}>{form.id ? 'Guardar Cambios' : 'Crear Servicio'}</Button>
        </div>
      </form>
    </Modal>
  );
}
