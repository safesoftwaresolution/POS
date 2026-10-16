import React, { useState, useEffect, useCallback } from 'react';
import { ShieldCheck, Plus, Edit3, KeyRound, Ban, CheckCircle2, X, AlertCircle, UserCog } from 'lucide-react';
import {
  getBackofficeStaffApi,
  createBackofficeStaffApi,
  updateBackofficeStaffApi,
  changeBackofficeStaffStatusApi,
  changeBackofficeStaffPasswordApi,
  deleteBackofficeStaffApi
} from '../../services/backoffice/staffApi';
import { useAuth } from '../../context/AuthContext';
import { useModal } from '../../context/ModalContext';

const EMPTY_STAFF_FORM = { fullName: '', username: '', email: '', password: '' };

export default function StaffBento() {
  const { user } = useAuth();
  const { confirm } = useModal();
  const [staff, setStaff] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_STAFF_FORM);
  const [createError, setCreateError] = useState('');

  const [editForm, setEditForm] = useState(null);
  const [editError, setEditError] = useState('');

  const [passwordTarget, setPasswordTarget] = useState(null);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');

  const loadStaff = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getBackofficeStaffApi();
      setStaff(data.content || []);
    } catch (e) {
      console.warn('Error cargando operadores del backoffice:', e);
      setStaff([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadStaff(); }, [loadStaff]);

  const isSelf = (member) => member.username?.toLowerCase() === user?.username?.toLowerCase();

  // ---------- Create ----------
  const openCreate = () => {
    setCreateForm(EMPTY_STAFF_FORM);
    setCreateError('');
    setShowCreateModal(true);
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreateError('');
    try {
      await createBackofficeStaffApi(createForm);
      setShowCreateModal(false);
      await loadStaff();
    } catch (err) {
      setCreateError(err.message || 'Error al crear el operador.');
    }
  };

  // ---------- Edit ----------
  const openEdit = (member) => {
    setEditForm({ id: member.id, fullName: member.fullName, email: member.email });
    setEditError('');
  };

  const handleEdit = async (e) => {
    e.preventDefault();
    setEditError('');
    try {
      await updateBackofficeStaffApi(editForm.id, { fullName: editForm.fullName, email: editForm.email });
      setEditForm(null);
      await loadStaff();
    } catch (err) {
      setEditError(err.message || 'Error al actualizar el operador.');
    }
  };

  // ---------- Status / Delete ----------
  const handleToggleStatus = async (member) => {
    setErrorMsg('');
    try {
      await changeBackofficeStaffStatusApi(member.id, !member.active);
      await loadStaff();
    } catch (err) {
      setErrorMsg(err.message || 'Error al cambiar el estado del operador.');
    }
  };

  const handleDelete = async (member) => {
    const ok = await confirm(`¿Desactivar al operador "${member.fullName}"?`, { title: 'Desactivar operador' });
    if (!ok) return;
    setErrorMsg('');
    try {
      await deleteBackofficeStaffApi(member.id);
      await loadStaff();
    } catch (err) {
      setErrorMsg(err.message || 'Error al desactivar el operador.');
    }
  };

  // ---------- Password reset ----------
  const openPasswordReset = (member) => {
    setPasswordTarget(member);
    setCurrentPassword('');
    setNewPassword('');
    setPasswordError('');
  };

  const handlePasswordReset = async (e) => {
    e.preventDefault();
    setPasswordError('');
    try {
      await changeBackofficeStaffPasswordApi(passwordTarget.id, newPassword, currentPassword || null);
      setPasswordTarget(null);
      setCurrentPassword('');
      setNewPassword('');
    } catch (err) {
      setPasswordError(err.message || 'Error al restablecer la contraseña.');
    }
  };

  return (
    <div className="space-y-6 max-w-5xl">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-ink">Operadores del Backoffice</h1>
          <p className="text-xs text-ink-soft font-medium">Cuentas SUPER_ADMIN con acceso al panel de administración de la plataforma</p>
        </div>

        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer"
        >
          <Plus className="w-4 h-4" /> Nuevo Operador
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
            Consultando operadores...
          </div>
        ) : staff.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <UserCog className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay operadores registrados</p>
              <p className="text-[11px] text-ink-muted">Crea la primera cuenta SUPER_ADMIN adicional</p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto -mx-2">
            <table className="w-full text-xs min-w-[680px]">
              <thead>
                <tr className="text-left text-[10px] font-extrabold text-ink-muted uppercase">
                  <th className="px-2 py-2">Operador</th>
                  <th className="px-2 py-2">Usuario</th>
                  <th className="px-2 py-2">Correo</th>
                  <th className="px-2 py-2">Estado</th>
                  <th className="px-2 py-2 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {staff.map(member => {
                  const self = isSelf(member);
                  return (
                    <tr key={member.id} className="border-t border-line hover:bg-gray-50/60 dark:hover:bg-white/5">
                      <td className="px-2 py-3">
                        <p className="font-extrabold text-ink flex items-center gap-1.5">
                          <ShieldCheck className="w-3.5 h-3.5 text-brand dark:text-brand" />
                          {member.fullName}
                          {self && <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400 font-extrabold">Tú</span>}
                        </p>
                      </td>
                      <td className="px-2 py-3 text-ink-soft font-mono">{member.username}</td>
                      <td className="px-2 py-3 text-ink-soft">{member.email}</td>
                      <td className="px-2 py-3">
                        <span className={`text-[10px] px-2 py-0.5 rounded-full font-extrabold ${
                          member.active
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                            : 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400'
                        }`}>
                          {member.active ? 'Activo' : 'Inactivo'}
                        </span>
                      </td>
                      <td className="px-2 py-3">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => openPasswordReset(member)}
                            title="Restablecer contraseña"
                            className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <KeyRound className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => openEdit(member)}
                            title="Editar operador"
                            className="p-1.5 text-ink-muted hover:text-brand dark:hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleToggleStatus(member)}
                            disabled={self && member.active}
                            title={self && member.active ? 'No puedes desactivar tu propia cuenta' : (member.active ? 'Desactivar' : 'Activar')}
                            className="p-1.5 text-ink-muted hover:text-amber-600 dark:hover:text-amber-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:text-gray-400"
                          >
                            {member.active ? <Ban className="w-4 h-4" /> : <CheckCircle2 className="w-4 h-4" />}
                          </button>
                          <button
                            onClick={() => handleDelete(member)}
                            disabled={self}
                            title={self ? 'No puedes eliminar tu propia cuenta' : 'Desactivar (baja lógica)'}
                            className="p-1.5 text-ink-muted hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:text-gray-400"
                          >
                            <Ban className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleCreate} className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-ink">Nuevo Operador</h3>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {createError && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{createError}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre completo:</label>
                <input
                  type="text"
                  value={createForm.fullName}
                  onChange={e => setCreateForm({ ...createForm, fullName: e.target.value })}
                  placeholder="Ej. Ana Torres"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Usuario:</label>
                <input
                  type="text"
                  value={createForm.username}
                  onChange={e => setCreateForm({ ...createForm, username: e.target.value })}
                  placeholder="ana.torres"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl font-mono text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Correo electrónico:</label>
                <input
                  type="email"
                  value={createForm.email}
                  onChange={e => setCreateForm({ ...createForm, email: e.target.value })}
                  placeholder="ana.torres@platform.internal"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Contraseña:</label>
                <input
                  type="password"
                  value={createForm.password}
                  onChange={e => setCreateForm({ ...createForm, password: e.target.value })}
                  placeholder="Mínimo 8 caracteres, letras y números"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                  minLength={8}
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
              >
                Crear Operador
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Edit Modal */}
      {editForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleEdit} className="bento-card max-w-md w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-ink">Editar Operador</h3>
              <button
                type="button"
                onClick={() => setEditForm(null)}
                className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {editError && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{editError}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nombre completo:</label>
                <input
                  type="text"
                  value={editForm.fullName}
                  onChange={e => setEditForm({ ...editForm, fullName: e.target.value })}
                  className="w-full p-2.5 bg-page border border-line rounded-2xl font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Correo electrónico:</label>
                <input
                  type="email"
                  value={editForm.email}
                  onChange={e => setEditForm({ ...editForm, email: e.target.value })}
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setEditForm(null)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
              >
                Guardar Cambios
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Password Reset Modal */}
      {passwordTarget && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handlePasswordReset} className="bento-card max-w-sm w-full bg-card p-6 rounded-3xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-extrabold text-base text-ink">Restablecer Contraseña</h3>
                <p className="text-[11px] text-ink-muted font-semibold">{passwordTarget.fullName}</p>
              </div>
              <button
                type="button"
                onClick={() => setPasswordTarget(null)}
                className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {passwordError && (
              <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{passwordError}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              {isSelf(passwordTarget) && (
                <div>
                  <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Contraseña actual:</label>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={e => setCurrentPassword(e.target.value)}
                    placeholder="Requerida para cambiar tu propia contraseña"
                    className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                    required
                  />
                </div>
              )}
              <div>
                <label className="font-bold text-gray-700 dark:text-gray-300 block mb-1">Nueva contraseña:</label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  placeholder="Mínimo 8 caracteres, letras y números"
                  className="w-full p-2.5 bg-page border border-line rounded-2xl text-xs text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
                  required
                  minLength={8}
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setPasswordTarget(null)}
                className="w-1/2 py-3 bg-gray-100 dark:bg-[#272730] hover:bg-gray-200 dark:hover:bg-line-strong text-gray-700 dark:text-gray-300 rounded-2xl text-xs font-bold cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="w-1/2 py-3 bg-solid hover:bg-solid-hover text-white rounded-2xl text-xs font-extrabold cursor-pointer shadow-md shadow-[#0A0A0A]/10"
              >
                Restablecer
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
