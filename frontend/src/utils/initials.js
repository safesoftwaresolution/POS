/** Iniciales (máx. 2) de un nombre para el avatar; 'US' si no hay nombre. */
export default function getInitials(name) {
  if (!name) return 'US';
  return name.split(' ').filter(Boolean).map((n) => n[0]).join('').substring(0, 2).toUpperCase();
}
