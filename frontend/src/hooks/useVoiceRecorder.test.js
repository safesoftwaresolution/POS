import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import useVoiceRecorder, { MAX_RECORDING_SECONDS } from './useVoiceRecorder';
import { MAX_AUDIO_BYTES } from '../services/voiceSalesApi';

// ---- dobles de MediaRecorder y del micrófono ----
class FakeRecorder {
  static instances = [];
  static supportedTypes = ['audio/webm;codecs=opus', 'audio/webm'];
  static isTypeSupported = (type) => FakeRecorder.supportedTypes.includes(type);
  static nextData = () => new Blob([new Uint8Array([1, 2, 3, 4])], { type: 'audio/webm;codecs=opus' });

  constructor(stream, options = {}) {
    this.stream = stream;
    this.options = options;
    this.mimeType = options.mimeType ?? 'audio/webm';
    this.state = 'inactive';
    this.stopCalls = 0;
    FakeRecorder.instances.push(this);
  }

  start() { this.state = 'recording'; }

  stop() {
    this.stopCalls += 1;
    if (this.state === 'inactive') return;
    this.state = 'inactive';
    this.ondataavailable?.({ data: FakeRecorder.nextData() });
    this.onstop?.();
  }
}

function makeStream() {
  const tracks = [{ stop: vi.fn() }, { stop: vi.fn() }];
  return { tracks, getTracks: () => tracks };
}

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
};

let stream;
let getUserMedia;

function stubBrowser({ mediaDevices = { getUserMedia }, recorder = FakeRecorder } = {}) {
  vi.stubGlobal('navigator', { mediaDevices });
  vi.stubGlobal('MediaRecorder', recorder);
}

beforeEach(() => {
  vi.useFakeTimers();
  FakeRecorder.instances = [];
  FakeRecorder.supportedTypes = ['audio/webm;codecs=opus', 'audio/webm'];
  FakeRecorder.nextData = () => new Blob([new Uint8Array([1, 2, 3, 4])], { type: 'audio/webm;codecs=opus' });
  stream = makeStream();
  getUserMedia = vi.fn(async () => stream);
  stubBrowser();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const setup = (options = {}) => {
  const onRecorded = vi.fn();
  const view = renderHook(() => useVoiceRecorder({ onRecorded, ...options }));
  return { ...view, onRecorded };
};
const startRecording = async (view) => { await act(async () => { await view.result.current.start(); }); };
const trackStops = () => stream.tracks.map((t) => t.stop.mock.calls.length);

describe('support detection', () => {
  it('explains when the browser has no microphone API', () => {
    stubBrowser({ mediaDevices: null });
    const { result } = setup();
    expect(result.current.supported).toBe(false);
    expect(result.current.unsupportedMessage).toMatch(/micrófono/i);
  });

  it('explains when MediaRecorder does not exist', () => {
    vi.stubGlobal('MediaRecorder', undefined);
    const { result } = setup();
    expect(result.current.supported).toBe(false);
    expect(result.current.unsupportedMessage).toMatch(/grabar audio/i);
  });

  it('explains when the browser cannot record WebM', () => {
    FakeRecorder.supportedTypes = ['audio/mp4'];
    const { result } = setup();
    expect(result.current.supported).toBe(false);
    expect(result.current.unsupportedMessage).toMatch(/webm/i);
  });

  it('never asks for the microphone when unsupported, and says why', async () => {
    FakeRecorder.supportedTypes = [];
    const view = setup();
    await startRecording(view);
    expect(getUserMedia).not.toHaveBeenCalled();
    expect(view.result.current.error).toMatch(/webm/i);
    expect(view.result.current.status).toBe('idle');
  });
});

describe('recording', () => {
  it('asks for audio only and records WebM', async () => {
    const view = setup();
    expect(view.result.current.supported).toBe(true);

    await startRecording(view);

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(FakeRecorder.instances).toHaveLength(1);
    expect(FakeRecorder.instances[0].options.mimeType).toBe('audio/webm;codecs=opus');
    expect(FakeRecorder.instances[0].state).toBe('recording');
    expect(view.result.current.status).toBe('recording');
  });

  it('falls back to plain audio/webm when the opus variant is not supported', async () => {
    FakeRecorder.supportedTypes = ['audio/webm'];
    const view = setup();
    await startRecording(view);
    expect(FakeRecorder.instances[0].options.mimeType).toBe('audio/webm');
  });

  it('stops, hands over one audio Blob and releases every microphone track', async () => {
    const view = setup();
    await startRecording(view);

    act(() => { view.result.current.stop(); });

    expect(view.onRecorded).toHaveBeenCalledTimes(1);
    const blob = view.onRecorded.mock.calls[0][0];
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.size).toBe(4);
    expect(blob.type).toMatch(/^audio\/webm/);
    expect(trackStops()).toEqual([1, 1]);
    expect(view.result.current.status).toBe('idle');
    expect(view.result.current.elapsed).toBe(0);
  });

  it('ignores a second start while a recording is in progress', async () => {
    const view = setup();
    await startRecording(view);
    await startRecording(view);
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    expect(FakeRecorder.instances).toHaveLength(1);
  });

  it('counts elapsed seconds while recording', async () => {
    const view = setup();
    await startRecording(view);
    act(() => { vi.advanceTimersByTime(3000); });
    expect(view.result.current.elapsed).toBe(3);
  });

  it('stops by itself at the 45 second cap and delivers the audio once', async () => {
    expect(MAX_RECORDING_SECONDS).toBe(45);
    const view = setup();
    await startRecording(view);

    act(() => { vi.advanceTimersByTime(44_999); });
    expect(view.onRecorded).not.toHaveBeenCalled();
    expect(view.result.current.status).toBe('recording');

    act(() => { vi.advanceTimersByTime(1); });
    expect(view.onRecorded).toHaveBeenCalledTimes(1);
    expect(trackStops()).toEqual([1, 1]);
    expect(view.result.current.status).toBe('idle');

    act(() => { vi.advanceTimersByTime(60_000); });
    expect(view.onRecorded).toHaveBeenCalledTimes(1);
  });

  it('does not fire the cap timer after a manual stop', async () => {
    const view = setup();
    await startRecording(view);
    act(() => { view.result.current.stop(); });
    act(() => { vi.advanceTimersByTime(60_000); });
    expect(FakeRecorder.instances[0].stopCalls).toBe(1);
    expect(view.onRecorded).toHaveBeenCalledTimes(1);
  });
});

describe('rejections before any upload', () => {
  it('rejects a recording over 5 MiB: no callback, an error and the microphone released', async () => {
    FakeRecorder.nextData = () => new Blob([new Uint8Array(MAX_AUDIO_BYTES + 1)], { type: 'audio/webm' });
    const view = setup();
    await startRecording(view);

    act(() => { view.result.current.stop(); });

    expect(view.onRecorded).not.toHaveBeenCalled();
    expect(view.result.current.error).toMatch(/supera los 5 MB/i);
    expect(trackStops()).toEqual([1, 1]);
    expect(view.result.current.status).toBe('idle');
  });

  it('accepts a recording of exactly 5 MiB', async () => {
    FakeRecorder.nextData = () => new Blob([new Uint8Array(MAX_AUDIO_BYTES)], { type: 'audio/webm' });
    const view = setup();
    await startRecording(view);
    act(() => { view.result.current.stop(); });
    expect(view.onRecorded).toHaveBeenCalledTimes(1);
  });

  it('rejects an empty recording', async () => {
    FakeRecorder.nextData = () => new Blob([], { type: 'audio/webm' });
    const view = setup();
    await startRecording(view);
    act(() => { view.result.current.stop(); });
    expect(view.onRecorded).not.toHaveBeenCalled();
    expect(view.result.current.error).toMatch(/no se grabó audio/i);
  });
});

describe('failures and cleanup', () => {
  it.each([
    ['NotAllowedError', /permiso/i],
    ['NotFoundError', /no se encontró un micrófono/i],
    ['AbortError', /no se pudo acceder al micrófono/i],
  ])('maps a %s from getUserMedia to a message and records nothing', async (name, message) => {
    getUserMedia.mockRejectedValueOnce(Object.assign(new Error('x'), { name }));
    const view = setup();

    await startRecording(view);

    expect(view.result.current.error).toMatch(message);
    expect(view.result.current.status).toBe('idle');
    expect(FakeRecorder.instances).toHaveLength(0);
    expect(view.onRecorded).not.toHaveBeenCalled();
  });

  it('releases the tracks and reports an error when the recorder fails', async () => {
    const view = setup();
    await startRecording(view);

    act(() => { FakeRecorder.instances[0].onerror?.({ error: new Error('boom') }); });

    expect(trackStops()).toEqual([1, 1]);
    expect(view.result.current.error).toMatch(/falló la grabación/i);
    expect(view.result.current.status).toBe('idle');
    expect(view.onRecorded).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(60_000); });
    expect(view.onRecorded).not.toHaveBeenCalled();
  });

  it('releases the tracks, stops the recorder and discards the audio when unmounted while recording', async () => {
    const view = setup();
    await startRecording(view);

    view.unmount();

    expect(trackStops()).toEqual([1, 1]);
    expect(FakeRecorder.instances[0].state).toBe('inactive');
    expect(view.onRecorded).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(60_000); });
    expect(view.onRecorded).not.toHaveBeenCalled();
  });

  it('releases the microphone at once if it is granted after the component unmounted', async () => {
    const pending = deferred();
    getUserMedia.mockReturnValueOnce(pending.promise);
    const view = setup();
    let started;
    act(() => { started = view.result.current.start(); });
    expect(view.result.current.status).toBe('requesting');

    view.unmount();
    await act(async () => { pending.resolve(stream); await started; });

    expect(trackStops()).toEqual([1, 1]);
    expect(FakeRecorder.instances).toHaveLength(0);
  });

  it('allows recording again after an error, clearing the previous message', async () => {
    getUserMedia.mockRejectedValueOnce(Object.assign(new Error('x'), { name: 'NotAllowedError' }));
    const view = setup();
    await startRecording(view);
    expect(view.result.current.error).not.toBe('');

    await startRecording(view);

    expect(view.result.current.error).toBe('');
    expect(view.result.current.status).toBe('recording');
  });

  it('keeps the audio out of every browser store and never makes object URLs', async () => {
    const createObjectURL = vi.fn();
    vi.stubGlobal('URL', Object.assign(function URLStub() {}, { createObjectURL }));
    localStorage.clear();
    sessionStorage.clear();
    const view = setup();

    await startRecording(view);
    act(() => { view.result.current.stop(); });

    expect(createObjectURL).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
