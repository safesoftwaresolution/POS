import React, { useMemo } from 'react';
import { StoreTemplateContext } from './contexts';
import useLoad from '../hooks/useLoad';
import { getOwnStoreTemplateApi } from '../services/storesApi';
import { DEFAULT_STORE_TEMPLATE } from '../constants/storeTemplates';
import { templateFeaturesFor } from '../utils/templateFeatures';

/**
 * Plantillas del local de la sesión (solo habilitan módulos como Citas; la apariencia es siempre la de PosBento). Las
 * deriva el backend; aquí se leen una vez para todos los roles. Si la consulta falla se usa RETAIL, así el POS sigue
 * funcionando como siempre.
 */
export function StoreTemplateProvider({ children }) {
  const { data, loading } = useLoad(() => getOwnStoreTemplateApi().catch(() => null), [], { initial: null });
  // El backend manda la lista (`templates`); una respuesta vieja solo trae la principal (`template`).
  const templates = useMemo(
    () => (data?.templates?.length ? data.templates : [data?.template ?? DEFAULT_STORE_TEMPLATE]),
    [data],
  );

  const value = useMemo(() => {
    const features = templateFeaturesFor(templates);
    return { template: features.template, templates: features.templates, loading, features };
  }, [templates, loading]);
  return <StoreTemplateContext.Provider value={value}>{children}</StoreTemplateContext.Provider>;
}
