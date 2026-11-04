/** Roles del backend (`UserRole`) y cómo se muestran. Una sola fuente para toda la interfaz. */
export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  ADMINISTRATOR: 'ADMINISTRATOR',
  SUPERVISOR: 'SUPERVISOR',
  SELLER: 'SELLER',
};

export const ROLE_LABELS = {
  SUPER_ADMIN: 'Super admin',
  ADMINISTRATOR: 'Administrador',
  SUPERVISOR: 'Supervisor',
  SELLER: 'Vendedor',
};

/** Para conteos ("3 Vendedores"). */
export const ROLE_LABELS_PLURAL = {
  SUPER_ADMIN: 'Super admin',
  ADMINISTRATOR: 'Administradores',
  SUPERVISOR: 'Supervisores',
  SELLER: 'Vendedores',
};

export const roleLabel = (role) => ROLE_LABELS[role] || role || '—';
