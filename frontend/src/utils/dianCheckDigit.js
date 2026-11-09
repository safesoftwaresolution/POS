// Dígito de verificación del NIT (módulo 11, algoritmo oficial de la DIAN). El backend calcula lo mismo
// (DianCheckDigit) y rechaza un DV que no corresponda, así que ambos lados deben coincidir.
const WEIGHTS = [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71];

/** DV del NIT (ignora puntos, guiones y espacios). Sin dígitos o con más de 15 devuelve ''. */
export function calculateDv(nit) {
  const clean = String(nit ?? '').replace(/\D/g, '');
  if (!clean || clean.length > WEIGHTS.length) return '';
  let sum = 0;
  for (let i = 0; i < clean.length; i++) {
    sum += Number(clean.charAt(clean.length - 1 - i)) * WEIGHTS[i];
  }
  const rem = sum % 11;
  return String(rem === 0 || rem === 1 ? rem : 11 - rem);
}

/** «900123456-8»: el NIT como figura en el RUT. Sin NIT devuelve ''. */
export function formatNitWithDv(nit) {
  const clean = String(nit ?? '').replace(/\D/g, '');
  const dv = calculateDv(clean);
  return dv ? `${clean}-${dv}` : '';
}
