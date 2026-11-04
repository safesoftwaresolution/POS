import { describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import useLoad from './useLoad';

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
};

describe('useLoad', () => {
  it('arranca cargando y entrega los datos', async () => {
    const { result } = renderHook(() => useLoad(() => Promise.resolve(['a']), [], { initial: [] }));
    expect(result.current.loading).toBe(true);
    expect(result.current.data).toEqual([]);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual(['a']);
    expect(result.current.error).toBe('');
  });

  it('expone el mensaje del servidor cuando falla y vuelve al valor inicial', async () => {
    const { result } = renderHook(() => useLoad(() => Promise.reject(new Error('Sin permiso')), [], { initial: [] }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe('Sin permiso');
    expect(result.current.data).toEqual([]);
  });

  it('usa el mensaje por defecto si el error no trae uno', async () => {
    const { result } = renderHook(() => useLoad(() => Promise.reject({}), [], { errorMessage: 'No se pudieron cargar las ventas.' }));
    await waitFor(() => expect(result.current.error).toBe('No se pudieron cargar las ventas.'));
  });

  it('al cambiar las dependencias vuelve a cargar y descarta la respuesta vieja', async () => {
    const slow = deferred();
    const fast = deferred();
    const fetcher = vi.fn((page) => (page === 0 ? slow.promise : fast.promise));
    const { result, rerender } = renderHook(({ page }) => useLoad(() => fetcher(page), [page]), { initialProps: { page: 0 } });

    rerender({ page: 1 });
    expect(result.current.loading).toBe(true);

    await act(async () => { fast.resolve('pagina-1'); });
    await waitFor(() => expect(result.current.data).toBe('pagina-1'));

    // La página 0 contesta tarde: no debe pisar lo que ya se ve.
    await act(async () => { slow.resolve('pagina-0'); });
    expect(result.current.data).toBe('pagina-1');
  });

  it('reload conserva los datos mientras consulta y los actualiza', async () => {
    let n = 0;
    const gate = deferred();
    const fetcher = vi.fn(() => (n++ === 0 ? Promise.resolve('v1') : gate.promise));
    const { result } = renderHook(() => useLoad(fetcher, []));
    await waitFor(() => expect(result.current.data).toBe('v1'));

    act(() => result.current.reload());
    expect(result.current.loading).toBe(false);
    expect(result.current.data).toBe('v1');

    await act(async () => { gate.resolve('v2'); });
    await waitFor(() => expect(result.current.data).toBe('v2'));
  });

  it('reload({ silent: false }) vuelve a mostrar la carga', async () => {
    let n = 0;
    const gate = deferred();
    const fetcher = () => (n++ === 0 ? Promise.resolve('v1') : gate.promise);
    const { result } = renderHook(() => useLoad(fetcher, []));
    await waitFor(() => expect(result.current.data).toBe('v1'));

    act(() => result.current.reload({ silent: false }));
    expect(result.current.loading).toBe(true);
    await act(async () => { gate.resolve('v2'); });
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toBe('v2');
  });

  it('un reload que falla no borra lo que ya se ve ni muestra error', async () => {
    let n = 0;
    const fetcher = () => (n++ === 0 ? Promise.resolve('v1') : Promise.reject(new Error('caido')));
    const { result } = renderHook(() => useLoad(fetcher, []));
    await waitFor(() => expect(result.current.data).toBe('v1'));

    await act(async () => { result.current.reload(); });
    expect(result.current.data).toBe('v1');
    expect(result.current.error).toBe('');
  });

  it('el fetcher recibe lo que ya estaba cargado', async () => {
    const seen = [];
    const fetcher = vi.fn((previous) => { seen.push(previous); return Promise.resolve(`v${seen.length}`); });
    const { result } = renderHook(() => useLoad(fetcher, [], { initial: 'inicial' }));
    await waitFor(() => expect(result.current.data).toBe('v1'));
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.data).toBe('v2'));
    expect(seen).toEqual(['inicial', 'v1']);
  });

  it('con enabled en false no consulta ni queda cargando', () => {
    const fetcher = vi.fn(() => Promise.resolve('x'));
    const { result } = renderHook(() => useLoad(fetcher, [], { enabled: false }));
    expect(result.current.loading).toBe(false);
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('setData permite actualizar la lista sin volver a consultar', async () => {
    const { result } = renderHook(() => useLoad(() => Promise.resolve([1, 2]), [], { initial: [] }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.setData((list) => list.filter((n) => n !== 1)));
    expect(result.current.data).toEqual([2]);
  });
});
