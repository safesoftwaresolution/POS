import { useMemo } from 'react';
import useAuth from '../hooks/useAuth';
import { ROLES } from '../constants/roles';

/**
 * Qué puede hacer el usuario actual en la interfaz. Refleja los `@PreAuthorize` del backend: ocultar un botón
 * es solo comodidad, el servidor sigue siendo quien decide.
 */
export function permissionsFor(role) {
  const isSuperAdmin = role === ROLES.SUPER_ADMIN;
  const isAdmin = role === ROLES.ADMINISTRATOR;
  const isSupervisor = role === ROLES.SUPERVISOR;
  return {
    role,
    isSuperAdmin,
    isAdmin,
    isSupervisor,
    isSeller: role === ROLES.SELLER,
    /** Administrador y supervisor: ven stock, compras, movimientos y pueden crear productos. */
    canManageStock: isAdmin || isSupervisor,
  };
}

export default function usePermissions() {
  const { user } = useAuth();
  const role = user?.role;
  return useMemo(() => permissionsFor(role), [role]);
}
