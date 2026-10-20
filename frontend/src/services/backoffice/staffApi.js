import { fetchApi } from '../http';

export async function getBackofficeStaffApi(page = 0, size = 20) {
  return await fetchApi(`/backoffice/staff?page=${page}&size=${size}`);
}

export async function getBackofficeStaffMemberApi(id) {
  return await fetchApi(`/backoffice/staff/${id}`);
}

export async function createBackofficeStaffApi(staffData) {
  return await fetchApi('/backoffice/staff', {
    method: 'POST',
    body: JSON.stringify(staffData),
  });
}

export async function updateBackofficeStaffApi(id, staffData) {
  return await fetchApi(`/backoffice/staff/${id}`, {
    method: 'PUT',
    body: JSON.stringify(staffData),
  });
}

export async function changeBackofficeStaffStatusApi(id, active) {
  return await fetchApi(`/backoffice/staff/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

export async function changeBackofficeStaffPasswordApi(id, newPassword, currentPassword = null) {
  return await fetchApi(`/backoffice/staff/${id}/password`, {
    method: 'PATCH',
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

export async function deleteBackofficeStaffApi(id) {
  return await fetchApi(`/backoffice/staff/${id}`, { method: 'DELETE' });
}
