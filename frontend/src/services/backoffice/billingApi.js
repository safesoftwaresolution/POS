import { fetchApi } from '../http';

export async function listBackofficeBillingByStatusApi(status) {
  return await fetchApi(`/backoffice/billing?status=${status}`);
}

export async function getBackofficeStoreBillingApi(storeId) {
  return await fetchApi(`/backoffice/stores/${storeId}/billing`);
}

export async function updateBackofficeBillingCredentialsApi(storeId, environment, credentials) {
  return await fetchApi(`/backoffice/stores/${storeId}/billing/credentials?environment=${environment}`, {
    method: 'PUT',
    body: JSON.stringify(credentials),
  });
}

export async function testBackofficeBillingConnectionApi(storeId, environment) {
  return await fetchApi(`/backoffice/stores/${storeId}/billing/test-connection?environment=${environment}`, {
    method: 'POST',
  });
}

export async function switchBackofficeBillingEnvironmentApi(storeId, environment) {
  return await fetchApi(`/backoffice/stores/${storeId}/billing/environment?environment=${environment}`, {
    method: 'PATCH',
  });
}

export async function transitionBackofficeBillingStatusApi(storeId, status, reason = null) {
  return await fetchApi(`/backoffice/stores/${storeId}/billing/transition`, {
    method: 'POST',
    body: JSON.stringify({ status, reason }),
  });
}
