import React, { useMemo, useRef, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button, Input, Modal, Select } from '../ui';
import ProductPicker from './ProductPicker';
import { createInventoryMovementApi, createPurchaseApi } from '../../services/inventoryApi';
import { newIdempotencyKey } from '../../services/http';
import { money } from '../../utils/format';
import { OPERATIONS, noteMaxLength, planMovement, planPurchase, reasonsFor } from './movementReasons';

const FORM_ID = 'stock-movement-form';
const ERROR_BOX = 'p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold';
const WARNING_BOX = 'p-3 bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/30 rounded-xl text-xs font-bold flex items-start gap-2';

const SUBMIT_LABELS = {
  PURCHASE: 'Registrar compra',
  ENTRY: 'Registrar entrada',
  EXIT: 'Registrar salida',
  COUNT: 'Ajustar inventario',
};

const HELP = {
  PURCHASE: 'La mercancía llega de un proveedor. Cuenta en el reporte de compras.',
  ENTRY: 'Sube el stock sin proveedor: inventario inicial, mercancía que ya tenías o una bonificación.',
  EXIT: 'Baja el stock: vencimiento, daño, pérdida, consumo propio o devolución a un proveedor.',
  COUNT: 'Escribe lo que contaste en el estante y el sistema ajusta la diferencia.',
};

const onlyDigits = (value) => value.replace(/\D/g, '').slice(0, 7);
const signed = (n) => `${n > 0 ? '+' : ''}${n.toLocaleString('es-CO')}`;

/**
 * Modal "Mover stock": compra a proveedor, entrada y salida sin proveedor, y conteo físico de un producto.
 * `product` (opcional) llega preseleccionado desde la fila de la tabla; `onDone` se llama tras registrar.
 */
export default function StockMovementModal({ product: initialProduct = null, suppliers = [], onClose, onDone, onGoToSuppliers }) {
  // El producto vive en el estado: es la foto del stock al abrir o elegir. La lista de Inventario se refresca sola
  // cada 15 s y no debe mover el "Sistema: X" del conteo mientras se escribe. El movimiento es relativo (suma o
  // resta la diferencia), así que una venta posterior se descuenta del conteo en vez de pisarse.
  const [product, setProduct] = useState(initialProduct);
  const [operation, setOperation] = useState(suppliers.length > 0 ? 'PURCHASE' : 'ENTRY');
  const [supplierId, setSupplierId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [counted, setCounted] = useState('');
  const [reasonCode, setReasonCode] = useState('');
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  // Candado síncrono: el estado `submitting` llega al siguiente render; este evita un segundo envío en el mismo tick.
  const submittingRef = useRef(false);
  // Clave de idempotencia de la compra: se reutiliza si se reintenta lo mismo y cambia si cambia la compra.
  const purchaseKeyRef = useRef({ signature: '', key: '' });

  const currentStock = product?.quantityAvailable ?? 0;
  const unitCost = Number(product?.purchasePrice) || 0;
  const reasons = reasonsFor(operation);
  const reason = reasons?.find((r) => r.code === reasonCode) ?? null;

  const plan = useMemo(
    () => (operation === 'PURCHASE'
      ? planPurchase({ supplierId, quantity })
      : planMovement({ operation, quantity, counted, reasonCode, note, currentStock })),
    [operation, supplierId, quantity, counted, reasonCode, note, currentStock]
  );

  const typed = operation === 'COUNT' ? counted !== '' : quantity !== '';
  const canSubmit = Boolean(product) && plan.ok && !submitting;

  const changeOperation = (id) => {
    if (id === operation) return;
    setOperation(id);
    setReasonCode('');
    setNote('');
    setError('');
  };

  const changeProduct = (next) => {
    setProduct(next);
    setCounted('');
    setError('');
  };

  const purchaseKey = (signature) => {
    if (purchaseKeyRef.current.signature !== signature) {
      purchaseKeyRef.current = { signature, key: newIdempotencyKey() };
    }
    return purchaseKeyRef.current.key;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (submittingRef.current || !canSubmit) return;
    submittingRef.current = true;
    setSubmitting(true);
    setError('');
    try {
      if (operation === 'PURCHASE') {
        await createPurchaseApi(
          { supplierId: Number(supplierId), items: [{ productId: product.id, quantity: plan.quantity, unitCost }] },
          purchaseKey(`${product.id}|${supplierId}|${plan.quantity}|${unitCost}`)
        );
      } else {
        await createInventoryMovementApi(product.id, plan.movementType, plan.quantity, plan.reason);
      }
    } catch (err) {
      setError(err.message || 'No se pudo registrar el movimiento.');
      submittingRef.current = false;
      setSubmitting(false);
      return;
    }
    // Ya quedó registrado: cerrar y refrescar, sin volver a habilitar el botón.
    onClose();
    onDone?.();
  };

  return (
    <Modal
      title="Mover stock"
      subtitle="Registra lo que entra o sale de tu inventario"
      onClose={onClose}
      dismissible={false}
      footer={(
        <>
          <Button variant="soft" onClick={onClose} disabled={submitting}>Cancelar</Button>
          <Button type="submit" form={FORM_ID} loading={submitting} disabled={!canSubmit}>{SUBMIT_LABELS[operation]}</Button>
        </>
      )}
    >
      <form id={FORM_ID} onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-1.5">
          <span className="block text-[11px] font-bold uppercase tracking-wider text-ink-soft">Producto</span>
          <ProductPicker value={product} onChange={changeProduct} />
        </div>

        <div role="tablist" aria-label="Operación" className="grid grid-cols-2 sm:grid-cols-4 gap-1 bg-gray-100 dark:bg-line rounded-2xl p-1">
          {OPERATIONS.map((op) => (
            <button
              key={op.id}
              type="button"
              role="tab"
              aria-selected={operation === op.id}
              onClick={() => changeOperation(op.id)}
              className={`px-2 py-2 rounded-xl text-[11px] font-bold cursor-pointer transition-all ${
                operation === op.id ? 'bg-solid text-white shadow-xs' : 'text-ink-soft hover:text-ink'
              }`}
            >
              {op.label}
            </button>
          ))}
        </div>
        <p className="text-[11px] text-ink-muted -mt-2">{HELP[operation]}</p>

        {operation === 'PURCHASE' && (
          <>
            {suppliers.length === 0 && (
              <div className={WARNING_BOX}>
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>
                  Aún no tienes proveedores registrados.{' '}
                  <button type="button" onClick={onGoToSuppliers} className="underline font-bold cursor-pointer">Crea uno aquí</button>
                  {' '}antes de registrar una compra, o usa Entrada para cargar stock sin proveedor.
                </span>
              </div>
            )}
            <Select
              label="Proveedor"
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
              disabled={suppliers.length === 0}
            >
              <option value="" disabled>Selecciona un proveedor...</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.companyName}</option>)}
            </Select>
          </>
        )}

        {operation === 'COUNT' ? (
          <>
            <Input
              label="Cantidad contada"
              inputMode="numeric"
              value={counted}
              onChange={(e) => setCounted(onlyDigits(e.target.value))}
              placeholder="Lo que hay en el estante"
              disabled={!product}
            />
            {product && plan.diff !== undefined && (
              <div className="p-3 bg-page border border-line rounded-xl text-xs font-semibold text-ink-soft space-y-1">
                <p>
                  Sistema: <span className="font-black text-ink tabular-nums">{currentStock.toLocaleString('es-CO')}</span>
                  {' → '}Contado: <span className="font-black text-ink tabular-nums">{Number(counted).toLocaleString('es-CO')}</span>
                  {' '}(diferencia <span className="font-black text-ink tabular-nums">{signed(plan.diff)}</span>)
                </p>
                <p className="text-[11px] text-ink-muted">Si se vende algo mientras tanto, esa venta se descuenta del conteo.</p>
              </div>
            )}
            <Input
              label="Nota (opcional)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={150}
              placeholder="Ej. Conteo de fin de mes"
            />
          </>
        ) : (
          <>
            <Input
              label={operation === 'PURCHASE' ? 'Cantidad comprada' : 'Cantidad'}
              inputMode="numeric"
              value={quantity}
              onChange={(e) => setQuantity(onlyDigits(e.target.value))}
              placeholder="Ej. 10, 25, 50..."
              disabled={!product}
            />
            {reasons && (
              <>
                <Select label="Motivo" value={reasonCode} onChange={(e) => setReasonCode(e.target.value)} disabled={!product}>
                  <option value="" disabled>Elige un motivo...</option>
                  {reasons.map((r) => <option key={r.code} value={r.code}>{r.label}</option>)}
                </Select>
                <Input
                  label={reason?.requiresNote ? 'Nota' : 'Nota (opcional)'}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={reason ? noteMaxLength(reason) : 200}
                  placeholder={reason?.requiresNote ? 'Cuéntanos qué pasó' : 'Detalle adicional'}
                />
              </>
            )}
            {operation === 'PURCHASE' && product && (
              <p className="text-[11px] text-ink-muted">
                {unitCost > 0
                  ? <>Costo unitario: <span className="font-bold text-ink-soft">{money(unitCost)}</span> (el costo actual del producto).</>
                  : 'Este producto no tiene costo de compra registrado: la compra se guarda con costo $0.'}
              </p>
            )}
            {product && plan.ok && (
              <p className="text-[11px] font-semibold text-ink-soft">
                Stock: {currentStock.toLocaleString('es-CO')} →{' '}
                <span className="font-black text-ink tabular-nums">
                  {(operation === 'EXIT' ? currentStock - plan.quantity : currentStock + plan.quantity).toLocaleString('es-CO')}
                </span>
              </p>
            )}
          </>
        )}

        {product && typed && !plan.ok && (
          <p className="text-[11px] font-semibold text-amber-600 dark:text-amber-400">{plan.error}</p>
        )}
        {error && <div className={ERROR_BOX}>{error}</div>}
      </form>
    </Modal>
  );
}
