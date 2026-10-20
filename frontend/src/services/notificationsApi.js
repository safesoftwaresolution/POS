import { fetchApi } from './http';

export async function getNotificationsApi() {
  return fetchApi('/notifications');
}

export async function getUnreadNotificationCountApi() {
  const data = await fetchApi('/notifications/unread-count');
  return data?.count ?? 0;
}

export async function markNotificationReadApi(id) {
  return fetchApi(`/notifications/${id}/read`, { method: 'PATCH' });
}

export async function markAllNotificationsReadApi() {
  return fetchApi('/notifications/read-all', { method: 'PATCH' });
}
