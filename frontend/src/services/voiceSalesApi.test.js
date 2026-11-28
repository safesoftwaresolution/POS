import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MAX_AUDIO_BYTES,
  agentTurnApi,
  isVoiceSalesDisabled,
  resetVoiceSalesAvailability,
  transcribeVoiceApi,
} from './voiceSalesApi';

// Se prueba contra el `fetchApi` real con `fetch` simulado: lo que se verifica es lo que viaja por la red.
const jsonResponse = (status, body) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const audio = (type = 'audio/webm;codecs=opus') => new Blob([new Uint8Array([1, 2, 3, 4])], { type });

// Un Blob real de 5 MiB + 1 byte pesaría en cada ejecución: se simula solo el tamaño.
const withSize = (blob, size) => Object.defineProperty(blob, 'size', { value: size });

const TURN = {
  reply: 'Listo: 2 cafés.',
  ticket: { lines: [], customer: null, paymentMethod: null, cashReceived: null, subtotal: 0, confirmationRequested: false },
  warnings: [],
};

beforeEach(() => resetVoiceSalesAvailability());
afterEach(() => vi.unstubAllGlobals());

describe('transcribeVoiceApi', () => {
  it('sends the raw Blob with its own audio Content-Type, never multipart, and returns only the text', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, { text: 'dos cafés grandes' }));
    vi.stubGlobal('fetch', fetchMock);
    const blob = audio();

    const text = await transcribeVoiceApi(blob);

    expect(text).toBe('dos cafés grandes');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/v1\/voice-sales\/agent\/transcribe$/);
    expect(init.method).toBe('POST');
    expect(init.body).toBe(blob);
    expect(init.body instanceof FormData).toBe(false);
    expect(init.headers['Content-Type']).toBe('audio/webm;codecs=opus');
    expect(init.credentials).toBe('include');
  });

  it('accepts audio/wav and does not add a store or a link to the request', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, { text: 'hola' }));
    vi.stubGlobal('fetch', fetchMock);

    await transcribeVoiceApi(audio('audio/wav'));

    const [url, init] = fetchMock.mock.calls[0];
    expect(init.headers['Content-Type']).toBe('audio/wav');
    expect(url).not.toContain('?');
    expect(url).not.toMatch(/store/i);
  });

  it('accepts exactly 5 MiB and rejects one byte more before any request', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, { text: 'x' }));
    vi.stubGlobal('fetch', fetchMock);
    expect(MAX_AUDIO_BYTES).toBe(5_242_880);

    await transcribeVoiceApi(withSize(audio(), MAX_AUDIO_BYTES));
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const error = await transcribeVoiceApi(withSize(audio(), MAX_AUDIO_BYTES + 1)).catch((e) => e);
    expect(error.status).toBe(413);
    expect(error.kind).toBe('too_large');
    expect(error.message).toMatch(/demasiado grande/i);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects an empty recording and an unsupported type before any request', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const empty = await transcribeVoiceApi(new Blob([], { type: 'audio/webm' })).catch((e) => e);
    expect(empty.kind).toBe('empty');
    expect(empty.message).toMatch(/no se grabó audio/i);

    const mp3 = await transcribeVoiceApi(audio('audio/mpeg')).catch((e) => e);
    expect(mp3.status).toBe(415);
    expect(mp3.kind).toBe('unsupported_type');

    const noType = await transcribeVoiceApi(audio('')).catch((e) => e);
    expect(noType.status).toBe(415);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('leaves nothing of the audio in browser storage', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, { text: 'x' })));
    localStorage.clear();
    sessionStorage.clear();

    await transcribeVoiceApi(audio());

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});

describe('agentTurnApi', () => {
  it('posts text, ticket and history as JSON and returns the reply with the resulting ticket', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, TURN));
    vi.stubGlobal('fetch', fetchMock);
    const ticket = { lines: [{ kind: 'PRODUCT', id: 11, quantity: 1 }], customerId: null, paymentMethodCode: 'CASH', cashReceived: null };
    const history = [{ role: 'user', text: 'hola' }, { role: 'assistant', text: 'dime' }];

    const result = await agentTurnApi({ text: 'dos cafés', ticket, history });

    expect(result).toEqual(TURN);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/v1\/voice-sales\/agent\/turn$/);
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ text: 'dos cafés', ticket, history });
  });

  it.each([
    [400, 'invalid', { message: 'Dime qué necesitas.' }, /dime qué necesitas/i],
    [403, 'forbidden', { message: 'No se permite el asistente para esta sesión.' }, /no se permite el asistente/i],
    [404, 'disabled', {}, /no está habilitado en este servidor/i],
    [413, 'too_large', { message: 'x' }, /demasiado grande/i],
    [429, 'quota', { message: 'x' }, /límite por minuto/i],
    [503, 'unavailable', { message: 'x' }, /venta manual/i],
    [500, 'unknown', { message: 'No se pudo procesar el pedido.' }, /no se pudo procesar el pedido/i],
  ])('maps a %i response to a Spanish message, keeping status and body', async (status, kind, body, message) => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(status, body)));

    const error = await agentTurnApi({ text: 'x', ticket: null, history: [] }).catch((e) => e);

    expect(error.status).toBe(status);
    expect(error.kind).toBe(kind);
    expect(error.data).toEqual(body);
    expect(error.message).toMatch(message);
  });

  it('marks the assistant as disabled for the session only after a 404', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(429, { message: 'x' })));
    await agentTurnApi({ text: 'x', ticket: null, history: [] }).catch(() => {});
    expect(isVoiceSalesDisabled()).toBe(false);

    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })));
    const error = await agentTurnApi({ text: 'x', ticket: null, history: [] }).catch((e) => e);

    expect(error.kind).toBe('disabled');
    expect(isVoiceSalesDisabled()).toBe(true);
    resetVoiceSalesAvailability();
    expect(isVoiceSalesDisabled()).toBe(false);
  });

  it('turns a network failure into a message that points to manual sales', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));

    const error = await agentTurnApi({ text: 'x', ticket: null, history: [] }).catch((e) => e);

    expect(error.kind).toBe('network');
    expect(error.message).toMatch(/venta manual/i);
  });
});
