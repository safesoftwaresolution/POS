/** Saludo de bienvenida con el primer nombre ("Ana María Pérez" -> "¡Hola, Ana!"); sin nombre, uno genérico. */
export function welcomeGreeting(fullName) {
  const first = (fullName ?? '').trim().split(/\s+/)[0];
  return first ? `¡Hola, ${first}!` : '¡Bienvenido!';
}
