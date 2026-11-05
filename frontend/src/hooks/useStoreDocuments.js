import { useState } from 'react';
import { getOwnStoreDocumentsApi, uploadOwnStoreDocumentApi } from '../services/storesApi';
import { latestDocumentOfType } from '../utils/storeDocuments';
import useLoad from './useLoad';
import useModal from './useModal';
import asList from '../utils/asList';

/**
 * Documentos KYC del local del ADMINISTRATOR: los carga, da el más reciente por tipo y sube uno nuevo
 * (avisando el error con un modal). Lo usan la página de documentos y la pantalla de revisión del local.
 */
export default function useStoreDocuments() {
  const { notify } = useModal();
  const { data: documents, loading, error, reload } = useLoad(
    () => asList(getOwnStoreDocumentsApi()),
    [],
    { initial: [], autoRefresh: true, errorMessage: 'Error cargando los documentos del local.' },
  );
  const [uploadingType, setUploadingType] = useState(null);

  const upload = async (type, file) => {
    setUploadingType(type);
    try {
      await uploadOwnStoreDocumentApi(type, file);
      reload();
    } catch (err) {
      notify(err.message || 'Error al subir el documento. Intenta nuevamente.');
    } finally {
      setUploadingType(null);
    }
  };

  return {
    loading,
    error,
    uploadingType,
    upload,
    latestByType: (type) => latestDocumentOfType(documents, type),
  };
}
