# FEAT-023 — Numeración local: asignador de consecutivos y rangos

> Parte del plan "Alegra como proveedor de facturación". Solo backend.

## Objetivo
Que el sistema asigne él mismo el consecutivo y la resolución que Alegra exige en cada documento, sin huecos ni duplicados, y que el servicio de rangos existente siga funcionando sin cambios de lógica.

## Depende de
FEAT-022 (tablas y entidades). Idealmente FEAT-021 ya mergeado, pero no es obligatorio.

## Context Pack (R8)
- Docs (3): `backend/docs/modules/billing/spec.md`, `backend/docs/_conventions.md`, memoria del repo `stock-adjust-atomic-update` (misma lección: nada de leer-modificar-guardar sin lock real).
- Código (≤5): `billing/internal/services/NumberingRangeServiceImpl.java`, `billing/internal/adapters/NumberingRangeProvider.java`, `billing/dto/NumberingRangeDto.java`, `billing/dto/CreateNumberingRangeRequest.java`, y las entidades/repositorios de FEAT-022.
- Test de referencia: `StockConcurrencyTest` (hilos reales).

## Qué se construye (paquete `billing/internal/adapters/alegra/`)
1. `AlegraNumberAllocator` (`@Component`, condicional a `billing.provider=alegra`): `Allocation allocate(Long storeId, BillingEnvironment env, String document, String referenceCode)`.
   - `@Transactional(propagation = REQUIRES_NEW)`.
   - Si ya existe una asignación para `(store, env, document, referenceCode)` la devuelve (mismo `number`, `prefix`, `range`) → **idempotente**: un reintento no consume otro número.
   - Si no, bloquea la fila del rango activo (`findActiveForUpdate`), valida `active && !deleted`, vigencia (`start_date ≤ hoy ≤ end_date` cuando existan) y `next_number ≤ max_number`; asigna `next_number`, lo incrementa y escribe la asignación con `idempotency_key` UUIDv4 nuevo y `completed=false`.
   - Errores → `BillingNotEnabledException` con mensaje accionable ("no hay rango activo", "rango agotado", "resolución vencida").
   - `markCompleted(allocationId)` y `rotateIdempotencyKey(allocationId)`: tras una respuesta final no aceptada se genera una llave nueva conservando el número.
2. `AlegraNumberingRangeClient` (clase simple, **no** implementa el puerto; lo usará `AlegraBillingAdapter` en FEAT-024) con la semántica de `NumberingRangeProvider` sobre las tablas locales:
   - `listNumberingRanges` / `getNumberingRange(id)` (null si no es del local) / `createNumberingRange` / `deleteNumberingRange` (borrado lógico si ya tuvo asignaciones) / `toggleNumberingRangeStatus` / `updateNumberingRangeCurrent` (`current` = **próximo número a usar**).
   - `queryDianNumberingRanges`: `GET /resolutions/{nit}` queda para FEAT-024/025 (necesita el HTTP helper); aquí devolver `UnsupportedOperationException` con mensaje claro en sandbox.
   - Todo filtra por `TenantContext.getStoreId()` y el entorno activo de `StoreBillingConfig`; sin local → `BillingNotEnabledException`.
3. `CreateNumberingRangeRequest`: campos **opcionales** `from`, `to`, `startDate`, `endDate`, `technicalKey` (Factus los ignora; mantener el mapeo `resolution_number`). Documento `document`: `01`, `91`, `92`.
4. `NumberingRangeServiceImpl` **no cambia de lógica** (guardas: rango en uso, un activo por documento, consecutivo no retrocede; auditoría con `range_id` local). Solo ajustar si algún test revela una suposición de Factus.

## Reglas
- `@Transactional` solo donde corresponda; el lock lo toma el repositorio, no `SELECT … FOR UPDATE` manual sobre entidades ya cargadas.
- Todo por `TenantContext`; ningún fallback al local 1.

## Criterios de aceptación
- [ ] Test de **concurrencia con hilos reales**: N (≥20) emisiones simultáneas con referencias distintas → números únicos y consecutivos, sin huecos, sin excepciones de unicidad.
- [ ] Reintento con la misma referencia devuelve el mismo número; con rango agotado/vencido/inexistente falla con mensaje claro.
- [ ] `rotateIdempotencyKey` cambia la llave y conserva el número.
- [ ] `NumberingRangeServiceImplTest` sigue verde; nuevo test que ejecuta el servicio real contra el cliente local (crear → activar → un solo activo por documento → `current` no retrocede).
- [ ] `.\mvnw.cmd test` en verde.

## Fuera de alcance
HTTP hacia Alegra; emisión de documentos; `frontend/`.

## Riesgos
Si FEAT-020 revela que NC/ND no usan rango propio, el documento `91/92` pasa a usar un contador simple; confirmar antes de cerrar.
