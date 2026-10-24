import React, { useState, useEffect } from 'react';
import { Users, UserPlus, Search, Mail, Phone, CheckCircle2, Edit3, Trash2 } from 'lucide-react';
import { getCustomersApi, deleteCustomerApi } from '../services/customersApi';
import CreateDianCustomerModal from '../components/customers/CreateDianCustomerModal';
import { useAuth } from '../context/AuthContext';
import { useModal } from '../context/ModalContext';
import { Button, Badge, Card, EmptyState, PageHeader } from '../components/ui';
import useAutoRefresh from '../hooks/useAutoRefresh';

export default function CustomersBento() {
  const { user } = useAuth();
  const { confirm, notify } = useModal();
  const canDelete = user?.role === 'ADMINISTRATOR';

  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  // null = cerrado, {} = alta, {customer} = edición
  const [modal, setModal] = useState(null);

  const fetchCustomers = async ({ silent = false } = {}) => {
    try {
      const data = await getCustomersApi();
      setCustomers(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Error cargando clientes:', e);
      if (!silent) setCustomers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCustomers();
  }, []);
  useAutoRefresh(() => fetchCustomers({ silent: true }));

  const handleDelete = async (c) => {
    const ok = await confirm(
      `¿Eliminar a "${c.fullName || c.name}"? Las ventas ya emitidas conservan sus datos.`,
      { title: 'Eliminar cliente' }
    );
    if (!ok) return;
    try {
      await deleteCustomerApi(c.id);
      await fetchCustomers();
    } catch (e) {
      notify('No se pudo eliminar el cliente: ' + e.message);
    }
  };

  const term = search.toLowerCase();
  const filtered = customers.filter(c =>
    (c.fullName && c.fullName.toLowerCase().includes(term)) ||
    (c.name && c.name.toLowerCase().includes(term)) ||
    (c.identification && c.identification.includes(search))
  );

  return (
    <div className="space-y-6 select-none">
      <PageHeader
        title="Directorio de Clientes"
        subtitle="Gestión de clientes para emisión y facturación electrónica DIAN en BentoPOS"
        actions={<Button icon={UserPlus} onClick={() => setModal({})}>Registrar Nuevo Cliente</Button>}
      />

      <Card className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="relative w-72">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por cédula, NIT o nombre..."
              className="w-full pl-10 pr-3 py-1.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-ink"
            />
          </div>
          <span className="text-xs font-bold text-ink-muted">{filtered.length} clientes registrados</span>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Consultando clientes...</div>
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={Users}
            title="No hay clientes registrados aún"
            description="El sistema incluye automáticamente el Cliente General para ventas en mostrador"
            action={<Button size="sm" onClick={() => setModal({})}>+ Registrar Primer Cliente</Button>}
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map(c => (
              <div key={c.id} className="p-5 bg-card rounded-2xl border border-line space-y-3 shadow-xs hover:border-line-strong transition-all">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-mono font-bold text-ink-soft">CC/NIT: {c.identification}</span>
                  <Badge tone={c.active === false ? 'neutral' : 'success'}>
                    {c.active === false ? 'Inactivo' : 'Habilitado'}
                  </Badge>
                </div>

                <div>
                  <h3 className="font-extrabold text-sm text-ink">{c.fullName || c.name}</h3>
                  <div className="mt-2 space-y-1 text-xs text-ink-soft font-medium">
                    <p className="flex items-center gap-1.5"><Mail className="w-3.5 h-3.5 text-ink-muted" /> {c.email || 'Sin correo'}</p>
                    <p className="flex items-center gap-1.5"><Phone className="w-3.5 h-3.5 text-ink-muted" /> {c.phone || 'Sin teléfono'}</p>
                  </div>
                </div>

                <div className="pt-2 border-t border-line flex justify-between items-center text-xs">
                  <span className="text-emerald-600 dark:text-emerald-400 font-bold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Facturación Activa
                  </span>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => setModal({ customer: c })}
                      title="Editar cliente"
                      className="p-1.5 text-ink-muted hover:text-brand rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                    >
                      <Edit3 className="w-4 h-4" />
                    </button>
                    {canDelete && (
                      <button
                        onClick={() => handleDelete(c)}
                        title="Eliminar cliente"
                        className="p-1.5 text-ink-muted hover:text-red-600 dark:hover:text-red-400 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 cursor-pointer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      {modal && (
        <CreateDianCustomerModal
          customer={modal.customer}
          onClose={() => setModal(null)}
          onCreated={fetchCustomers}
        />
      )}
    </div>
  );
}
