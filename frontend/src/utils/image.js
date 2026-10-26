const MAX_SIDE = 1280;
const JPEG_QUALITY = 0.85;
// Por debajo de este peso y en un formato que el backend acepta, se sube tal cual.
const SKIP_BELOW_BYTES = 1.5 * 1024 * 1024;
const PASSTHROUGH_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** Tamaño final manteniendo proporción, sin ampliar nunca. */
export function fitWithin(width, height, maxSide = MAX_SIDE) {
  const scale = Math.min(1, maxSide / Math.max(width, height));
  return { width: Math.round(width * scale), height: Math.round(height * scale) };
}

async function decode(file) {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(file, { imageOrientation: 'from-image' });
  }
  const url = URL.createObjectURL(file);
  try {
    return await new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = url;
    });
  } finally {
    URL.revokeObjectURL(url);
  }
}

/**
 * Reduce una foto (típicamente de la cámara del celular, varios MB y a veces HEIC) a un JPEG
 * de máx. 1280 px. Si no se puede procesar devuelve el archivo original: la subida nunca se
 * bloquea por la compresión.
 */
export async function prepareImageForUpload(file) {
  if (!file || file.type === 'image/gif') return file;
  if (file.size < SKIP_BELOW_BYTES && PASSTHROUGH_TYPES.includes(file.type)) return file;
  try {
    const bitmap = await decode(file);
    const { width, height } = fitWithin(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    canvas.getContext('2d').drawImage(bitmap, 0, 0, width, height);
    bitmap.close?.();
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY));
    if (!blob) return file;
    return new File([blob], 'producto.jpg', { type: 'image/jpeg' });
  } catch {
    return file;
  }
}
