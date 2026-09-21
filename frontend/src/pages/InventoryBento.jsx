import React, { useState, useEffect } from 'react';
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
  PackageX
} from 'lucide-react';
import { getProductsApi } from '../services/productsApi';
import { createPurchaseApi } from '../services/inventoryApi';
import { getSuppliersApi } from '../services/suppliersApi';
import { useModal } from '../context/ModalContext';
import BarcodeModal from '../components/BarcodeModal';
import ReturnsBento from './ReturnsBento';
import SuppliersBento from './SuppliersBento';

const TABS = [
  { id: 'stock', label: 'Stock', icon: Package },
  { id: 'returns', label: 'Devoluciones', icon: RotateCcw },
  { id: 'suppliers', label: 'Proveedores', icon: Truck },
];

export default function InventoryBento() {
  const { notify } = useModal();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = TABS.some(t => t.id === searchParams.get('tab')) ? searchParams.get('tab') : 'stock';
  const setActiveTab = (tab) => setSearchParams(tab === 'stock' ? {} : { tab }, { replace: true });
  const [productsList, setProductsList] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showPurchaseModal, setShowPurchaseModal] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [selectedSupplierId, setSelectedSupplierId] = useState('');
  const [increaseAmount, setIncreaseAmount] = useState('');
  const [barcodeProduct, setBarcodeProduct] = useState(null);

  const loadInventory = async () => {
    setLoading(true);
    try {
      const data = await getProductsApi();
      setProductsList(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Error cargando inventario:', e);
      setProductsList([]);
    } finally {
      setLoading(false);
    }
  };

  const loadSuppliers = async () => {
    try {
      setSuppliers(await getSuppliersApi());
    } catch (e) {
      console.warn('Error cargando proveedores:', e);
      setSuppliers([]);
    }
  };

  useEffect(() => {
    loadInventory();
    loadSuppliers();
  }, []);

  const lowStockCount = productsList.filter(p => (p.quantityAvailable ?? p.stock ?? 0) <= (p.minStock || 5)).length;
  const outOfStockCount = productsList.filter(p => (p.quantityAvailable ?? p.stock ?? 0) <= 0).length;

  const openPurchaseModal = (product) => {
    setSelectedProduct(product);
    setSelectedSupplierId('');
    setIncreaseAmount('');
    setShowPurchaseModal(true);
  };

  const handleStockIncrease = async (e) => {
    e.preventDefault();
    const qty = parseInt(increaseAmount);
    if (!selectedProduct || !selectedSupplierId || isNaN(qty) || qty <= 0) return;

    try {
      await createPurchaseApi({
        supplierId: Number(selectedSupplierId),
        items: [{
          productId: selectedProduct.id,
          quantity: qty,
          unitCost: selectedProduct.purchasePrice || (selectedProduct.price * 0.7)
        }]
      });
      setShowPurchaseModal(false);
      setSelectedProduct(null);
      setIncreaseAmount('');
      await loadInventory();
    } catch (e) {
      notify('Error registrando compra: ' + e.message);
    }
  };

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
          <h1 className="text-2xl font-black text-[#0A0A0A] dark:text-white">Inventario</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Stock, devoluciones y proveedores en un solo módulo de BentoPOS</p>
        </div>

        {activeTab === 'stock' && (
          <button
            onClick={() => {
              if (productsList.length > 0) {
                openPurchaseModal(productsList[0]);
              } else {
                notify('Debes crear primero un producto en el catálogo.', { danger: false });
              }
            }}
            className="px-4 py-2.5 bg-[#0A0A0A] hover:bg-[#202020] text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer transition-all"
          >
            <Truck className="w-4 h-4" /> Registrar Compra (+Stock)
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
                  ? 'bg-[#0A0A0A] text-white shadow-xs'
                  : 'text-gray-500 dark:text-gray-400 hover:text-[#FF5520] dark:hover:text-[#FF5520]'
              }`}
            >
              <Icon className="w-4 h-4" /> {tab.label}
            </button>
          );
        })}
      </div>

      {activeTab === 'returns' && <ReturnsBento />}
      {activeTab === 'suppliers' && <SuppliersBento />}

      {activeTab === 'stock' && (
      <>
      {/* Bento Stats Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="bento-card p-6 flex items-center justify-between bg-white dark:bg-[#17171B] border-[#EAEAEA] dark:border-[#272730]">
          <div>
            <span className="text-xs font-extrabold uppercase text-gray-400 dark:text-gray-500">Total Referencias</span>
            <h3 className="text-2xl font-black text-[#0A0A0A] dark:text-white mt-1 tabular-nums">{productsList.length} ítems</h3>
            <span className="text-[11px] text-[#FF5520] dark:text-[#FF5520] font-bold">Monitoreo Activo</span>
          </div>
          <div className="p-3 bg-[#FF5520]/10 dark:bg-[#FF5520]/20 text-[#FF5520] dark:text-[#FF5520] rounded-2xl border border-[#FF5520]/25">
            <Package className="w-6 h-6 text-[#FF5520] dark:text-[#FF5520]" />
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

        <div className="bento-card p-6 flex items-center justify-between bg-white dark:bg-[#17171B] border-[#EAEAEA] dark:border-[#272730]">
          <div>
            <span className="text-xs font-extrabold uppercase text-gray-400 dark:text-gray-500">Valorizado Inventario</span>
            <h3 className="text-2xl font-black text-[#0A0A0A] dark:text-white mt-1 tabular-nums">
              ${productsList.reduce((acc, p) => acc + ((p.salePrice || p.price || 0) * (p.quantityAvailable ?? p.stock ?? 0)), 0).toLocaleString('es-CO')}
            </h3>
            <span className="text-[11px] text-gray-500 dark:text-gray-400 font-bold">Moneda Local (COP)</span>
          </div>
          <div className="p-3 bg-[#FF5520]/10 dark:bg-[#FF5520]/20 text-[#FF5520] dark:text-[#FF5520] rounded-2xl border border-[#FF5520]/25">
            <ArrowUpRight className="w-6 h-6 text-[#FF5520] dark:text-[#FF5520]" />
          </div>
        </div>
      </div>

      {/* Main Table Card */}
      <div className="bento-card p-6 space-y-4 bg-white dark:bg-[#17171B] border-[#EAEAEA] dark:border-[#272730]">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 text-gray-400 dark:text-gray-500 absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar producto o código..."
              className="w-full pl-10 pr-3 py-1.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-xs font-semibold text-[#0A0A0A] dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-[#0A0A0A]"
            />
          </div>
          <button onClick={loadInventory} className="text-xs font-bold text-[#FF5520] dark:text-[#FF5520] flex items-center gap-1 hover:underline cursor-pointer">
            <RefreshCw className="w-3.5 h-3.5" /> Actualizar Kardex
          </button>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-gray-400 dark:text-gray-500">
            Cargando niveles de stock...
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-10 text-center space-y-2 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-gray-600 dark:text-gray-300">No hay productos en inventario</p>
            <p className="text-[11px] text-gray-400 dark:text-gray-500">Crea productos desde el catálogo para visualizar las existencias en tiempo real</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-gray-200 dark:border-gray-800 text-gray-400 dark:text-gray-500 uppercase tracking-wider font-extrabold text-[10px]">
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
                      <td className="py-3 px-3 font-mono font-bold text-gray-500 dark:text-gray-400">{item.internalCode}</td>
                      <td className="py-3 px-3 font-bold text-[#0A0A0A] dark:text-white">{item.name}</td>
                      <td className="py-3 px-3 text-right font-extrabold text-[#0A0A0A] dark:text-white tabular-nums">
                        ${(item.salePrice || item.price || 0).toLocaleString('es-CO')}
                      </td>
                      <td className="py-3 px-3 text-center font-black text-sm tabular-nums text-[#0A0A0A] dark:text-white">
                        {stockVal}
                      </td>
                      <td className="py-3 px-3 text-center font-semibold text-gray-400 dark:text-gray-500 tabular-nums">
                        {item.minStock || 5}
                      </td>
                      <td className="py-3 px-3 text-center">
                        <span className={`px-2.5 py-1 rounded-full text-[10px] font-extrabold ${
                          isLow ? 'bg-amber-100 dark:bg-amber-500/20 text-amber-800 dark:text-amber-400 border border-amber-300 dark:border-amber-500/30' : 'bg-gray-100 dark:bg-white/10 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700'
                        }`}>
                          {isLow ? 'REABASTECER' : 'ÓPTIMO'}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-center">
                        <div className="flex items-center justify-center gap-1.5">
                          <button
                            onClick={() => setBarcodeProduct(item)}
                            title="Ver código de barras"
                            className="p-1.5 text-gray-400 dark:text-gray-500 hover:text-[#FF5520] dark:hover:text-[#FF5520] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <Barcode className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => openPurchaseModal(item)}
                            className="px-2.5 py-1 bg-[#FF5520]/10 dark:bg-[#FF5520]/20 hover:bg-[#0A0A0A] hover:text-white text-[#FF5520] dark:text-[#FF5520] rounded-xl text-[11px] font-bold transition-all cursor-pointer"
                          >
                            + Cargar Stock
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

      {/* Stock Increase Modal */}
      {showPurchaseModal && selectedProduct && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleStockIncrease} className="bento-card max-w-sm w-full bg-white dark:bg-[#17171B] p-6 rounded-3xl shadow-2xl space-y-4 border border-gray-200 dark:border-[#272730]">
            <h3 className="font-extrabold text-base text-[#0A0A0A] dark:text-white">Registrar Compra / Carga de Stock</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Producto: <span className="font-bold text-[#FF5520] dark:text-[#FF5520]">{selectedProduct.name}</span></p>

            {suppliers.length === 0 && (
              <div className="p-3 bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/30 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>
                  Aún no tienes proveedores registrados.{' '}
                  <button
                    type="button"
                    onClick={() => { setShowPurchaseModal(false); setActiveTab('suppliers'); }}
                    className="underline font-bold cursor-pointer"
                  >
                    Crea uno aquí
                  </button> antes de registrar una compra.
                </span>
              </div>
            )}

            <div className="space-y-1">
              <label className="text-xs font-bold text-gray-600 dark:text-gray-300">Proveedor:</label>
              <select
                value={selectedSupplierId}
                onChange={(e) => setSelectedSupplierId(e.target.value)}
                className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-gray-300 dark:border-[#272730] rounded-2xl text-sm font-bold text-[#0A0A0A] dark:text-white focus:outline-none focus:border-[#0A0A0A]"
                required
                disabled={suppliers.length === 0}
              >
                <option value="" disabled>Selecciona un proveedor...</option>
                {suppliers.map(s => (
                  <option key={s.id} value={s.id}>{s.companyName}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-bold text-gray-600 dark:text-gray-300">Cantidad Comprada:</label>
              <input
                type="number"
                value={increaseAmount}
                onChange={(e) => setIncreaseAmount(e.target.value)}
                placeholder="Ej. 10, 25, 50..."
                className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-gray-300 dark:border-[#272730] rounded-2xl text-sm font-bold text-[#0A0A0A] dark:text-white focus:outline-none focus:border-[#0A0A0A]"
                required
                min="1"
              />
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowPurchaseModal(false)}
                className="w-1/2 py-2.5 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-[#2C2C36] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={suppliers.length === 0}
                className="w-1/2 py-2.5 bg-[#0A0A0A] hover:bg-[#202020] disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-2xl text-xs font-extrabold shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
              >
                Registrar Compra
              </button>
            </div>
          </form>
        </div>
      )}

      <BarcodeModal product={barcodeProduct} onClose={() => setBarcodeProduct(null)} />
      </>
      )}
    </div>
  );
}
