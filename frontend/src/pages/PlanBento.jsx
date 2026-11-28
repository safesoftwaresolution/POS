import React from 'react';
import { Link } from 'react-router-dom';
import { Users } from 'lucide-react';
import { getMyPlansApi } from '../services/storesApi';
import { getUserQuotaApi } from '../services/usersApi';
import { Badge, Card, EmptyState, PageHeader } from '../components/ui';
import useLoad from '../hooks/useLoad';
import { formatPlanPrice } from '../utils/planPrice';

const hasPrice = (value) => value !== null && value !== undefined && value !== '';

function PriceLine({ value, suffix }) {
  return (
    <p className="text-sm font-bold text-ink">
      {formatPlanPrice(value)}
      {hasPrice(value) && Number(value) !== 0 && <span className="text-xs font-semibold text-ink-muted">{suffix}</span>}
    </p>
  );
}

export default function PlanBento() {
  const { data, loading, error } = useLoad(
    async () => {
      const [catalog, quota] = await Promise.all([getMyPlansApi(), getUserQuotaApi().catch(() => null)]);
      return { currentPlanCode: catalog.currentPlanCode, plans: catalog.plans || [], quota };
    },
    [],
    { initial: { currentPlanCode: null, plans: [], quota: null } },
  );
  const { currentPlanCode, plans, quota } = data;
  const current = plans.find((p) => p.code === currentPlanCode);

  return (
    <div className="space-y-6">
      <PageHeader title="Mi plan" subtitle="Consulta el plan de tu local y los planes disponibles" />

      {loading ? (
        <div className="py-12 text-center text-xs font-bold text-ink-muted">Consultando planes...</div>
      ) : error ? (
        <p className="py-12 text-center text-xs font-semibold text-red-500">{error}</p>
      ) : (
        <>
          {(current || quota) && (
            <div className="px-4 py-3 rounded-2xl border border-line bg-card text-xs font-bold text-ink-soft flex items-center gap-2">
              <Users className="w-4 h-4 shrink-0" />
              <span>
                Plan actual: {quota?.planName || current?.name}
                {quota && <> · <span>{`${quota.activeUsers} de ${quota.maxActiveUsers} usuarios activos`}</span></>}
              </span>
            </div>
          )}

          {plans.length === 0 ? (
            <EmptyState icon={Users} title="No hay planes disponibles" description="Aún no se ha publicado el catálogo de planes" />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {plans.map((plan) => (
                <div key={plan.code} data-plan={plan.code} className="p-5 bg-card rounded-2xl border border-line space-y-3 shadow-xs">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="font-extrabold text-sm text-ink">{plan.name}</h3>
                    {plan.code === currentPlanCode && <Badge tone="brand">Plan actual</Badge>}
                  </div>
                  <p className="text-xs font-medium text-ink-soft">
                    {`Hasta ${plan.maxActiveUsers} usuarios activos (administrador incluido)`}
                  </p>
                  <div className="space-y-1">
                    <PriceLine value={plan.monthlyPrice} suffix="/mes" />
                    <PriceLine value={plan.yearlyPrice} suffix="/año" />
                  </div>
                </div>
              ))}
            </div>
          )}

          <Card>
            <p className="text-xs font-semibold text-ink-soft">
              Para cambiar de plan, escríbenos a <Link to="/support" className="underline text-ink">soporte</Link>.
            </p>
          </Card>
        </>
      )}
    </div>
  );
}
