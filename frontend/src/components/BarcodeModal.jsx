import React, { useEffect, useMemo, useRef, useState } from 'react';
import JsBarcode from 'jsbarcode';
import { X, Printer } from 'lucide-react';

const MAX_LABELS = 200;

export default function BarcodeModal({ product, onClose }) {
  const svgRef = useRef(null);
  const [quantity, setQuantity] = useState(12);
  const hasBarcode = Boolean(product?.barcode);

  useEffect(() => {
    if (svgRef.current && hasBarcode) {
      JsBarcode(svgRef.current, product.barcode, {
        format: 'CODE128',
        lineColor: '#17171B',
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

  const labelSrc = useMemo(() => {
    if (!hasBarcode) return null;
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    JsBarcode(svg, product.barcode, {
      format: 'CODE128',
      lineColor: '#000000',
      width: 1.6,
      height: 50,
      displayValue: true,
      font: 'monospace',
      fontSize: 12,
      textMargin: 4,
      margin: 6,
      background: '#ffffff',
    });
    return `data:image/svg+xml;utf8,${encodeURIComponent(new XMLSerializer().serializeToString(svg))}`;
  }, [product, hasBarcode]);

  if (!product) return null;

  const count = Math.min(Math.max(parseInt(quantity, 10) || 1, 1), MAX_LABELS);

  return (
    <div className="fixed inset-0 bg-navy-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div className="bento-card max-w-sm w-full bg-card border border-line p-6 rounded-3xl shadow-2xl space-y-4">
        <div className="flex items-center justify-between border-b border-gray-100 dark:border-gray-800 pb-3">
          <div className="min-w-0">
            <h3 className="font-extrabold text-base text-[#17171B] dark:text-white truncate">{product.name}</h3>
            <p className="text-[11px] text-ink-soft font-mono">{product.internalCode}</p>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-gray-600 dark:hover:text-gray-300 shrink-0 cursor-pointer">
            <X className="w-5 h-5" />
          </button>
        </div>

        {hasBarcode ? (
          <>
            <div className="bg-[#0E0E12] rounded-2xl p-4 space-y-3">
              <span className="text-[10px] font-extrabold uppercase tracking-widest text-gray-400">
                Código de Barras (Code 128)
              </span>
              <div className="bg-white rounded-xl p-4 flex items-center justify-center overflow-x-auto">
                <svg ref={svgRef}></svg>
              </div>
            </div>
            <label className="flex items-center justify-between gap-3 text-xs font-bold text-ink-soft">
              Etiquetas por hoja
              <input
                type="number"
                min="1"
                max={MAX_LABELS}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-24 px-3 py-2 rounded-xl border border-line bg-card text-ink text-right"
              />
            </label>
            <div id="barcode-print-sheet" aria-hidden="true">
              {Array.from({ length: count }, (_, i) => (
                <div key={i} className="barcode-label">
                  <img src={labelSrc} alt="" />
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="py-8 text-center bg-gray-50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <p className="text-xs font-bold text-ink-soft">Este producto no tiene código de barras asignado</p>
          </div>
        )}

        <div className="flex gap-2 pt-1">
          <button
            onClick={onClose}
            className="w-1/2 py-2.5 bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer transition-colors"
          >
            Cerrar
          </button>
          <button
            onClick={() => window.print()}
            disabled={!hasBarcode}
            className={`w-1/2 py-2.5 rounded-2xl text-xs font-extrabold flex items-center justify-center gap-2 transition-all ${
              hasBarcode
                ? 'bg-solid hover:bg-solid-hover text-solid-ink cursor-pointer shadow-lg shadow-[#0A0A0A]/10'
                : 'bg-gray-200 dark:bg-gray-700 text-ink-muted cursor-not-allowed'
            }`}
          >
            <Printer className="w-4 h-4" /> Imprimir
          </button>
        </div>
      </div>
    </div>
  );
}
