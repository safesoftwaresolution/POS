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

export async function getTopProductsReportApi(dateFrom, dateTo, limit = 10) {
  const params = dateParams(dateFrom, dateTo);
  return await fetchApi(`/reports/top-products?${params}&limit=${limit}`);
}

export async function getStockReportApi(belowMinStock = false) {
  return await fetchApi(`/reports/stock?belowMinStock=${belowMinStock}`);
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
