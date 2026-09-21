import React, { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  TrendingUp,
  Package,
  Truck,
  RefreshCw,
  AlertTriangle,
  DollarSign,
  ShoppingBag,
  Wallet,
  CheckCircle2,
  MinusCircle,
  PlusCircle,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import {
  getSalesReportApi,
  getTopProductsReportApi,
  getStockReportApi,
  getProfitabilityReportApi,
  getPurchasesReportApi,
  getCashClosingReportApi,
} from '../services/reportsApi';

const money = (n) => `$${Math.round(n || 0).toLocaleString('es-CO')}`;

function isoDaysAgo(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

/** Fila de barra horizontal de una sola serie (magnitud), con etiqueta directa. */
function RankedBarRow({ label, value, max, formatValue = money }) {
  const pct = max > 0 ? Math.max(Math.round((value / max) * 100), 4) : 4;
  return (
    <div className="space-y-1" title={`${label}: ${formatValue(value)}`}>
      <div className="flex items-center justify-between text-xs">
        <span className="font-bold text-[#0A0A0A] dark:text-[#F4F4F6] truncate pr-2">{label}</span>
        <span className="font-extrabold text-[#FF5520] dark:text-[#FF5520] tabular-nums shrink-0">{formatValue(value)}</span>
      </div>
      <div className="h-2.5 w-full bg-[#F5F5F3] dark:bg-[#272730] rounded-full overflow-hidden">
        <div
          className="h-full bg-[#0A0A0A] dark:bg-[#FF5520] rounded-full transition-all duration-500"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

function RankedList({ entries, formatValue = money, emptyLabel = 'Sin datos en este período' }) {
  if (!entries.length) {
    return <p className="text-xs text-gray-400 dark:text-gray-500 font-semibold py-6 text-center">{emptyLabel}</p>;
  }
  const max = Math.max(...entries.map((e) => e.value), 1);
  return (
    <div className="space-y-3.5">
      {entries.map((e) => (
        <RankedBarRow key={e.label} label={e.label} value={e.value} max={max} formatValue={formatValue} />
      ))}
    </div>
  );
}

function SectionCard({ icon: Icon, title, subtitle, children, action }) {
  return (
    <div className="bento-card p-6 bg-white dark:bg-[#17171B] rounded-3xl border border-[#EAEAEA] dark:border-[#272730] shadow-xs">
      <div className="flex items-start justify-between gap-3 mb-5">
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-10 h-10 rounded-full bg-[#FF5520]/10 flex items-center justify-center text-[#FF5520] dark:text-[#FF5520] shrink-0">
            <Icon className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <h3 className="text-sm font-extrabold text-[#0A0A0A] dark:text-[#F4F4F6] truncate">{title}</h3>
            {subtitle && <p className="text-[11px] text-gray-500 dark:text-gray-400 font-medium truncate">{subtitle}</p>}
          </div>
        </div>
        {action}
      </div>
      {children}
    </div>
  );
}

export default function ReportsBento() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMINISTRATOR';

  const [dateFrom, setDateFrom] = useState(isoDaysAgo(30));
  const [dateTo, setDateTo] = useState(isoDaysAgo(0));
  const [belowMinStock, setBelowMinStock] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [sales, setSales] = useState(null);
  const [topProducts, setTopProducts] = useState([]);
  const [stock, setStock] = useState([]);
  const [profitability, setProfitability] = useState([]);
  const [purchases, setPurchases] = useState(null);
  const [cashClosing, setCashClosing] = useState(null);
  const [countedCash, setCountedCash] = useState('');

  const rangeParams = useMemo(
    () => [`${dateFrom}T00:00:00`, `${dateTo}T23:59:59`],
    [dateFrom, dateTo]
  );

  const load = async () => {
    setLoading(true);
    setError('');
    const [from, to] = rangeParams;
    try {
      const requests = [getStockReportApi(belowMinStock)];
      if (isAdmin) {
        requests.push(
          getSalesReportApi(from, to),
          getTopProductsReportApi(from, to, 8),
          getProfitabilityReportApi(from, to),
          getPurchasesReportApi(from, to),
          getCashClosingReportApi(from, to)
        );
      }
      const results = await Promise.all(requests);
      setStock(Array.isArray(results[0]) ? results[0] : []);
      if (isAdmin) {
        setSales(results[1] || null);
        setTopProducts(Array.isArray(results[2]) ? results[2] : []);
        setProfitability(Array.isArray(results[3]) ? results[3] : []);
        setPurchases(results[4] || null);
        setCashClosing(results[5] || null);
      }
    } catch (e) {
      setError(e.message || 'No se pudieron cargar los reportes.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [belowMinStock]);

  const sellerEntries = sales
    ? Object.entries(sales.salesBySeller || {}).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value)
    : [];
  const supplierEntries = purchases
    ? Object.entries(purchases.purchasesBySupplier || {}).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value)
    : [];
  const topProductEntries = topProducts.map((p) => ({ label: p.productName, value: p.quantitySold }));
  const profitabilityEntries = profitability.map((p) => ({ label: p.productName, value: Number(p.profitAmount) }));
  const cashBySellerEntries = cashClosing
    ? Object.entries(cashClosing.cashBySeller || {}).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value)
    : [];
  const paymentMethodEntries = cashClosing
    ? Object.entries(cashClosing.totalByPaymentMethod || {}).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value)
    : [];

  const expectedCash = Number(cashClosing?.expectedCash || 0);
  const countedCashNumber = countedCash === '' ? null : Number(countedCash);
  const cashDiff = countedCashNumber === null ? null : countedCashNumber - expectedCash;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#0A0A0A] dark:text-[#F4F4F6]">Reportes</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">
            {isAdmin ? 'Ventas, productos, rentabilidad y compras de tu local' : 'Estado de inventario de tu local'}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {isAdmin && (
            <>
              <input
                type="date"
                value={dateFrom}
                max={dateTo}
                onChange={(e) => setDateFrom(e.target.value)}
                className="px-3 py-2 bg-white dark:bg-[#17171B] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-xs font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
              />
              <span className="text-xs text-gray-400 font-bold">–</span>
              <input
                type="date"
                value={dateTo}
                min={dateFrom}
                max={isoDaysAgo(0)}
                onChange={(e) => setDateTo(e.target.value)}
                className="px-3 py-2 bg-white dark:bg-[#17171B] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-xs font-semibold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
              />
            </>
          )}
          <button
            onClick={load}
            className="px-3.5 py-2 bg-[#0A0A0A] hover:bg-[#202020] text-white rounded-2xl text-xs font-bold flex items-center gap-1.5 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} /> Actualizar
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/30 rounded-2xl text-xs font-bold text-red-600 dark:text-red-400 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {isAdmin && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div className="bento-card p-6 flex items-center justify-between">
            <div>
              <span className="text-xs font-extrabold uppercase text-gray-400 dark:text-gray-500">Total Vendido</span>
              <div className="text-3xl font-black mt-1 text-[#0A0A0A] dark:text-white tabular-nums">{money(sales?.totalSold)}</div>
              <span className="text-[11px] text-gray-500 dark:text-gray-400 font-bold">{sales?.transactionCount ?? 0} transacciones</span>
            </div>
            <div className="p-3 bg-[#FF5520]/10 text-[#FF5520] rounded-2xl border border-[#FF5520]/20"><DollarSign className="w-6 h-6" /></div>
          </div>
          <div className="bento-card p-6 bg-white dark:bg-[#17171B] rounded-3xl border border-[#EAEAEA] dark:border-[#272730] shadow-xs flex items-center justify-between">
            <div>
              <span className="text-xs font-extrabold uppercase text-gray-400 dark:text-gray-500">Total Invertido en Compras</span>
              <div className="text-3xl font-black mt-1 text-[#0A0A0A] dark:text-[#F4F4F6] tabular-nums">{money(purchases?.totalInverted)}</div>
              <span className="text-[11px] text-gray-500 dark:text-gray-400 font-bold">{supplierEntries.length} proveedores en el período</span>
            </div>
            <div className="p-3 bg-[#FF5520]/10 text-[#FF5520] dark:text-[#FF5520] rounded-2xl border border-[#FF5520]/20">
              <ShoppingBag className="w-6 h-6" />
            </div>
          </div>
        </div>
      )}

      {isAdmin && (
        <div className="bento-card p-6 bg-white dark:bg-[#17171B] rounded-3xl border border-[#EAEAEA] dark:border-[#272730] shadow-xs">
          <div className="flex items-center gap-3 mb-5">
            <div className="w-10 h-10 rounded-full bg-[#FF5520]/10 flex items-center justify-center text-[#FF5520] dark:text-[#FF5520] shrink-0">
              <Wallet className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-extrabold text-[#0A0A0A] dark:text-[#F4F4F6]">Cierre de Caja</h3>
              <p className="text-[11px] text-gray-500 dark:text-gray-400 font-medium">
                Efectivo esperado según las ventas en efectivo del período — cuenta el cajón y compáralo aquí
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="space-y-4">
              <div>
                <span className="text-[11px] font-extrabold uppercase text-gray-400 dark:text-gray-500">Efectivo Esperado</span>
                <div className="text-2xl font-black text-[#0A0A0A] dark:text-[#F4F4F6] tabular-nums mt-0.5">{money(expectedCash)}</div>
              </div>

              <div className="space-y-1">
                <label className="text-[11px] font-extrabold uppercase text-gray-400 dark:text-gray-500">Efectivo Contado</label>
                <input
                  type="number"
                  min="0"
                  step="1"
                  value={countedCash}
                  onChange={(e) => setCountedCash(e.target.value)}
                  placeholder="Ingresa el total contado en caja..."
                  className="w-full p-2.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-sm font-bold text-[#0A0A0A] dark:text-[#F4F4F6] focus:outline-none focus:border-[#0A0A0A] dark:focus:border-[#FF5520]"
                />
              </div>

              {cashDiff !== null && (
                <div className={`p-3 rounded-2xl border text-xs font-bold flex items-center gap-2 ${
                  cashDiff === 0
                    ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-600 dark:text-emerald-400'
                    : cashDiff < 0
                    ? 'bg-red-50 dark:bg-red-500/10 border-red-200 dark:border-red-500/30 text-red-600 dark:text-red-400'
                    : 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/30 text-amber-700 dark:text-amber-400'
                }`}>
                  {cashDiff === 0 ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : cashDiff < 0 ? <MinusCircle className="w-4 h-4 shrink-0" /> : <PlusCircle className="w-4 h-4 shrink-0" />}
                  <span>
                    {cashDiff === 0
                      ? 'La caja cuadra exactamente.'
                      : cashDiff < 0
                      ? `Faltan ${money(Math.abs(cashDiff))} en caja.`
                      : `Sobran ${money(cashDiff)} en caja.`}
                  </span>
                </div>
              )}
            </div>

            <div>
              <span className="text-[11px] font-extrabold uppercase text-gray-400 dark:text-gray-500 block mb-3">Efectivo por Cajero</span>
              {loading ? <LoadingRows /> : <RankedList entries={cashBySellerEntries} emptyLabel="Sin ventas en efectivo en este período" />}
            </div>

            <div>
              <span className="text-[11px] font-extrabold uppercase text-gray-400 dark:text-gray-500 block mb-3">Por Método de Pago (referencia)</span>
              {loading ? <LoadingRows /> : <RankedList entries={paymentMethodEntries} />}
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {isAdmin && (
          <SectionCard icon={TrendingUp} title="Ventas por Vendedor" subtitle="Monto vendido en el período seleccionado">
            {loading ? <LoadingRows /> : <RankedList entries={sellerEntries} />}
          </SectionCard>
        )}

        {isAdmin && (
          <SectionCard icon={BarChart3} title="Productos Más Vendidos" subtitle="Unidades vendidas en el período">
            {loading ? <LoadingRows /> : <RankedList entries={topProductEntries} formatValue={(v) => `${v} uds`} />}
          </SectionCard>
        )}

        {isAdmin && (
          <SectionCard icon={DollarSign} title="Rentabilidad por Producto" subtitle="Ganancia bruta = (venta − costo) × unidades">
            {loading ? <LoadingRows /> : <RankedList entries={profitabilityEntries} />}
          </SectionCard>
        )}

        {isAdmin && (
          <SectionCard icon={Truck} title="Compras por Proveedor" subtitle="Monto invertido en el período">
            {loading ? <LoadingRows /> : <RankedList entries={supplierEntries} />}
          </SectionCard>
        )}

        <SectionCard
          icon={Package}
          title="Estado de Inventario"
          subtitle={belowMinStock ? 'Solo productos bajo su stock mínimo' : 'Todos los productos activos'}
          action={
            <label className="flex items-center gap-1.5 text-[11px] font-bold text-gray-500 dark:text-gray-400 cursor-pointer shrink-0">
              <input
                type="checkbox"
                checked={belowMinStock}
                onChange={(e) => setBelowMinStock(e.target.checked)}
                className="accent-[#FF5520]"
              />
              Solo bajo mínimo
            </label>
          }
        >
          {loading ? (
            <LoadingRows />
          ) : stock.length === 0 ? (
            <p className="text-xs text-gray-400 dark:text-gray-500 font-semibold py-6 text-center">
              {belowMinStock ? 'Ningún producto está bajo su stock mínimo 🎉' : 'No hay productos activos'}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#EAEAEA] dark:border-[#272730] text-gray-400 dark:text-gray-500 uppercase tracking-wider font-extrabold text-[10px]">
                    <th className="py-2 px-2">Producto</th>
                    <th className="py-2 px-2 text-center">Stock</th>
                    <th className="py-2 px-2 text-center">Mínimo</th>
                    <th className="py-2 px-2 text-center">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#EAEAEA] dark:divide-[#272730] font-medium">
                  {stock.map((p) => (
                    <tr key={p.productId}>
                      <td className="py-2.5 px-2 font-bold text-[#0A0A0A] dark:text-[#F4F4F6]">{p.productName}</td>
                      <td className="py-2.5 px-2 text-center font-black tabular-nums text-[#0A0A0A] dark:text-[#F4F4F6]">{p.quantityAvailable}</td>
                      <td className="py-2.5 px-2 text-center font-semibold text-gray-400 dark:text-gray-500 tabular-nums">{p.minStock}</td>
                      <td className="py-2.5 px-2 text-center">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-extrabold ${
                          p.status === 'OK'
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                            : 'bg-amber-100 dark:bg-amber-500/20 text-amber-800 dark:text-amber-400 border border-amber-300 dark:border-amber-500/30'
                        }`}>
                          {p.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>
      </div>
    </div>
  );
}

function LoadingRows() {
  return (
    <div className="space-y-3.5 animate-pulse">
      {[0, 1, 2].map((i) => (
        <div key={i} className="space-y-1.5">
          <div className="h-3 w-2/3 bg-gray-200 dark:bg-gray-700 rounded-full" />
          <div className="h-2.5 w-full bg-gray-100 dark:bg-gray-800 rounded-full" />
        </div>
      ))}
    </div>
  );
}
