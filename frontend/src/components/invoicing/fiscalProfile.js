// Datos del RUT del asistente de facturación: del perfil que devuelve el backend al formulario y de vuelta a
// FiscalProfileRequest. Las reglas reflejan las de FiscalProfileRequest; el servidor sigue siendo quien valida.
import { calculateDv } from '../../utils/dianCheckDigit';

/** Responsabilidades fiscales que acepta el proveedor, con el nombre con que aparecen en el RUT (casilla 53). */
export const RESPONSIBILITIES = [
  { code: 'R-99-PN', label: 'No responsable de IVA' },
  { code: 'O-13', label: 'Gran contribuyente' },
  { code: 'O-15', label: 'Autorretenedor' },
  { code: 'O-23', label: 'Agente de retención en el impuesto sobre las ventas' },
  { code: 'O-47', label: 'Régimen simple de tributación' },
];

const KNOWN = new Set(RESPONSIBILITIES.map((r) => r.code));

export const ORGANIZATION_TYPES = [
  { value: '1', label: 'Persona jurídica' },
  { value: '2', label: 'Persona natural' },
];

export const ORGANIZATION_LABELS = { 1: 'Persona jurídica', 2: 'Persona natural' };

const text = (value) => (value == null ? '' : String(value));

export function profileToForm(profile) {
  const p = profile || {};
  return {
    legalName: text(p.legalName),
    tradeName: text(p.tradeName),
    nit: text(p.nit).replace(/\D/g, ''),
    organizationType: p.organizationType === 2 ? '2' : '1',
    responsibilities: text(p.regimeCode).split(';').map((c) => c.trim()).filter((c) => KNOWN.has(c)),
    economicActivity: text(p.economicActivity),
    email: text(p.email),
    phone: text(p.phone),
    address: text(p.address),
    cityCode: text(p.cityCode),
    departmentCode: text(p.departmentCode),
  };
}

const orNull = (value) => (value.trim() ? value.trim() : null);

export function formToRequest(form) {
  const nit = form.nit.replace(/\D/g, '');
  return {
    legalName: form.legalName.trim(),
    tradeName: orNull(form.tradeName),
    nit,
    dv: calculateDv(nit),
    organizationType: Number(form.organizationType),
    regimeCode: form.responsibilities.join(';'),
    economicActivity: orNull(form.economicActivity),
    email: form.email.trim(),
    phone: orNull(form.phone),
    address: form.address.trim(),
    cityCode: form.cityCode,
    departmentCode: form.departmentCode,
  };
}

/** Errores por campo (vacío si todo está bien). */
export function validateFiscalForm(form) {
  const errors = {};
  const legalName = form.legalName.trim();
  if (legalName.length < 5) errors.legalName = 'Escribe la razón social como aparece en tu RUT (mínimo 5 letras).';
  if (!/^\d{3,15}$/.test(form.nit)) errors.nit = 'Escribe el NIT solo con números, sin el dígito de verificación.';
  if (!['1', '2'].includes(String(form.organizationType))) errors.organizationType = 'Elige el tipo de persona.';
  if (form.responsibilities.length === 0) errors.responsibilities = 'Marca al menos una responsabilidad de tu RUT.';
  if (form.economicActivity.trim() && !/^\d{4}$/.test(form.economicActivity.trim())) {
    errors.economicActivity = 'El código CIIU tiene 4 números.';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Escribe un correo válido.';
  if (!form.address.trim()) errors.address = 'Escribe la dirección de tu RUT.';
  if (!/^\d{5}$/.test(form.cityCode)) errors.cityCode = 'Elige tu municipio.';
  return errors;
}

// Palabras de los mensajes de FiscalProfileRequest y el campo del formulario donde se muestran. El orden importa:
// «El dígito de verificación no corresponde al NIT» va al NIT, igual que los mensajes del propio NIT.
const FIELD_KEYWORDS = [
  ['razón social', 'legalName'],
  ['nombre comercial', 'tradeName'],
  ['dígito de verificación', 'nit'],
  ['nit', 'nit'],
  ['tipo de organización', 'organizationType'],
  ['responsabilidades', 'responsibilities'],
  ['actividad económica', 'economicActivity'],
  ['correo', 'email'],
  ['teléfono', 'phone'],
  ['dirección', 'address'],
  ['municipio', 'cityCode'],
  ['departamento', 'cityCode'],
];

/**
 * El backend junta los errores de validación en un solo mensaje («A. B. C.»). Los reparte entre los campos;
 * lo que no reconoce queda en `general`.
 */
export function fieldErrorsFromMessage(message) {
  const fields = {};
  const unknown = [];
  String(message || '')
    .split(/\.\s+|\.$/)
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach((part) => {
      const lower = part.toLowerCase();
      const match = FIELD_KEYWORDS.find(([keyword]) => lower.includes(keyword));
      if (match && !fields[match[1]]) fields[match[1]] = part;
      else if (!match) unknown.push(part);
    });
  return { fields, general: unknown.join('. ') };
}
