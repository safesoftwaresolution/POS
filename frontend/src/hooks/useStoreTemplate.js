import { useContext } from 'react';
import { StoreTemplateContext } from '../context/contexts';
import { templateFeaturesFor } from '../utils/templateFeatures';

const FALLBACK = { template: 'RETAIL', templates: ['RETAIL'], loading: false, features: templateFeaturesFor(['RETAIL']) };

/** Plantillas (`templates`, y la principal en `template`) del local actual y los módulos que habilitan: `{ template, templates, loading, features }`. */
export default function useStoreTemplate() {
  return useContext(StoreTemplateContext) ?? FALLBACK;
}
