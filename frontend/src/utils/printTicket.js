const escapeHtml = (value) =>
  String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const money = (value) => `$${Number(value || 0).toLocaleString('es-CO')}`;

/**
 * Imprime el tiquete de una venta en una ventana aparte (formato angosto de 80 mm).
 * Devuelve false si el navegador bloqueó la ventana emergente.
 */
export function printSaleTicket(sale) {
  const win = window.open('', '_blank', 'width=360,height=640');
  if (!win) return false;
  const rows = (sale.items || [])
    .map((i) => `<tr><td>${escapeHtml(i.quantity)} x ${escapeHtml(i.name)}</td><td class="r">${money(i.quantity * i.unitPrice)}</td></tr>`)
    .join('');
  const change = sale.change > 0 ? `<tr><td>Cambio</td><td class="r">${money(sale.change)}</td></tr>` : '';
  const credit = sale.onCredit ? '<tr><td colspan="2">Venta a fiado</td></tr>' : '';
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Tiquete ${escapeHtml(sale.number)}</title>
<style>body{font:12px monospace;width:72mm;margin:0 auto;padding:8px}h1{font-size:14px;text-align:center;margin:0 0 6px}
table{width:100%;border-collapse:collapse}td{padding:2px 0;vertical-align:top}.r{text-align:right}hr{border:0;border-top:1px dashed #000}</style></head>
<body><h1>Venta ${escapeHtml(sale.number)}</h1><div>Cliente: ${escapeHtml(sale.customerName)}</div><hr>
<table>${rows}</table><hr>
<table><tr><td><b>Total</b></td><td class="r"><b>${money(sale.total)}</b></td></tr>${change}${credit}</table>
</body></html>`);
  win.document.close();
  win.focus();
  win.print();
  win.close();
  return true;
}
