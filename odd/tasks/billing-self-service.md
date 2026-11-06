# billing-self-service

Objective: fix the critical billing bugs found in the onboarding review and make DIAN e-invoicing enablement self-service through Alegra.
Branch: `feature/billing-self-service` (from `feature/alegra-billing`). Delivery: ask-on-risk, one work-unit commit per task. Runners: `.\mvnw.cmd test` (backend), `npm test` / `npm run lint` / `npm run build` (frontend).
Approved plan: `C:\Users\User\.claude\plans\quiero-que-me-des-glistening-token.md`.

## Specs

- S1 (retry): an invoice whose store has no active e-invoicing ends in ERROR with no automatic retry and the message "La facturación electrónica no está activa en este local"; `findDueForRetry` only returns invoices of stores whose billing is ACTIVE, so inactive stores never fill the batch.
- S2 (read without create): `GET` invoice by sale is read-only and answers 404 when there is none; the POS treats 404 as "no invoice".
- S3 (cashier summary): `GET /api/v1/billing/activation/summary` for every store role returns `{ active, environment }`; the checkout hides the DIAN switch when billing is not active and shows "MODO PRUEBA" in SANDBOX.
- S4 (self-service backend): fiscal profile (V37), Alegra company create/update, test set, resolutions import, auto-activation, setup progress endpoint, ACTIVE requires AUTHORIZED + usable range, allocator honours the default range, environment switch guarded and notified, DANE catalog, no document requirement. User: "Alegra no lo necesita" — verified: Alegra requires no documents. Stores go straight to PRODUCTION (user chose "Sin modo prueba (Recomendado)"): the Alegra sandbox only invoices with the guide NIT 900559088, so SANDBOX stays for platform QA only.
  - S4a (schema + states): V37 drops `CK_store_billing_config_status` (no new `CHECK … IN`), maps DOCUMENTS_SUBMITTED / AWAITING_PROVIDER / REJECTED to `IN_SETUP`; states become `NOT_REQUESTED → IN_SETUP → ACTIVE ⇄ SUSPENDED` (SUPER_ADMIN may also suspend from IN_SETUP); `requestActivation` no longer requires documents and moves NOT_REQUESTED → IN_SETUP; creates `billing_company_profile` (entity + repository); adds nullable `environment` to `electronic_invoices`, backfilled from the store's config and set when an emission is reserved; `ElectronicInvoiceDto` exposes it.
  - S4b (Alegra onboarding port): `BillingOnboardingProvider` (Alegra + mock, chosen by `billing.provider`) with create company (`POST /companies`, `useAlegraCertificate=true`, `notificationByEmail.enabled=true`), update company (`PATCH /companies/{id}`), start DIAN enablement (`POST /test-sets`, `type=invoices`, `governmentId`), read enablement (`governmentStatus.invoices`), list resolutions (`GET /resolutions/{nit}`); platform access per environment without a company.
  - S4c (setup service): fiscal profile save → company in PRODUCTION, test set with the owner's TestSetId, import resolutions, auto-activation, `GET /billing/setup`, guarded environment switch, default range honoured by the allocator, clear messages, notifications link to `/invoicing?tab=activation`, "platform not ready" when the production token is missing.
- S5 (self-service frontend): wizard with Stepper (RUT data, DIAN enablement, numbering, done), "PRUEBA" badge for sandbox invoices, retry REJECTED, star only on invoice ranges, back office becomes monitoring.
- S6 (store onboarding): delegated to its own session (proposal "Fase 4: arreglos del alta del local").

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S1 | inline | done (RED→GREEN: InvoiceRetryJobTest.invoicesOfStoresWithoutActiveBillingNeverFillTheBatch, InvoiceServiceImplTest.processElectronicInvoice_StoreWithoutActiveBilling_*) | uncommitted, awaiting approval |
| T2 | S2 | inline | done (RED→GREEN: InvoiceServiceImplTest.getBySaleId_WithoutInvoice_*) | uncommitted, awaiting approval |
| T3 | S3 | inline | done (RED→GREEN: BillingActivationControllerTest.getSummary_*; browser: note + link verified, SANDBOX badge not exercised) | uncommitted, awaiting approval |
| T1-T3 commits | S1-S3 | — | committed | 48e2a90, 8375168 |
| T4a | S4a | delegated (writer trigger: parallel with T4b, disjoint surfaces; long-session backstop) | done (V37MigrationSqlTest, BillingActivationStatusTest, StoreBillingConfigServiceImplTest…; suite 907 green; dev boot on H2 applies V37) + parent fix: StorePurgeDao deletes billing_company_profile (NativeSqlDaosTest RED→GREEN) | uncommitted |
| T4b | S4b | delegated (writer trigger: parallel with T4a, disjoint surfaces) | done (21 new tests RED→GREEN; 42 in its set) + parent: AlegraBeansNotRegisteredTest covers AlegraOnboardingClient | uncommitted |
| T4c | S4c | delegated (long-session backstop) | done (DianCheckDigitTest, FiscalProfileRequestValidationTest, BillingSetupServiceImplTest, BillingSetupControllerTest, allocator default-range tests; suite 986 green; API walk on H2 mock ends ACTIVE/PRODUCTION). Deviations: import registers only the newest valid resolution (RN-BILL-008 one active range per document), NC/ND note ranges only after an invoice range exists | uncommitted; independent verifier: S4a/S4b/S4c PASS, no blockers; advisory 2 fixed by parent (a SUSPENDED store keeps its environment when saving the fiscal profile, StoreBillingConfigServiceImplTest RED→GREEN) |
| T5 | S5 | inline | pending | |
| T6 | S6 | separate session | proposed | |

## Log

- L1 (2026-11-05, user): "Quiero que me des el plan de cada uno de los arreglos. Adicionalmente dame un concreta afirmacion si los documentos se necesitan o no. Contexto: "Antes factus necesitaba los dou¿cumentos para poder activar la facturacion, pero alegra no lo necesita, ESO CREO. "Revisa"  Tambien quiero que me favorescaz al cliente en el sentido experiencia de usario que sea sencillo la utilizacion y confiugracion de toda la facturacion"
- L2 (2026-11-05, user decision): self-service flow ("Autoservicio (Recomendado)"); plan approved.
- L3 (2026-11-05): project rule requires explicit approval before any commit, so work-unit commits wait for the user. Phase 1 (T1-T3) done: frontend lint 0, 38 tests, build ok; focused backend tests green. Store-onboarding phase proposed as its own session (task card "Fase 4"). Next: T4 (V37 + Alegra onboarding client).
- L4 (2026-11-06, user): "Sí, commit y sigue" → Phase 1 committed (48e2a90, 8375168).
- L5 (2026-11-06, user decision): "Sin modo prueba (Recomendado)". Evidence: Alegra sandbox SETP resolution 18760000001 belongs to NIT 900559088; any other NIT is rejected with DIAN code 89 (Engram #31). S4 rewritten: production only for stores.
- L6 (2026-11-06): Phase 2 verified independently (985 tests in scratch, V37 data probe, allocator 40-thread probe, no provider call inside a transaction, tenant isolation). Open advisories: Flyway V35-V37 collide with main/develop (renumber before merging there); double-submit of the fiscal profile can orphan an Alegra company; concurrent GETs can auto-activate twice (duplicate notification); a store suspended while IN_SETUP can only return to ACTIVE; DANE catalog still pending (needs a download, ask the user).
