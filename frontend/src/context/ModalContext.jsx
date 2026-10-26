import React, { useState, useCallback } from 'react';
import { ModalContext } from './contexts';
import { AlertTriangle, HelpCircle } from 'lucide-react';

// Reemplaza window.confirm()/alert() por modales propios, consistentes con el
// resto de la UI (bento-card + soporte dark mode), en vez de los diálogos
// nativos del navegador.
export function ModalProvider({ children }) {
  const [modal, setModal] = useState(null);
  // modal = { type: 'confirm' | 'alert', title, message, confirmText, cancelText, danger, resolve }

  const confirm = useCallback((message, options = {}) => {
    return new Promise((resolve) => {
      setModal({
        type: 'confirm',
        title: options.title || '¿Confirmar acción?',
        message,
        confirmText: options.confirmText || 'Aceptar',
        cancelText: options.cancelText || 'Cancelar',
        danger: options.danger ?? true,
        resolve,
      });
    });
  }, []);

  const notify = useCallback((message, options = {}) => {
    return new Promise((resolve) => {
      setModal({
        type: 'alert',
        title: options.title || (options.danger === false ? 'Aviso' : 'Ocurrió un error'),
        message,
        confirmText: options.confirmText || 'Entendido',
        danger: options.danger ?? true,
        resolve,
      });
    });
  }, []);

  const close = (result) => {
    modal?.resolve(result);
    setModal(null);
  };

  return (
    <ModalContext.Provider value={{ confirm, notify }}>
      {children}
      {modal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-100 flex items-center justify-center p-4">
          <div className="bento-card max-w-sm w-full bg-card border border-line p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-start gap-3">
              <div className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${
                modal.danger
                  ? 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400'
                  : 'bg-brand/10 dark:bg-brand/20 text-brand'
              }`}>
                {modal.type === 'confirm'
                  ? <HelpCircle className="w-5 h-5" />
                  : <AlertTriangle className="w-5 h-5" />}
              </div>
              <div className="min-w-0 flex-1 pt-1">
                <h3 className="font-extrabold text-sm text-[#17171B] dark:text-white">{modal.title}</h3>
                <p className="text-xs text-ink-soft mt-1.5 leading-relaxed whitespace-pre-line">{modal.message}</p>
              </div>
            </div>

            {modal.type === 'confirm' ? (
              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => close(false)}
                  className="w-1/2 py-2.5 bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer transition-colors"
                >
                  {modal.cancelText}
                </button>
                <button
                  type="button"
                  onClick={() => close(true)}
                  className={`w-1/2 py-2.5 text-white rounded-2xl text-xs font-extrabold cursor-pointer transition-all shadow-md ${
                    modal.danger
                      ? 'bg-red-500 hover:bg-red-600 shadow-red-500/20'
                      : 'bg-solid hover:bg-solid-hover shadow-[#0A0A0A]/10'
                  }`}
                >
                  {modal.confirmText}
                </button>
              </div>
            ) : (
              <div className="pt-1">
                <button
                  type="button"
                  onClick={() => close()}
                  className="w-full py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold cursor-pointer transition-all shadow-lg shadow-[#0A0A0A]/10"
                >
                  {modal.confirmText}
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </ModalContext.Provider>
  );
}
