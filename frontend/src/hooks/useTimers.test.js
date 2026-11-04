import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import useDebouncedValue from './useDebouncedValue';
import useTransientFlag from './useTransientFlag';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('useDebouncedValue', () => {
  it('solo publica el último valor tras el retraso', () => {
    const { result, rerender } = renderHook(({ v }) => useDebouncedValue(v, 300), { initialProps: { v: 'a' } });
    rerender({ v: 'ab' });
    rerender({ v: 'abc' });
    expect(result.current).toBe('a');
    act(() => { vi.advanceTimersByTime(299); });
    expect(result.current).toBe('a');
    act(() => { vi.advanceTimersByTime(1); });
    expect(result.current).toBe('abc');
  });
});

describe('useTransientFlag', () => {
  it('se enciende y se apaga solo', () => {
    const { result } = renderHook(() => useTransientFlag(1000));
    expect(result.current[0]).toBe(false);
    act(() => result.current[1]());
    expect(result.current[0]).toBe(true);
    act(() => { vi.advanceTimersByTime(1000); });
    expect(result.current[0]).toBe(false);
  });

  it('volver a encenderlo reinicia el plazo', () => {
    const { result } = renderHook(() => useTransientFlag(1000));
    act(() => result.current[1]());
    act(() => { vi.advanceTimersByTime(800); });
    act(() => result.current[1]());
    act(() => { vi.advanceTimersByTime(800); });
    expect(result.current[0]).toBe(true);
    act(() => { vi.advanceTimersByTime(200); });
    expect(result.current[0]).toBe(false);
  });
});
