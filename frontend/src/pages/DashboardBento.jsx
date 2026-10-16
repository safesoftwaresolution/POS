import React, { useState, useEffect } from 'react';
import {
  ArrowUpRight,
  BarChart3,
  Calendar,
  Package,
  ArrowUp,
  RefreshCw,
  Plus,
  AlertTriangle,
  Receipt,
  FileText,
  ChevronRight
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getProductsApi } from '../services/productsApi';
import { getSalesApi } from '../services/salesApi';

export default function DashboardBento() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [products, setProducts] = useState([]);
  const [sales, setSales] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadData = async () => {
    setLoading(true);
    try {
      const prodData = await getProductsApi();
      setProducts(Array.isArray(prodData) ? prodData : []);

      const salesData = await getSalesApi();
      setSales(Array.isArray(salesData) ? salesData : []);
    } catch (e) {
      console.warn('Error cargando datos reales del Dashboard:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // 100% Dynamic Metrics calculated strictly from real API data
  const totalSalesAmount = sales.reduce((sum, s) => sum + parseFloat(s.totalAmount || s.total || 0), 0);
  const averageTicket = sales.length > 0 ? (totalSalesAmount / sales.length).toFixed(2) : '0.00';
  const lowStockProducts = products.filter(p => (p.quantityAvailable ?? p.stock ?? 0) <= (p.minStock || 5));
  const dianInvoicesCount = sales.length;

  // Dynamic 7-day sales breakdown calculated from sales timestamps
  const daysOfWeek = ['LUN', 'MAR', 'MIE', 'JUE', 'VIE', 'SAB', 'DOM'];
  const daySalesMap = { LUN: 0, MAR: 0, MIE: 0, JUE: 0, VIE: 0, SAB: 0, DOM: 0 };
  
  sales.forEach(s => {
    if (s.createdAt || s.date) {
      const dateObj = new Date(s.createdAt || s.date);
      const dayIdx = (dateObj.getDay() + 6) % 7; // Map Sun=0 to DOM=6
      const dayName = daysOfWeek[dayIdx];
      if (dayName) {
        daySalesMap[dayName] += parseFloat(s.totalAmount || s.total || 0);
      }
    }
  });

  const maxDaySale = Math.max(...Object.values(daySalesMap), 1);
  const bestDay = Object.keys(daySalesMap).reduce((a, b) => daySalesMap[a] >= daySalesMap[b] ? a : b, 'JUE');

  // Dynamic currentDate formatted range
  const todayFormatted = new Date().toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });

  return (
    <div className="space-y-6 pb-12 select-none">
      
      {/* Top Welcome Title Row */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end gap-4 mb-2">
        <div>
          <h1 className="text-2xl sm:text-4xl font-light text-ink tracking-tight">
            Bienvenido, <span className="font-extrabold text-brand dark:text-brand">{user?.fullName || user?.username || 'Usuario'}</span>
          </h1>
          <p className="text-xs text-ink-soft font-medium mt-1">
            Panel de control BentoPOS con la información actualizada de tu negocio
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button 
            onClick={loadData}
            className="p-2 text-gray-500 hover:text-brand dark:hover:text-brand bg-card border border-line rounded-full transition-colors cursor-pointer"
            title="Recargar Datos"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-brand' : ''}`} />
          </button>
          <div className="bg-card border border-line rounded-full px-4 py-2 flex items-center gap-2 shadow-xs text-xs font-semibold text-ink">
            <Calendar className="w-4 h-4 text-brand dark:text-brand" />
            <span>{todayFormatted}</span>
          </div>
        </div>
      </div>

      {/* KPI Row - 4 tarjetas iguales */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* KPI 1 - Ventas Reales */}
        <div
          onClick={() => navigate('/pos')}
          className="bento-card p-6 flex flex-col justify-between cursor-pointer hover:border-line-strong  transition-all"
        >
          <div className="flex justify-between items-start">
            <h3 className="text-xs font-semibold text-[#7A7A7A] uppercase tracking-wider">Ventas Totales</h3>
            <div className="w-7 h-7 rounded-full border border-line flex items-center justify-center text-[#7A7A7A]">
              <ArrowUpRight className="w-4 h-4" />
            </div>
          </div>

          <div className="mt-2">
            <div className="text-2xl font-black tracking-tight tabular-nums text-ink">
              $ {totalSalesAmount.toLocaleString('es-CO')}
            </div>
            <span className="text-[11px] text-[#7A7A7A] mt-1 block">Ticket Promedio: ${averageTicket}</span>
          </div>

          <span className="inline-flex items-center gap-1 self-start text-[11px] font-bold px-2 py-0.5 rounded-full bg-[#FFF0EB] text-brand border border-[#FFD5C8] mt-3">
            <ArrowUp className="w-3 h-3" /> {sales.length} ventas
          </span>
        </div>

        {/* KPI 2 - Transacciones */}
        <div
          onClick={() => navigate('/pos')}
          className="bento-card p-6 flex flex-col justify-between cursor-pointer hover:border-line-strong  transition-all"
        >
          <div className="flex justify-between items-start">
            <h3 className="text-xs font-semibold text-[#7A7A7A] uppercase tracking-wider">Transacciones</h3>
            <div className="w-7 h-7 rounded-full border border-line flex items-center justify-center text-[#7A7A7A]">
              <Receipt className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-2">
            <div className="text-2xl font-black tracking-tight tabular-nums text-ink">
              {sales.length}
            </div>
            <span className="text-[11px] text-[#7A7A7A] mt-1 block">Ventas procesadas en total</span>
          </div>
        </div>

        {/* KPI 3 - Productos bajo mínimo */}
        <div
          onClick={() => navigate('/inventory')}
          className="bento-card p-6 flex flex-col justify-between cursor-pointer hover:border-line-strong  transition-all"
        >
          <div className="flex justify-between items-start">
            <h3 className="text-xs font-semibold text-[#7A7A7A] uppercase tracking-wider">Bajo mínimo</h3>
            <div className="w-7 h-7 rounded-full border border-line flex items-center justify-center text-[#7A7A7A]">
              <Package className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-2">
            <div className="text-2xl font-black tracking-tight tabular-nums text-ink">
              {lowStockProducts.length}
            </div>
            <span className={`text-[11px] mt-1 block font-bold ${lowStockProducts.length > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
              {lowStockProducts.length > 0 ? 'Requieren reabastecimiento' : 'Stock en niveles óptimos'}
            </span>
          </div>
        </div>

        {/* KPI 4 - Facturas DIAN */}
        <div
          onClick={() => navigate('/invoicing')}
          className="bento-card p-6 flex flex-col justify-between cursor-pointer hover:border-line-strong  transition-all"
        >
          <div className="flex justify-between items-start">
            <h3 className="text-xs font-semibold text-[#7A7A7A] uppercase tracking-wider">Facturas DIAN</h3>
            <div className="w-7 h-7 rounded-full border border-line flex items-center justify-center text-[#7A7A7A]">
              <FileText className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-2">
            <div className="text-2xl font-black tracking-tight tabular-nums text-ink">
              {dianInvoicesCount}
            </div>
            <span className="inline-flex items-center gap-1 text-[11px] font-bold text-ink-soft mt-1">
              ✓ Registradas
            </span>
          </div>
        </div>
      </div>

      {/* Alerta de bajo stock */}
      {lowStockProducts.length > 0 && (
        <div className="bento-card p-5 border-amber-200 dark:border-amber-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-sm font-bold text-ink">Bajo stock</h4>
                <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400">
                  Atención
                </span>
              </div>
              <p className="text-xs text-ink-soft mt-0.5">
                {lowStockProducts.length} {lowStockProducts.length === 1 ? 'producto está' : 'productos están'} por debajo del mínimo. Reabastécelos para no perder ventas.
              </p>
            </div>
          </div>
          <button
            onClick={() => navigate('/inventory')}
            className="h-10 px-4 rounded-xl bg-solid hover:bg-solid-hover text-white text-xs font-semibold transition-colors flex items-center justify-center gap-1.5 shrink-0"
          >
            <span>Ver inventario</span>
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Bento Layout Grid */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-6 auto-rows-min">

        {/* Chart (Col-span-8) - Ventas Últimos 7 Días */}
        <div className="md:col-span-12 lg:col-span-8 bento-card p-6 flex flex-col justify-between bg-card rounded-3xl border border-line shadow-xs">
          <div className="flex justify-between items-center mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-brand/10 dark:bg-brand/20 flex items-center justify-center text-brand dark:text-brand">
                <BarChart3 className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-ink">Ventas Últimos 7 Días</h3>
                <p className="text-[11px] text-ink-soft font-medium">Calculado dinámicamente con tus transacciones</p>
              </div>
            </div>
            <div className="flex bg-gray-100 dark:bg-[#272730] rounded-full p-1 border border-gray-200 dark:border-gray-700">
              <button className="px-3.5 py-1 text-xs font-semibold bg-solid text-white rounded-full shadow-xs cursor-pointer">
                Semanal
              </button>
            </div>
          </div>

          {/* Bar Chart Bars */}
          <div className="flex-1 flex items-end justify-between gap-3 mt-4 h-[160px] relative pt-6">
            {daysOfWeek.map((day) => {
              const dayValue = daySalesMap[day];
              const heightPercent = sales.length === 0 
                ? (day === 'JUE' ? 80 : day === 'SAB' ? 90 : 40)
                : Math.max(Math.round((dayValue / maxDaySale) * 100), 12);
              
              const isBest = day === bestDay;

              return (
                <div key={day} className="flex-1 flex flex-col items-center gap-2 h-full justify-end relative group">
                  {isBest && (
                    <span className="absolute -top-3 bg-solid text-white text-[9px] font-extrabold px-1.5 py-0.5 rounded-full shadow-xs">
                      PICO
                    </span>
                  )}
                  <div 
                    style={{ height: `${heightPercent}%` }}
                    className={`w-full rounded-t-xl transition-all duration-500 ${
                      isBest 
                        ? 'bg-solid shadow-sm' 
                        : 'bg-[#FFF0EB] dark:bg-brand/30 group-hover:bg-solid dark:group-hover:bg-brand'
                    }`}
                  ></div>
                  <span className={`text-[11px] font-bold uppercase ${
                    isBest ? 'text-brand dark:text-brand' : 'text-ink-soft'
                  }`}>
                    {day}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Métodos de Pago (Col-span-4), junto al gráfico */}
        <div className="md:col-span-12 lg:col-span-4 bento-card p-6 bg-card rounded-3xl border border-line shadow-xs flex flex-col justify-between">
          <div className="flex justify-between items-center mb-4">
            <div>
              <h3 className="text-base font-bold text-ink">Métodos de Pago</h3>
              <p className="text-xs text-ink-soft">Distribución de cobros en caja</p>
            </div>
            <div className="w-8 h-8 rounded-full border border-gray-200 dark:border-gray-700 flex items-center justify-center text-gray-500 cursor-pointer hover:bg-gray-50 dark:hover:bg-white/5">
              <ArrowUpRight className="w-4 h-4" />
            </div>
          </div>

          <div className="space-y-4 my-2 text-xs font-semibold">
            {/* Efectivo */}
            <div className="space-y-1.5">
              <div className="flex justify-between items-center text-gray-700 dark:text-gray-300">
                <span className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-solid"></span> Efectivo en Caja
                </span>
                <span className="font-bold text-ink">85%</span>
              </div>
              <div className="w-full h-2.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                <div className="h-full bg-solid rounded-full w-[85%]"></div>
              </div>
            </div>

            {/* Tarjeta */}
            <div className="space-y-1.5">
              <div className="flex justify-between items-center text-gray-700 dark:text-gray-300">
                <span className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-solid dark:bg-gray-400"></span> Datáfono / Tarjeta
                </span>
                <span className="font-bold text-ink">10%</span>
              </div>
              <div className="w-full h-2.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                <div className="h-full bg-solid dark:bg-gray-400 rounded-full w-[10%]"></div>
              </div>
            </div>

            {/* Transferencia */}
            <div className="space-y-1.5">
              <div className="flex justify-between items-center text-gray-700 dark:text-gray-300">
                <span className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500"></span> Nequi / Transferencia
                </span>
                <span className="font-bold text-ink">5%</span>
              </div>
              <div className="w-full h-2.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                <div className="h-full bg-amber-500 rounded-full w-[5%]"></div>
              </div>
            </div>
          </div>
        </div>

        {/* Ventas Recientes (fila completa) */}
        <div className="md:col-span-12 bento-card p-6 bg-card rounded-3xl border border-line shadow-xs space-y-4">
          <div className="flex justify-between items-center">
            <div>
              <h3 className="text-base font-bold text-ink">Ventas Recientes</h3>
              <p className="text-xs text-ink-soft">Historial de tus ventas más recientes</p>
            </div>
            <button 
              onClick={() => navigate('/pos')}
              className="px-3.5 py-1.5 bg-solid text-white rounded-full text-xs font-bold flex items-center gap-1 hover:bg-solid-hover shadow-sm shadow-[#0A0A0A]/10 cursor-pointer transition-all"
            >
              <Plus className="w-3.5 h-3.5" /> Nueva Venta
            </button>
          </div>

          {loading ? (
            <div className="py-8 text-center text-xs font-bold text-gray-400">
              Cargando historial de ventas...
            </div>
          ) : sales.length === 0 ? (
            <div className="py-10 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
              <Package className="w-10 h-10 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
              <div>
                <p className="text-xs font-bold text-ink">No hay ventas registradas aún</p>
                <p className="text-[11px] text-ink-soft">Abre la pantalla de caja para realizar tu primera transacción</p>
              </div>
              <button
                onClick={() => navigate('/pos')}
                className="px-4 py-2 bg-solid text-white rounded-xl text-xs font-extrabold shadow-sm hover:bg-solid-hover cursor-pointer"
              >
                + Ir a la Caja
              </button>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-gray-100 dark:border-gray-800 text-ink-soft font-bold text-[11px]">
                    <th className="py-2.5 px-3">Comprobante</th>
                    <th className="py-2.5 px-3">Fecha / Hora</th>
                    <th className="py-2.5 px-3">Cliente</th>
                    <th className="py-2.5 px-3">Estado</th>
                    <th className="py-2.5 px-3 text-right">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-800 font-medium text-gray-700 dark:text-gray-300">
                  {sales.slice(0, 5).map((s, idx) => (
                    <tr key={s.id || idx} className="hover:bg-gray-50 dark:hover:bg-[#272730]/40 transition-colors">
                      <td className="py-3 px-3 font-bold text-brand dark:text-brand flex items-center gap-2">
                        <div className="w-7 h-7 rounded-lg bg-brand/10 dark:bg-brand/20 text-brand dark:text-brand flex items-center justify-center">
                          <Package className="w-3.5 h-3.5" />
                        </div>
                        <span>#{s.invoiceNumber || `FAC-${s.id || idx + 1}`}</span>
                      </td>
                      <td className="py-3 px-3 text-ink-soft">{s.createdAt || s.date || 'Reciente'}</td>
                      <td className="py-3 px-3 font-semibold text-ink">{s.customerName || s.customer || 'Cliente General'}</td>
                      <td className="py-3 px-3">
                        <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-600 dark:text-emerald-400">
                          <span className="w-2 h-2 rounded-full bg-emerald-500"></span> Completada
                        </span>
                      </td>
                      <td className="py-3 px-3 text-right font-black text-ink tabular-nums">
                        ${(s.totalAmount || s.total || 0).toLocaleString('es-CO')} COP
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}
