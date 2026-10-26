import React, { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Package,
  AlertTriangle,
  ArrowUpRight,
  Search,
  Truck,
  RotateCcw,
  RefreshCw,
  Barcode,
  PackageX,
  FileSpreadsheet,
  History,
  ArrowLeftRight
} from 'lucide-react';
import { getProductsApi } from '../services/productsApi';
import { getSuppliersApi } from '../services/suppliersApi';
import useAuth from '../hooks/useAuth';
import { SegmentedControl } from '../components/ui';
import BarcodeModal from '../components/BarcodeModal';
import ProductImportModal from '../components/products/ProductImportModal';
import ExpiryBadge from '../components/products/ExpiryBadge';
import StockMovementModal from '../components/inventory/StockMovementModal';
import MovementsPanel from '../components/inventory/MovementsPanel';
import ReturnsBento from './ReturnsBento';
import SuppliersBento from './SuppliersBento';
import useLoad from '../hooks/useLoad';
import asList from '../utils/asList';

const TABS = [
  { id: 'stock', label: 'Stock', icon: Package },
  { id: 'movements', label: 'Movimientos', icon: History },
  { id: 'returns', label: 'Devoluciones', icon: RotateCcw },
  { id: 'suppliers', label: 'Proveedores', icon: Truck },
];

export default function InventoryBento() {
  const { user } = useAuth();
  // Mover stock, comprar y el conteo masivo escriben en el kardex: mismo permiso que POST /inventory/movements
  // y POST /purchases en el backend (un vendedor solo puede consultar).
  const canMoveStock = user?.role === 'ADMINISTRATOR' || user?.role === 'SUPERVISOR';
  const [showCountModal, setShowCountModal] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = TABS.some(t => t.id === searchParams.get('tab')) ? searchParams.get('tab') : 'stock';
  const setActiveTab = (tab) => setSearchParams(tab === 'stock' ? {} : { tab }, { replace: true });
  const { data: productsList, loading, reload: reloadInventory } = useLoad(
    () => asList(getProductsApi()),
    [],
    { initial: [], autoRefresh: true },
  );
  const { data: suppliers } = useLoad(() => asList(getSuppliersApi()), [], { initial: [], autoRefresh: true });
  const [search, setSearch] = useState('');
  // null = cerrado; { product } = abierto (product puede ser null: el producto se elige dentro del modal).
  const [moveModal, setMoveModal] = useState(null);
  const [barcodeProduct, setBarcodeProduct] = useState(null);

  const lowStockCount = productsList.filter(p => (p.quantityAvailable ?? p.stock ?? 0) <= (p.minStock || 5)).length;
  const outOfStockCount = productsList.filter(p => (p.quantityAvailable ?? p.stock ?? 0) <= 0).length;

  const filtered = productsList.filter(p =>
    (p.name && p.name.toLowerCase().includes(search.toLowerCase())) ||
    (p.barcode && p.barcode.includes(search)) ||
    (p.internalCode && p.internalCode.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="space-y-6 select-none">
      {/* Header banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-ink">Inventario</h1>
          <p className="text-xs text-ink-soft font-medium">Stock, movimientos, devoluciones y proveedores en un solo módulo de BentoPOS</p>
        </div>

        {activeTab === 'stock' && (
          <div className="flex items-center gap-2">
            {canMoveStock && (
              <>
                <button
                  onClick={() => setShowCountModal(true)}
                  className="px-4 py-2.5 border border-line hover:border-line-strong text-ink bg-card rounded-2xl text-xs font-bold flex items-center gap-2 cursor-pointer transition-all"
                >
                  <FileSpreadsheet className="w-4 h-4" /> Conteo masivo (Excel)
                </button>
                <button
                  onClick={() => setMoveModal({ product: null })}
                  className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-solid-ink rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer transition-all"
                >
                  <ArrowLeftRight className="w-4 h-4" /> Mover stock
                </button>
              </>
            )}
          </div>
        )}
      </div>

      {/* Tab Switcher */}
      <SegmentedControl options={TABS} value={activeTab} onChange={setActiveTab} ariaLabel="Secciones de inventario" pill scrollable equal={false} className="w-fit" />

      {activeTab === 'movements' && <MovementsPanel />}
      {activeTab === 'returns' && <ReturnsBento />}
      {activeTab === 'suppliers' && <SuppliersBento />}

      {activeTab === 'stock' && (
      <>
      {/* Bento Stats Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="bento-card p-6 flex items-center justify-between bg-card border-line">
          <div>
            <span className="text-xs font-extrabold uppercase text-ink-muted">Total Referencias</span>
            <h3 className="text-2xl font-black text-ink mt-1 tabular-nums">{productsList.length} ítems</h3>
            <span className="text-[11px] text-brand dark:text-brand font-bold">Monitoreo Activo</span>
          </div>
          <div className="p-3 bg-brand/10 dark:bg-brand/20 text-brand dark:text-brand rounded-2xl border border-brand/25">
            <Package className="w-6 h-6 text-brand dark:text-brand" />
          </div>
        </div>

        <div className="bento-card p-6 flex items-center justify-between bg-amber-50/50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/30">
          <div>
            <span className="text-xs font-extrabold uppercase text-amber-800 dark:text-amber-400">Bajo Stock Mínimo</span>
            <h3 className="text-2xl font-black text-amber-950 dark:text-amber-300 mt-1 tabular-nums">{lowStockCount} Alertas</h3>
            <span className="text-[11px] text-amber-700 dark:text-amber-400 font-bold">Requieren Recompra</span>
          </div>
          <div className="p-3 bg-amber-500 text-white rounded-2xl shadow-xs">
            <AlertTriangle className="w-6 h-6" />
          </div>
        </div>

        <div className="bento-card p-6 flex items-center justify-between bg-red-50/50 dark:bg-red-500/10 border-red-200 dark:border-red-500/30">
          <div>
            <span className="text-xs font-extrabold uppercase text-red-700 dark:text-red-400">Agotados</span>
            <h3 className="text-2xl font-black text-red-800 dark:text-red-300 mt-1 tabular-nums">{outOfStockCount} ítems</h3>
            <span className="text-[11px] text-red-600 dark:text-red-400 font-bold">Sin existencias</span>
          </div>
          <div className="p-3 bg-red-500 text-white rounded-2xl shadow-xs">
            <PackageX className="w-6 h-6" />
          </div>
        </div>

        <div className="bento-card p-6 flex items-center justify-between bg-card border-line">
          <div>
            <span className="text-xs font-extrabold uppercase text-ink-muted">Valorizado Inventario</span>
            <h3 className="text-2xl font-black text-ink mt-1 tabular-nums">
              ${productsList.reduce((acc, p) => acc + ((p.salePrice || p.price || 0) * (p.quantityAvailable ?? p.stock ?? 0)), 0).toLocaleString('es-CO')}
            </h3>
            <span className="text-[11px] text-ink-soft font-bold">Moneda Local (COP)</span>
          </div>
          <div className="p-3 bg-brand/10 dark:bg-brand/20 text-brand dark:text-brand rounded-2xl border border-brand/25">
            <ArrowUpRight className="w-6 h-6 text-brand dark:text-brand" />
          </div>
        </div>
      </div>

      {/* Main Table Card */}
      <div className="bento-card p-6 space-y-4 bg-card border-line">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar producto o código..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
            />
          </div>
          <button onClick={() => reloadInventory({ silent: false })} className="text-xs font-bold text-brand dark:text-brand flex items-center gap-1 hover:underline cursor-pointer">
            <RefreshCw className="w-3.5 h-3.5" /> Actualizar stock
          </button>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">
            Cargando niveles de stock...
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-10 text-center space-y-2 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-gray-600 dark:text-gray-300">No hay productos en inventario</p>
            <p className="text-[11px] text-ink-muted">Crea productos desde el catálogo para visualizar las existencias en tiempo real</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-cards w-full text-left text-xs">
              <thead>
                <tr className="border-b border-gray-200 dark:border-gray-800 text-ink-muted uppercase tracking-wider font-extrabold text-[10px]">
                  <th className="py-3 px-3">Código</th>
                  <th className="py-3 px-3">Producto</th>
                  <th className="py-3 px-3 text-right">Precio Venta</th>
                  <th className="py-3 px-3 text-center">Stock Actual</th>
                  <th className="py-3 px-3 text-center">Stock Mínimo</th>
                  <th className="py-3 px-3 text-center">Estado</th>
                  <th className="py-3 px-3 text-center">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800 font-medium">
                {filtered.map((item) => {
                  const stockVal = item.quantityAvailable ?? item.stock ?? 0;
                  const isLow = stockVal <= (item.minStock || 5);
                  return (
                    <tr key={item.id} className="hover:bg-gray-50 dark:hover:bg-white/5 transition-colors">
                      <td data-label="" className="py-3 px-3 font-mono font-bold text-ink-soft">{item.internalCode}</td>
                      <td data-label="Producto" className="py-3 px-3 font-bold text-ink">{item.name}</td>
                      <td data-label="Precio Venta" className="py-3 px-3 text-right font-extrabold text-ink tabular-nums">
                        ${(item.salePrice || item.price || 0).toLocaleString('es-CO')}
                      </td>
                      <td data-label="Stock Actual" className="py-3 px-3 text-center font-black text-sm tabular-nums text-ink">
                        {stockVal}
                      </td>
                      <td data-label="Stock Mínimo" className="py-3 px-3 text-center font-semibold text-ink-muted tabular-nums">
                        {item.minStock || 5}
                      </td>
                      <td data-label="Estado" className="py-3 px-3 text-center">
                        <div className="flex flex-col items-center gap-1">
                          <span className={`px-2.5 py-1 rounded-full text-[10px] font-extrabold ${
                            isLow ? 'bg-amber-100 dark:bg-amber-500/20 text-amber-800 dark:text-amber-400 border border-amber-300 dark:border-amber-500/30' : 'bg-gray-100 dark:bg-white/10 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700'
                          }`}>
                            {isLow ? 'REABASTECER' : 'ÓPTIMO'}
                          </span>
                          {stockVal > 0 && <ExpiryBadge date={item.expirationDate} />}
                        </div>
                      </td>
                      <td data-label="Acciones" className="py-3 px-3 text-center">
                        <div className="flex items-center justify-center gap-1.5">
                          <button
                            onClick={() => setBarcodeProduct(item)}
                            title="Ver código de barras"
                            className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <Barcode className="w-4 h-4" />
                          </button>
                          {canMoveStock && (
                            <button
                              onClick={() => setMoveModal({ product: item })}
                              className="px-2.5 py-1 bg-brand/10 dark:bg-brand/20 hover:bg-solid hover:text-solid-ink text-brand dark:text-brand rounded-xl text-[11px] font-bold transition-all cursor-pointer"
                            >
                              Mover stock
                            </button>
                          )}
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

      {moveModal && (
        <StockMovementModal
          product={moveModal.product}
          suppliers={suppliers}
          onClose={() => setMoveModal(null)}
          onDone={() => reloadInventory()}
          onGoToSuppliers={() => { setMoveModal(null); setActiveTab('suppliers'); }}
        />
      )}

      {showCountModal && (
        <ProductImportModal mode="count" onClose={() => setShowCountModal(false)} onImported={() => reloadInventory()} />
      )}

      <BarcodeModal product={barcodeProduct} onClose={() => setBarcodeProduct(null)} />
      </>
      )}
    </div>
  );
}
