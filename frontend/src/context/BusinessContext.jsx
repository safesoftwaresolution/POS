import React, { useMemo } from 'react';
import { BusinessContext } from './contexts';
import useAuth from '../hooks/useAuth';
import useLoad from '../hooks/useLoad';
import { getMyBusinessApi } from '../services/businessApi';

/**
 * Negocio del dueño y sucursal activa, cargados una sola vez para la barra lateral, la cabecera y la pantalla de
 * revisión del local. Solo consulta al ADMINISTRATOR con local; para el resto no hace ninguna llamada.
 */
export function BusinessProvider({ children }) {
  const { user } = useAuth();
  const enabled = user?.role === 'ADMINISTRATOR' && Boolean(user?.storeId);
  const { data, loading, reload } = useLoad(getMyBusinessApi, [user?.storeId], { enabled, errorMessage: '' });

  const value = useMemo(() => ({
    business: data?.business ?? null,
    activeStoreId: data?.activeStoreId ?? null,
    loading: enabled && loading,
    reload,
  }), [data, enabled, loading, reload]);

  return <BusinessContext.Provider value={value}>{children}</BusinessContext.Provider>;
}
