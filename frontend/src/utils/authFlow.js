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

/**
 * A dónde sacar a quien abre `/onboarding/store` sin tener que registrar un local: sin sesión al login, el
 * operador de la plataforma al backoffice, y quien ya tiene local (o no es ADMINISTRATOR) a la app.
 * `null` = debe registrar su local.
 */
export function onboardingRedirect(user) {
  if (!user) return '/login';
  if (user.role === 'SUPER_ADMIN') return '/backoffice';
  if (user.role !== 'ADMINISTRATOR' || user.storeId) return '/';
  return null;
}
