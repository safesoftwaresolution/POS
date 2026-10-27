import { useRef, useState } from 'react';
import useAuth from './useAuth';
import { prepareAvatarForUpload } from '../utils/image';
import { uploadMyAvatarApi, removeMyAvatarApi } from '../services/usersApi';

/**
 * Cambiar o quitar la foto de perfil propia. `onFile` va en el onChange de un `<input type="file">` (con `inputRef`)
 * y `remove` quita la foto; ambos actualizan la sesión para que se vea al instante en toda la interfaz.
 */
export default function useAvatarUpload() {
  const { updateUser } = useAuth();
  const inputRef = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const run = async (action, fallbackMessage) => {
    setError('');
    setBusy(true);
    try {
      const updated = await action();
      updateUser({ avatarUrl: updated.avatarUrl ?? null });
    } catch (err) {
      setError(err.message || fallbackMessage);
    } finally {
      setBusy(false);
    }
  };

  const onFile = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    await run(async () => uploadMyAvatarApi(await prepareAvatarForUpload(file)), 'No se pudo subir la foto.');
  };

  const remove = () => run(removeMyAvatarApi, 'No se pudo quitar la foto.');

  return { inputRef, busy, error, onFile, remove };
}
