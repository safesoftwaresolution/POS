import React, { useState } from 'react';
import { Edit3, Plus, X, AlertCircle, Eye, EyeOff, Trash2, ScrollText } from 'lucide-react';
import {
  getBackofficeLegalDocumentsApi,
  createBackofficeLegalDocumentApi,
  updateBackofficeLegalDocumentApi,
  deleteBackofficeLegalDocumentApi,
  toggleBackofficeLegalDocumentPublishApi
} from '../../services/backoffice/legalApi';
import useModal from '../../hooks/useModal';
import useLoad from '../../hooks/useLoad';
import asList from '../../utils/asList';

const EMPTY_FORM = { id: null, slug: '', title: '', content: '', version: '' };

export default function LegalDocumentsBento() {
  const { confirm } = useModal();
  const { data: documents, loading, reload } = useLoad(
    () => asList(getBackofficeLegalDocumentsApi()),
    [],
    { initial: [], autoRefresh: true },
  );
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [showForm, setShowForm] = useState(false);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setError('');
    setShowForm(true);
  };

  const openEdit = (doc) => {
    setForm({ id: doc.id, slug: doc.slug, title: doc.title, content: doc.content || '', version: doc.version || '' });
    setError('');
    setShowForm(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const payload = {
      slug: form.slug.trim(),
      title: form.title.trim(),
      content: form.content,
      version: form.version.trim()
    };
    try {
      if (form.id) {
        await updateBackofficeLegalDocumentApi(form.id, payload);
      } else {
        await createBackofficeLegalDocumentApi(payload);
      }
      setShowForm(false);
      setForm(EMPTY_FORM);
      reload();
    } catch (err) {
      setError(err.message || 'Error al guardar el documento.');
    }
  };

  const handleTogglePublish = async (doc) => {
    setErrorMsg('');
    try {
      await toggleBackofficeLegalDocumentPublishApi(doc.id);
      reload();
    } catch (err) {
      setErrorMsg(err.message || 'Error al publicar/despublicar el documento.');
    }
  };

  const handleDelete = async (doc) => {
    const ok = await confirm(`¿Eliminar el documento "${doc.title}"? Solo es posible si no está publicado.`, { title: 'Eliminar documento' });
    if (!ok) return;
    setErrorMsg('');
    try {
      await deleteBackofficeLegalDocumentApi(doc.id);
      reload();
    } catch (err) {
      setErrorMsg(err.message || 'Error al eliminar el documento.');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-ink">Textos Legales</h1>
          <p className="text-xs text-ink-soft font-medium">CMS de políticas de privacidad, términos y demás documentos públicos</p>
        </div>

        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
        >
          <Plus className="w-4 h-4" /> Nuevo Documento
        </button>
      </div>

      {errorMsg && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      <div className="bento-card p-6 space-y-4">
        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">
            Consultando documentos...
          </div>
        ) : documents.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <ScrollText className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay documentos legales</p>
              <p className="text-[11px] text-ink-muted">Crea el primer texto legal (términos, privacidad, cookies...)</p>
            </div>
          </div>
        ) : (
          <div className="md:overflow-x-auto md:-mx-2">
            <table className="table-cards w-full text-xs md:min-w-[720px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Documento</th>
                  <th className="px-2 py-2">Slug</th>
                  <th className="px-2 py-2">Versión</th>
                  <th className="px-2 py-2">Estado</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {documents.map(doc => (
                  <tr key={doc.id} className="border-t border-line hover:bg-gray-50/60 dark:hover:bg-white/5">
                    <td data-label="" className="px-2 py-3">
                      <p className="font-extrabold text-ink">{doc.title}</p>
                      <p className="text-[10px] text-ink-muted">Actualizado: {doc.updatedAt ? new Date(doc.updatedAt).toLocaleDateString() : '—'}</p>
                    </td>
                    <td data-label="Slug" className="px-2 py-3 text-ink-soft font-mono">{doc.slug}</td>
                    <td data-label="Versión" className="px-2 py-3 text-ink-soft font-semibold">{doc.version || '—'}</td>
                    <td data-label="Estado" className="px-2 py-3">
                      <span className={`text-[10px] px-2 py-0.5 rounded-full font-extrabold ${
                        doc.published
                          ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                          : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300'
                      }`}>
                        {doc.published ? 'Publicado' : 'Borrador'}
                      </span>
                    </td>
                    <td data-label="Acciones" className="px-2 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => handleTogglePublish(doc)}
                          title={doc.published ? 'Despublicar' : 'Publicar'}
                          className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                        >
                          {doc.published ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                        <button
                          onClick={() => openEdit(doc)}
                          title="Editar documento"
                          className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                        >
                          <Edit3 className="w-4 h-4" />
                        </button>
                        {!doc.published && (
                          <button
                            onClick={() => handleDelete(doc)}
                            title="Eliminar documento"
                            className="p-1.5 text-ink-muted hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmit} className="bento-card max-w-2xl w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-ink">
                {form.id ? 'Editar Documento Legal' : 'Nuevo Documento Legal'}
              </h3>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {error && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Título:</label>
                <input
                  type="text"
                  value={form.title}
                  onChange={e => setForm({ ...form, title: e.target.value })}
                  placeholder="Ej. Términos y Condiciones"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Slug (identificador único):</label>
                  <input
                    type="text"
                    value={form.slug}
                    onChange={e => setForm({ ...form, slug: e.target.value })}
                    placeholder="terminos-y-condiciones"
                    className="w-full p-2.5 bg-page border border-line rounded-2xl font-mono text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                    required
                    disabled={!!form.id}
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Versión:</label>
                  <input
                    type="text"
                    value={form.version}
                    onChange={e => setForm({ ...form, version: e.target.value })}
                    placeholder="1.0"
                    className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Contenido:</label>
                <textarea
                  value={form.content}
                  onChange={e => setForm({ ...form, content: e.target.value })}
                  placeholder="Contenido del documento legal..."
                  rows={10}
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink resize-y focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-line hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
              >
                {form.id ? 'Guardar Cambios' : 'Crear Documento'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
