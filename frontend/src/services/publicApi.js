import { fetchApi } from './http';

// Endpoints públicos (no requieren sesión): textos legales vigentes y soporte (PQRs / bugs).

export async function getLegalDocumentApi(slug) {
  return await fetchApi(`/legal/${encodeURIComponent(slug)}`);
}

export async function createSupportTicketApi({ type, priority, title, description, contactName, contactEmail, contactPhone, storeId }) {
  return await fetchApi('/support/tickets', {
    method: 'POST',
    body: JSON.stringify({
      type,
      priority,
      title,
      description,
      contactName,
      contactEmail,
      contactPhone: contactPhone || undefined,
      storeId: storeId ?? undefined,
      // Ayuda al equipo de soporte a reproducir el problema.
      systemInfo: typeof navigator !== 'undefined' ? navigator.userAgent : undefined,
    }),
  });
}

export async function trackSupportTicketApi(ticketNumber) {
  return await fetchApi(`/support/tickets/track/${encodeURIComponent(ticketNumber.trim())}`);
}
