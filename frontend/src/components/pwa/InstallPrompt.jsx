import React, { useEffect, useState } from 'react';
import { Download, Share, SquarePlus, X } from 'lucide-react';

const DISMISS_KEY = 'bento_pwa_dismissed';
const HIDE_DAYS = 7;

function dismissedRecently() {
  try {
    const t = parseInt(localStorage.getItem(DISMISS_KEY), 10);
    return !!t && Date.now() - t < HIDE_DAYS * 86400000;
  } catch { return false; }
}

function rememberDismiss() {
  try { localStorage.setItem(DISMISS_KEY, String(Date.now())); } catch { /* sin storage: el banner reaparece, sin más */ }
}

function isStandalone() {
  return window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
}

// iPadOS 13+ se presenta como Mac con pantalla táctil.
function detectIos() {
  const ua = navigator.userAgent;
  return /iphone|ipod|ipad/i.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

// Chrome/Firefox/Edge en iOS y los navegadores internos de apps no pueden instalar.
function detectIosSafari() {
  return !/CriOS|FxiOS|EdgiOS|OPiOS|GSA\/|FBAN|FBAV|Instagram|WhatsApp|Line\/|TikTok|Snapchat/i.test(navigator.userAgent);
}

/**
 * Invita a instalar PosBento en el teléfono. Android/Chrome usa `beforeinstallprompt`;
 * iOS Safari no permite instalar desde código, así que se muestran los pasos de "Añadir a inicio".
 * Solo aparece en móvil y si la app no está ya instalada.
 */
export default function InstallPrompt() {
  // Se decide una sola vez al montar: ya instalada o descartada hace poco → no se ofrece.
  const [initial] = useState(() => {
    const eligible = !isStandalone() && !dismissedRecently();
    return { eligible, ios: eligible && detectIos() };
  });
  const [installEvent, setInstallEvent] = useState(null);
  const iosMode = initial.ios;
  const [visible, setVisible] = useState(initial.ios);
  const [showGuide, setShowGuide] = useState(false);

  useEffect(() => {
    if (!initial.eligible) return undefined;

    const onPrompt = (e) => {
      e.preventDefault();
      setInstallEvent(e);
      setVisible(true);
    };
    const onInstalled = () => { setVisible(false); setInstallEvent(null); };
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, [initial.eligible]);

  const close = () => { rememberDismiss(); setVisible(false); setShowGuide(false); };

  const install = async () => {
    if (!installEvent) return;
    installEvent.prompt();
    const { outcome } = await installEvent.userChoice;
    if (outcome !== 'accepted') rememberDismiss();
    setInstallEvent(null);
    setVisible(false);
  };

  if (!visible) return null;
  const safari = detectIosSafari();

  return (
    <>
      <div className="fixed left-3 right-3 bottom-3 z-120 max-w-md mx-auto flex items-center gap-3 p-3 rounded-2xl bg-solid text-solid-ink shadow-2xl" role="dialog" aria-label="Instalar PosBento">
        <img src="/icons/icon-192.png" alt="" className="w-11 h-11 rounded-xl shrink-0" />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-extrabold">Instala PosBento</p>
          <p className="text-[11px] opacity-70 leading-tight">
            {iosMode ? 'Añádela a tu pantalla de inicio' : 'Acceso directo en tu pantalla de inicio'}
          </p>
        </div>
        <button
          type="button"
          onClick={iosMode ? () => setShowGuide(true) : install}
          className="shrink-0 px-4 py-2 rounded-full bg-brand text-white text-xs font-black cursor-pointer flex items-center gap-1.5"
        >
          {!iosMode && <Download className="w-3.5 h-3.5" />}
          {iosMode ? 'Cómo' : 'Instalar'}
        </button>
        <button type="button" onClick={close} aria-label="Cerrar" className="shrink-0 p-1 opacity-70 cursor-pointer">
          <X className="w-5 h-5" />
        </button>
      </div>

      {showGuide && (
        <div
          className="fixed inset-0 z-130 bg-black/70 flex items-center justify-center p-4 lg:hidden"
          role="dialog"
          aria-modal="true"
          onClick={(e) => e.target === e.currentTarget && close()}
        >
          <div className="w-full max-w-sm bg-card text-ink rounded-3xl p-5 shadow-2xl space-y-4">
            <div>
              <h2 className="text-base font-black">
                {safari ? 'Instala PosBento en tu iPhone' : 'Ábrela en Safari para instalarla'}
              </h2>
              <p className="text-xs text-ink-soft mt-1">
                {safari
                  ? 'Pocos toques y queda como una app en tu pantalla de inicio.'
                  : 'Desde este navegador el iPhone no permite instalar la app.'}
              </p>
            </div>
            <ol className="space-y-3 text-sm">
              {safari ? (
                <>
                  <Step n={1} icon={<Share className="w-5 h-5" />}>Toca <b>Compartir</b> en la barra de Safari (si ves “⋯”, tócalo primero)</Step>
                  <Step n={2} icon={<SquarePlus className="w-5 h-5" />}>Desliza y toca <b>Añadir a pantalla de inicio</b></Step>
                  <Step n={3} icon={<b className="text-xs">OK</b>}>Toca <b>Añadir</b> y busca el ícono en tu pantalla de inicio</Step>
                </>
              ) : (
                <>
                  <Step n={1} icon={<Share className="w-5 h-5" />}>Copia el enlace <b>{window.location.origin}</b></Step>
                  <Step n={2} icon={<b className="text-xs">S</b>}>Ábrelo en Safari</Step>
                  <Step n={3} icon={<SquarePlus className="w-5 h-5" />}>Toca Compartir y <b>Añadir a pantalla de inicio</b></Step>
                </>
              )}
            </ol>
            <button type="button" onClick={close} className="w-full py-3 rounded-2xl bg-solid text-solid-ink text-sm font-black cursor-pointer">
              Entendido
            </button>
          </div>
        </div>
      )}
    </>
  );
}

function Step({ n, icon, children }) {
  return (
    <li className="flex items-center gap-3">
      <span className="shrink-0 w-6 h-6 rounded-full bg-solid text-solid-ink text-[11px] font-black flex items-center justify-center">{n}</span>
      <span className="shrink-0 w-10 h-10 rounded-xl bg-page border border-line flex items-center justify-center text-brand">{icon}</span>
      <span className="text-xs leading-snug">{children}</span>
    </li>
  );
}
