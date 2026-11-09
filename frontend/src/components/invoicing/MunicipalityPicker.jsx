import React, { useId, useMemo, useState } from 'react';
import { MapPin } from 'lucide-react';
import useDivipola from '../../hooks/useDivipola';
import { Field, inputClass } from '../ui/Field';
import { cx } from '../ui/cx';
import { normalizeText } from '../../utils/divipola';

/**
 * Departamento + municipio (catálogo DIVIPOLA del DANE) sin pedir códigos: el usuario elige el departamento y
 * escribe o elige su municipio. Controlado: `cityCode` / `departmentCode` y `onChange({ cityCode, departmentCode })`.
 * Si escribe el municipio sin elegir departamento, busca en todo el país y completa el departamento.
 */
export default function MunicipalityPicker({ cityCode, departmentCode, onChange, error, disabled, required }) {
  const { departments, municipalitiesOf, findMunicipality, search, loading, error: loadError } = useDivipola();
  const listId = useId();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  const selected = findMunicipality(cityCode);
  const department = selected?.departmentCode || departmentCode || '';

  const options = useMemo(() => {
    const q = normalizeText(query);
    if (department) {
      const all = municipalitiesOf(department);
      return q ? all.filter((m) => m.nameText.includes(q)) : all;
    }
    return search(query, 30);
  }, [department, query, municipalitiesOf, search]);

  const choose = (m) => {
    onChange({ cityCode: m.code, departmentCode: m.departmentCode });
    setQuery('');
    setOpen(false);
  };

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setOpen(true);
      setActive((i) => Math.min(i + 1, options.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter' && open && options[active]) {
      e.preventDefault();
      choose(options[active]);
    } else if (e.key === 'Escape') {
      setOpen(false);
      setQuery('');
    }
  };

  const showList = open && !disabled;
  const placeholder = loading
    ? 'Cargando municipios...'
    : department ? 'Escribe o elige tu municipio' : 'Escribe tu municipio';

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <Field label="Departamento">
        <select
          className={inputClass}
          value={department}
          disabled={disabled || loading}
          onChange={(e) => {
            onChange({ cityCode: '', departmentCode: e.target.value });
            setQuery('');
          }}
        >
          <option value="">Elige tu departamento</option>
          {departments.map((d) => <option key={d.code} value={d.code}>{d.name}</option>)}
        </select>
      </Field>

      <Field label={required ? 'Municipio *' : 'Municipio'} error={error || loadError}>
        <div className="relative">
          <MapPin className="w-3.5 h-3.5 text-ink-muted absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            role="combobox"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList && options[active] ? `${listId}-${options[active].code}` : undefined}
            autoComplete="off"
            disabled={disabled || loading}
            placeholder={placeholder}
            value={open ? query : (selected?.name || '')}
            onFocus={() => { setOpen(true); setActive(0); }}
            onBlur={() => setOpen(false)}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); setActive(0); }}
            onKeyDown={onKeyDown}
            className={cx(inputClass, 'pl-8', error && 'border-red-400')}
          />
          {showList && (
            <ul
              id={listId}
              role="listbox"
              className="absolute z-20 mt-1 w-full max-h-56 overflow-y-auto rounded-2xl border border-line bg-card shadow-xl p-1"
            >
              {options.length === 0 ? (
                <li className="px-3 py-2 text-[11px] text-ink-muted">
                  {department || query.trim() ? 'No encontramos ese municipio.' : 'Elige el departamento o escribe el nombre.'}
                </li>
              ) : options.map((m, i) => (
                <li
                  key={m.code}
                  id={`${listId}-${m.code}`}
                  role="option"
                  aria-selected={m.code === cityCode}
                  // onMouseDown para elegir antes de que el blur cierre la lista.
                  onMouseDown={(e) => { e.preventDefault(); choose(m); }}
                  onMouseEnter={() => setActive(i)}
                  className={cx(
                    'px-3 py-2 rounded-xl text-xs font-semibold cursor-pointer flex items-center justify-between gap-2',
                    i === active ? 'bg-gray-100 dark:bg-white/10 text-ink' : 'text-ink-soft',
                  )}
                >
                  <span className="truncate">{m.name}</span>
                  {!department && <span className="text-[10px] text-ink-muted shrink-0">{m.department?.name}</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      </Field>
    </div>
  );
}
