import React, { useState } from 'react';
import { AlertCircle, Plus, Search, Store } from 'lucide-react';
import useLoad from '../../hooks/useLoad';
import useDebouncedValue from '../../hooks/useDebouncedValue';
import { Badge, Button, EmptyState, PageHeader } from '../../components/ui';
import Pager from '../../components/ui/Pager';
import NewBusinessModal from '../../components/backoffice/NewBusinessModal';
import BusinessDetailModal from '../../components/backoffice/BusinessDetailModal';
import { getBusinessesApi } from '../../services/backoffice/businessesApi';
import { BILLING_MODE_LABELS, additionalBranches } from '../../utils/business';

const EMPTY_PAGE = { content: [], totalPages: 0, totalElements: 0 };

/** Negocios con varias sucursales: la plataforma los activa y fija si facturan con un mismo NIT o con distintos. */
export default function BusinessesBento() {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState(null);
  const q = useDebouncedValue(query.trim());

  const { data, loading, error, reload } = useLoad(
    () => getBusinessesApi(q, page),
    [q, page],
    { initial: EMPTY_PAGE, errorMessage: 'No se pudieron cargar los negocios.' },
  );

  return (
    <div className="space-y-6 pb-12">
      <PageHeader
        title="Negocios"
        subtitle="Dueños con varias sucursales: activa el módulo y define cómo facturan"
        actions={<Button icon={Plus} onClick={() => setCreating(true)}>Nuevo negocio</Button>}
      />

      <div className="relative max-w-sm">
        <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="search"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setPage(0); }}
          placeholder="Buscar por nombre"
          className="w-full pl-10 pr-3.5 py-2.5 bg-card border border-line rounded-2xl text-xs font-semibold text-ink focus:outline-none focus:border-ink"
        />
      </div>

      {error && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="py-16 text-center text-xs font-bold text-ink-muted">Cargando...</div>
      ) : data.content.length === 0 ? (
        <EmptyState
          icon={Store}
          title={q ? 'Ningún negocio coincide con la búsqueda' : 'Aún no hay negocios con sucursales'}
          description={q ? undefined : 'Crea uno a partir de un local existente y su administrador.'}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {data.content.map((b) => (
            <button
              key={b.id}
              type="button"
              onClick={() => setSelected(b)}
              className="bento-card text-left p-5 space-y-3 cursor-pointer"
            >
              <div className="flex items-start justify-between gap-3">
                <h3 className="text-sm font-extrabold text-ink truncate">{b.name}</h3>
                <Badge tone="brand">{additionalBranches(b)} de {b.maxBranches} adicionales</Badge>
              </div>
              <p className="text-[11px] font-semibold text-ink-soft">{BILLING_MODE_LABELS[b.billingMode]}</p>
              <p className="text-[11px] font-medium text-ink-muted truncate">
                {b.branches.map((br) => br.name).join(' · ')}
              </p>
            </button>
          ))}
        </div>
      )}

      <Pager page={page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />

      {creating && (
        <NewBusinessModal
          onClose={() => setCreating(false)}
          onCreated={(business) => { setCreating(false); setSelected(business); reload({ silent: false }); }}
        />
      )}
      {selected && (
        <BusinessDetailModal
          key={selected.id}
          business={selected}
          onClose={() => setSelected(null)}
          onChanged={() => reload()}
          onDissolved={() => { setSelected(null); reload({ silent: false }); }}
        />
      )}
    </div>
  );
}
