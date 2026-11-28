import React, { useState } from 'react';
import { Pencil } from 'lucide-react';
import { getPlansApi, updatePlanPricesApi } from '../../services/backoffice/storesApi';
import { Button, Card, EmptyState, Modal, MoneyInput, PageHeader } from '../../components/ui';
import useLoad from '../../hooks/useLoad';
import { formatPlanPrice } from '../../utils/planPrice';
import { toDigits } from '../../utils/format';

// An empty input means "not defined yet" and travels as null.
const toPrice = (digits) => (digits === '' ? null : Number(digits));

function PlanPricesModal({ plan, onClose, onSaved }) {
  const [monthly, setMonthly] = useState(toDigits(plan.monthlyPrice));
  const [yearly, setYearly] = useState(toDigits(plan.yearlyPrice));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSaving(true);
    try {
      await updatePlanPricesApi(plan.code, { monthlyPrice: toPrice(monthly), yearlyPrice: toPrice(yearly) });
      onSaved();
      onClose();
    } catch (err) {
      setError(err.message || 'No se pudieron guardar los precios.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Editar precios" subtitle={`${plan.name} (${plan.code})`} onClose={onClose} size="sm" dismissible={false}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <MoneyInput label="Precio mensual (COP)" value={monthly} onChange={setMonthly} placeholder="Sin definir" />
        <MoneyInput label="Precio anual (COP)" value={yearly} onChange={setYearly} placeholder="Sin definir" />
        <p className="text-[11px] font-medium text-ink-muted">Déjalo vacío si el precio aún no está definido.</p>
        {error && <p className="text-xs font-semibold text-red-500">{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="soft" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={saving}>Guardar</Button>
        </div>
      </form>
    </Modal>
  );
}

export default function PlansBento() {
  const { data: plans, loading, error, reload } = useLoad(getPlansApi, [], { initial: [] });
  const [editing, setEditing] = useState(null);

  return (
    <div className="space-y-6">
      <PageHeader title="Planes" subtitle="Precios del catálogo de planes que ven los locales" />

      <Card>
        {loading ? (
          <div className="py-12 text-center text-xs font-bold text-ink-muted">Consultando planes...</div>
        ) : error ? (
          <p className="py-12 text-center text-xs font-semibold text-red-500">{error}</p>
        ) : plans.length === 0 ? (
          <EmptyState title="No hay planes" description="El catálogo de planes está vacío" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="text-ink-muted font-bold border-b border-line">
                  <th className="py-2 pr-4">Plan</th>
                  <th className="py-2 pr-4">Código</th>
                  <th className="py-2 pr-4">Usuarios activos</th>
                  <th className="py-2 pr-4">Mensual</th>
                  <th className="py-2 pr-4">Anual</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {plans.map((p) => (
                  <tr key={p.code} className="border-b border-line last:border-0 text-ink">
                    <td className="py-3 pr-4 font-extrabold">{p.name}</td>
                    <td className="py-3 pr-4 font-mono text-ink-soft">{p.code}</td>
                    <td className="py-3 pr-4">{p.maxActiveUsers}</td>
                    <td className="py-3 pr-4">{formatPlanPrice(p.monthlyPrice)}</td>
                    <td className="py-3 pr-4">{formatPlanPrice(p.yearlyPrice)}</td>
                    <td className="py-3 text-right">
                      <Button size="sm" variant="soft" icon={Pencil} onClick={() => setEditing(p)}>Editar precios</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {editing && <PlanPricesModal plan={editing} onClose={() => setEditing(null)} onSaved={reload} />}
    </div>
  );
}
