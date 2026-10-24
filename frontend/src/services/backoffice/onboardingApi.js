import { fetchApi } from '../http';

export async function getPendingUsersApi(page = 0, size = 20) {
  const params = new URLSearchParams({ page, size });
  return await fetchApi(`/backoffice/onboarding/pending-users?${params.toString()}`);
}

export async function getVerifiedUsersApi(page = 0, size = 20) {
  const params = new URLSearchParams({ page, size, sort: 'id,desc' });
  return await fetchApi(`/backoffice/onboarding/verified-users?${params.toString()}`);
}

export async function resendUserVerificationApi(userId) {
  return await fetchApi(`/backoffice/onboarding/pending-users/${userId}/resend-verification`, {
    method: 'POST',
  });
}

export async function deletePendingUserApi(userId) {
  return await fetchApi(`/backoffice/onboarding/pending-users/${userId}`, { method: 'DELETE' });
}

export async function getOnboardingStatsApi() {
  return await fetchApi('/backoffice/onboarding/stats');
}
