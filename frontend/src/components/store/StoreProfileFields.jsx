import React from 'react';
import { Store, Phone, Mail, MapPin, Globe, FileText } from 'lucide-react';

const INPUT_CLASS = 'w-full pl-10 pr-4 py-2.5 bg-gray-50 dark:bg-[#0E0E12] border border-line rounded-2xl text-xs font-semibold focus:outline-none focus:border-ink transition-all';

function IconInput({ label, icon: Icon, field, values, onChange, ...inputProps }) {
  return (
    <div className="space-y-1">
      <label htmlFor={`store-${field}`} className="text-xs font-bold text-ink-soft block">{label}</label>
      <div className="relative">
        <Icon className="w-4 h-4 text-gray-400 absolute left-3.5 top-3" />
        <input
          id={`store-${field}`}
          value={values[field] ?? ''}
          onChange={onChange(field)}
          className={INPUT_CLASS}
          {...inputProps}
        />
      </div>
    </div>
  );
}

/**
 * Campos del perfil del local, compartidos por el alta (`/onboarding/store`) y la corrección de un local rechazado.
 * El correo solo se pide en el alta: después queda atado a la verificación y lo cambia el backoffice.
 */
export default function StoreProfileFields({ values, onChange, includeEmail = false }) {
  const props = { values, onChange };
  return (
    <>
      <IconInput {...props} label="Nombre del local:" icon={Store} field="name" type="text" placeholder="Mi Tienda" maxLength={100} required />
      {includeEmail && (
        <IconInput {...props} label="Correo del local:" icon={Mail} field="email" type="email" placeholder="contacto@mitienda.com" maxLength={100} required />
      )}
      <div className="grid grid-cols-2 gap-3">
        <IconInput {...props} label="Teléfono:" icon={Phone} field="phone" type="tel" placeholder="3001234567" maxLength={20} />
        <IconInput {...props} label="NIT / Tax ID:" icon={FileText} field="taxId" type="text" placeholder="900123456-7" maxLength={50} />
      </div>
      <IconInput {...props} label="Dirección:" icon={MapPin} field="address" type="text" placeholder="Calle 123 #45-67" maxLength={255} />
      <IconInput {...props} label="Sitio web (opcional):" icon={Globe} field="website" type="text" placeholder="https://mitienda.com" maxLength={255} />
    </>
  );
}
