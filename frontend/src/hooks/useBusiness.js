import { useContext } from 'react';
import { BusinessContext } from '../context/contexts';

const NO_BUSINESS = { business: null, activeStoreId: null, loading: false, reload: () => {} };

/**
 * `{ business, activeStoreId, loading, reload }` del dueño de un negocio con sucursales. Para quien no lo es (o fuera
 * del provider) `business` es null y no se muestra nada de sucursales.
 */
export default function useBusiness() {
  return useContext(BusinessContext) ?? NO_BUSINESS;
}
