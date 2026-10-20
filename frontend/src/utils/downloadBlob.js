// Entrega un Blob al usuario: los PDF se abren en una pestaña nueva, el resto se descarga.
export function deliverBlob(blob, filename, { openInTab = false } = {}) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  if (openInTab) a.target = '_blank';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}
