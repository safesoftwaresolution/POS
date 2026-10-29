import React, { useState } from 'react';
import { Plus, Search, Sparkles, Edit3, Trash2, Power, PowerOff } from 'lucide-react';
import {
  getServicesApi, createServiceApi, updateServiceApi, changeServiceStatusApi, deleteServiceApi,
} from '../services/servicesApi';
import { getCategoriesApi } from '../services/categoriesApi';
import ServiceFormModal from '../components/services/ServiceFormModal';
import { Badge, Button, Card, EmptyState, PageHeader } from '../components/ui';
import useModal from '../hooks/useModal';
import useLoad from '../hooks/useLoad';
import usePermissions from '../hooks/usePermissions';
import asList from '../utils/asList';
import { money } from '../utils/format';

const EMPTY_SERVICE = {
  id: null,
  code: '',
  name: '',
  description: '',
  categoryId: '',
  price: '',
  taxRate: '0',
  unspscCode: '',
  unitMeasureCode: '94',
  durationMinutes: '',
};

const ICON_BTN = 'p-1.5 text-ink-muted rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer';

/** Catálogo de servicios que se cobran sin inventario (spa, consultoría, mantenimiento…). */
export default function ServicesBento() {
  const { confirm, notify } = useModal();
  // Mismo criterio que el backend: crear/editar es de ADMINISTRATOR y SUPERVISOR; dar de baja, solo ADMINISTRATOR.
  const { isAdmin, canManageStock: canManage } = usePermissions();
  const [search, setSearch] = useState('');
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [saving, setSaving] = useState(false);

  const { data: services, loading, reload } = useLoad(
    () => asList(getServicesApi('', null, 0, 100, !showInactive)),
    [showInactive],
    { initial: [], autoRefresh: true },
  );
  const { data: categories } = useLoad(() => asList(getCategoriesApi()), [], { initial: [] });

  const openCreate = () => { setForm(EMPTY_SERVICE); setErrorMsg(''); };

  const openEdit = (s) => {
    const category = categories.find((c) => c.name === s.categoryName);
    setForm({
      id: s.id,
      code: s.code,
      name: s.name || '',
      description: s.description || '',
      categoryId: category ? String(category.id) : '',
      price: s.price != null ? String(Math.round(s.price)) : '',
      taxRate: String(s.taxRate ?? 0),
      unspscCode: s.unspscCode || '',
      unitMeasureCode: s.unitMeasureCode || '94',
      durationMinutes: s.durationMinutes ? String(s.durationMinutes) : '',
    });
    setErrorMsg('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    if (!form.name || !form.price || !form.categoryId) return;

    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      categoryId: Number(form.categoryId),
      price: Number(form.price),
      taxRate: Number(form.taxRate || 0),
      unspscCode: form.unspscCode.trim() || null,
      unitMeasureCode: form.unitMeasureCode.trim() || '94',
      durationMinutes: form.durationMinutes ? Number(form.durationMinutes) : null,
    };

    setSaving(true);
    try {
      if (form.id) {
        await updateServiceApi(form.id, payload);
      } else {
        await createServiceApi({ ...payload, code: form.code.trim() });
      }
      setForm(null);
      reload();
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo guardar el servicio. Intenta nuevamente.');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleStatus = async (s) => {
    const activate = s.active === false;
    if (!activate) {
      const ok = await confirm(`"${s.name}" dejará de aparecer en el punto de venta. Podrás reactivarlo cuando quieras.`, {
        title: 'Desactivar servicio', confirmText: 'Desactivar', danger: false,
      });
      if (!ok) return;
    }
    try {
      await changeServiceStatusApi(s.id, activate);
      reload();
    } catch (err) {
      notify('No se pudo cambiar el estado del servicio: ' + err.message);
    }
  };

  const handleDelete = async (s) => {
    const ok = await confirm(
      `"${s.name}" quedará inactivo y no se podrá vender. Las ventas ya emitidas no cambian.`,
      { title: 'Dar de baja el servicio', confirmText: 'Dar de baja' },
    );
    if (!ok) return;
    try {
      await deleteServiceApi(s.id);
      reload();
    } catch (err) {
      notify('Error eliminando el servicio: ' + err.message);
    }
  };

  const q = search.trim().toLowerCase();
  const filtered = services.filter((s) =>
    !q || (s.name || '').toLowerCase().includes(q) || (s.code || '').toLowerCase().includes(q));

  return (
    <div className="space-y-6">
      <PageHeader
        title="Servicios"
        subtitle="Servicios que ofreces y cobras sin inventario"
        actions={canManage ? <Button icon={Plus} onClick={openCreate}>Nuevo Servicio</Button> : null}
      />

      <Card className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filtrar por nombre o código..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
            />
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowInactive((v) => !v)}
              className={`px-3 py-1.5 rounded-full text-[11px] font-bold cursor-pointer border transition-colors ${
                showInactive ? 'bg-solid text-solid-ink border-transparent' : 'border-line text-ink-soft hover:border-line-strong'
              }`}
            >
              {showInactive ? 'Viendo inactivos' : 'Ver inactivos'}
            </button>
            <span className="text-xs font-bold text-ink-muted">
              {filtered.length} {showInactive ? 'servicios inactivos' : 'servicios registrados'}
            </span>
          </div>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Consultando servicios...</div>
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={Sparkles}
            title="No se encontraron servicios"
            description="Registra los servicios que ofreces para cobrarlos en el punto de venta y en la factura"
            action={canManage ? <Button size="sm" onClick={openCreate}>+ Registrar Primer Servicio</Button> : null}
          />
        ) : (
          <div className="md:overflow-x-auto md:-mx-6">
            <table className="table-cards w-full text-left text-xs">
              <thead>
                <tr className="border-b border-line bg-gray-50/50 dark:bg-page text-ink-muted uppercase text-[10px] tracking-wider font-extrabold">
                  <th className="py-3 px-6">Servicio</th>
                  <th className="py-3 px-3">Código</th>
                  <th className="py-3 px-3">Categoría</th>
                  <th className="py-3 px-3">Duración</th>
                  <th className="py-3 px-3 text-right">Precio</th>
                  <th className="py-3 px-3">IVA</th>
                  {canManage && <th className="py-3 px-6 text-right">Acciones</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-line">
                {filtered.map((s) => (
                  <tr
                    key={s.id}
                    className={`hover:bg-gray-50 dark:hover:bg-white/5 transition-colors group ${canManage ? 'cursor-pointer' : ''}`}
                    onClick={canManage ? () => openEdit(s) : undefined}
                  >
                    <td data-label="" className="py-3 px-6">
                      <span className="font-bold text-sm text-ink block truncate group-hover:text-brand transition-colors">{s.name}</span>
                      {s.description && <span className="text-[11px] text-ink-soft block truncate">{s.description}</span>}
                    </td>
                    <td data-label="Código" className="py-3 px-3 font-mono font-semibold text-ink-soft">{s.code}</td>
                    <td data-label="Categoría" className="py-3 px-3">
                      <Badge tone="brand">{s.categoryName || 'General'}</Badge>
                    </td>
                    <td data-label="Duración" className="py-3 px-3 text-ink-soft tabular-nums">
                      {s.durationMinutes ? `${s.durationMinutes} min` : '—'}
                    </td>
                    <td data-label="Precio" className="py-3 px-3 text-right font-black text-sm text-ink tabular-nums">{money(s.price)}</td>
                    <td data-label="IVA" className="py-3 px-3 text-ink-soft tabular-nums">{Number(s.taxRate || 0)}%</td>
                    {canManage && (
                      <td data-label="Acciones" className="py-3 px-6 text-right" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-1">
                          <button onClick={() => openEdit(s)} title="Editar servicio" className={`${ICON_BTN} hover:text-brand`}>
                            <Edit3 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleToggleStatus(s)}
                            title={s.active === false ? 'Reactivar servicio' : 'Desactivar servicio'}
                            className={`${ICON_BTN} hover:text-amber-600 dark:hover:text-amber-400`}
                          >
                            {s.active === false ? <Power className="w-4 h-4" /> : <PowerOff className="w-4 h-4" />}
                          </button>
                          {isAdmin && s.active !== false && (
                            <button onClick={() => handleDelete(s)} title="Dar de baja" className={`${ICON_BTN} hover:text-red-600 dark:hover:text-red-400`}>
                              <Trash2 className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {form && (
        <ServiceFormModal
          form={form}
          setForm={setForm}
          categories={categories}
          errorMsg={errorMsg}
          saving={saving}
          onSubmit={handleSubmit}
          onClose={() => setForm(null)}
        />
      )}
    </div>
  );
}
