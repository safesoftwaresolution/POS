# FEAT-024 — Infraestructura de Alegra (acceso, empresa, adaptador esqueleto)

> Parte del plan "Alegra como proveedor de facturación". Solo backend.

## Objetivo
Dejar a `billing.provider=alegra` arrancando y capaz de **probar conexión por local**, con el cliente HTTP, la resolución de credenciales por local/entorno y la lectura de la empresa en Alegra. Aún sin emitir documentos.

## Depende de
FEAT-021 (puerto de cuenta, `ActiveBillingProvider`) y FEAT-022 (`company.id` por local). Usa FEAT-023 solo para cablear el cliente de rangos.

## Datos de la API de Alegra (verificados en la documentación)
- Base: sandbox `https://sandbox-api.alegra.com/e-provider/col/v1`, producción `https://api.alegra.com/e-provider/col/v1`. Entornos independientes.
- Auth: `Authorization: Bearer <token>`; el token está ligado a una empresa principal; las empresas que emiten son "asociadas" (`company.id`). Cómo se obtiene el token **no está documentado** (ver FEAT-020).
- `GET /companies/{id}` → `id, identification, dv, name, tradeName, regimeCode, organizationType, identificationType, address{address,city,department,country}, email, phone, governmentStatus`.
- `GET /resolutions/{nit}` (solo producción) → `resolutions[{resolutionNumber,prefix,minNumber,maxNumber,startDate,endDate,technicalKey}]`.
- Errores: array `[{code,message}]` (400/404/500); idempotencia: 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS`/`IDEMPOTENCY_OUTCOME_INDETERMINATE`, 422 `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD`.

## Context Pack (R8)
- Docs (3): `backend/docs/modules/billing/spec.md`, `backend/docs/_security.md`, `backend/docs/_conventions.md`.
- Código (≤5): `adapters/factus/FactusClientHelper.java`, `adapters/factus/FactusCredentialsResolver.java`, `adapters/factus/FactusAccess.java` (patrones a imitar, **no modificar**), `config/http/OutboundHttp.java`, `billing/exception/BillingNotEnabledException.java`.
- Tests de referencia: `FactusCredentialsResolverTest`, `FactusInvoiceClientTest` (MockRestServiceServer).

## Qué se construye (`billing/internal/adapters/alegra/`, todo `@ConditionalOnProperty(billing.provider=alegra)`)
1. Propiedades: `alegra.url.sandbox`/`production` (`ALEGRA_URL_SANDBOX|PRODUCTION`, con los defaults de arriba), `alegra.token.sandbox`/`production` (`ALEGRA_API_TOKEN_SANDBOX|PRODUCTION`, **vacío por defecto**; el token nunca se loguea ni se serializa).
2. `AlegraAccess` (record: `storeId, environment, baseUrl, token, companyId`; `toString` sin secretos).
3. `AlegraAccessResolver`: como `FactusCredentialsResolver`: `storeId` explícito o `TenantContext`; exige `StoreBillingConfig` `ACTIVE`; `resolveIgnoringStatus(storeId, env)` para el test de conexión; falta de token o de `companyId` → `BillingNotEnabledException`. Sin fallback a otro local.
4. `AlegraApiException extends BillingProviderException` y `AlegraClientHelper` (`authorizedGet/Post`, `translate(action, ex)`; usar `OutboundHttp.restClient()`; truncar cuerpos de error a 500).
5. `AlegraCompanyProvider`: `GET /companies/{id}` con caché en memoria (TTL ≈ 10 min, clave `(storeId, env)`, invalidable); devuelve un record `AlegraCompany`.
6. `AlegraBillingAdapter implements ElectronicInvoicingProvider` (`getProviderName()="alegra"`, `displayName()="Alegra"`): implementa `BillingAccountProvider` (`isAccountConfigured` = token del entorno + `companyId`; `verifyConnection` = `GET /companies/{id}` ok; `onAccountChanged` = invalidar caché de empresa) y delega rangos en `AlegraNumberingRangeClient` (FEAT-023). Los métodos de factura/notas aún lanzan `UnsupportedOperationException("pendiente FEAT-025/026")` (el puerto ya trae `default`s; los abstractos `emitInvoice`, `emitCreditNote`, `emitDebitNote` devuelven `*Result.error("…no implementado")`).
7. `application.properties`: añadir las propiedades `alegra.*` (no cambiar aún el default de `billing.provider`; eso es FEAT-029). `.env.example`: variables nuevas comentadas.

## Criterios de aceptación
- [ ] Con `BILLING_PROVIDER=alegra` y sin tokens, la app arranca; `isAccountConfigured` = false y `test-connection` responde "cuenta no configurada" (sin 500).
- [ ] Con token y `company.id` válidos (sandbox), `POST …/billing/test-connection` responde éxito; con token inválido (401) responde fallo con mensaje claro y sin filtrar el token.
- [ ] Un local sin `ACTIVE` no puede resolver acceso; un local nunca usa el `company.id` de otro (test).
- [ ] Con `BILLING_PROVIDER=factus` o `mock` ningún bean de Alegra se registra.
- [ ] Pruebas con `MockRestServiceServer` para helper, resolver y caché de empresa. `.\mvnw.cmd test` en verde.

## Fuera de alcance
Emitir facturas/notas (FEAT-025/026); cambiar el default de `billing.provider`; `frontend/`.

## Riesgos
El token único de plataforma rompe la regla de CLAUDE.md "sin cuenta global" para Factus: documentar en FEAT-029 que el aislamiento en Alegra es por `company.id`.
