# FEAT-018 — Emisión en 3 pasos (proveedor fuera de la transacción) y reintento automático

> Alcance acotado el 2026-10-20 (el mapeo DIAN y la rotación de claves pasaron a FEAT-030). **Prerrequisito de FEAT-025 (Alegra).** Solo backend.

## Objetivo
Que ninguna llamada HTTP al proveedor de facturación ocurra dentro de una transacción con lock de base de datos, que dos hilos nunca emitan la misma venta a la vez, y que los fallos temporales se reintenten solos con espera creciente.

## Depende de
FEAT-022 (para no pelear por `ElectronicInvoice` ni por el número de migración). Independiente de FEAT-023/024.

## Estado actual (verificado en el código)
- `InvoiceServiceImpl.processElectronicInvoice` es `@Transactional`: toma `findBySaleIdForUpdate` (lock pesimista), llama a `provider.emitInvoice(...)` y guarda, todo en una transacción.
- `applyResultToEntity` convierte cualquier resultado que no sea `VALIDATED`/`REJECTED` (incluido `PENDING`) en `markAsError`.
- `CreditNoteServiceImpl.emit` / `DebitNoteServiceImpl.emit` repiten el patrón: lock por `reference_code` + llamada al proveedor dentro de la transacción.
- `ElectronicInvoiceEventListener` (`@Async` + `AFTER_COMMIT`) llama a `processElectronicInvoice`; `retryInvoice` hace `prepareForRetry()` y reemite.
- No existe `@EnableScheduling` en el proyecto.

## Context Pack (R8)
- Docs (3): `backend/docs/modules/billing/spec.md`, `backend/docs/_conventions.md`, `backend/docs/_architecture.md`.
- Código por etapas (≤5): (A) `services/InvoiceServiceImpl.java`, `entities/ElectronicInvoice.java`, `repositories/ElectronicInvoiceRepository.java`, `listeners/ElectronicInvoiceEventListener.java`; (B) `services/CreditNoteServiceImpl.java`, `services/DebitNoteServiceImpl.java`, sus entidades y repositorios.
- Tests de referencia: `InvoiceServiceImplTest`, `ElectronicInvoiceEventListenerTest`, `CreditNoteServiceImplTest`, `DebitNoteServiceImplTest`, `StockConcurrencyTest` (patrón de hilos reales).

## Qué se construye
**Migración** `V<N>` (siguiente libre al implementar; una a la vez con FEAT-019/022): en `electronic_invoices` añadir `attempts` INT NOT NULL DEFAULT 0, `processing_started_at` TIMESTAMP NULL (lease), `next_retry_at` TIMESTAMP NULL; índice `(status, next_retry_at)`. Seguro en H2 (sin `CHECK … IN`). Test `V<N>MigrationSqlTest`.

**1. Emisión en tres pasos** (facturas; `processElectronicInvoice` deja de ser `@Transactional`):
- Paso A, bean auxiliar `InvoiceEmissionTransactions` (package-private, métodos `@Transactional`; **no** auto-invocación): `reserve(saleId, storeId)` → bloquea la fila, si `VALIDATED` devuelve "ya emitida"; si hay un lease vigente (`processing_started_at` más reciente que `billing.emission.lease`, por defecto 5 min) devuelve "en proceso" sin emitir; si no, pone `PENDING`, `attempts++`, `processing_started_at=now` y confirma.
- Paso B: llamada a `provider.emitInvoice(request)` **sin transacción**, con el mapeo de la venta a `InvoiceRequest` hecho antes. Cualquier excepción se convierte en `InvoiceResult.error`.
- Paso C, `complete(saleId, result)` `@Transactional`: vuelve a bloquear la fila, aplica `VALIDATED`/`REJECTED`/error (`PENDING` del proveedor se conserva como `PENDING`, no como `ERROR`), limpia `processing_started_at`, calcula `next_retry_at` según el resultado y guarda.
- Dominio en `ElectronicInvoice`: `startAttempt(now, lease)`, `finishAttempt()`, `scheduleRetry(nextAt)`; invariantes: una `VALIDATED` es inmutable y no se reserva.
- Gancho para FEAT-025: antes del paso B, un punto de extensión `reconcileIfProviderKnows(einvoice)` (no-op por defecto) para que Alegra consulte por `providerDocumentId` en vez de reemitir.

**2. Reintento automático**:
- `@EnableScheduling` en una clase de configuración de `config/` (primera del proyecto) y `InvoiceRetryJob` en `billing/internal/listeners/` (o `jobs/`): `@Scheduled(fixedDelayString = "${billing.retry.interval:60000}")`, habilitable con `billing.retry.enabled` (**apagado en el perfil `test`**).
- Selecciona un lote (`billing.retry.batch-size`, 20) de facturas `PENDING` con lease vencido o `ERROR` reintentable con `next_retry_at <= now` y `attempts < billing.retry.max-attempts` (8); `REJECTED` **nunca** se reintenta solo (requiere corrección de datos).
- Espera creciente: `min(2^attempts minutos, 60 min)` más un pequeño jitter.
- Para cada factura fija `TenantContext` con su `storeId` (y lo limpia), omite locales cuya facturación no esté `ACTIVE`, y reutiliza el mismo flujo de tres pasos. Un fallo en una factura no detiene el lote.
- Un lock distribuido no hace falta: el lease en BD evita doble emisión entre instancias.

**3. Notas crédito/débito** (segundo commit de la tarea): aplicar el mismo patrón de tres pasos a `CreditNoteServiceImpl.emit`/`DebitNoteServiceImpl.emit` (reservar por `reference_code`, llamar sin transacción, registrar). Sin job automático para notas (se emiten por acción del usuario); el reintento sigue siendo manual con el mismo `reference_code`.

**4. `retryInvoice`**: sigue siendo manual e idempotente; resetea `next_retry_at`/`attempts` solo si el estado lo permite y delega en el flujo de tres pasos.

## Reglas
- Ninguna llamada de red dentro de `@Transactional` ni con un lock tomado. Verificarlo con un proveedor falso que compruebe `TransactionSynchronizationManager.isActualTransactionActive() == false`.
- `@Transactional(readOnly = true)` para lecturas; mappers en `internal/mappers/`; respetar `TenantContext`.
- No cambiar contratos REST ni DTOs públicos.

## Criterios de aceptación
- [ ] Proveedor falso: durante `emitInvoice` no hay transacción activa ni lock de la fila.
- [ ] **Concurrencia con hilos reales**: dos (y diez) emisiones simultáneas de la misma venta → el proveedor se llama **una sola vez**; el resto recibe "en proceso" o "ya emitida".
- [ ] Fallo temporal (timeout/5xx) → la factura queda `PENDING`/`ERROR` con `next_retry_at`; el job la reintenta y termina `VALIDATED`; con `max-attempts` agotado deja de reintentar y queda visible.
- [ ] `REJECTED` no se reintenta automáticamente; `VALIDATED` nunca se reemite.
- [ ] Lease vencido (hilo caído a mitad) → otro hilo/instancia retoma.
- [ ] Notas crédito/débito: misma garantía de "sin HTTP dentro de la transacción" y sin doble emisión por `reference_code`.
- [ ] `.\mvnw.cmd test` completo en verde (listener, controladores y servicios existentes adaptados, no eliminados).

## Fuera de alcance
Código de Alegra; catálogo DIAN y rotación de claves (FEAT-030); outbox transaccional; reintento automático de notas; `frontend/`.

## Riesgos
Cambiar el estado de un `PENDING` que antes se guardaba como `ERROR` puede alterar lo que ve el usuario en `GET …/sales/{id}`: documentarlo en `billing/spec.md`. El job y el listener comparten el servicio: cuidar que `TenantContext` no se filtre entre hilos.
