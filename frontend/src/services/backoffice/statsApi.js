import { fetchApi } from '../http';

export async function getBackofficeStatsOverviewApi() {
  return await fetchApi('/backoffice/stats/overview');
}
