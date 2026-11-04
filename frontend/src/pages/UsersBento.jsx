import React, { useState } from 'react';
import { UserPlus, Search, Edit3, KeyRound, Power, PowerOff, Trash2, ShieldCheck, Users } from 'lucide-react';
import {
  getUsersApi, getUserQuotaApi, createUserApi, updateUserApi, changeUserStatusApi, changeUserPasswordApi, deleteUserApi,
} from '../services/usersApi';
import useAuth from '../hooks/useAuth';
import Avatar from '../components/ui/Avatar';
import useModal from '../hooks/useModal';
import { Button, Badge, Card, EmptyState, Input, Modal, PageHeader, Select } from '../components/ui';
import useLoad from '../hooks/useLoad';

const ROLES = [
  { value: 'ADMINISTRATOR', label: 'Administrador', hint: 'Control total del local' },
  { value: 'SUPERVISOR', label: 'Supervisor', hint: 'Compras, inventario, clientes y reintentos de facturas' },
  { value: 'SELLER', label: 'Vendedor', hint: 'Caja: ventas y consulta de catálogo' },
];
const ROLE_LABEL = Object.fromEntries(ROLES.map(r => [r.value, r.label]));
const ROLE_TONE = { ADMINISTRATOR: 'brand', SUPERVISOR: 'info', SELLER: 'neutral' };

const EMPTY_FORM = { fullName: '', username: '', email: '', password: '', role: 'SELLER' };
const MIN_PASSWORD = 8;

function UserFormModal({ user: editing, storeId, onClose, onSaved }) {
  const isEdit = !!editing;
  const [form, setForm] = useState(isEdit
    ? { fullName: editing.fullName || '', username: editing.username, email: editing.email || '', password: '', role: editing.role }
    : EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const set = (patch) => setForm(prev => ({ ...prev, ...patch }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!form.fullName.trim()) return setError('El nombre completo es obligatorio.');
    if (!isEdit) {
      if (!form.username.trim()) return setError('El nombre de usuario es obligatorio.');
      if (form.password.length < MIN_PASSWORD) return setError(`La contraseña debe tener al menos ${MIN_PASSWORD} caracteres.`);
    }
    setSaving(true);
    try {
      if (isEdit) {
        await updateUserApi(editing.id, { fullName: form.fullName.trim(), email: form.email.trim(), role: form.role });
      } else {
        await createUserApi({
          fullName: form.fullName.trim(),
          username: form.username.trim(),
          email: form.email.trim() || undefined,
          password: form.password,
          role: form.role,
          storeId,
        });
      }
      onSaved();
      onClose();
    } catch (err) {
      setError(err.message || 'No se pudo guardar el usuario.');
    } finally {
      setSaving(false);
    }
  };

  const roleHint = ROLES.find(r => r.value === form.role)?.hint;

  return (
    <Modal
      title={isEdit ? 'Editar usuario' : 'Nuevo usuario'}
      subtitle={isEdit ? `@${editing.username}` : 'Personal con acceso a tu local'}
      onClose={onClose}
      dismissible={false}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Nombre completo" value={form.fullName} onChange={e => set({ fullName: e.target.value })} required />
        {!isEdit && (
          <Input label="Usuario" value={form.username} autoComplete="off"
            onChange={e => set({ username: e.target.value.replace(/\s/g, '') })} required />
        )}
        <Input label="Correo" type="email" value={form.email} onChange={e => set({ email: e.target.value })} />
        {!isEdit && (
          <Input label="Contraseña" type="password" autoComplete="new-password" value={form.password}
            hint={`Mínimo ${MIN_PASSWORD} caracteres`} onChange={e => set({ password: e.target.value })} required />
        )}
        <Select label="Rol" value={form.role} hint={roleHint} onChange={e => set({ role: e.target.value })}>
          {ROLES.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
        </Select>

        {error && <p className="text-xs font-semibold text-red-500">{error}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="soft" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={saving}>{isEdit ? 'Guardar cambios' : 'Crear usuario'}</Button>
        </div>
      </form>
    </Modal>
  );
}

function PasswordModal({ user: target, onClose }) {
  const { notify } = useModal();
  const [password, setPassword] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (password.length < MIN_PASSWORD) return setError(`La contraseña debe tener al menos ${MIN_PASSWORD} caracteres.`);
    setSaving(true);
    try {
      await changeUserPasswordApi(target.id, password);
      notify(`Contraseña de @${target.username} actualizada.`, { title: 'Listo', danger: false });
      onClose();
    } catch (err) {
      setError(err.message || 'No se pudo cambiar la contraseña.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Cambiar contraseña" subtitle={`${target.fullName} (@${target.username})`} onClose={onClose} size="sm" dismissible={false}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Nueva contraseña" type="password" autoComplete="new-password" value={password}
          hint={`Mínimo ${MIN_PASSWORD} caracteres`} onChange={e => { setPassword(e.target.value); setError(''); }} required />
        {error && <p className="text-xs font-semibold text-red-500">{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="soft" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={saving}>Actualizar</Button>
        </div>
      </form>
    </Modal>
  );
}

export default function UsersBento() {
  const { user: me } = useAuth();
  const { confirm, notify } = useModal();
  const { data: usersData, loading, reload } = useLoad(
    async () => {
      const [{ users, total }, quota] = await Promise.all([getUsersApi(), getUserQuotaApi().catch(() => null)]);
      return { users, total, quota };
    },
    [],
    { initial: { users: [], total: 0, quota: null }, autoRefresh: true },
  );
  const { users, total, quota } = usersData;
  const [search, setSearch] = useState('');
  // { type: 'form' | 'password', user? }
  const [modal, setModal] = useState(null);

  const run = async (action, errorPrefix) => {
    try {
      await action();
      reload();
    } catch (e) {
      notify(`${errorPrefix}: ${e.message}`);
    }
  };

  const handleToggle = async (u) => {
    if (u.active) {
      const ok = await confirm(`@${u.username} ya no podrá iniciar sesión hasta que lo reactives.`, {
        title: 'Desactivar usuario', confirmText: 'Desactivar', danger: false,
      });
      if (!ok) return;
    }
    run(() => changeUserStatusApi(u.id, !u.active), 'No se pudo cambiar el estado');
  };

  const handleDelete = async (u) => {
    const ok = await confirm(
      `Se eliminará definitivamente a ${u.fullName} (@${u.username}). Si solo quieres quitarle el acceso, desactívalo.`,
      { title: 'Eliminar usuario' },
    );
    if (!ok) return;
    run(() => deleteUserApi(u.id), 'No se pudo eliminar el usuario');
  };

  const term = search.toLowerCase();
  const filtered = users.filter(u =>
    (u.fullName || '').toLowerCase().includes(term) ||
    (u.username || '').toLowerCase().includes(term) ||
    (u.email || '').toLowerCase().includes(term)
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Usuarios del Local"
        subtitle="Administra quién accede a tu negocio y con qué permisos"
        actions={
          <Button icon={UserPlus} disabled={quota && !quota.canCreateMore} onClick={() => setModal({ type: 'form' })}>
            Nuevo usuario
          </Button>
        }
      />

      {quota && (
        <div className={`px-4 py-3 rounded-2xl border text-xs font-bold flex items-center gap-2 ${
          quota.canCreateMore
            ? 'bg-card border-line text-ink-soft'
            : 'bg-amber-50 dark:bg-amber-950/30 border-amber-200 dark:border-amber-800/40 text-amber-700 dark:text-amber-400'
        }`}>
          <Users className="w-4 h-4 shrink-0" />
          <span>
            Plan {quota.planName}: {quota.activeUsers} de {quota.maxActiveUsers} usuarios activos (tú incluido).
            {!quota.canCreateMore && ' Llegaste al límite: desactiva un usuario o pide cambiar de plan para crear más.'}
          </span>
        </div>
      )}

      <Card className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div className="relative w-72 max-w-full">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por nombre, usuario o correo..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
            />
          </div>
          <span className="text-xs font-bold text-ink-muted">
            {filtered.length} de {Math.max(total, users.length)} usuarios
          </span>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Consultando usuarios...</div>
        ) : filtered.length === 0 ? (
          <EmptyState icon={Users} title="No se encontraron usuarios" description="Ajusta la búsqueda o crea un nuevo usuario" />
        ) : (
          <div className="md:overflow-x-auto md:-mx-6">
            <table className="table-cards w-full text-left text-xs">
              <thead>
                <tr className="text-[10px] uppercase tracking-wider text-ink-muted border-b border-line">
                  <th className="py-3 px-6 font-extrabold">Usuario</th>
                  <th className="py-3 px-3 font-extrabold">Rol</th>
                  <th className="py-3 px-3 font-extrabold">Estado</th>
                  <th className="py-3 px-6 font-extrabold text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-line">
                {filtered.map(u => {
                  const isMe = u.username === me?.username;
                  return (
                    <tr key={u.id} className="hover:bg-gray-50 dark:hover:bg-white/5 transition-colors">
                      <td data-label="" className="py-3 px-6">
                        <div className="flex items-center gap-3">
                          <Avatar name={u.fullName || u.username} src={u.avatarUrl} className="w-9 h-9 rounded-full text-[11px]" />
                          <div className="min-w-0">
                            <span className="font-bold text-sm text-ink block truncate">
                              {u.fullName} {isMe && <span className="text-[10px] text-brand font-extrabold">(tú)</span>}
                            </span>
                            <span className="text-[11px] text-ink-soft truncate block">@{u.username}{u.email ? ` · ${u.email}` : ''}</span>
                          </div>
                        </div>
                      </td>
                      <td data-label="Rol" className="py-3 px-3">
                        <Badge tone={ROLE_TONE[u.role] || 'neutral'}>
                          {u.role === 'ADMINISTRATOR' && <ShieldCheck className="w-3 h-3" />}
                          {ROLE_LABEL[u.role] || u.role}
                        </Badge>
                      </td>
                      <td data-label="Estado" className="py-3 px-3">
                        <Badge tone={u.active ? 'success' : 'neutral'}>{u.active ? 'Activo' : 'Inactivo'}</Badge>
                      </td>
                      <td data-label="Acciones" className="py-3 px-6">
                        <div className="flex items-center justify-end gap-1">
                          <IconAction title="Editar" onClick={() => setModal({ type: 'form', user: u })}><Edit3 className="w-4 h-4" /></IconAction>
                          <IconAction title="Cambiar contraseña" onClick={() => setModal({ type: 'password', user: u })}><KeyRound className="w-4 h-4" /></IconAction>
                          <IconAction
                            title={isMe ? 'No puedes desactivarte a ti mismo' : u.active ? 'Desactivar' : 'Reactivar'}
                            disabled={isMe}
                            onClick={() => handleToggle(u)}
                          >
                            {u.active ? <PowerOff className="w-4 h-4" /> : <Power className="w-4 h-4" />}
                          </IconAction>
                          <IconAction
                            title={isMe ? 'No puedes eliminarte a ti mismo' : 'Eliminar'}
                            disabled={isMe}
                            danger
                            onClick={() => handleDelete(u)}
                          >
                            <Trash2 className="w-4 h-4" />
                          </IconAction>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {modal?.type === 'form' && (
        <UserFormModal user={modal.user} storeId={me?.storeId} onClose={() => setModal(null)} onSaved={() => reload()} />
      )}
      {modal?.type === 'password' && <PasswordModal user={modal.user} onClose={() => setModal(null)} />}
    </div>
  );
}

function IconAction({ danger, disabled, title, onClick, children }) {
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={`p-1.5 rounded-lg text-ink-muted hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed ${
        danger ? 'hover:text-red-600 dark:hover:text-red-400' : 'hover:text-brand'
      }`}
    >
      {children}
    </button>
  );
}
