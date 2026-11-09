import { describe, expect, it } from 'vitest';
import {
  buildDivipolaIndex,
  findMunicipality,
  municipalitiesOf,
  normalizeText,
  searchMunicipalities,
  toDaneCode,
} from './divipola';

const CATALOG = {
  source: 'DANE',
  departments: [
    { code: '05', name: 'Antioquia' },
    { code: '11', name: 'Bogotá, D.C.' },
    { code: '25', name: 'Cundinamarca' },
  ],
  municipalities: [
    { code: '05001', name: 'Medellín', departmentCode: '05' },
    { code: '05002', name: 'Abejorral', departmentCode: '05' },
    { code: '11001', name: 'Bogotá, D.C.', departmentCode: '11' },
    { code: '25175', name: 'Chía', departmentCode: '25' },
    { code: '25001', name: 'Agua de Dios', departmentCode: '25' },
  ],
};

const index = buildDivipolaIndex(CATALOG);

describe('normalizeText', () => {
  it('quita tildes, mayúsculas y espacios de más', () => {
    expect(normalizeText('  Bogotá, D.C. ')).toBe('bogota, d.c.');
    expect(normalizeText('MEDELLÍN')).toBe('medellin');
    expect(normalizeText(null)).toBe('');
  });
});

describe('toDaneCode', () => {
  it('completa con ceros el código que perdió el cero inicial', () => {
    expect(toDaneCode(5001)).toBe('05001');
    expect(toDaneCode('11001')).toBe('11001');
    expect(toDaneCode('')).toBe('');
    expect(toDaneCode(null)).toBe('');
    expect(toDaneCode('abc')).toBe('');
  });
});

describe('municipalitiesOf', () => {
  it('devuelve los municipios del departamento ordenados por nombre', () => {
    expect(municipalitiesOf(index, '05').map((m) => m.name)).toEqual(['Abejorral', 'Medellín']);
    expect(municipalitiesOf(index, '25').map((m) => m.code)).toEqual(['25001', '25175']);
  });

  it('sin departamento o con uno que no existe no hay municipios', () => {
    expect(municipalitiesOf(index, '')).toEqual([]);
    expect(municipalitiesOf(index, '99')).toEqual([]);
  });
});

describe('findMunicipality', () => {
  it('encuentra el municipio y su departamento por código', () => {
    const found = findMunicipality(index, '05001');
    expect(found.name).toBe('Medellín');
    expect(found.department.name).toBe('Antioquia');
  });

  it('acepta el código numérico sin el cero inicial', () => {
    expect(findMunicipality(index, 5001)?.name).toBe('Medellín');
  });

  it('un código desconocido o vacío no encuentra nada', () => {
    expect(findMunicipality(index, '99999')).toBeNull();
    expect(findMunicipality(index, '')).toBeNull();
  });
});

describe('searchMunicipalities', () => {
  it('busca por nombre sin importar tildes ni mayúsculas', () => {
    expect(searchMunicipalities(index, 'medellin').map((m) => m.code)).toEqual(['05001']);
    expect(searchMunicipalities(index, 'CHIA').map((m) => m.code)).toEqual(['25175']);
  });

  it('primero los que empiezan por el texto', () => {
    expect(searchMunicipalities(index, 'a').map((m) => m.name)).toEqual(['Abejorral', 'Agua de Dios', 'Bogotá, D.C.', 'Chía', 'Medellín']);
  });

  it('también busca por el nombre del departamento y por el código', () => {
    expect(searchMunicipalities(index, 'antioquia').map((m) => m.code)).toEqual(['05002', '05001']);
    expect(searchMunicipalities(index, '11001').map((m) => m.name)).toEqual(['Bogotá, D.C.']);
  });

  it('respeta el límite y no devuelve nada sin texto', () => {
    expect(searchMunicipalities(index, 'a', 2)).toHaveLength(2);
    expect(searchMunicipalities(index, '  ')).toEqual([]);
  });
});

describe('buildDivipolaIndex', () => {
  it('con un catálogo vacío o nulo no falla', () => {
    const empty = buildDivipolaIndex(null);
    expect(empty.departments).toEqual([]);
    expect(findMunicipality(empty, '05001')).toBeNull();
    expect(searchMunicipalities(empty, 'medellin')).toEqual([]);
  });

  it('ordena los departamentos por nombre', () => {
    expect(index.departments.map((d) => d.code)).toEqual(['05', '11', '25']);
  });
});
