import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchApi } from './http';
import {
  createBranchApi, getBranchesStatsApi, getBusinessOverviewApi, getMyBusinessApi, periodQuery, switchStoreApi,
} from './businessApi';

vi.mock('./http', () => ({ fetchApi: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

describe('business API contracts', () => {
  it('treats a 404 from /business/me as "not the owner of a business", and nothing else', async () => {
    fetchApi.mockRejectedValueOnce(Object.assign(new Error('Not found'), { status: 404 }));
    await expect(getMyBusinessApi()).resolves.toBeNull();

    fetchApi.mockRejectedValueOnce(Object.assign(new Error('Prohibido'), { status: 403 }));
    await expect(getMyBusinessApi()).rejects.toThrow('Prohibido');

    fetchApi.mockResolvedValueOnce({ business: { id: 7 }, activeStoreId: 1 });
    await expect(getMyBusinessApi()).resolves.toEqual({ business: { id: 7 }, activeStoreId: 1 });
    expect(fetchApi).toHaveBeenLastCalledWith('/business/me');
  });

  it('switches branch with only the store id (the refresh token travels in its HttpOnly cookie)', async () => {
    await switchStoreApi(2);
    expect(fetchApi).toHaveBeenLastCalledWith('/auth/switch-store', { method: 'POST', body: JSON.stringify({ storeId: 2 }) });
  });

  it('creates a branch with the payload unchanged', async () => {
    const payload = { name: 'Norte', email: 'norte@x.co', taxId: null };
    await createBranchApi(payload);
    expect(fetchApi).toHaveBeenLastCalledWith('/business/branches', { method: 'POST', body: JSON.stringify(payload) });
  });

  it('sends the period as ISO dates and omits what is missing', async () => {
    expect(periodQuery('2026-03-10', '2026-03-12')).toBe('?from=2026-03-10&to=2026-03-12');
    expect(periodQuery('2026-03-10')).toBe('?from=2026-03-10');
    expect(periodQuery()).toBe('');

    await getBusinessOverviewApi('2026-03-10', '2026-03-12');
    expect(fetchApi).toHaveBeenLastCalledWith('/business/stats/overview?from=2026-03-10&to=2026-03-12');
    await getBranchesStatsApi();
    expect(fetchApi).toHaveBeenLastCalledWith('/business/stats/branches');
  });
});
