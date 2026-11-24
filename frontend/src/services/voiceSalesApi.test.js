import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MAX_AUDIO_BYTES,
  isVoiceSalesDisabled,
  previewVoiceSaleApi,
  resetVoiceSalesAvailability,
} from './voiceSalesApi';

// Se prueba contra el `fetchApi` real con `fetch` simulado: lo que se verifica es lo que viaja por la red.
const jsonResponse = (status, body) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const DRAFT = {
  items: [{ item: { mention: 'café', status: 'RESOLVED', candidates: [{ kind: 'PRODUCT', id: 7, code: 'CAF', label: 'Café' }] }, quantity: 2 }],
  customer: { mention: null, status: 'NOT_PROVIDED', candidates: [] },
  paymentMethod: { mention: 'efectivo', status: 'RESOLVED', candidates: [{ kind: 'PAYMENT_METHOD', id: 3, code: 'CASH', label: 'Efectivo' }] },
  cashReceived: 50000,
  pendingFields: [],
};

const audio = (type = 'audio/webm;codecs=opus') => new Blob([new Uint8Array([1, 2, 3, 4])], { type });

// Un Blob real de 5 MiB + 1 byte pesaría en cada ejecución: se simula solo el tamaño.
const withSize = (blob, size) => Object.defineProperty(blob, 'size', { value: size });

beforeEach(() => resetVoiceSalesAvailability());
afterEach(() => vi.unstubAllGlobals());

describe('previewVoiceSaleApi', () => {
  it('sends the raw Blob as the body with its own audio Content-Type, never multipart', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, DRAFT));
    vi.stubGlobal('fetch', fetchMock);
    const blob = audio();

    const draft = await previewVoiceSaleApi(blob);

    expect(draft).toEqual(DRAFT);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/v1\/voice-sales\/preview$/);
    expect(init.method).toBe('POST');
    expect(init.body).toBe(blob);
    expect(init.body instanceof FormData).toBe(false);
    expect(init.headers['Content-Type']).toBe('audio/webm;codecs=opus');
    expect(init.credentials).toBe('include');
  });

  it('accepts audio/wav and does not add a store or a link to the request', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, DRAFT));
    vi.stubGlobal('fetch', fetchMock);

    await previewVoiceSaleApi(audio('audio/wav'));

    const [url, init] = fetchMock.mock.calls[0];
    expect(init.headers['Content-Type']).toBe('audio/wav');
    expect(url).not.toContain('?');
    expect(url).not.toMatch(/store/i);
  });

  it('accepts exactly 5 MiB and rejects one byte more before any request', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, DRAFT));
    vi.stubGlobal('fetch', fetchMock);
    expect(MAX_AUDIO_BYTES).toBe(5_242_880);

    await previewVoiceSaleApi(withSize(audio(), MAX_AUDIO_BYTES));
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const error = await previewVoiceSaleApi(withSize(audio(), MAX_AUDIO_BYTES + 1)).catch((e) => e);
    expect(error.status).toBe(413);
    expect(error.kind).toBe('too_large');
    expect(error.message).toMatch(/demasiado grande/i);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects an empty recording and an unsupported type before any request', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const empty = await previewVoiceSaleApi(new Blob([], { type: 'audio/webm' })).catch((e) => e);
    expect(empty.kind).toBe('empty');
    expect(empty.message).toMatch(/no se grabó audio/i);

    const mp3 = await previewVoiceSaleApi(audio('audio/mpeg')).catch((e) => e);
    expect(mp3.status).toBe(415);
    expect(mp3.kind).toBe('unsupported_type');

    const noType = await previewVoiceSaleApi(audio('')).catch((e) => e);
    expect(noType.status).toBe(415);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    [403, 'forbidden', { message: 'No se permite el dictado para esta sesión.' }, /no se permite el dictado/i],
    [404, 'disabled', {}, /no está habilitado en este servidor/i],
    [413, 'too_large', { message: 'x' }, /demasiado grande/i],
    [415, 'unsupported_type', { message: 'x' }, /formato de audio no es compatible/i],
    [429, 'quota', { message: 'x' }, /límite de dictados/i],
    [503, 'unavailable', { message: 'No se pudo interpretar el audio.' }, /registrar la venta manualmente/i],
    [500, 'unknown', { message: 'No se pudo preparar el borrador.' }, /no se pudo preparar el borrador/i],
  ])('maps a %i response to a Spanish message, keeping status and body', async (status, kind, body, message) => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(status, body)));

    const error = await previewVoiceSaleApi(audio()).catch((e) => e);

    expect(error.status).toBe(status);
    expect(error.kind).toBe(kind);
    expect(error.data).toEqual(body);
    expect(error.message).toMatch(message);
  });

  it('marks dictation as disabled for the session only after a 404', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(429, { message: 'x' })));
    await previewVoiceSaleApi(audio()).catch(() => {});
    expect(isVoiceSalesDisabled()).toBe(false);

    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })));
    const error = await previewVoiceSaleApi(audio()).catch((e) => e);

    expect(error.kind).toBe('disabled');
    expect(isVoiceSalesDisabled()).toBe(true);
    resetVoiceSalesAvailability();
    expect(isVoiceSalesDisabled()).toBe(false);
  });

  it('turns a network failure into a message that points to manual sales', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));

    const error = await previewVoiceSaleApi(audio()).catch((e) => e);

    expect(error.kind).toBe('network');
    expect(error.message).toMatch(/registrar la venta manualmente/i);
  });

  it('leaves nothing of the audio in browser storage', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, DRAFT)));
    localStorage.clear();
    sessionStorage.clear();

    await previewVoiceSaleApi(audio());

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
