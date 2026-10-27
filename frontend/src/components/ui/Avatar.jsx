import React, { useState } from 'react';
import getInitials from '../../utils/initials';
import { cx } from './cx';

/**
 * Foto de perfil con respaldo a las iniciales (sin foto, o si la imagen no carga).
 * `className` define tamaño, forma y anillo (ej. 'w-9 h-9 rounded-full text-xs').
 */
export default function Avatar({ name, src, className = 'w-9 h-9 rounded-full text-xs' }) {
  const [failedSrc, setFailedSrc] = useState(null);
  const showImage = src && failedSrc !== src;
  return (
    <div
      className={cx(
        'shrink-0 overflow-hidden bg-solid text-solid-ink flex items-center justify-center font-bold select-none',
        className,
      )}
    >
      {showImage ? (
        <img
          src={src}
          alt={name || 'Foto de perfil'}
          className="w-full h-full object-cover"
          draggable={false}
          onError={() => setFailedSrc(src)}
        />
      ) : (
        getInitials(name)
      )}
    </div>
  );
}
