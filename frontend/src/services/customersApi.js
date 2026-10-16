import { fetchApi } from './http';

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
      address: customerData.address || 'Ciudad',
      // Datos fiscales DIAN: solo se envían si el formulario los captura (el backend aplica sus defaults).
      ...(customerData.dv != null && { dv: customerData.dv }),
      ...(customerData.legalOrganizationId != null && { legalOrganizationId: customerData.legalOrganizationId }),
      ...(customerData.tributeId != null && { tributeId: customerData.tributeId }),
      ...(customerData.municipalityId != null && { municipalityId: customerData.municipalityId })
    }),
  });
}

export async function updateCustomerApi(id, customerData) {
  return await fetchApi(`/customers/${id}`, {
    method: 'PUT',
    body: JSON.stringify({
      fullName: customerData.fullName,
      email: customerData.email,
      phone: customerData.phone,
      address: customerData.address,
      ...(customerData.dv != null && { dv: customerData.dv }),
      ...(customerData.legalOrganizationId != null && { legalOrganizationId: customerData.legalOrganizationId }),
      ...(customerData.tributeId != null && { tributeId: customerData.tributeId }),
      ...(customerData.municipalityId != null && { municipalityId: customerData.municipalityId })
    }),
  });
}

// Solo ADMINISTRATOR (el backend responde 403 al resto de roles).
export async function deleteCustomerApi(id) {
  return await fetchApi(`/customers/${id}`, { method: 'DELETE' });
}
