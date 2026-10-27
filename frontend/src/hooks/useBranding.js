import { useEffect } from 'react';
import { getBrandingApi } from '../services/settingsApi';
import useLoad from './useLoad';

// Aviso entre pantallas: Configuración lo dispara al cambiar el logo o el nombre para que la cabecera se actualice.
export const BRANDING_CHANGED_EVENT = 'branding:changed';

/** Nombre y logo del negocio: `const { data: branding } = useBranding()` → { businessName, logoUrl }. */
export default function useBranding() {
  const result = useLoad(getBrandingApi, [], { initial: null });
  const { reload } = result;
  useEffect(() => {
    const onChanged = () => reload();
    window.addEventListener(BRANDING_CHANGED_EVENT, onChanged);
    return () => window.removeEventListener(BRANDING_CHANGED_EVENT, onChanged);
  }, [reload]);
  return result;
}
