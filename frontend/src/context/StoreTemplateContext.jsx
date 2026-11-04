import React, { useMemo } from 'react';
import { StoreTemplateContext } from './contexts';
import useLoad from '../hooks/useLoad';
import { getOwnStoreTemplateApi } from '../services/storesApi';
import { DEFAULT_STORE_TEMPLATE } from '../constants/storeTemplates';
import { templateFeaturesFor } from '../utils/templateFeatures';

/**
 * Plantilla del local de la sesión. La deriva el backend de la categoría del local; aquí solo se lee
 * una vez para todos los roles. Si la consulta falla se usa la plantilla por defecto (RETAIL), así el
 * POS sigue funcionando como siempre.
 */
export function StoreTemplateProvider({ children }) {
  const { data, loading } = useLoad(() => getOwnStoreTemplateApi().catch(() => null), [], { initial: null });
  const template = data?.template ?? DEFAULT_STORE_TEMPLATE;
  const value = useMemo(
    () => ({ template, loading, features: templateFeaturesFor(template) }),
    [template, loading],
  );
  return <StoreTemplateContext.Provider value={value}>{children}</StoreTemplateContext.Provider>;
}
