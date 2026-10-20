import { fetchApi } from '../http';

export async function getPendingUsersApi(page = 0, size = 20) {
  const params = new URLSearchParams({ page, size });
  return await fetchApi(`/backoffice/onboarding/pending-users?${params.toString()}`);
}

export async function getOnboardingStatsApi() {
  return await fetchApi('/backoffice/onboarding/stats');
}
