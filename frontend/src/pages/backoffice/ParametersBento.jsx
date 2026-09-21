import React, { useState, useEffect, useCallback } from 'react';
import { Puzzle, Plus, Edit3, Ban, AlertCircle, X, Lock, ChevronRight } from 'lucide-react';
import {
  getParameterTopicsApi,
  getParameterValuesApi,
  createParameterTopicApi,
  addParameterValueApi,
  updateParameterValueApi,
  deactivateParameterValueApi
} from '../../services/parametersApi';
import { useModal } from '../../context/ModalContext';

const EMPTY_TOPIC_FORM = { code: '', name: '', description: '' };
const EMPTY_VALUE_FORM = { id: null, code: '', label: '', extraValue: '', sortOrder: 0 };

export default function ParametersBento() {
  const { confirm, notify } = useModal();
  const [topics, setTopics] = useState([]);
  const [loadingTopics, setLoadingTopics] = useState(true);
  const [selectedTopic, setSelectedTopic] = useState(null);
  const [values, setValues] = useState([]);
  const [loadingValues, setLoadingValues] = useState(false);

  const [showTopicForm, setShowTopicForm] = useState(false);
  const [topicForm, setTopicForm] = useState(EMPTY_TOPIC_FORM);
  const [topicError, setTopicError] = useState('');

  const [showValueForm, setShowValueForm] = useState(false);
  const [valueForm, setValueForm] = useState(EMPTY_VALUE_FORM);
  const [valueError, setValueError] = useState('');

  const loadTopics = useCallback(async () => {
    setLoadingTopics(true);
    try {
      setTopics(await getParameterTopicsApi());
    } catch (e) {
      console.warn('Error cargando temas de parámetros:', e);
      setTopics([]);
    } finally {
      setLoadingTopics(false);
    }
  }, []);

  useEffect(() => { loadTopics(); }, [loadTopics]);

  const loadValues = useCallback(async (topicCode) => {
    setLoadingValues(true);
    try {
      setValues(await getParameterValuesApi(topicCode));
    } catch (e) {
      console.warn('Error cargando valores del tema:', e);
      setValues([]);
    } finally {
      setLoadingValues(false);
    }
  }, []);

  const selectTopic = (topic) => {
    setSelectedTopic(topic);
    loadValues(topic.code);
  };

  const handleCreateTopic = async (e) => {
    e.preventDefault();
    setTopicError('');
    try {
      await createParameterTopicApi(topicForm.code, topicForm.name, topicForm.description);
      setShowTopicForm(false);
      setTopicForm(EMPTY_TOPIC_FORM);
      await loadTopics();
    } catch (err) {
      setTopicError(err.message || 'Error al crear el tema.');
    }
  };

  const openAddValue = () => {
    setValueForm(EMPTY_VALUE_FORM);
    setValueError('');
    setShowValueForm(true);
  };

  const openEditValue = (val) => {
    setValueForm({
      id: val.id,
      code: val.code,
      label: val.label,
      extraValue: val.extraValue || '',
      sortOrder: val.sortOrder ?? 0
    });
    setValueError('');
    setShowValueForm(true);
  };

  const handleSubmitValue = async (e) => {
    e.preventDefault();
    setValueError('');
    try {
      if (valueForm.id) {
        await updateParameterValueApi(valueForm.id, {
          label: valueForm.label,
          extraValue: valueForm.extraValue || null,
          sortOrder: Number(valueForm.sortOrder) || 0,
          active: true
        });
      } else {
        await addParameterValueApi(selectedTopic.code, valueForm.code, valueForm.label, valueForm.extraValue, Number(valueForm.sortOrder) || 0);
      }
      setShowValueForm(false);
      setValueForm(EMPTY_VALUE_FORM);
      await loadValues(selectedTopic.code);
    } catch (err) {
      setValueError(err.message || 'Error al guardar el valor.');
    }
  };

  const handleToggleActive = async (val) => {
    if (val.active) {
      const ok = await confirm(`¿Desactivar "${val.label}"? Dejará de aparecer como opción disponible.`, { title: 'Desactivar valor' });
      if (!ok) return;
      try {
        await deactivateParameterValueApi(val.id);
        await loadValues(selectedTopic.code);
      } catch (err) {
        notify('Error desactivando valor: ' + err.message);
      }
    } else {
      try {
        await updateParameterValueApi(val.id, { label: val.label, extraValue: val.extraValue, sortOrder: val.sortOrder, active: true });
        await loadValues(selectedTopic.code);
      } catch (err) {
        notify('Error reactivando valor: ' + err.message);
      }
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#161b22] dark:text-[#f0f6fc]">Parámetros y Catálogos</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">
            Catálogos globales (métodos de pago, motivos de devolución, zonas de entrega...) que consumen todos los locales
          </p>
        </div>

        <button
          onClick={() => { setTopicForm(EMPTY_TOPIC_FORM); setTopicError(''); setShowTopicForm(true); }}
          className="px-4 py-2.5 bg-[#067A57] hover:bg-[#055C43] text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#067A57]/20 cursor-pointer"
        >
          <Plus className="w-4 h-4" /> Nuevo Tema
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Lista de temas */}
        <div className="bento-card p-5 space-y-3 lg:col-span-1">
          <h2 className="text-xs font-extrabold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Temas</h2>
          {loadingTopics ? (
            <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Cargando...</div>
          ) : topics.length === 0 ? (
            <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">No hay temas todavía</div>
          ) : (
            <div className="space-y-1.5">
              {topics.map(t => (
                <button
                  key={t.code}
                  onClick={() => selectTopic(t)}
                  className={`w-full text-left p-3 rounded-2xl border transition-all cursor-pointer flex items-center justify-between gap-2 ${
                    selectedTopic?.code === t.code
                      ? 'bg-[#067A57] border-[#067A57] text-white'
                      : 'bg-gray-50/60 dark:bg-white/5 border-[#e2e8f0] dark:border-[#262f38] hover:border-[#067A57]'
                  }`}
                >
                  <div className="min-w-0">
                    <p className={`text-xs font-extrabold truncate ${selectedTopic?.code === t.code ? 'text-white' : 'text-[#161b22] dark:text-[#f0f6fc]'}`}>{t.name}</p>
                    <p className={`text-[10px] font-mono truncate ${selectedTopic?.code === t.code ? 'text-white/70' : 'text-gray-400 dark:text-gray-500'}`}>{t.code}</p>
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    {t.isSystem && <Lock className={`w-3 h-3 ${selectedTopic?.code === t.code ? 'text-white/70' : 'text-gray-400 dark:text-gray-500'}`} />}
                    <ChevronRight className={`w-4 h-4 ${selectedTopic?.code === t.code ? 'text-white' : 'text-gray-300 dark:text-gray-600'}`} />
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Valores del tema seleccionado */}
        <div className="bento-card p-5 space-y-4 lg:col-span-2">
          {!selectedTopic ? (
            <div className="py-16 text-center space-y-2">
              <Puzzle className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
              <p className="text-xs font-bold text-gray-500 dark:text-gray-400">Selecciona un tema para ver y administrar sus valores</p>
            </div>
          ) : (
            <>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-sm font-extrabold text-[#161b22] dark:text-[#f0f6fc] flex items-center gap-2">
                    {selectedTopic.name}
                    {selectedTopic.isSystem && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400 font-bold flex items-center gap-1">
                        <Lock className="w-3 h-3" /> Sistema
                      </span>
                    )}
                  </h2>
                  {selectedTopic.description && (
                    <p className="text-[11px] text-gray-500 dark:text-gray-400 mt-0.5">{selectedTopic.description}</p>
                  )}
                </div>
                <button
                  onClick={openAddValue}
                  className="px-3 py-1.5 bg-[#067A57] hover:bg-[#055C43] text-white rounded-xl text-[11px] font-bold flex items-center gap-1 shrink-0 cursor-pointer shadow-sm shadow-[#067A57]/20"
                >
                  <Plus className="w-3.5 h-3.5" /> Nuevo Valor
                </button>
              </div>

              {loadingValues ? (
                <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Cargando valores...</div>
              ) : values.length === 0 ? (
                <div className="py-8 text-center text-xs font-bold text-gray-400 dark:text-gray-500">Este tema aún no tiene valores</div>
              ) : (
                <div className="overflow-x-auto -mx-2">
                  <table className="w-full text-xs min-w-[480px]">
                    <thead>
                      <tr className="text-left text-[10px] font-extrabold text-gray-400 dark:text-gray-500 uppercase">
                        <th className="px-2 py-2">Código</th>
                        <th className="px-2 py-2">Etiqueta</th>
                        <th className="px-2 py-2">Dato extra</th>
                        <th className="px-2 py-2 text-center">Estado</th>
                        <th className="px-2 py-2 text-right">Acciones</th>
                      </tr>
                    </thead>
                    <tbody>
                      {values.map(v => (
                        <tr key={v.id} className="border-t border-[#e2e8f0] dark:border-[#262f38]">
                          <td className="px-2 py-3 font-mono font-bold text-gray-500 dark:text-gray-400">{v.code}</td>
                          <td className="px-2 py-3 font-extrabold text-[#161b22] dark:text-[#f0f6fc]">{v.label}</td>
                          <td className="px-2 py-3 text-gray-500 dark:text-gray-400">{v.extraValue || '—'}</td>
                          <td className="px-2 py-3 text-center">
                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-extrabold ${
                              v.active
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                                : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400'
                            }`}>
                              {v.active ? 'Activo' : 'Inactivo'}
                            </span>
                          </td>
                          <td className="px-2 py-3 text-right space-x-1">
                            <button
                              onClick={() => openEditValue(v)}
                              title="Editar valor"
                              className="p-1.5 text-gray-400 dark:text-gray-500 hover:text-[#067A57] dark:hover:text-[#00D47E] rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer inline-flex"
                            >
                              <Edit3 className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => handleToggleActive(v)}
                              title={v.active ? 'Desactivar' : 'Reactivar'}
                              className="p-1.5 text-gray-400 dark:text-gray-500 hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer inline-flex"
                            >
                              <Ban className="w-3.5 h-3.5" />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Modal: nuevo tema */}
      {showTopicForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleCreateTopic} className="bento-card max-w-md w-full bg-white dark:bg-[#161b22] p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-[#161b22] dark:text-[#f0f6fc]">Nuevo Tema</h3>
              <button type="button" onClick={() => setShowTopicForm(false)} className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            {topicError && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{topicError}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Código (mayúsculas, sin espacios):</label>
                <input
                  type="text"
                  value={topicForm.code}
                  onChange={e => setTopicForm({ ...topicForm, code: e.target.value })}
                  placeholder="LOYALTY_TIERS"
                  className="w-full p-2.5 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl font-mono text-xs text-[#161b22] dark:text-[#f0f6fc] focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre:</label>
                <input
                  type="text"
                  value={topicForm.name}
                  onChange={e => setTopicForm({ ...topicForm, name: e.target.value })}
                  placeholder="Niveles de Fidelización"
                  className="w-full p-2.5 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl font-semibold text-[#161b22] dark:text-[#f0f6fc] focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Descripción (opcional):</label>
                <input
                  type="text"
                  value={topicForm.description}
                  onChange={e => setTopicForm({ ...topicForm, description: e.target.value })}
                  className="w-full p-2.5 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl text-xs text-[#161b22] dark:text-[#f0f6fc] focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button type="button" onClick={() => setShowTopicForm(false)} className="w-1/2 py-3 bg-gray-100 dark:bg-[#262f38] hover:bg-gray-200 dark:hover:bg-[#303b47] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer">
                Cancelar
              </button>
              <button type="submit" className="w-1/2 py-3 bg-[#067A57] hover:bg-[#055C43] text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#067A57]/20">
                Crear Tema
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Modal: nuevo/editar valor */}
      {showValueForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSubmitValue} className="bento-card max-w-md w-full bg-white dark:bg-[#161b22] p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-[#161b22] dark:text-[#f0f6fc]">
                {valueForm.id ? 'Editar Valor' : 'Nuevo Valor'}
              </h3>
              <button type="button" onClick={() => setShowValueForm(false)} className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            {valueError && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{valueError}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Código:</label>
                <input
                  type="text"
                  value={valueForm.code}
                  onChange={e => setValueForm({ ...valueForm, code: e.target.value })}
                  placeholder="GOLD"
                  disabled={!!valueForm.id}
                  className="w-full p-2.5 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl font-mono text-xs text-[#161b22] dark:text-[#f0f6fc] disabled:opacity-60 focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Etiqueta:</label>
                <input
                  type="text"
                  value={valueForm.label}
                  onChange={e => setValueForm({ ...valueForm, label: e.target.value })}
                  placeholder="Nivel Oro"
                  className="w-full p-2.5 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl font-semibold text-[#161b22] dark:text-[#f0f6fc] focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Dato extra (opcional):</label>
                  <input
                    type="text"
                    value={valueForm.extraValue}
                    onChange={e => setValueForm({ ...valueForm, extraValue: e.target.value })}
                    className="w-full p-2.5 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl text-xs text-[#161b22] dark:text-[#f0f6fc] focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
                  />
                </div>
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Orden:</label>
                  <input
                    type="number"
                    value={valueForm.sortOrder}
                    onChange={e => setValueForm({ ...valueForm, sortOrder: e.target.value })}
                    className="w-full p-2.5 bg-[#f8f9fa] dark:bg-[#0d1117] border border-[#e2e8f0] dark:border-[#262f38] rounded-2xl text-xs text-[#161b22] dark:text-[#f0f6fc] focus:outline-none focus:border-[#067A57] dark:focus:border-[#00D47E]"
                  />
                </div>
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button type="button" onClick={() => setShowValueForm(false)} className="w-1/2 py-3 bg-gray-100 dark:bg-[#262f38] hover:bg-gray-200 dark:hover:bg-[#303b47] text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer">
                Cancelar
              </button>
              <button type="submit" className="w-1/2 py-3 bg-[#067A57] hover:bg-[#055C43] text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#067A57]/20">
                {valueForm.id ? 'Guardar Cambios' : 'Agregar Valor'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
