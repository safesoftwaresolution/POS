import { getUnreadNotificationCountApi } from '../services/notificationsApi';
import useLoad from './useLoad';

const POLL_MS = 45000;

/**
 * Cantidad de notificaciones sin leer, consultada cada 45 s. Devuelve `[count, setCount]` para poder descontar una
 * al marcarla como leída sin esperar al siguiente ciclo. Si la consulta falla conserva el último valor.
 */
export default function useUnreadNotifications() {
  const { data, setData } = useLoad(
    (previous) => getUnreadNotificationCountApi().catch(() => previous),
    [],
    { initial: 0, autoRefresh: POLL_MS },
  );
  return [data, setData];
}
