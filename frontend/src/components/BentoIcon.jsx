import React from 'react';

/** Ícono de marca "bento box": 4 compartimentos, uno acentuado en naranja, con un punto de marca. */
export default function BentoIcon({ size = 'w-10 h-10', rounded = 'rounded-2xl', dot = true }) {
  return (
    <div className={`${size} ${rounded} bg-solid p-2 flex items-center justify-center relative shrink-0 shadow-sm`}>
      <svg viewBox="0 0 24 24" fill="none" className="w-full h-full">
        <rect x="3" y="3" width="8" height="8" rx="2" fill="#FF5520" />
        <rect x="13" y="3" width="8" height="8" rx="2" fill="white" fillOpacity="0.85" />
        <rect x="3" y="13" width="8" height="8" rx="2" fill="white" fillOpacity="0.85" />
        <rect x="13" y="13" width="8" height="8" rx="2" fill="#FF5520" fillOpacity="0.3" />
      </svg>
      {dot && (
        <div className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 bg-brand rounded-full ring-2 ring-white dark:ring-page"></div>
      )}
    </div>
  );
}
