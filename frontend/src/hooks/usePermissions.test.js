import { describe, expect, it } from 'vitest';
import { permissionsFor } from './usePermissions';

describe('permissionsFor', () => {
  it('administrador y supervisor son encargados del local; vendedor y super admin no', () => {
    expect(permissionsFor('ADMINISTRATOR').isStoreManager).toBe(true);
    expect(permissionsFor('SUPERVISOR').isStoreManager).toBe(true);
    expect(permissionsFor('SELLER').isStoreManager).toBe(false);
    expect(permissionsFor('SUPER_ADMIN').isStoreManager).toBe(false);
  });

  it('solo el administrador cambia el stock; el supervisor lo consulta', () => {
    expect(permissionsFor('ADMINISTRATOR').canModifyStock).toBe(true);
    expect(permissionsFor('SUPERVISOR').canModifyStock).toBe(false);
    expect(permissionsFor('SELLER').canModifyStock).toBe(false);
    expect(permissionsFor('SUPER_ADMIN').canModifyStock).toBe(false);
  });

  it('solo un rol a la vez', () => {
    const p = permissionsFor('ADMINISTRATOR');
    expect([p.isAdmin, p.isSupervisor, p.isSeller, p.isSuperAdmin]).toEqual([true, false, false, false]);
  });

  it('sin sesión no tiene permisos', () => {
    const p = permissionsFor(undefined);
    expect(p.isStoreManager).toBe(false);
    expect(p.isAdmin).toBe(false);
  });
});
