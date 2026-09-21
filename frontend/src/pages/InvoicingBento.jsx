import React, { useState } from 'react';
import { FileCheck2, RefreshCw, ShieldCheck, Download } from 'lucide-react';

export default function InvoicingBento() {
  const [invoices] = useState([]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#191c1e] dark:text-white">Facturación Bento DIAN (Factus Integration)</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Monitoreo en vivo de facturas electrónicas emitidas mediante la API de Factus</p>
        </div>

        <div className="flex items-center gap-2">
          <span className="px-3 py-1.5 rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-extrabold text-xs border border-emerald-500/20 flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-emerald-500" /> Factus API Conectado
          </span>
        </div>
      </div>

      <div className="bento-card p-6 space-y-4">
        <div className="flex items-center justify-between border-b border-[#e2e8f0] dark:border-[#262f38] pb-3">
          <h3 className="font-extrabold text-sm text-[#161b22] dark:text-[#f0f6fc]">Monitoreo de Emisión DIAN</h3>
          <button className="text-xs font-bold text-[#067A57] dark:text-[#00D47E] flex items-center gap-1 hover:underline cursor-pointer">
            <RefreshCw className="w-3.5 h-3.5" /> Sincronizar Estado
          </button>
        </div>

        {invoices.length === 0 ? (
          <div className="py-12 text-center space-y-2 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <FileCheck2 className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay comprobantes de factura electrónica generados aún</p>
            <p className="text-[11px] text-gray-400 dark:text-gray-500">
              Al realizar una venta en la caja seleccionando un cliente con NIT/Cédula y marcando transmitir DIAN, la factura aparecerá aquí.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-gray-200 dark:border-gray-800 text-gray-400 dark:text-gray-500 uppercase tracking-wider font-extrabold text-[10px]">
                  <th className="py-3 px-3">Comprobante</th>
                  <th className="py-3 px-3">CUFE</th>
                  <th className="py-3 px-3">Cliente</th>
                  <th className="py-3 px-3 text-right">Monto</th>
                  <th className="py-3 px-3 text-center">Estado DIAN</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800 font-medium">
                {/* Real invoices list */}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
