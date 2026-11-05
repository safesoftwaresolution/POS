// Reglas del flujo de acceso compartidas por las pantallas públicas (login, verificación de correo).

/** A dónde va la persona después de verificar su correo y quedar con sesión iniciada. */
export function destinationAfterVerification(session) {
  if (session?.role === 'SUPER_ADMIN') return '/backoffice';
  return session?.storeId ? '/' : '/onboarding/store';
}

/** El backend rechazó el login porque la cuenta aún no verifica su correo (código `EmailNotVerified`). */
export function isEmailNotVerified(error) {
  return error?.data?.error === 'EmailNotVerified';
}
