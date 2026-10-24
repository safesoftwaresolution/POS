// Real API client — Zero mockups, strictly real data operations

import { API_BASE_URL } from './http';

let authToken = localStorage.getItem('jwt_token') || '';

export function getAuthToken() {
  return authToken;
}

export function setAuthToken(token) {
  authToken = token || '';
  if (token) {
    localStorage.setItem('jwt_token', token);
  } else {
    localStorage.removeItem('jwt_token');
  }
}

// Core HTTP Fetch Wrapper
export async function fetchApi(endpoint, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    setAuthToken('');
    throw new Error('Sesión expirada o credenciales no válidas.');
  }

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `Error HTTP ${response.status}`);
  }

  if (response.status === 204) return true;
  return await response.json();
}

// ----------------------------------------------------
// AUTH API
// ----------------------------------------------------
export async function loginApi(username, password) {
  const data = await fetchApi('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  if (data && data.token) {
    setAuthToken(data.token);
  }
  return data;
}

export async function logoutApi() {
  try {
    await fetchApi('/auth/logout', { method: 'POST' });
  } catch (e) {
    // Ignore on logout
  } finally {
    setAuthToken('');
  }
}

// ----------------------------------------------------
// PRODUCTS API
// ----------------------------------------------------
export async function getProductsApi(search = '', categoryId = null, page = 0, size = 50) {
  const params = new URLSearchParams({ page, size });
  if (search) params.append('search', search);
  if (categoryId) params.append('categoryId', categoryId);

  const data = await fetchApi(`/products?${params.toString()}`);
  return data.content || data || [];
}

export async function createProductApi(productData) {
  return await fetchApi('/products', {
    method: 'POST',
    body: JSON.stringify({
      name: productData.name,
      description: productData.description || productData.name,
      internalCode: productData.internalCode,
      barcode: productData.barcode,
      categoryId: parseInt(productData.categoryId),
      purchasePrice: parseFloat(productData.purchasePrice || productData.price * 0.7),
      salePrice: parseFloat(productData.price || productData.salePrice),
      quantityAvailable: parseInt(productData.stock || productData.quantityAvailable || 0),
      minStock: parseInt(productData.minStock || 5),
    }),
  });
}

export async function updateProductApi(id, productData) {
  return await fetchApi(`/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(productData),
  });
}

export async function deleteProductApi(id) {
  return await fetchApi(`/products/${id}`, { method: 'DELETE' });
}

// ----------------------------------------------------
// CATEGORIES API
// ----------------------------------------------------
export async function getCategoriesApi() {
  const data = await fetchApi('/categories');
  return data.content || data || [];
}

export async function createCategoryApi(name, description = '') {
  return await fetchApi('/categories', {
    method: 'POST',
    body: JSON.stringify({ name, description }),
  });
}

export async function updateCategoryApi(id, name, description = '') {
  return await fetchApi(`/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description }),
  });
}

export async function deleteCategoryApi(id) {
  return await fetchApi(`/categories/${id}`, { method: 'DELETE' });
}

// ----------------------------------------------------
// CUSTOMERS API
// ----------------------------------------------------
export async function getCustomersApi() {
  const data = await fetchApi('/customers');
  return data.content || data || [];
}

export async function createCustomerApi(customerData) {
  return await fetchApi('/customers', {
    method: 'POST',
    body: JSON.stringify({
      fullName: customerData.name || customerData.fullName,
      identification: customerData.identification,
      email: customerData.email,
      phone: customerData.phone,
      address: customerData.address || 'Ciudad'
    }),
  });
}

// ----------------------------------------------------
// SALES API
// ----------------------------------------------------
export async function getSalesApi(page = 0, size = 20) {
  const data = await fetchApi(`/sales?page=${page}&size=${size}`);
  return data.content || data || [];
}

export async function createSaleApi(saleCommand) {
  return await fetchApi('/sales', {
    method: 'POST',
    body: JSON.stringify({
      customerId: saleCommand.customerId || null,
      items: saleCommand.items.map(item => ({
        productId: item.productId,
        quantity: item.quantity,
        unitPrice: item.unitPrice
      })),
      cashReceived: saleCommand.cashReceived
    }),
  });
}

// ----------------------------------------------------
// INVENTORY & STOCK API
// ----------------------------------------------------
export async function getStockReportApi(belowMinStock = false) {
  return await fetchApi(`/reports/stock?belowMinStock=${belowMinStock}`);
}

export async function createPurchaseApi(purchaseData) {
  return await fetchApi('/purchases', {
    method: 'POST',
    body: JSON.stringify(purchaseData),
  });
}

// ----------------------------------------------------
// SETTINGS API
// ----------------------------------------------------
export async function getSettingsApi() {
  return await fetchApi('/settings');
}

export async function updateSettingsApi(settingsData) {
  return await fetchApi('/settings', {
    method: 'PUT',
    body: JSON.stringify(settingsData),
  });
}
