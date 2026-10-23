import { fetchApi } from '../http';

export async function getUserDirectoryApi({ role = '', storeId = '', q = '', unassigned = false, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page, size });
  if (role && role !== 'ALL') params.append('role', role);
  if (storeId) params.append('storeId', storeId);
  if (q) params.append('q', q);
  if (unassigned) params.append('unassigned', 'true');
  return await fetchApi(`/backoffice/users?${params.toString()}`);
}

export async function getAssignableOwnersApi() {
  return await fetchApi('/backoffice/users/assignable-owners');
}

export async function getStoresWithoutOwnerApi() {
  return await fetchApi('/backoffice/stores-without-owner');
}

export async function assignStoreOwnerApi(storeId, userId) {
  return await fetchApi(`/backoffice/stores/${storeId}/assign-owner`, {
    method: 'POST',
    body: JSON.stringify({ userId }),
  });
}

export async function getOwnerAssignmentsApi(page = 0, size = 10) {
  const params = new URLSearchParams({ page, size });
  return await fetchApi(`/backoffice/user-assignments?${params.toString()}`);
}
