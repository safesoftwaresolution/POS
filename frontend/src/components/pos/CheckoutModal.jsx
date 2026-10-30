import React, { useState } from 'react';
import MoneyInput from '../ui/MoneyInput';
import {
  X,
  CreditCard,
  Banknote,
  Smartphone,
  Landmark,
  Wallet,
  CheckCircle2,
  Printer,
  ShoppingBag,
  FileText,
  UserPlus,
  Mail,
  Download,
  Copy,
  Check,
  ExternalLink,
  ShieldCheck,
  ShieldAlert,
  AlertCircle,
  RefreshCw,
  QrCode,
  HandCoins
} from 'lucide-react';
import CreateDianCustomerModal from '../customers/CreateDianCustomerModal';
import {
  getSaleInvoiceApi,
  sendInvoiceEmailApi,
  downloadInvoicePdfApi,
  downloadInvoiceXmlApi
} from '../../services/billingApi';
import { deliverBlob } from '../../utils/downloadBlob';
import useModal from '../../hooks/useModal';
import useLoad from '../../hooks/useLoad';
import CreditPanel from './CreditPanel';
import { getCreditAccountApi } from '../../services/creditApi';
import { creditBlockReason, isGeneralCustomer } from '../../utils/credit';

const money = (value) => `$${Number(value || 0).toLocaleString('es-CO')}`;

// Ícono por código del catálogo PAYMENT_METHODS; los códigos nuevos usan el ícono genérico.
const PAYMENT_ICONS = { CASH: Banknote, CARD: CreditCard, NEQUI: Smartphone, TRANSFER: Landmark, CREDIT: HandCoins };

const INVOICE_BADGE = {
  VALIDATED: { label: 'Validada por la DIAN', cls: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20' },
  PENDING: { label: 'Pendiente de la DIAN', cls: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20' },
  ERROR: { label: 'Con error', cls: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20' },
  REJECTED: { label: 'Rechazada por la DIAN', cls: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20' }
};

const SECTION_LABEL = 'text-[10px] font-extrabold uppercase tracking-wider text-ink-muted';

// ==================== Paso 1: cobro ====================

export default function CheckoutModal({
  total,
  itemCount,
  customers,
  selectedCustomer,
  onSelectCustomer,
  onCustomerCreated,
  paymentMethods,
  selectedPaymentMethod,
  onSelectPaymentMethod,
  issueDian,
  onToggleDian,
  cashTendered,
  onCashTenderedChange,
  creditDueDate,
  onCreditDueDateChange,
  isSubmitting,
  onConfirm,
  onClose
}) {
  const [showCreateCustomer, setShowCreateCustomer] = useState(false);
  const isCash = selectedPaymentMethod === 'CASH';
  const cashNumber = parseFloat(cashTendered) || 0;
  const change = cashNumber - total;
  const isCredit = selectedPaymentMethod === 'CREDIT';
  const creditCustomerId = isCredit && !isGeneralCustomer(selectedCustomer) ? selectedCustomer.id : null;
  const { data: creditAccount } = useLoad(
    () => (creditCustomerId ? getCreditAccountApi(creditCustomerId) : Promise.resolve(null)),
    [creditCustomerId],
    { initial: null },
  );
  const creditBlock = isCredit ? creditBlockReason(selectedCustomer, creditAccount, total) : null;
  const blocked = isSubmitting || creditBlock !== null || (isCash ? change < 0 || cashNumber <= 0 : false);

  return (
    <>
      <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
        <div className="bento-card max-w-lg w-full bg-card rounded-3xl shadow-2xl max-h-[92vh] overflow-hidden flex flex-col border border-line animate-in fade-in zoom-in duration-150">
          <div className="p-6 pb-4 flex items-center justify-between gap-3">
            <div>
              <h3 className="font-black text-xl text-ink tracking-tight">Confirmar Cobro</h3>
              <p className="text-[11px] text-ink-soft font-semibold">
                {itemCount} {itemCount === 1 ? 'producto' : 'productos'} en la orden
              </p>
            </div>
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              aria-label="Cerrar"
              className="p-2 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-full hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer disabled:opacity-40"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <div className="px-6 pb-2 space-y-5 overflow-y-auto">
            <div className="p-4 rounded-2xl bg-page border border-line flex items-baseline justify-between gap-3">
              <span className={SECTION_LABEL}>Total a cobrar</span>
              <span className="text-3xl font-black text-ink tabular-nums tracking-tight">
                {money(total)} <span className="text-xs font-bold text-gray-400">COP</span>
              </span>
            </div>

            {/* Factura electrónica DIAN */}
            <div className={`p-4 rounded-2xl border transition-colors ${
              issueDian
                ? 'bg-brand/5 dark:bg-brand/10 border-brand/25'
                : 'bg-gray-50 dark:bg-white/5 border-line'
            }`}>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-9 h-9 rounded-xl bg-solid text-brand flex items-center justify-center shrink-0">
                    <FileText className="w-4 h-4" />
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="text-xs font-extrabold text-ink">Emitir factura electrónica DIAN</span>
                    </div>
                    <p className="text-[11px] text-ink-soft">Se envía a la DIAN a través de Factus, nuestro proveedor autorizado (incluye CUFE, QR y XML)</p>
                  </div>
                </div>

                <button
                  type="button"
                  role="switch"
                  aria-checked={issueDian}
                  aria-label="Emitir factura electrónica"
                  onClick={() => onToggleDian(!issueDian)}
                  className={`relative w-12 h-7 shrink-0 rounded-full transition-colors cursor-pointer ${issueDian ? 'bg-brand' : 'bg-gray-300 dark:bg-[#33333D]'}`}
                >
                  <span className={`absolute top-1 left-1 w-5 h-5 rounded-full bg-white shadow-md transition-transform ${issueDian ? 'translate-x-5' : 'translate-x-0'}`} />
                </button>
              </div>

              {issueDian && (
                <div className="mt-4 pt-3 border-t border-brand/20 space-y-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <label htmlFor="checkout-customer" className={SECTION_LABEL}>Cliente fiscal receptor</label>
                    <button
                      type="button"
                      onClick={() => setShowCreateCustomer(true)}
                      className="text-[11px] font-extrabold text-brand flex items-center gap-1 hover:underline cursor-pointer"
                    >
                      <UserPlus className="w-3.5 h-3.5" /> Registrar cliente DIAN
                    </button>
                  </div>

                  <select
                    id="checkout-customer"
                    value={selectedCustomer?.id || ''}
                    onChange={(e) => onSelectCustomer(customers.find(c => c.id === parseInt(e.target.value)) || null)}
                    className="w-full h-11 px-3 bg-white dark:bg-page border border-line rounded-2xl text-xs font-bold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  >
                    <option value="">Consumidor final (sin datos fiscales)</option>
                    {customers.map(c => (
                      <option key={c.id} value={c.id}>
                        {c.fullName || c.name} — {c.identification}
                      </option>
                    ))}
                  </select>

                  {selectedCustomer ? (
                    <div className="p-3 rounded-2xl bg-white dark:bg-page border border-line text-[11px] space-y-1">
                      <div className="flex justify-between gap-3">
                        <span className="text-ink-soft">Identificación</span>
                        <span className="font-mono font-bold text-ink">
                          {selectedCustomer.identification}{selectedCustomer.dv ? `-${selectedCustomer.dv}` : ''}
                        </span>
                      </div>
                      <div className="flex justify-between gap-3">
                        <span className="text-ink-soft">Correo de recepción</span>
                        <span className={`font-semibold truncate ${selectedCustomer.email ? 'text-ink' : 'text-amber-600 dark:text-amber-400'}`}>
                          {selectedCustomer.email || 'No registrado'}
                        </span>
                      </div>
                    </div>
                  ) : (
                    <p className="text-[11px] text-amber-700 dark:text-amber-400 font-semibold flex items-start gap-1.5">
                      <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" />
                      <span>La factura se emitirá a nombre de consumidor final.</span>
                    </p>
                  )}
                </div>
              )}
            </div>

            {/* Método de pago */}
            {paymentMethods.length > 0 && (
              <div className="space-y-2">
                <span className={SECTION_LABEL}>Método de pago</span>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {paymentMethods.map(m => {
                    const Icon = PAYMENT_ICONS[m.code] || Wallet;
                    const active = selectedPaymentMethod === m.code;
                    return (
                      <button
                        key={m.code}
                        type="button"
                        onClick={() => onSelectPaymentMethod(m.code)}
                        className={`py-3 px-2 rounded-2xl border flex flex-col items-center justify-center gap-1.5 transition-all cursor-pointer ${
                          active
                            ? 'bg-solid text-solid-ink border-line-strong shadow-sm'
                            : 'bg-white dark:bg-page text-gray-600 dark:text-gray-300 border-line hover:border-line-strong'
                        }`}
                      >
                        <Icon className={`w-[18px] h-[18px] ${active ? 'text-brand' : 'text-gray-400'}`} />
                        <span className="text-[11px] font-bold text-center leading-tight">{m.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {isCash ? (
              <div className="space-y-3">
                <div>
                  <label htmlFor="checkout-cash" className={`${SECTION_LABEL} block mb-1.5`}>Efectivo recibido</label>
                  <div className="relative">
                    <span className="absolute left-4 top-1/2 -translate-y-1/2 font-black text-gray-400">$</span>
                    <MoneyInput
                      id="checkout-cash"
                      autoFocus
                      value={cashTendered}
                      onChange={onCashTenderedChange}
                      placeholder="0"
                      className="h-12 pl-9 pr-4 rounded-2xl text-lg font-black tabular-nums dark:focus:border-brand"
                    />
                  </div>
                </div>

                <div className="flex flex-wrap gap-1.5">
                  {[total, 20000, 50000, 100000].map((val, i) => (
                    <button
                      key={`${val}-${i}`}
                      type="button"
                      onClick={() => onCashTenderedChange(String(val))}
                      className={`px-3 py-1.5 rounded-xl text-xs font-bold border transition-all cursor-pointer ${
                        cashNumber === val
                          ? 'bg-solid text-solid-ink border-line-strong'
                          : 'bg-white dark:bg-page text-gray-600 dark:text-gray-300 border-line hover:border-line-strong'
                      }`}
                    >
                      {i === 0 ? 'Exacto' : `${money(val)}`}
                    </button>
                  ))}
                </div>

                <div className={`p-3.5 rounded-2xl border flex items-center justify-between gap-3 ${
                  change < 0 && cashNumber > 0
                    ? 'bg-red-50 dark:bg-red-950/30 border-red-200 dark:border-red-800/40'
                    : 'bg-white dark:bg-page border-line'
                }`}>
                  <span className={SECTION_LABEL}>Cambio / vuelto</span>
                  {change < 0 && cashNumber > 0 ? (
                    <span className="text-xs font-bold text-red-600 dark:text-red-400 flex items-center gap-1">
                      <ShieldAlert className="w-3.5 h-3.5" /> El efectivo es menor al total
                    </span>
                  ) : (
                    <span className={`text-lg font-black tabular-nums px-3 py-0.5 rounded-xl ${
                      change > 0
                        ? 'bg-brand/10 text-brand border border-brand/25'
                        : 'text-ink'
                    }`}>
                      {money(Math.max(0, change))}
                    </span>
                  )}
                </div>
              </div>
            ) : isCredit ? (
              <CreditPanel
                customers={customers}
                selectedCustomer={selectedCustomer}
                onSelectCustomer={onSelectCustomer}
                account={creditAccount}
                total={total}
                dueDate={creditDueDate}
                onDueDateChange={onCreditDueDateChange}
                blockReason={creditBlock}
              />
            ) : (
              <p className="text-[11px] text-ink-soft font-semibold text-center p-3 rounded-2xl bg-gray-50 dark:bg-white/5">
                Pago electrónico: se registra por el monto exacto de la venta, sin cambio.
              </p>
            )}
          </div>

          <div className="p-6 pt-4 flex items-center gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="w-1/3 py-3.5 bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer disabled:opacity-50"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={onConfirm}
              disabled={blocked}
              className={`w-2/3 py-3.5 rounded-2xl font-black text-xs flex items-center justify-center gap-2 text-white shadow-md ${
                blocked ? 'bg-gray-300 dark:bg-gray-700 cursor-not-allowed' : 'bg-solid hover:bg-solid-hover shadow-[#0A0A0A]/10 cursor-pointer'
              }`}
            >
              {isSubmitting ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <CheckCircle2 className="w-4 h-4 text-brand" />
              )}
              <span>{isSubmitting ? 'Procesando...' : isCredit ? (issueDian ? 'Fiar y emitir factura' : 'Confirmar fiado') : issueDian ? 'Cobrar y emitir factura' : 'Confirmar cobro'}</span>
            </button>
          </div>
        </div>
      </div>

      {showCreateCustomer && (
        <CreateDianCustomerModal
          onClose={() => setShowCreateCustomer(false)}
          onCreated={(created) => onCustomerCreated(created)}
        />
      )}
    </>
  );
}

// ==================== Paso 2: venta registrada ====================

export function SaleResultModal({ sale, onClose }) {
  const { notify } = useModal();
  const [invoice, setInvoice] = useState(sale.invoice);
  const [refreshing, setRefreshing] = useState(false);
  const [copied, setCopied] = useState(false);
  const [showEmail, setShowEmail] = useState(false);
  const [emailValue, setEmailValue] = useState(sale.customerEmail || '');
  const [sendingEmail, setSendingEmail] = useState(false);
  const [emailSent, setEmailSent] = useState(false);

  const badge = invoice ? (INVOICE_BADGE[invoice.status] || INVOICE_BADGE.PENDING) : null;

  const refreshInvoice = async () => {
    setRefreshing(true);
    try {
      setInvoice(await getSaleInvoiceApi(sale.saleId));
    } catch {
      notify('La DIAN todavía no responde para esta factura. Intenta de nuevo en unos segundos.', { title: 'Factura en proceso', danger: false });
    } finally {
      setRefreshing(false);
    }
  };

  const copyCufe = async () => {
    try {
      await navigator.clipboard.writeText(invoice.cufe);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      notify('No se pudo copiar el CUFE. Selecciónalo y cópialo manualmente.');
    }
  };

  const download = async (type) => {
    try {
      const blob = type === 'pdf' ? await downloadInvoicePdfApi(sale.saleId) : await downloadInvoiceXmlApi(sale.saleId);
      deliverBlob(blob, `factura-${invoice.factusNumber || sale.saleId}.${type}`, { openInTab: type === 'pdf' });
    } catch (err) {
      notify(`Error descargando el ${type.toUpperCase()}: ` + err.message);
    }
  };

  const sendEmail = async (e) => {
    e.preventDefault();
    setSendingEmail(true);
    try {
      await sendInvoiceEmailApi(sale.saleId, emailValue.trim());
      setEmailSent(true);
    } catch (err) {
      notify('Error enviando la factura por correo: ' + err.message);
    } finally {
      setSendingEmail(false);
    }
  };

  const actionBtn = 'p-2.5 rounded-2xl bg-white dark:bg-page border border-line hover:border-line-strong text-[10px] font-bold text-ink flex flex-col items-center justify-center gap-1 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed';

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-5 max-h-[92vh] overflow-y-auto border border-line">
        <div className="text-center space-y-1.5">
          <div className="w-14 h-14 rounded-3xl bg-brand/10 border border-brand/25 flex items-center justify-center mx-auto">
            <CheckCircle2 className="w-8 h-8 text-brand" />
          </div>
          <h3 className="text-2xl font-black text-ink tabular-nums">{money(sale.total)}</h3>
          <p className="text-xs text-ink-soft font-medium">
            Venta <strong className="text-ink">{sale.number}</strong> registrada
          </p>
        </div>

        {sale.dianRequested && (
          <div className="p-4 rounded-2xl bg-page border border-line space-y-3">
            <div className="flex items-center justify-between gap-2 pb-2.5 border-b border-line">
              <span className="text-xs font-extrabold text-ink flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-brand" /> Factura electrónica
              </span>
              {badge ? (
                <span className={`text-[10px] px-2.5 py-0.5 rounded-full font-extrabold ${badge.cls}`}>{badge.label}</span>
              ) : (
                <span className="text-[10px] px-2.5 py-0.5 rounded-full font-extrabold bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20">
                  En proceso
                </span>
              )}
            </div>

            {invoice ? (
              <>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="min-w-0">
                    <span className="text-[10px] text-ink-muted block">Número</span>
                    <span className="font-mono font-bold text-ink">{invoice.factusNumber || '—'}</span>
                  </div>
                  <div className="min-w-0">
                    <span className="text-[10px] text-ink-muted block">Receptor</span>
                    <span className="font-semibold text-ink truncate block">{sale.customerName}</span>
                  </div>
                </div>

                {invoice.errorMessage && (
                  <p className="text-[11px] font-semibold text-red-600 dark:text-red-400">{invoice.errorMessage}</p>
                )}

                {invoice.cufe && (
                  <div className="p-2.5 rounded-xl bg-card border border-line">
                    <div className="flex items-center justify-between mb-0.5">
                      <span className="text-[10px] font-extrabold uppercase text-ink-muted">CUFE</span>
                      <button type="button" onClick={copyCufe} className="text-[10px] font-extrabold text-brand flex items-center gap-1 cursor-pointer">
                        {copied ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                        {copied ? 'Copiado' : 'Copiar'}
                      </button>
                    </div>
                    <p className="font-mono text-[10px] text-ink-soft break-all select-all">{invoice.cufe}</p>
                  </div>
                )}

                <div className="grid grid-cols-3 gap-2">
                  <button type="button" onClick={() => download('pdf')} disabled={invoice.status !== 'VALIDATED'} className={actionBtn}>
                    <Download className="w-4 h-4 text-brand" /> PDF
                  </button>
                  <button type="button" onClick={() => download('xml')} disabled={invoice.status !== 'VALIDATED'} className={actionBtn}>
                    <FileText className="w-4 h-4 text-blue-500" /> XML
                  </button>
                  <button type="button" onClick={() => setShowEmail(v => !v)} disabled={invoice.status !== 'VALIDATED'} className={actionBtn}>
                    <Mail className="w-4 h-4 text-emerald-500" /> Correo
                  </button>
                </div>

                {showEmail && (
                  <form onSubmit={sendEmail} className="flex items-center gap-2">
                    <input
                      id="result-email"
                      type="email"
                      required
                      value={emailValue}
                      onChange={e => setEmailValue(e.target.value)}
                      aria-label="Correo de destino"
                      className="flex-1 min-w-0 h-10 px-3 bg-card border border-line rounded-xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                    />
                    <button
                      type="submit"
                      disabled={sendingEmail}
                      className="px-3.5 h-10 rounded-xl bg-solid hover:bg-solid-hover disabled:opacity-50 text-solid-ink text-xs font-extrabold cursor-pointer"
                    >
                      {sendingEmail ? 'Enviando...' : emailSent ? 'Enviado' : 'Enviar'}
                    </button>
                  </form>
                )}

                {invoice.qrCode && (
                  <div className="flex items-center justify-between text-[11px] text-ink-soft">
                    <span className="flex items-center gap-1.5"><QrCode className="w-3.5 h-3.5" /> Código QR certificado</span>
                    <a href={invoice.qrCode} target="_blank" rel="noreferrer" className="text-brand font-extrabold text-[10px] flex items-center gap-1 hover:underline">
                      Validar en la DIAN <ExternalLink className="w-3 h-3" />
                    </a>
                  </div>
                )}
              </>
            ) : (
              <p className="text-[11px] text-ink-soft font-medium">
                La venta ya quedó guardada. La DIAN todavía no devuelve la factura; puedes seguir vendiendo y consultarla luego en Facturación.
              </p>
            )}

            {(!invoice || invoice.status === 'PENDING') && (
              <button
                type="button"
                onClick={refreshInvoice}
                disabled={refreshing}
                className="w-full py-2 rounded-xl bg-card border border-line text-[11px] font-bold text-gray-600 dark:text-gray-300 flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} /> Actualizar estado
              </button>
            )}
          </div>
        )}

        <div className="p-4 rounded-2xl bg-gray-50 dark:bg-white/5 border border-gray-200 dark:border-gray-700 text-xs space-y-1.5">
          <div className="flex justify-between font-bold text-gray-700 dark:text-gray-300">
            <span>Cliente</span><span className="truncate ml-3">{sale.customerName}</span>
          </div>
          {sale.onCredit && (
            <div className="flex justify-between font-bold text-amber-700 dark:text-amber-400">
              <span>Queda a fiado</span><span className="tabular-nums">{money(sale.total)}</span>
            </div>
          )}
          {sale.change > 0 && (
            <div className="flex justify-between font-bold text-emerald-600 dark:text-emerald-400">
              <span>Cambio entregado</span><span className="tabular-nums">{money(sale.change)}</span>
            </div>
          )}
        </div>

        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => notify(`Imprimiendo tiquete térmico para la venta ${sale.number}...`, { title: 'Imprimir tiquete', danger: false })}
            className="w-1/2 py-3 bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-ink rounded-2xl font-bold text-xs flex items-center justify-center gap-1.5 cursor-pointer"
          >
            <Printer className="w-4 h-4" /> Imprimir tiquete
          </button>
          <button
            type="button"
            onClick={onClose}
            className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-solid-ink rounded-2xl font-extrabold text-xs flex items-center justify-center gap-1.5 cursor-pointer shadow-md shadow-[#0A0A0A]/10"
          >
            <ShoppingBag className="w-4 h-4 text-brand" /> Nueva venta
          </button>
        </div>
      </div>
    </div>
  );
}
