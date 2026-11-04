import { useContext } from 'react';
import { StoreTemplateContext } from '../context/contexts';
import { templateFeaturesFor } from '../utils/templateFeatures';

const FALLBACK = { template: 'RETAIL', loading: false, features: templateFeaturesFor('RETAIL') };

/** Plantilla del local actual y los módulos que habilita: `{ template, loading, features }`. */
export default function useStoreTemplate() {
  return useContext(StoreTemplateContext) ?? FALLBACK;
}
