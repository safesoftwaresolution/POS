import React from 'react';
import { Camera } from 'lucide-react';
import useAuth from '../../hooks/useAuth';
import useAvatarUpload from '../../hooks/useAvatarUpload';
import { roleLabel } from '../../constants/roles';
import { Button, Modal } from '../ui';
import Avatar from '../ui/Avatar';

/** Ficha de la persona con la foto de perfil editable (para los encabezados que no tienen menú de cuenta propio). */
export default function ProfileModal({ onClose }) {
  const { user } = useAuth();
  const { inputRef, busy, error, onFile, remove } = useAvatarUpload();
  const name = user?.fullName || user?.username;

  return (
    <Modal title="Mi perfil" onClose={onClose} size="sm">
      <div className="px-6 pb-6 flex flex-col items-center gap-4 text-center">
        <div className="relative">
          <Avatar
            name={name}
            src={user?.avatarUrl}
            className={`w-24 h-24 rounded-3xl text-2xl ring-2 ring-[#EAEAEA] dark:ring-line ${busy ? 'opacity-50' : ''}`}
          />
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            disabled={busy}
            aria-label="Cambiar foto de perfil"
            title="Cambiar foto de perfil"
            className="absolute -bottom-1 -right-1 w-8 h-8 rounded-full bg-brand text-white flex items-center justify-center ring-2 ring-white dark:ring-card cursor-pointer disabled:cursor-wait"
          >
            <Camera className="w-4 h-4" />
          </button>
          <input ref={inputRef} type="file" accept="image/png,image/jpeg" className="hidden" onChange={onFile} />
        </div>

        <div className="min-w-0">
          <p className="text-base font-extrabold text-ink truncate">{name}</p>
          {user?.email && <p className="text-xs text-ink-muted truncate">{user.email}</p>}
          <p className="text-xs font-bold text-brand mt-0.5">{roleLabel(user?.role)}</p>
        </div>

        {error && <p className="text-xs font-semibold text-red-600 dark:text-red-400">{error}</p>}

        {user?.avatarUrl && (
          <Button size="sm" variant="soft" disabled={busy} onClick={remove}>
            Quitar foto
          </Button>
        )}
      </div>
    </Modal>
  );
}
