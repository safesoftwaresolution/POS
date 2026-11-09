import { describe, expect, it } from 'vitest';
import { fieldErrorsFromMessage, formToRequest, profileToForm, validateFiscalForm } from './fiscalProfile';

const VALID = {
  legalName: 'Tienda La Esquina SAS',
  tradeName: '',
  nit: '900123456',
  organizationType: '1',
  responsibilities: ['O-13', 'O-47'],
  economicActivity: '',
  email: 'conta@esquina.co',
  phone: '',
  address: 'Calle 10 # 5-20',
  cityCode: '05001',
  departmentCode: '05',
};

describe('profileToForm', () => {
  it('toma los datos guardados o sugeridos y separa las responsabilidades', () => {
    const form = profileToForm({ legalName: 'La Esquina', nit: '900.123.456', organizationType: 2, regimeCode: 'O-13;O-47', cityCode: '05001', departmentCode: '05' });
    expect(form.nit).toBe('900123456');
    expect(form.organizationType).toBe('2');
    expect(form.responsibilities).toEqual(['O-13', 'O-47']);
    expect(form.cityCode).toBe('05001');
  });

  it('sin perfil arranca vacío como persona jurídica y sin responsabilidades', () => {
    const form = profileToForm(null);
    expect(form.organizationType).toBe('1');
    expect(form.responsibilities).toEqual([]);
    expect(form.legalName).toBe('');
  });

  it('descarta responsabilidades que no conoce', () => {
    expect(profileToForm({ regimeCode: 'O-13;ZZ' }).responsibilities).toEqual(['O-13']);
  });
});

describe('formToRequest', () => {
  it('calcula el DV y une las responsabilidades con ;', () => {
    const request = formToRequest(VALID);
    expect(request.dv).toBe('8');
    expect(request.regimeCode).toBe('O-13;O-47');
    expect(request.organizationType).toBe(1);
    expect(request.economicActivity).toBeNull();
    expect(request.phone).toBeNull();
    expect(request.departmentCode).toBe('05');
  });
});

describe('validateFiscalForm', () => {
  it('un formulario completo no tiene errores', () => {
    expect(validateFiscalForm(VALID)).toEqual({});
  });

  it('marca cada campo con su problema', () => {
    const errors = validateFiscalForm({
      ...VALID, legalName: 'abc', nit: '12', responsibilities: [], economicActivity: '47', email: 'x', address: ' ', cityCode: '',
    });
    expect(Object.keys(errors).sort()).toEqual(
      ['address', 'cityCode', 'economicActivity', 'email', 'legalName', 'nit', 'responsibilities'].sort(),
    );
  });
});

describe('fieldErrorsFromMessage', () => {
  it('reparte los mensajes de validación del servidor entre los campos', () => {
    const { fields, general } = fieldErrorsFromMessage(
      'El correo no es válido. El dígito de verificación no corresponde al NIT. El departamento no corresponde al municipio (son los dos primeros dígitos de su código DANE).',
    );
    expect(fields.email).toBe('El correo no es válido');
    expect(fields.nit).toBe('El dígito de verificación no corresponde al NIT');
    expect(fields.cityCode).toMatch(/^El departamento no corresponde/);
    expect(general).toBe('');
  });

  it('lo que no reconoce queda como mensaje general', () => {
    const { fields, general } = fieldErrorsFromMessage('Algo salió mal');
    expect(fields).toEqual({});
    expect(general).toBe('Algo salió mal');
  });
});
