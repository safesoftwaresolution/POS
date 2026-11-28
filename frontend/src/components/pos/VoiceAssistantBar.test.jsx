import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import VoiceAssistantBar from './VoiceAssistantBar';
import useVoiceRecorder from '../../hooks/useVoiceRecorder';
import { isVoiceSalesDisabled } from '../../services/voiceSalesApi';

vi.mock('../../hooks/useVoiceRecorder', () => ({ default: vi.fn() }));
vi.mock('../../services/voiceSalesApi', () => ({ isVoiceSalesDisabled: vi.fn(() => false) }));

const recorder = (overrides = {}) => ({
  status: 'idle', elapsed: 0, supported: true, unsupportedMessage: 'Tu navegador no puede grabar.',
  error: '', start: vi.fn(), stop: vi.fn(), clearError: vi.fn(), ...overrides,
});

const agentOf = (overrides = {}) => ({
  messages: [], notes: [], status: 'idle', busy: false, error: '', disabled: false,
  sendText: vi.fn(async () => true), sendAudio: vi.fn(), clearError: vi.fn(), ...overrides,
});

beforeEach(() => {
  vi.mocked(isVoiceSalesDisabled).mockReturnValue(false);
  vi.mocked(useVoiceRecorder).mockReturnValue(recorder());
});
afterEach(cleanup);

const input = () => screen.getByLabelText(/escríbele al asistente/i);
const type = (value) => fireEvent.change(input(), { target: { value } });
const sendButton = () => screen.getByRole('button', { name: /enviar al asistente/i });

describe('VoiceAssistantBar', () => {
  it('sends what the cashier typed, clears the field and does not open any dialog', async () => {
    const agent = agentOf();
    render(<VoiceAssistantBar agent={agent} />);

    type('dos cafés y una agua');
    fireEvent.click(sendButton());

    await waitFor(() => expect(agent.sendText).toHaveBeenCalledWith('dos cafés y una agua'));
    expect(input().value).toBe('');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('sends with Enter, but never an empty text', async () => {
    const agent = agentOf();
    render(<VoiceAssistantBar agent={agent} />);

    fireEvent.submit(input().closest('form'));
    expect(agent.sendText).not.toHaveBeenCalled();
    expect(sendButton().disabled).toBe(true);

    type('hola');
    fireEvent.submit(input().closest('form'));
    await waitFor(() => expect(agent.sendText).toHaveBeenCalledWith('hola'));
  });

  it('puts the text back when sending failed, so nothing typed is lost', async () => {
    const agent = agentOf({ sendText: vi.fn(async () => false) });
    render(<VoiceAssistantBar agent={agent} />);

    type('dos cafés');
    fireEvent.click(sendButton());

    await waitFor(() => expect(input().value).toBe('dos cafés'));
  });

  it('shows the last thing said, the assistant reply and the server notes', () => {
    render(<VoiceAssistantBar agent={agentOf({
      messages: [
        { role: 'user', text: 'agrega una gaseosa' }, { role: 'assistant', text: '¿Cola o limón?' },
        { role: 'user', text: 'cola' }, { role: 'assistant', text: 'Listo: una gaseosa cola.' },
      ],
      notes: ['Se quitó del ticket un artículo que ya no está disponible.'],
    })} />);

    expect(screen.getByText(/dijiste: «cola»/i)).toBeTruthy();
    expect(screen.getByText('Listo: una gaseosa cola.')).toBeTruthy();
    expect(screen.queryByText('¿Cola o limón?')).toBeNull();
    expect(screen.getByText(/ya no está disponible/i)).toBeTruthy();
  });

  it('shows what it is doing while working and locks the inputs', () => {
    render(<VoiceAssistantBar agent={agentOf({ status: 'thinking', busy: true })} />);

    expect(screen.getByText(/armando el ticket/i)).toBeTruthy();
    expect(input().disabled).toBe(true);
  });

  it('shows an error and lets the cashier dismiss it', () => {
    const agent = agentOf({ error: 'El asistente no está disponible ahora. Puedes seguir con la venta manual.' });
    render(<VoiceAssistantBar agent={agent} />);

    expect(screen.getByRole('alert').textContent).toMatch(/venta manual/i);
    fireEvent.click(screen.getByRole('button', { name: /cerrar aviso/i }));

    expect(agent.clearError).toHaveBeenCalled();
  });

  it('starts recording with the microphone and stops with a second tap', () => {
    const idle = recorder();
    vi.mocked(useVoiceRecorder).mockReturnValue(idle);
    const { rerender } = render(<VoiceAssistantBar agent={agentOf()} />);

    fireEvent.click(screen.getByRole('button', { name: /hablarle al asistente/i }));
    expect(idle.start).toHaveBeenCalledTimes(1);

    const recording = recorder({ status: 'recording', elapsed: 7 });
    vi.mocked(useVoiceRecorder).mockReturnValue(recording);
    rerender(<VoiceAssistantBar agent={agentOf()} />);
    fireEvent.click(screen.getByRole('button', { name: /detener, 0:07/i }));
    expect(recording.stop).toHaveBeenCalledTimes(1);
    expect(input().disabled).toBe(true);
  });

  it('hands the recorded audio to the agent', () => {
    const agent = agentOf();
    render(<VoiceAssistantBar agent={agent} />);

    expect(vi.mocked(useVoiceRecorder).mock.calls[0][0].onRecorded).toBe(agent.sendAudio);
  });

  it('explains why the microphone is unavailable instead of failing silently', () => {
    vi.mocked(useVoiceRecorder).mockReturnValue(recorder({ supported: false }));
    render(<VoiceAssistantBar agent={agentOf()} />);

    const mic = screen.getByRole('button', { name: /hablarle al asistente/i });
    expect(mic.getAttribute('aria-disabled')).toBe('true');
    expect(mic.getAttribute('title')).toBe('Tu navegador no puede grabar.');
  });

  it('disappears when the server says the assistant is off', () => {
    const { container, rerender } = render(<VoiceAssistantBar agent={agentOf({ disabled: true })} />);
    expect(container.innerHTML).toBe('');

    rerender(<VoiceAssistantBar agent={agentOf()} />);
    expect(screen.getByTestId('voice-assistant')).toBeTruthy();

    vi.mocked(isVoiceSalesDisabled).mockReturnValue(true);
    rerender(<VoiceAssistantBar agent={agentOf()} />);
    expect(container.innerHTML).toBe('');
  });
});
