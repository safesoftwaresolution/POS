import { describe, expect, it } from 'vitest';
import { welcomeGreeting } from './greeting';

describe('welcomeGreeting', () => {
  it('usa el primer nombre', () => {
    expect(welcomeGreeting('Ana María Pérez')).toBe('¡Hola, Ana!');
    expect(welcomeGreeting('  Luis ')).toBe('¡Hola, Luis!');
  });
  it('sin nombre cae a un saludo genérico', () => {
    expect(welcomeGreeting('')).toBe('¡Bienvenido!');
    expect(welcomeGreeting('   ')).toBe('¡Bienvenido!');
    expect(welcomeGreeting(undefined)).toBe('¡Bienvenido!');
  });
});
