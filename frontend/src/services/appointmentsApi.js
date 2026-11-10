import { fetchApi } from './http';

// from/to: 'YYYY-MM-DDTHH:mm:ss'; el rango es [from, to).
export async function getAppointmentsApi(from, to) {
  const params = new URLSearchParams({ from, to });
  return await fetchApi(`/appointments?${params.toString()}`);
}

export async function createAppointmentApi({ customerId, serviceId, startAt, notes }) {
  return await fetchApi('/appointments', {
    method: 'POST',
    body: JSON.stringify({ customerId, serviceId, startAt, notes: notes || null }),
  });
}

export async function rescheduleAppointmentApi(id, startAt) {
  return await fetchApi(`/appointments/${id}/reschedule`, {
    method: 'PATCH',
    body: JSON.stringify({ startAt }),
  });
}

export async function cancelAppointmentApi(id) {
  return await fetchApi(`/appointments/${id}/cancel`, { method: 'PATCH' });
}
