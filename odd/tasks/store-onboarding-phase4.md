# Store onboarding phase 4 (alta del local)
Objective: remove the dead ends of register → verify email → create store → SUPER_ADMIN approval.
Branch: feature/store-onboarding-phase4 (from main). Runner: `.\mvnw.cmd test` in backend; `npm test`, `npm run lint`, `npm run build` in frontend.

## Specs
- S1 Rejected store gets a way out: `StoreApprovalMailer.sendRejected(storeName, reason, recipients)` sent after commit from `rejectStore`; `POST /api/v1/stores/me/resubmit` (ADMINISTRATOR) REJECTED → PENDING_VERIFICATION clearing `rejectionReason`; `PendingStoreReviewBento` per-status copy (PENDING_VERIFICATION, REJECTED, SUSPENDED, INACTIVE, load error) and, for REJECTED, an edit form shared with `StoreOnboardingBento` (`PUT /stores/me`) + "Reenviar a revisión".
- S2 Optional KYC upload while pending: `DocumentCard` + upload logic extracted from `DocumentsBento` into `components/`, shown on the pending screen as optional ("ayuda a aprobar más rápido").
- S3 Backend enforces store status: any store role of a non-ACTIVE store gets 403 `{"error":"StoreNotActive"}` from `JwtAuthenticationFilter`, except onboarding paths, `GET/PUT /stores/me`, `/stores/me/documents`, `/stores/me/resubmit`, `/auth/**`. Status read through `StoreFacade` in `UserServiceImpl.loadUserByUsername` into `PosUserDetails`. Frontend: `RequireActiveStore` wraps all store roles; `http.js` reacts to `StoreNotActive`.
- S4 Email verification: invalid/used/expired tokens → dedicated exception, 400 with a clear Spanish message; `VerifyEmailBento` offers resend on error and navigates correctly when the user already has a store; login detects unverified by `error` code `EmailNotVerified` (keep `data` on public auth endpoint errors); accents fixed in user-facing backend messages.
- S5 Email misconfiguration: startup validator fails when `email.provider=smtp` and `email.from` or `spring.mail.host` is blank; email mode in `GET /backoffice/onboarding/stats`; SUPER_ADMIN endpoint to mark a pending user as verified.
- S6 Less re-typing: `registerOwnStore` seeds settings via `SettingsFacade.seedSettings(storeId, ...)`; `GettingStarted` adds "Completa los datos de tu negocio" and "Activa la facturación electrónica (opcional)" (`/invoicing?tab=activation`).
- S7 Route guards: `/settings` read-only for SUPERVISOR (no admin-only `getOwnStoreApi`); `/categories` store route removed; `/onboarding/store` requires a session, redirects if the user already has a store, offers logout, handles `refreshSession()` failure; `createStore` validated with `@Valid` (name ≤100, phone ≤20, email format) and inputs get `maxLength`.

## Tasks
- [ ] T1 S1 inline
- [ ] T2 S2 inline
- [ ] T3 S3 inline (Risk: item 2, security guard)
- [ ] T4 S4 inline
- [ ] T5 S5 inline
- [ ] T6 S6 inline
- [ ] T7 S7 inline

## Log
L1 (session brief, 2026-11-05): "Implement "Fase 4: alta del local" in its own branch, one work-unit commit per item (tests with code), then report." Items 1-7 as quoted in Specs. Do not touch billing files (`billing/`, `POSBento.jsx`, `CheckoutModal.jsx`, `components/invoicing/`, `InvoicingBento.jsx`, `pages/backoffice/BillingBento.jsx`); V37 is taken by the billing session, use V38 if needed.
