import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import useVoiceAgent, { HISTORY_LIMIT } from './useVoiceAgent';
import { agentTurnApi, transcribeVoiceApi } from '../services/voiceSalesApi';

vi.mock('../services/voiceSalesApi', () => ({ agentTurnApi: vi.fn(), transcribeVoiceApi: vi.fn() }));

const CAFE = { id: 11, name: 'Café grande', price: 3500, stock: 40, quantity: 1 };
const ticketState = (overrides = {}) => ({ cart: [CAFE], customer: { id: 1 }, paymentMethod: 'CASH', cashTendered: '', ...overrides });
const response = (reply = 'Listo.') => ({ reply, ticket: { lines: [], customer: null }, warnings: [] });

const setup = (state = ticketState()) => {
  const onTurn = vi.fn();
  const hook = renderHook(({ s }) => useVoiceAgent({ ticketState: s, onTurn }), { initialProps: { s: state } });
  return { ...hook, onTurn };
};

beforeEach(() => {
  vi.resetAllMocks();
  agentTurnApi.mockResolvedValue(response());
});

describe('useVoiceAgent', () => {
  it('sends the text with the current ticket (ids and quantities only) and hands the response to onTurn', async () => {
    const { result, onTurn } = setup();

    let ok;
    await act(async () => { ok = await result.current.sendText('  dos cafés  '); });

    expect(ok).toBe(true);
    expect(agentTurnApi).toHaveBeenCalledWith({
      text: 'dos cafés',
      ticket: { lines: [{ kind: 'PRODUCT', id: 11, quantity: 1 }], customerId: 1, paymentMethodCode: 'CASH', cashReceived: null },
      history: [],
    });
    expect(onTurn).toHaveBeenCalledWith(response());
    expect(result.current.messages).toEqual([{ role: 'user', text: 'dos cafés' }, { role: 'assistant', text: 'Listo.' }]);
    expect(result.current.status).toBe('idle');
    expect(result.current.error).toBe('');
  });

  it('ignores empty text without calling the server', async () => {
    const { result } = setup();

    await act(async () => { await result.current.sendText('   '); });

    expect(agentTurnApi).not.toHaveBeenCalled();
    expect(result.current.messages).toEqual([]);
  });

  it('sends only the last turns as history', async () => {
    const { result } = setup();
    for (let i = 0; i < 6; i += 1) {
      await act(async () => { await result.current.sendText(`pedido ${i}`); });
    }

    await act(async () => { await result.current.sendText('último'); });

    const { history } = agentTurnApi.mock.calls.at(-1)[0];
    expect(history).toHaveLength(HISTORY_LIMIT);
    expect(history.at(-1)).toEqual({ role: 'assistant', text: 'Listo.' });
    expect(history.at(-2)).toEqual({ role: 'user', text: 'pedido 5' });
  });

  it('transcribes audio first and then runs the turn with the transcript', async () => {
    transcribeVoiceApi.mockResolvedValue(' dos cafés y cobra ');
    const { result, onTurn } = setup();
    const blob = new Blob([new Uint8Array([1])], { type: 'audio/webm' });

    await act(async () => { await result.current.sendAudio(blob); });

    expect(transcribeVoiceApi).toHaveBeenCalledWith(blob);
    expect(agentTurnApi.mock.calls[0][0].text).toBe('dos cafés y cobra');
    expect(onTurn).toHaveBeenCalledTimes(1);
  });

  it('does not run a turn when the audio has no words', async () => {
    transcribeVoiceApi.mockResolvedValue('   ');
    const { result, onTurn } = setup();

    await act(async () => { await result.current.sendAudio(new Blob([new Uint8Array([1])], { type: 'audio/webm' })); });

    expect(agentTurnApi).not.toHaveBeenCalled();
    expect(onTurn).not.toHaveBeenCalled();
  });

  it('reports the status while it works and ignores a second send meanwhile', async () => {
    let finish;
    agentTurnApi.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const { result } = setup();

    let first;
    act(() => { first = result.current.sendText('uno'); });
    expect(result.current.status).toBe('thinking');
    expect(result.current.busy).toBe(true);

    let second;
    await act(async () => { second = await result.current.sendText('dos'); });
    expect(second).toBe(false);
    expect(agentTurnApi).toHaveBeenCalledTimes(1);

    await act(async () => { finish(response()); await first; });
    expect(result.current.busy).toBe(false);
  });

  it('shows the error, keeps the ticket untouched and lets the cashier try again', async () => {
    agentTurnApi.mockRejectedValueOnce(Object.assign(new Error('El asistente no está disponible ahora.'), { kind: 'unavailable' }));
    const { result, onTurn } = setup();

    await act(async () => { await result.current.sendText('hola'); });

    expect(result.current.error).toBe('El asistente no está disponible ahora.');
    expect(result.current.disabled).toBe(false);
    expect(onTurn).not.toHaveBeenCalled();

    await act(async () => { await result.current.sendText('otra vez'); });
    expect(result.current.error).toBe('');
    expect(onTurn).toHaveBeenCalledTimes(1);
  });

  it('marks itself disabled when the server says the assistant is off', async () => {
    agentTurnApi.mockRejectedValueOnce(Object.assign(new Error('No habilitado'), { kind: 'disabled' }));
    const { result } = setup();

    await act(async () => { await result.current.sendText('hola'); });

    expect(result.current.disabled).toBe(true);
  });

  it('drops the response when the ticket changed while the assistant was answering', async () => {
    let finish;
    agentTurnApi.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const { result, rerender, onTurn } = setup();

    let pending;
    act(() => { pending = result.current.sendText('quita el café'); });
    rerender({ s: ticketState({ cart: [{ ...CAFE, quantity: 5 }] }) }); // el cajero toco la cantidad
    await act(async () => { finish(response('Quité el café.')); await pending; });

    expect(onTurn).not.toHaveBeenCalled();
    expect(result.current.error).toMatch(/ticket cambió/i);
  });

  it('clears the conversation on reset', async () => {
    const { result } = setup();
    await act(async () => { await result.current.sendText('hola'); });

    act(() => result.current.reset());

    expect(result.current.messages).toEqual([]);
  });
});
