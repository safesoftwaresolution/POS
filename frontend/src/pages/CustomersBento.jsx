import React, { useState, useEffect } from 'react';
import { Users, UserPlus, Search, Mail, Phone, CheckCircle2 } from 'lucide-react';
import { getCustomersApi } from '../services/customersApi';
import CreateDianCustomerModal from '../components/customers/CreateDianCustomerModal';

export default function CustomersBento() {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);

  const loadCustomers = async () => {
    setLoading(true);
    try {
      const data = await getCustomersApi();
      setCustomers(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Error cargando clientes:', e);
      setCustomers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCustomers();
  }, []);

  const filtered = customers.filter(c =>
    (c.fullName && c.fullName.toLowerCase().includes(search.toLowerCase())) ||
    (c.name && c.name.toLowerCase().includes(search.toLowerCase())) ||
    (c.identification && c.identification.includes(search))
  );

  return (
    <div className="space-y-6 select-none">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-[#0A0A0A] dark:text-white">Directorio de Clientes</h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Gestión de clientes para emisión y facturación electrónica DIAN en BentoPOS</p>
        </div>

        <button
          onClick={() => setShowModal(true)}
          className="px-4 py-2.5 bg-[#0A0A0A] hover:bg-[#202020] text-white rounded-2xl text-xs font-bold flex items-center gap-2 shadow-md shadow-[#0A0A0A]/10 cursor-pointer transition-all"
        >
          <UserPlus className="w-4 h-4" /> Registrar Nuevo Cliente
        </button>
      </div>

      <div className="bento-card p-6 space-y-4 bg-white dark:bg-[#17171B] border-[#EAEAEA] dark:border-[#272730]">
        <div className="flex items-center justify-between">
          <div className="relative w-72">
            <Search className="w-4 h-4 text-gray-400 dark:text-gray-500 absolute left-3.5 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por cédula, NIT o nombre..."
              className="w-full pl-10 pr-3 py-1.5 bg-[#F7F7F5] dark:bg-[#0E0E12] border border-[#EAEAEA] dark:border-[#272730] rounded-2xl text-xs font-semibold text-[#0A0A0A] dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-[#0A0A0A]"
            />
          </div>
          <span className="text-xs font-bold text-gray-400 dark:text-gray-500">{filtered.length} clientes registrados</span>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-gray-400 dark:text-gray-500">
            Consultando clientes...
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-12 text-center space-y-3 bg-gray-50/50 dark:bg-white/5 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
            <Users className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto stroke-1" />
            <div>
              <p className="text-xs font-bold text-gray-700 dark:text-gray-300">No hay clientes registrados aún</p>
              <p className="text-[11px] text-gray-400 dark:text-gray-500">El sistema incluye automáticamente el Cliente General para ventas en mostrador</p>
            </div>
            <button
              onClick={() => setShowModal(true)}
              className="px-4 py-2 bg-[#0A0A0A] text-white rounded-xl text-xs font-extrabold shadow-sm hover:bg-[#202020] cursor-pointer"
            >
              + Registrar Primer Cliente
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map(c => (
              <div key={c.id} className="p-5 bg-white dark:bg-[#17171B] rounded-2xl border border-gray-200 dark:border-[#272730] space-y-3 shadow-xs hover:border-[#D0D0CE] transition-all">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-mono font-bold text-gray-500 dark:text-gray-400">CC/NIT: {c.identification}</span>
                  <span className="text-[10px] px-2.5 py-0.5 rounded-full font-extrabold bg-emerald-100 dark:bg-emerald-950/30 text-emerald-600 dark:text-emerald-400">
                    Habilitado
                  </span>
                </div>

                <div>
                  <h3 className="font-extrabold text-sm text-[#0A0A0A] dark:text-white">{c.fullName || c.name}</h3>
                  <div className="mt-2 space-y-1 text-xs text-gray-500 dark:text-gray-400 font-medium">
                    <p className="flex items-center gap-1.5"><Mail className="w-3.5 h-3.5 text-gray-400 dark:text-gray-500" /> {c.email || 'Sin correo'}</p>
                    <p className="flex items-center gap-1.5"><Phone className="w-3.5 h-3.5 text-gray-400 dark:text-gray-500" /> {c.phone || 'Sin teléfono'}</p>
                  </div>
                </div>

                <div className="pt-2 border-t border-gray-200 dark:border-[#272730] flex justify-between items-center text-xs">
                  <span className="text-emerald-600 dark:text-emerald-400 font-bold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Facturación Activa
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showModal && (
        <CreateDianCustomerModal
          onClose={() => setShowModal(false)}
          onCreated={loadCustomers}
        />
      )}
    </div>
  );
}
