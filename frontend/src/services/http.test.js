import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchApi, STORE_NOT_ACTIVE_EVENT } from './http';

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('fetchApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('announces a 403 StoreNotActive with the store status and keeps the body on the error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(403, {
      error: 'StoreNotActive', status: 'SUSPENDED', message: 'Tu local no está activo.',
    })));
    const listener = vi.fn();
    window.addEventListener(STORE_NOT_ACTIVE_EVENT, listener);

    const error = await fetchApi('/products').catch((e) => e);

    window.removeEventListener(STORE_NOT_ACTIVE_EVENT, listener);
    expect(error.status).toBe(403);
    expect(error.data.error).toBe('StoreNotActive');
    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener.mock.calls[0][0].detail).toEqual({ status: 'SUSPENDED' });
  });

  it('does not announce other 403 responses', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(403, { error: 'Forbidden', message: 'Acceso denegado' })));
    const listener = vi.fn();
    window.addEventListener(STORE_NOT_ACTIVE_EVENT, listener);

    await fetchApi('/users').catch(() => {});

    window.removeEventListener(STORE_NOT_ACTIVE_EVENT, listener);
    expect(listener).not.toHaveBeenCalled();
  });
});
