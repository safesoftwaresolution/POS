import { describe, expect, it } from 'vitest';
import { permissionsFor } from './usePermissions';

describe('permissionsFor', () => {
  it('administrador y supervisor gestionan stock; vendedor y super admin no', () => {
    expect(permissionsFor('ADMINISTRATOR').canManageStock).toBe(true);
    expect(permissionsFor('SUPERVISOR').canManageStock).toBe(true);
    expect(permissionsFor('SELLER').canManageStock).toBe(false);
    expect(permissionsFor('SUPER_ADMIN').canManageStock).toBe(false);
  });

  it('solo un rol a la vez', () => {
    const p = permissionsFor('ADMINISTRATOR');
    expect([p.isAdmin, p.isSupervisor, p.isSeller, p.isSuperAdmin]).toEqual([true, false, false, false]);
  });

  it('sin sesión no tiene permisos', () => {
    const p = permissionsFor(undefined);
    expect(p.canManageStock).toBe(false);
    expect(p.isAdmin).toBe(false);
  });
});
