import React from 'react';
import { Package, Barcode, Edit3, Trash2, Power, PowerOff } from 'lucide-react';
import { Button, EmptyState } from '../ui';

const ICON_BTN = 'p-1.5 text-ink-muted rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer';

/** Tabla del catálogo: una fila por producto, con acciones de código de barras, edición, estado y borrado. */
export default function ProductTable({
  products, loading, canToggleStatus, onCreate, onEdit, onBarcode, onToggleStatus, onDelete,
}) {
  if (loading) {
    return <div className="py-12 text-center text-xs font-bold text-ink-muted">Consultando catálogo de productos...</div>;
  }

  if (products.length === 0) {
    return (
      <EmptyState
        icon={Package}
        title="No se encontraron productos"
        description="Crea tu primer producto para empezar a registrar ventas"
        action={<Button size="sm" onClick={onCreate}>+ Registrar Primer Producto</Button>}
      />
    );
  }

  return (
    <div className="overflow-x-auto -mx-6">
      <table className="w-full text-left text-xs">
        <thead>
          <tr className="border-b border-line bg-gray-50/50 dark:bg-page text-ink-muted uppercase text-[10px] tracking-wider font-extrabold">
            <th className="py-3 px-6">Producto</th>
            <th className="py-3 px-3">Código</th>
            <th className="py-3 px-3">Categoría</th>
            <th className="py-3 px-3 text-right">Precio</th>
            <th className="py-3 px-3">Stock</th>
            <th className="py-3 px-6 text-right">Acciones</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 dark:divide-line">
          {products.map(p => {
            const stockVal = p.quantityAvailable ?? p.stock ?? 0;
            const isLow = stockVal <= (p.minStock || 5);
            return (
              <tr
                key={p.id}
                className="hover:bg-gray-50 dark:hover:bg-white/5 transition-colors cursor-pointer group"
                onClick={() => onEdit(p)}
              >
                <td className="py-3 px-6">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-gray-50 dark:bg-page border border-line flex items-center justify-center overflow-hidden shrink-0">
                      {p.imageUrl ? (
                        <img src={p.imageUrl} alt={p.name} className="w-full h-full object-cover" />
                      ) : (
                        <Package className="w-4 h-4 text-gray-300 dark:text-gray-600" />
                      )}
                    </div>
                    <div className="min-w-0">
                      <span className="font-bold text-sm text-ink block truncate group-hover:text-brand transition-colors">
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
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 font-extrabold">
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
                    <button onClick={() => onBarcode(p)} title="Ver código de barras" className={`${ICON_BTN} hover:text-brand`}>
                      <Barcode className="w-4 h-4" />
                    </button>
                    <button onClick={() => onEdit(p)} title="Editar producto" className={`${ICON_BTN} hover:text-brand`}>
                      <Edit3 className="w-4 h-4" />
                    </button>
                    {canToggleStatus && (
                      <button
                        onClick={() => onToggleStatus(p)}
                        title={p.active === false ? 'Reactivar producto' : 'Desactivar producto'}
                        className={`${ICON_BTN} hover:text-amber-600 dark:hover:text-amber-400`}
                      >
                        {p.active === false ? <Power className="w-4 h-4" /> : <PowerOff className="w-4 h-4" />}
                      </button>
                    )}
                    <button
                      onClick={() => onDelete(p.id)}
                      title="Eliminar producto"
                      className={`${ICON_BTN} hover:text-red-600 dark:hover:text-red-400`}
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
  );
}
