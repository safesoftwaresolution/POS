import { describe, expect, it, vi } from 'vitest';
import { fitWithin, prepareImageForUpload } from './image';

describe('fitWithin', () => {
  it('reduce manteniendo la proporción', () => {
    expect(fitWithin(4000, 3000)).toEqual({ width: 1280, height: 960 });
    expect(fitWithin(3000, 4000)).toEqual({ width: 960, height: 1280 });
  });

  it('no amplía imágenes pequeñas', () => {
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
  });
});

describe('prepareImageForUpload', () => {
  it('deja pasar tal cual una imagen pequeña en formato aceptado', async () => {
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    expect(await prepareImageForUpload(file)).toBe(file);
  });

  it('devuelve el original si no se puede decodificar', async () => {
    vi.stubGlobal('createImageBitmap', () => Promise.reject(new Error('formato no soportado')));
    try {
      const file = new File([new Uint8Array(2 * 1024 * 1024)], 'foto.heic', { type: 'image/heic' });
      expect(await prepareImageForUpload(file)).toBe(file);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
