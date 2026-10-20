import { fetchApi } from './http';

export async function getSuppliersApi(search = '') {
  const params = new URLSearchParams({ page: 0, size: 100 });
  if (search) params.append('search', search);
  const data = await fetchApi(`/suppliers?${params.toString()}`);
  return data.content || data || [];
}

export async function createSupplierApi(supplierData) {
  return await fetchApi('/suppliers', {
    method: 'POST',
    body: JSON.stringify({
      companyName: supplierData.companyName,
      taxId: supplierData.taxId,
      contactName: supplierData.contactName || null,
      email: supplierData.email || null,
      phone: supplierData.phone || null,
      address: supplierData.address || null,
    }),
  });
}

export async function updateSupplierApi(id, supplierData) {
  return await fetchApi(`/suppliers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(supplierData),
  });
}

export async function deleteSupplierApi(id) {
  return await fetchApi(`/suppliers/${id}`, { method: 'DELETE' });
}
