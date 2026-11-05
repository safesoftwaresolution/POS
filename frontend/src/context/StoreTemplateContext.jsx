import React, { useEffect, useMemo } from 'react';
import { StoreTemplateContext } from './contexts';
import useLoad from '../hooks/useLoad';
import { getOwnStoreTemplateApi } from '../services/storesApi';
import { DEFAULT_STORE_TEMPLATE } from '../constants/storeTemplates';
import { DEFAULT_STORE_THEME, storeThemeFor } from '../constants/storeThemes';
import { templateFeaturesFor } from '../utils/templateFeatures';

/**
 * Plantilla y tema del local de la sesión. Los deriva el backend de la categoría del local; aquí solo se leen una vez
 * para todos los roles. Si la consulta falla se usan los predeterminados (RETAIL y el naranja de marca), así el POS
 * sigue viéndose y funcionando como siempre. El tema se aplica cambiando el acento (`--color-primary`); con el
 * predeterminado no se toca nada.
 */
export function StoreTemplateProvider({ children }) {
  const { data, loading } = useLoad(() => getOwnStoreTemplateApi().catch(() => null), [], { initial: null });
  const template = data?.template ?? DEFAULT_STORE_TEMPLATE;
  const theme = data?.theme ?? DEFAULT_STORE_THEME;

  useEffect(() => {
    if (theme === DEFAULT_STORE_THEME) return undefined;
    const { accent, hover } = storeThemeFor(theme);
    const root = document.documentElement;
    root.style.setProperty('--color-primary', accent);
    root.style.setProperty('--color-primary-hover', hover);
    root.dataset.storeTheme = theme;
    return () => {
      root.style.removeProperty('--color-primary');
      root.style.removeProperty('--color-primary-hover');
      delete root.dataset.storeTheme;
    };
  }, [theme]);

  const value = useMemo(
    () => ({ template, theme, loading, features: templateFeaturesFor(template) }),
    [template, theme, loading],
  );
  return <StoreTemplateContext.Provider value={value}>{children}</StoreTemplateContext.Provider>;
}
