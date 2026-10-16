import React from 'react';
import { Layers, Search, Edit3, Trash2 } from 'lucide-react';
import { Button, Card, EmptyState } from '../ui';

const ICON_BTN = 'p-1.5 text-ink-muted rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer';

/** Pestaña "Subcategorías": resumen de categorías globales + subcategorías propias de la tienda. */
export default function SubcategoriesPanel({
  categories, subcategories, filtered, search, onSearchChange, canManage, onCreate, onEdit, onDelete,
}) {
  return (
    <div className="space-y-6">
      <Card className="space-y-3">
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
              <span key={cat.id} className="text-xs font-bold px-3 py-1.5 rounded-full bg-brand/10 dark:bg-brand/20 text-brand border border-brand/25">
                {cat.name}
                <span className="ml-1.5 opacity-70 font-semibold">
                  · {subcategories.filter(s => s.categoryId === cat.id).length} subcat.
                </span>
              </span>
            ))}
          </div>
        )}
      </Card>

      <Card className="space-y-4">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => onSearchChange(e.target.value)}
              placeholder="Buscar subcategoría o categoría..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
            />
          </div>
          <span className="text-xs font-bold text-ink-muted">{filtered.length} subcategorías</span>
        </div>

        {filtered.length === 0 ? (
          <EmptyState
            icon={Layers}
            title="No hay subcategorías registradas"
            description={canManage
              ? 'Crea la primera para organizar mejor tu catálogo de productos'
              : 'Pide a un administrador o supervisor que cree la primera'}
            action={canManage && categories.length > 0 && (
              <Button size="sm" onClick={onCreate}>+ Crear Primera Subcategoría</Button>
            )}
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map(sub => (
              <div key={sub.id} className="p-5 bg-card rounded-2xl border border-line space-y-3 flex flex-col justify-between hover:border-line-strong transition-all shadow-xs group">
                <div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-line text-gray-600 dark:text-gray-300 font-extrabold truncate">
                      {sub.categoryName}
                    </span>
                    <span className="text-[10px] font-mono text-ink-muted font-bold shrink-0">
                      {sub.productCount} {sub.productCount === 1 ? 'producto' : 'productos'}
                    </span>
                  </div>
                  <h3 className="font-extrabold text-sm text-ink mt-2 group-hover:text-brand transition-colors">{sub.name}</h3>
                  <p className="text-[11px] text-ink-soft mt-0.5">{sub.description || 'Sin descripción'}</p>
                </div>

                {canManage && (
                  <div className="pt-3 border-t border-line flex items-center justify-end gap-1">
                    <button onClick={() => onEdit(sub)} title="Editar subcategoría" className={`${ICON_BTN} hover:text-brand`}>
                      <Edit3 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => onDelete(sub)}
                      title="Eliminar subcategoría"
                      className={`${ICON_BTN} hover:text-red-600 dark:hover:text-red-400`}
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
