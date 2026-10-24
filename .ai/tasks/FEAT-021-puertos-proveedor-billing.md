# FEAT-021 — Preparar los puertos del proveedor (refactor sin Alegra)

> Parte del plan "Alegra como proveedor de facturación". Solo backend. Factus debe comportarse **exactamente igual** al terminar.

## Objetivo
Quitar de la capa de servicios y de `settings` las dependencias directas a Factus, para que cualquier proveedor activo (Factus, mock o el futuro Alegra) pueda activarse, probarse y reportar errores sin tocar clases `Factus*`.

## Depende de
Nada. Puede hacerse en paralelo con FEAT-020 y FEAT-022.

## Context Pack (R8)
- Docs (3): `backend/docs/modules/billing/spec.md`, `backend/docs/_conventions.md`, `backend/docs/_architecture.md`.
- Código a leer/tocar, por etapas (≤5 a la vez):
  - Etapa A: `billing/internal/adapters/ElectronicInvoicingProvider.java`, `adapters/factus/FactusBillingAdapter.java`, `adapters/mock/MockBillingAdapter.java`.
  - Etapa B: `billing/internal/services/StoreBillingConfigServiceImpl.java`, `billing/internal/mappers/BillingActivationMapper.java`, `billing/exception/FactusApiException.java`, `exceptions/GlobalExceptionHandler.java`.
  - Etapa C: `settings/internal/services/CompanyLogoServiceImpl.java`.
- Raíz de paquetes: `backend/src/main/java/com/sciencebot/pos/`.

## Qué se construye
1. `BillingProviderException` (RuntimeException con `upstreamStatus`, `isAuthenticationError()`, `isTemporary()`); `FactusApiException` pasa a extenderla sin cambiar su API. Handler en `GlobalExceptionHandler` para la base (mismo mapeo HTTP que el de Factus) y un handler de `UnsupportedOperationException` → **501** con mensaje claro. El handler `handleFactusApi` se conserva.
2. Puerto `BillingAccountProvider` en `billing/internal/adapters/`: `boolean isAccountConfigured(Long storeId, BillingEnvironment env)`, `void verifyConnection(Long storeId, BillingEnvironment env)` (lanza `BillingProviderException`), `void onAccountChanged(Long storeId)`, `String displayName()`. `ElectronicInvoicingProvider` lo extiende.
   - `FactusBillingAdapter`: delega en `FactusCredentialsResolver`/`FactusTokenManager` y `FactusCredentials.isComplete()`; `displayName()="Factus"`.
   - `MockBillingAdapter`: `true`/no-op, `displayName()="Mock"`.
3. `ActiveBillingProvider` (`@Component`): recibe `List<ElectronicInvoicingProvider>` y `${billing.provider}`; `get()` devuelve el activo o lanza `IllegalStateException` con los disponibles. **No** reemplaza los `resolveProvider()` de los 4 servicios (siguen igual).
4. `BillingProviderStartupValidator`: falla el arranque si `billing.provider` no existe entre los beans `ElectronicInvoicingProvider` (cumple el "fail fast" de CLAUDE.md).
5. `StoreBillingConfigServiceImpl`: sustituir `FactusTokenManager` y `credentialsFor(env).isComplete()` (transición a `ACTIVE`, `switchEnvironment`, `testConnection`, invalidar caché tras `updateCredentials`) por `ActiveBillingProvider`; el texto de `notifyStore` usa `displayName()`. `BillingConnectionTestResult.success` recibe el nombre del proveedor en el mensaje.
6. `BillingActivationMapper`: `sandboxConfigured`/`productionConfigured` salen de `isAccountConfigured`.
7. `CompanyLogoServiceImpl.syncLogoWithFactus`: salir temprano (devolver `false`, log info) si `billing.provider != factus` (campo `@Value`, sin cambiar los constructores existentes).

## Reglas a respetar
- `@Transactional(readOnly = true)` en lecturas; mappers en `internal/mappers/`; nada de importar `internal/` de otro módulo. No cambiar rutas REST ni DTOs públicos.
- No leer `FactusTokenManager` desde servicios fuera de `adapters/factus` al terminar (`grep -r "adapters.factus" src/main` solo debe dar `adapters/factus/**`, más `settings` si aún lo usa para el sync, que sigue protegido por la guarda).

## Criterios de aceptación
- [ ] `.\mvnw.cmd test` completo en verde (los tests de `StoreBillingConfigServiceImplTest` se adaptan al puerto; los de Factus no cambian).
- [ ] Con `BILLING_PROVIDER=siigo` la aplicación **no arranca** y el error lista los proveedores disponibles.
- [ ] Con `BILLING_PROVIDER=factus` y `=mock`, `test-connection`, activación y cambio de entorno funcionan igual que antes.
- [ ] Una excepción `UnsupportedOperationException` desde un controlador de billing responde 501, no 500.

## Pruebas nuevas
`BillingProviderStartupValidatorTest`, `ActiveBillingProviderTest`, test del handler 501, `StoreBillingConfigServiceImplTest` actualizado, `CompanyLogoServiceTest` (no sincroniza si el proveedor no es Factus).

## Fuera de alcance
Cualquier código de Alegra; consolidar los 4 `resolveProvider()`; renombrar `factusNumber`; `frontend/`.
