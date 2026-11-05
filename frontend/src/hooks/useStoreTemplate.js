import { useContext } from 'react';
import { StoreTemplateContext } from '../context/contexts';
import { templateFeaturesFor } from '../utils/templateFeatures';

const FALLBACK = { template: 'RETAIL', theme: 'DEFAULT', loading: false, features: templateFeaturesFor('RETAIL') };

/** Plantilla y tema visual del local actual y los módulos que habilita: `{ template, theme, loading, features }`. */
export default function useStoreTemplate() {
  return useContext(StoreTemplateContext) ?? FALLBACK;
}
