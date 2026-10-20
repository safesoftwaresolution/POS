import { fetchApi } from './http';

// Gestión de usuarios del local. Todos los endpoints exigen rol ADMINISTRATOR
// (cambiar la propia contraseña es la excepción: lo puede hacer cualquier usuario).

export async function getUsersApi(page = 0, size = 100) {
  const data = await fetchApi(`/users?page=${page}&size=${size}&sort=fullName,asc`);
  return {
    users: Array.isArray(data) ? data : data?.content || [],
    total: data?.page?.totalElements ?? data?.totalElements ?? (Array.isArray(data) ? data.length : 0),
  };
}

export async function createUserApi({ fullName, username, email, password, role, storeId }) {
  return await fetchApi('/users', {
    method: 'POST',
    body: JSON.stringify({ fullName, username, email, password, role, storeId }),
  });
}

export async function updateUserApi(id, { fullName, email, role }) {
  return await fetchApi(`/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ fullName, email, role }),
  });
}

export async function changeUserStatusApi(id, active) {
  return await fetchApi(`/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

// Un ADMINISTRATOR puede fijar la contraseña de otro usuario sin conocer la actual.
export async function changeUserPasswordApi(id, newPassword, currentPassword) {
  return await fetchApi(`/users/${id}/password`, {
    method: 'PATCH',
    body: JSON.stringify({ newPassword, ...(currentPassword && { currentPassword }) }),
  });
}

export async function deleteUserApi(id) {
  return await fetchApi(`/users/${id}`, { method: 'DELETE' });
}
