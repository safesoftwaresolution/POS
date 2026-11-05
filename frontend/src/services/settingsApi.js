import { fetchApi, uploadFileApi } from './http';

export async function getSettingsApi() {
  return await fetchApi('/settings');
}

export async function updateSettingsApi(settingsData) {
  return await fetchApi('/settings', {
    method: 'PUT',
    body: JSON.stringify(settingsData),
  });
}

// Sube el logo del negocio (solo ADMINISTRATOR). El backend valida PNG/JPG, <= 200 KB y <= 300x300 px,
// lo guarda en el almacenamiento del local.
// Devuelve { logoUrl, message, ... }.
export async function uploadCompanyLogoApi(file) {
  const formData = new FormData();
  formData.append('file', file);
  return await uploadFileApi('/settings/logo', formData);
}
