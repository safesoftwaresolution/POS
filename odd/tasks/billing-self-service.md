# billing-self-service

Objective: fix the critical billing bugs found in the onboarding review and make DIAN e-invoicing enablement self-service through Alegra.
Branch: `feature/billing-self-service` (from `feature/alegra-billing`). Delivery: ask-on-risk, one work-unit commit per task. Runners: `.\mvnw.cmd test` (backend), `npm test` / `npm run lint` / `npm run build` (frontend).
Approved plan: `C:\Users\User\.claude\plans\quiero-que-me-des-glistening-token.md`.

## Specs

- S1 (retry): an invoice whose store has no active e-invoicing ends in ERROR with no automatic retry and the message "La facturación electrónica no está activa en este local"; `findDueForRetry` only returns invoices of stores whose billing is ACTIVE, so inactive stores never fill the batch.
- S2 (read without create): `GET` invoice by sale is read-only and answers 404 when there is none; the POS treats 404 as "no invoice".
- S3 (cashier summary): `GET /api/v1/billing/activation/summary` for every store role returns `{ active, environment }`; the checkout hides the DIAN switch when billing is not active and shows "MODO PRUEBA" in SANDBOX.
- S4 (self-service backend): fiscal profile (V37), Alegra company create/update, test set, resolutions import, auto-activation, setup progress endpoint, ACTIVE requires AUTHORIZED + usable range, allocator honours the default range, environment switch guarded and notified, DANE catalog, no document requirement. User: "Alegra no lo necesita" — verified: Alegra requires no documents.
- S5 (self-service frontend): wizard with Stepper (RUT data, DIAN enablement, numbering, done), "PRUEBA" badge for sandbox invoices, retry REJECTED, star only on invoice ranges, back office becomes monitoring.
- S6 (store onboarding): delegated to its own session (proposal "Fase 4: arreglos del alta del local").

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S1 | inline | done (RED→GREEN: InvoiceRetryJobTest.invoicesOfStoresWithoutActiveBillingNeverFillTheBatch, InvoiceServiceImplTest.processElectronicInvoice_StoreWithoutActiveBilling_*) | uncommitted, awaiting approval |
| T2 | S2 | inline | done (RED→GREEN: InvoiceServiceImplTest.getBySaleId_WithoutInvoice_*) | uncommitted, awaiting approval |
| T3 | S3 | inline | done (RED→GREEN: BillingActivationControllerTest.getSummary_*; browser: note + link verified, SANDBOX badge not exercised) | uncommitted, awaiting approval |
| T4 | S4 | inline | pending | |
| T5 | S5 | inline | pending | |
| T6 | S6 | separate session | proposed | |

## Log

- L1 (2026-11-05, user): "Quiero que me des el plan de cada uno de los arreglos. Adicionalmente dame un concreta afirmacion si los documentos se necesitan o no. Contexto: "Antes factus necesitaba los dou¿cumentos para poder activar la facturacion, pero alegra no lo necesita, ESO CREO. "Revisa"  Tambien quiero que me favorescaz al cliente en el sentido experiencia de usario que sea sencillo la utilizacion y confiugracion de toda la facturacion"
- L2 (2026-11-05, user decision): self-service flow ("Autoservicio (Recomendado)"); plan approved.
- L3 (2026-11-05): project rule requires explicit approval before any commit, so work-unit commits wait for the user. Phase 1 (T1-T3) done: frontend lint 0, 38 tests, build ok; focused backend tests green. Store-onboarding phase proposed as its own session (task card "Fase 4"). Next: T4 (V37 + Alegra onboarding client).
