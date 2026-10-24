import React, { useMemo, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Download, FileSpreadsheet, Info, Upload } from 'lucide-react';
import { Badge, Button, Modal } from '../ui';
import {
  downloadProductImportTemplateApi,
  previewProductImportApi,
  importProductsApi,
} from '../../services/inventoryApi';
import { deliverBlob } from '../../utils/downloadBlob';

const MAX_VISIBLE_ROWS = 500;

const ACTIONS = {
  CREATE: { label: 'Nuevo', tone: 'success' },
  UPDATE: { label: 'Actualizar', tone: 'info' },
  UNCHANGED: { label: 'Sin cambios', tone: 'neutral' },
  ERROR: { label: 'Error', tone: 'danger' },
};

const ERROR_BOX = 'p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-start gap-2';
const WARNING_BOX = 'p-3 bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/30 rounded-xl text-xs font-bold flex items-start gap-2';
const INFO_BOX = 'p-3 bg-page text-ink-soft border border-line rounded-xl text-xs font-semibold flex items-start gap-2';

const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Importación masiva desde Excel: descargar la plantilla, subirla llena, revisar la vista previa que
 * calcula el backend y confirmar. Es "todo o nada": con una fila con errores no se importa nada.
 * `mode="count"` presenta el mismo flujo como conteo de inventario (plantilla con los productos del local).
 */
export default function ProductImportModal({ mode = 'catalog', onClose, onImported }) {
  const fileInputRef = useRef(null);
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [result, setResult] = useState(null);
  const [downloading, setDownloading] = useState(null);
  const [validating, setValidating] = useState(false);
  const [importing, setImporting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [onlyIssues, setOnlyIssues] = useState(false);
  const isCount = mode === 'count';

  const handleDownload = async (withProducts) => {
    setErrorMsg('');
    setDownloading(withProducts ? 'filled' : 'blank');
    try {
      const blob = await downloadProductImportTemplateApi(withProducts);
      deliverBlob(blob, withProducts ? `inventario-${today()}.xlsx` : 'plantilla-productos.xlsx');
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo descargar la plantilla.');
    } finally {
      setDownloading(null);
    }
  };

  const handleFileChange = async (e) => {
    const selected = e.target.files?.[0];
    e.target.value = '';
    if (!selected) return;
    setErrorMsg('');
    setResult(null);
    if (!selected.name.toLowerCase().endsWith('.xlsx')) {
      setErrorMsg('Selecciona un archivo de Excel .xlsx (la plantilla descargada del sistema).');
      return;
    }
    setFile(selected);
    setPreview(null);
    setOnlyIssues(false);
    setValidating(true);
    try {
      setPreview(await previewProductImportApi(selected));
    } catch (err) {
      setFile(null);
      setErrorMsg(err.message || 'No se pudo leer el archivo.');
    } finally {
      setValidating(false);
    }
  };

  const handleImport = async () => {
    setErrorMsg('');
    setImporting(true);
    try {
      setResult(await importProductsApi(file));
      onImported?.();
    } catch (err) {
      // 422: el archivo tiene errores (p. ej. otro usuario cambió algo desde la vista previa).
      if (err.status === 422 && err.data?.preview) {
        setPreview(err.data.preview);
      }
      setErrorMsg(err.message || 'No se pudo importar el archivo.');
    } finally {
      setImporting(false);
    }
  };

  const changes = preview ? preview.toCreate + preview.toUpdate : 0;
  const canImport = preview && !preview.withErrors && changes > 0 && !result;

  const footer = result ? (
    <Button onClick={onClose}>Listo</Button>
  ) : (
    <>
      <Button variant="ghost" onClick={onClose} disabled={importing}>Cancelar</Button>
      {preview && changes > 0 && (
        <Button variant="brand" icon={Upload} onClick={handleImport} loading={importing} disabled={!canImport}>
          {changes === 1 ? 'Importar 1 producto' : `Importar ${changes} productos`}
        </Button>
      )}
    </>
  );

  return (
    <Modal
      title={isCount ? 'Conteo masivo de inventario' : 'Importar productos desde Excel'}
      subtitle={isCount
        ? 'Descarga tus productos, escribe lo que contaste en la columna Stock y súbelo'
        : 'Crea o actualiza muchos productos a la vez con la plantilla'}
      size="xl"
      onClose={importing ? undefined : onClose}
      dismissible={false}
      footer={footer}
    >
      {errorMsg && (
        <div className={ERROR_BOX}>
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {result ? (
        <ImportResult result={result} />
      ) : (
        <>
          <section className="grid gap-3 md:grid-cols-2">
            <div className="rounded-2xl border border-line p-4 space-y-3">
              <p className="text-xs font-extrabold text-ink">1. Descarga la plantilla</p>
              <p className="text-[11px] text-ink-soft">
                {isCount
                  ? 'La plantilla «con mis productos» trae tu catálogo y el stock actual: corrige solo la columna Stock.'
                  : 'Usa la plantilla vacía para productos nuevos, o la que trae tus productos para editarlos.'}
              </p>
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  variant={isCount ? 'outline' : 'solid'}
                  icon={Download}
                  loading={downloading === 'blank'}
                  disabled={!!downloading}
                  onClick={() => handleDownload(false)}
                >
                  Plantilla vacía
                </Button>
                <Button
                  size="sm"
                  variant={isCount ? 'solid' : 'outline'}
                  icon={Download}
                  loading={downloading === 'filled'}
                  disabled={!!downloading}
                  onClick={() => handleDownload(true)}
                >
                  Con mis productos
                </Button>
              </div>
            </div>

            <div className="rounded-2xl border border-dashed border-line-strong p-4 space-y-3">
              <p className="text-xs font-extrabold text-ink">2. Sube el archivo lleno</p>
              <p className="text-[11px] text-ink-soft truncate">
                {file ? <>Archivo: <span className="font-bold text-ink">{file.name}</span></> : 'Solo archivos .xlsx, hasta 5.000 filas.'}
              </p>
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                className="hidden"
                onChange={handleFileChange}
              />
              <Button
                size="sm"
                variant={file ? 'outline' : 'brand'}
                icon={FileSpreadsheet}
                loading={validating}
                disabled={importing}
                onClick={() => fileInputRef.current?.click()}
              >
                {file ? 'Cambiar archivo' : 'Seleccionar archivo .xlsx'}
              </Button>
            </div>
          </section>

          {!preview && !validating && (
            <div className={INFO_BOX}>
              <Info className="w-4 h-4 shrink-0" />
              <span>
                Si el código interno no existe se crea el producto; si ya existe se actualiza y la columna Stock se toma
                como conteo físico (queda en el kardex). Antes de guardar verás una vista previa fila por fila.
              </span>
            </div>
          )}

          {preview && (
            <ImportPreview preview={preview} onlyIssues={onlyIssues} setOnlyIssues={setOnlyIssues} />
          )}
        </>
      )}
    </Modal>
  );
}

function ImportPreview({ preview, onlyIssues, setOnlyIssues }) {
  const rows = useMemo(() => {
    const withIssues = (r) => r.errors.length > 0 || r.warnings.length > 0;
    const list = onlyIssues ? preview.rows.filter(withIssues) : preview.rows;
    // Primero las filas con errores, para que se vean sin tener que buscarlas.
    return [...list].sort((a, b) => (b.errors.length > 0) - (a.errors.length > 0) || a.rowNumber - b.rowNumber);
  }, [preview, onlyIssues]);
  const hasIssues = preview.rows.some(r => r.errors.length > 0 || r.warnings.length > 0);
  const nothingToDo = !preview.withErrors && preview.toCreate + preview.toUpdate === 0;

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-extrabold text-ink mr-1">{preview.totalRows} filas</span>
        <Badge tone="success">{preview.toCreate} nuevos</Badge>
        <Badge tone="info">{preview.toUpdate} a actualizar</Badge>
        <Badge tone="neutral">{preview.unchanged} sin cambios</Badge>
        {preview.withErrors > 0 && <Badge tone="danger">{preview.withErrors} con errores</Badge>}
        {hasIssues && (
          <button
            type="button"
            onClick={() => setOnlyIssues(!onlyIssues)}
            className={`ml-auto px-3 py-1 rounded-full text-[11px] font-bold cursor-pointer border transition-colors ${
              onlyIssues ? 'bg-solid text-white border-transparent' : 'border-line text-ink-soft hover:border-line-strong'
            }`}
          >
            {onlyIssues ? 'Viendo solo errores y avisos' : 'Ver solo errores y avisos'}
          </button>
        )}
      </div>

      {preview.withErrors > 0 ? (
        <div className={ERROR_BOX}>
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>
            {preview.withErrors === 1 ? 'Hay 1 fila con errores.' : `Hay ${preview.withErrors} filas con errores.`}{' '}
            Corrige el Excel y vuelve a subirlo: no se importa nada mientras haya errores.
          </span>
        </div>
      ) : nothingToDo ? (
        <div className={INFO_BOX}>
          <Info className="w-4 h-4 shrink-0" />
          <span>Todo el archivo coincide con lo que ya tienes: no hay nada que importar.</span>
        </div>
      ) : (
        <div className={WARNING_BOX}>
          <Info className="w-4 h-4 shrink-0" />
          <span>Revisa los cambios. Al importar, el stock de los productos existentes queda igual a la columna Stock.</span>
        </div>
      )}

      <div className="md:overflow-x-auto rounded-2xl md:border md:border-line">
        <table className="table-cards w-full text-left text-xs">
          <thead>
            <tr className="border-b border-line bg-gray-50/50 dark:bg-page text-ink-muted uppercase text-[10px] tracking-wider font-extrabold">
              <th className="py-2.5 px-3">Fila</th>
              <th className="py-2.5 px-3">Código</th>
              <th className="py-2.5 px-3">Producto</th>
              <th className="py-2.5 px-3">Acción</th>
              <th className="py-2.5 px-3 text-right">Stock</th>
              <th className="py-2.5 px-3">Detalle</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-line">
            {rows.slice(0, MAX_VISIBLE_ROWS).map(row => {
              const action = ACTIONS[row.action] || ACTIONS.ERROR;
              return (
                <tr key={row.rowNumber} className="align-top">
                  <td data-label="Fila" className="py-2.5 px-3 font-bold text-ink-muted tabular-nums">{row.rowNumber}</td>
                  <td data-label="Código" className="py-2.5 px-3 font-mono font-semibold text-ink-soft">{row.internalCode || '—'}</td>
                  <td data-label="Producto" className="py-2.5 px-3 font-bold text-ink">{row.name || '—'}</td>
                  <td data-label="Acción" className="py-2.5 px-3"><Badge tone={action.tone}>{action.label}</Badge></td>
                  <td data-label="Stock" className="py-2.5 px-3 text-right tabular-nums text-ink whitespace-nowrap">
                    <StockChange row={row} />
                  </td>
                  <td data-label="Detalle" className="py-2.5 px-3">
                    {row.errors.length === 0 && row.warnings.length === 0 ? (
                      <span className="text-ink-muted">—</span>
                    ) : (
                      <ul className="space-y-1">
                        {row.errors.map((msg, i) => (
                          <li key={`e${i}`} className="text-red-600 dark:text-red-400 font-semibold">{msg}</li>
                        ))}
                        {row.warnings.map((msg, i) => (
                          <li key={`w${i}`} className="text-amber-700 dark:text-amber-400 font-semibold">{msg}</li>
                        ))}
                      </ul>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {rows.length > MAX_VISIBLE_ROWS && (
        <p className="text-[11px] text-ink-muted font-semibold">
          Mostrando {MAX_VISIBLE_ROWS} de {rows.length} filas (las que tienen errores van primero).
        </p>
      )}
    </section>
  );
}

function StockChange({ row }) {
  if (row.currentStock == null) {
    return row.newStock != null ? <span className="font-bold">{row.newStock}</span> : <span className="text-ink-muted">—</span>;
  }
  if (row.newStock == null) {
    return <span className="text-ink-soft">{row.currentStock}</span>;
  }
  return (
    <span>
      <span className="text-ink-muted">{row.currentStock}</span>
      <span className="text-ink-muted mx-1">→</span>
      <span className="font-bold">{row.newStock}</span>
    </span>
  );
}

function ImportResult({ result }) {
  const stats = [
    { label: 'Productos creados', value: result.created },
    { label: 'Productos actualizados', value: result.updated },
    { label: 'Sin cambios', value: result.unchanged },
    { label: 'Movimientos de inventario', value: result.stockMovements },
  ];
  return (
    <section className="space-y-4">
      <div className="flex items-center gap-3">
        <CheckCircle2 className="w-8 h-8 text-emerald-500 shrink-0" />
        <div>
          <p className="font-extrabold text-ink">Importación completada</p>
          <p className="text-xs text-ink-soft">Se procesaron {result.totalRows} filas del archivo.</p>
        </div>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {stats.map(stat => (
          <div key={stat.label} className="rounded-2xl border border-line p-3">
            <p className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">{stat.label}</p>
            <p className="text-xl font-black text-ink tabular-nums">{stat.value}</p>
          </div>
        ))}
      </div>
      {result.subcategoriesCreated > 0 && (
        <div className={INFO_BOX}>
          <Info className="w-4 h-4 shrink-0" />
          <span>
            {result.subcategoriesCreated === 1
              ? 'Se creó 1 subcategoría nueva.'
              : `Se crearon ${result.subcategoriesCreated} subcategorías nuevas.`}
          </span>
        </div>
      )}
      {result.lowStockProducts > 0 && (
        <div className={WARNING_BOX}>
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>
            {result.lowStockProducts === 1
              ? '1 producto quedó con stock igual o por debajo de su mínimo.'
              : `${result.lowStockProducts} productos quedaron con stock igual o por debajo de su mínimo.`}{' '}
            Revísalos en Inventario.
          </span>
        </div>
      )}
    </section>
  );
}
