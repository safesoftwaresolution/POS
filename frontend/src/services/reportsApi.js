import { fetchApi } from './http';

function dateParams(dateFrom, dateTo) {
  const params = new URLSearchParams();
  if (dateFrom) params.set('dateFrom', dateFrom);
  if (dateTo) params.set('dateTo', dateTo);
  return params.toString();
}

export async function getSalesReportApi(dateFrom, dateTo) {
  return await fetchApi(`/reports/sales?${dateParams(dateFrom, dateTo)}`);
}

/** Ventas por día de la semana (1 = lunes) y hora: `[{ dayOfWeek, hourOfDay, transactionCount, totalSold }]`. */
export async function getSalesHeatmapApi(dateFrom, dateTo) {
  return await fetchApi(`/reports/sales-heatmap?${dateParams(dateFrom, dateTo)}`);
}

export async function getTopProductsReportApi(dateFrom, dateTo, limit = 10) {
  const params = dateParams(dateFrom, dateTo);
  return await fetchApi(`/reports/top-products?${params}&limit=${limit}`);
}

export async function getStockReportApi(belowMinStock = false) {
  return await fetchApi(`/reports/stock?belowMinStock=${belowMinStock}`);
}

/** Productos vencidos y los que vencen dentro de `days` días (activos y con existencias). */
export async function getExpiringReportApi(days = 30) {
  return await fetchApi(`/reports/expiring?days=${days}`);
}

export async function getProfitabilityReportApi(dateFrom, dateTo) {
  return await fetchApi(`/reports/profitability?${dateParams(dateFrom, dateTo)}`);
}

export async function getPurchasesReportApi(dateFrom, dateTo) {
  return await fetchApi(`/reports/purchases?${dateParams(dateFrom, dateTo)}`);
}

export async function getCashClosingReportApi(dateFrom, dateTo) {
  return await fetchApi(`/reports/cash-closing?${dateParams(dateFrom, dateTo)}`);
}
