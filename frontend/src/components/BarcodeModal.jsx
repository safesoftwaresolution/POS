import React, { useEffect, useRef } from 'react';
import JsBarcode from 'jsbarcode';
import { X, Printer } from 'lucide-react';

export default function BarcodeModal({ product, onClose }) {
  const svgRef = useRef(null);
  const hasBarcode = Boolean(product?.barcode);

  useEffect(() => {
    if (svgRef.current && hasBarcode) {
      JsBarcode(svgRef.current, product.barcode, {
        format: 'CODE128',
        lineColor: '#111c2d',
        width: 2.2,
        height: 90,
        displayValue: true,
        font: 'monospace',
        fontSize: 15,
        textMargin: 8,
        margin: 14,
        background: '#ffffff',
      });
    }
  }, [product, hasBarcode]);

  if (!product) return null;

  return (
    <div className="fixed inset-0 bg-navy-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div className="bento-card max-w-sm w-full bg-white dark:bg-[#161b22] border border-gray-100 dark:border-[#262f38] p-6 rounded-3xl shadow-2xl space-y-4">
        <div className="flex items-center justify-between border-b border-gray-100 dark:border-gray-800 pb-3">
          <div className="min-w-0">
            <h3 className="font-extrabold text-base text-[#191c1e] dark:text-white truncate">{product.name}</h3>
            <p className="text-[11px] text-gray-500 dark:text-gray-400 font-mono">{product.internalCode}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 shrink-0 cursor-pointer">
            <X className="w-5 h-5" />
          </button>
        </div>

        {hasBarcode ? (
          <div id="barcode-print-area" className="bg-[#101827] rounded-2xl p-4 space-y-3">
            <span className="text-[10px] font-extrabold uppercase tracking-widest text-gray-400">
              Código de Barras (Code 128)
            </span>
            <div className="bg-white rounded-xl p-4 flex items-center justify-center overflow-x-auto">
              <svg ref={svgRef}></svg>
            </div>
          </div>
        ) : (
          <div className="py-8 text-center bg-gray-50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400">Este producto no tiene código de barras asignado</p>
          </div>
        )}

        <div className="flex gap-2 pt-1">
          <button
            onClick={onClose}
            className="w-1/2 py-2.5 bg-gray-100 dark:bg-[#262f38] hover:bg-gray-200 dark:hover:bg-[#323d48] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer transition-colors"
          >
            Cerrar
          </button>
          <button
            onClick={() => window.print()}
            disabled={!hasBarcode}
            className={`w-1/2 py-2.5 rounded-2xl text-xs font-extrabold flex items-center justify-center gap-2 transition-all ${
              hasBarcode
                ? 'bg-[#067A57] hover:bg-[#055C43] text-white cursor-pointer shadow-lg shadow-[#067A57]/20'
                : 'bg-gray-200 dark:bg-gray-700 text-gray-400 dark:text-gray-500 cursor-not-allowed'
            }`}
          >
            <Printer className="w-4 h-4" /> Imprimir
          </button>
        </div>
      </div>
    </div>
  );
}
