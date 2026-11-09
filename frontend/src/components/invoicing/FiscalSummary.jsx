import React from 'react';
import useDivipola from '../../hooks/useDivipola';
import { ORGANIZATION_LABELS, RESPONSIBILITIES } from './fiscalProfile';

/** Resumen de los datos fiscales guardados (solo lectura): lo ven el supervisor y el back office. */
export default function FiscalSummary({ profile }) {
  const { findMunicipality } = useDivipola();
  if (!profile) return null;
  const municipality = findMunicipality(profile.cityCode);
  const responsibilities = (profile.regimeCode || '')
    .split(';')
    .map((code) => RESPONSIBILITIES.find((r) => r.code === code)?.label)
    .filter(Boolean)
    .join(', ');
  const rows = [
    ['Razón social', profile.legalName],
    ['Nombre comercial', profile.tradeName],
    ['NIT', profile.nit ? `${profile.nit}${profile.dv ? `-${profile.dv}` : ''}` : null],
    ['Tipo de persona', ORGANIZATION_LABELS[profile.organizationType]],
    ['Responsabilidades', responsibilities],
    ['Correo de facturación', profile.email],
    ['Teléfono', profile.phone],
    ['Dirección', profile.address],
    ['Municipio', municipality ? `${municipality.name}, ${municipality.department?.name || ''}` : null],
    ['Actividad económica (CIIU)', profile.economicActivity],
  ];
  return (
    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2.5 p-4 rounded-2xl bg-page border border-line text-xs">
      {rows.filter(([, value]) => value).map(([label, value]) => (
        <div key={label} className="min-w-0">
          <dt className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">{label}</dt>
          <dd className="font-semibold text-ink break-words">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
