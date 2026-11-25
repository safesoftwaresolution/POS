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
    /** Administrador o supervisor (encargados del local). No da permiso para cambiar stock: eso es `canModifyStock`. */
    isStoreManager: isAdmin || isSupervisor,
    /** Solo administrador: cambia el stock (movimientos, compras, conteo e importación, lotes, stock inicial). */
    canModifyStock: isAdmin,
  };
}

export default function usePermissions() {
  const { user } = useAuth();
  const role = user?.role;
  return useMemo(() => permissionsFor(role), [role]);
}
