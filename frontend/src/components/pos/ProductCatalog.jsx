import React from 'react';
import { Search, Package, Plus, ScanLine } from 'lucide-react';

function ProductCard({ product, onAdd }) {
  const stockVal = product.quantityAvailable ?? product.stock ?? 0;
  const isLowStock = stockVal <= (product.minStock || 5);
  const isOut = stockVal <= 0;
  const priceVal = product.salePrice || product.price || 0;

  return (
    <button
      onClick={() => !isOut && onAdd(product)}
      disabled={isOut}
      className={`group relative flex flex-col justify-between text-left p-3 rounded-2xl bg-card border border-line hover:border-ink transition-all duration-150 shadow-[0_2px_12px_rgba(0,0,0,0.02)] hover:shadow-[0_8px_24px_rgba(0,0,0,0.05)] active:scale-[0.98] ${
        isOut ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'
      }`}
    >
      <div className="w-full aspect-square rounded-xl overflow-hidden bg-page mb-2 relative">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Package className="w-8 h-8 text-gray-300 dark:text-gray-600" />
          </div>
        )}

        <div className="absolute top-1.5 right-1.5">
          {isOut ? (
            <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-lg bg-rose-500 text-white shadow-sm">Agotado</span>
          ) : isLowStock ? (
            <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-lg bg-amber-500 text-white shadow-sm">Stock: {stockVal}</span>
          ) : (
            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-lg bg-white/90 dark:bg-black/60 backdrop-blur-sm text-ink shadow-xs">
              Stock: {stockVal}
            </span>
          )}
        </div>

        {product.categoryName && (
          <div className="absolute bottom-1.5 left-1.5">
            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-lg bg-[#0A0A0A]/80 text-white backdrop-blur-sm">
              {product.categoryName}
            </span>
          </div>
        )}
      </div>

      <div className="flex-1 flex flex-col justify-between">
        <div>
          <h4 className="text-sm font-bold text-ink line-clamp-2 leading-snug">{product.name}</h4>
          <span className="text-[11px] font-mono text-ink-muted">{product.internalCode}</span>
        </div>

        <div className="mt-2 pt-2 border-t border-[#F5F5F3] dark:border-line flex items-center justify-between">
          <span className="text-lg font-extrabold text-ink font-mono tracking-tight">${priceVal.toLocaleString('es-CO')}</span>
          <div className="w-8 h-8 rounded-xl bg-page group-hover:bg-solid group-hover:text-solid-ink flex items-center justify-center transition-colors shrink-0">
            <Plus className="w-3.5 h-3.5" />
          </div>
        </div>
      </div>
    </button>
  );
}

/** Columna izquierda del POS: buscador / lector de códigos, chips de categoría y grilla de productos. */
export default function ProductCatalog({
  inputRef, searchQuery, onSearchChange, onSearchKeyDown,
  categories, selectedCategory, onSelectCategory,
  products, loading, onAdd, onOpenScanner,
}) {
  return (
    <div className="flex flex-col gap-4 lg:h-full lg:overflow-hidden lg:pt-8 lg:pl-8 lg:pr-4">
      <div className="flex gap-2 shrink-0">
      <div className="relative flex-1">
        <Search className="w-4 h-4 text-ink-muted absolute left-4 top-1/2 -translate-y-1/2" />
        <input
          ref={inputRef}
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          onKeyDown={onSearchKeyDown}
          placeholder="Buscar productos por nombre, SKU o código de barra..."
          className="w-full h-12 pl-11 pr-16 bg-card rounded-2xl border border-line text-xs font-semibold focus:outline-none focus:border-ink text-ink placeholder-gray-400 dark:placeholder-gray-500 shadow-[0_2px_8px_rgba(0,0,0,0.02)] transition-all"
        />
        {searchQuery && (
          <button
            onClick={() => onSearchChange('')}
            className="absolute right-4 top-1/2 -translate-y-1/2 text-[10px] font-bold text-ink-muted hover:text-ink-soft px-2 py-0.5 rounded-full bg-gray-100 dark:bg-line cursor-pointer"
          >
            Limpiar
          </button>
        )}
      </div>
      {onOpenScanner && (
        <button
          type="button"
          onClick={onOpenScanner}
          aria-label="Escanear con la cámara"
          className="lg:hidden h-12 w-12 shrink-0 rounded-2xl bg-solid text-solid-ink flex items-center justify-center active:scale-95 transition-all cursor-pointer"
        >
          <ScanLine className="w-5 h-5" />
        </button>
      )}
      </div>

      <div className="shrink-0 flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
        {categories.map(cat => (
          <button
            key={cat}
            onClick={() => onSelectCategory(cat)}
            className={`px-4 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all duration-150 border cursor-pointer ${
              selectedCategory === cat
                ? 'bg-solid text-solid-ink border-transparent shadow-sm'
                : 'bg-card text-ink-soft border-line hover:border-line-strong hover:text-ink'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      <div className="flex-1 lg:overflow-y-auto lg:pr-1 lg:pb-8">
        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Cargando catálogo real de productos...</div>
        ) : products.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-3xl border border-dashed border-line">
            <Package className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <p className="text-xs font-bold text-ink">No hay productos en el catálogo</p>
            <p className="text-[11px] text-ink-muted">Ve al módulo de productos para registrar artículos</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3">
            {products.map(product => <ProductCard key={product.id} product={product} onAdd={onAdd} />)}
          </div>
        )}
      </div>
    </div>
  );
}
