// Une clases condicionales (ignora valores falsy). Evita instalar clsx para un caso tan simple.
export const cx = (...parts) => parts.filter(Boolean).join(' ');
