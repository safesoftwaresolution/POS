import { useContext } from 'react';
import { ModalContext } from '../context/contexts';

/** `confirm(...)` y `notify(...)`: modales propios en lugar de los dialogos nativos del navegador. */
export default function useModal() {
  const ctx = useContext(ModalContext);
  if (!ctx) {
    throw new Error('useModal debe usarse dentro de <ModalProvider>');
  }
  return ctx;
}
