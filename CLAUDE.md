# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Monorepo for a retail POS system (sales, real-time inventory, and Colombian DIAN electronic invoicing).

- `backend/` — Java 21 + Spring Boot REST API, structured as a **modular monolith** (vertical slicing).
- `frontend/` — React 19 + Vite + Tailwind CSS 4 SPA. Pure API consumer; contains **no business logic**.

Documentation (Spanish) lives in `backend/docs/` — `_architecture.md`, `_conventions.md`, `_database-schema.md`, `_security.md`, `_roadmap.md` (per-module status/gaps/backlog), and per-module docs under `backend/docs/modules/`. Consult these before non-trivial backend changes; they are the source of truth for conventions.

This repo also has a parallel `.ai/` harness (`.ai/agents.md`) describing a Leader/Implementer/Reviewer/Committer protocol with its own validation script (`.ai/init.ps1` / `init.sh`) and progress log (`.ai/progress/history.md`), authored independently of Claude Code. Its substantive rules (SSOT adherence to `backend/docs/`, no direct schema edits outside Flyway, explicit approval before any commit, never push/amend/rebase without being asked) already match how you should operate here regardless; treat `.ai/progress/history.md` as another source of project history worth checking, and flag (don't silently "fix") any drift you find between `.ai/features.json`/`init.ps1`'s module list and the actual modules under `backend/src/main/java/com/sciencebot/pos/`.

## Commands

All backend commands run from `backend/` (use `.\mvnw.cmd` on this Windows machine, `./mvnw` on Unix):

```powershell
.\mvnw.cmd spring-boot:run                    # Run API on :8080 (default 'dev' profile = file-based H2 in backend/data, no Docker needed)
.\mvnw.cmd test                               # Run all tests
.\mvnw.cmd test -Dtest=SaleServiceImplTest    # Single test class
.\mvnw.cmd test "-Dtest=AuthServiceTest#login_succeeds"   # Single test method
.\mvnw.cmd clean package -DskipTests          # Build production JAR
docker compose up -d                          # Full stack (PostgreSQL + app); db exposed on :5434
```

Frontend commands run from `frontend/` (requires backend running first; Vite proxies `/api` → `http://localhost:8080`):

```powershell
npm install
npm run dev       # Dev server on :3000
npm run build     # Production build
npm run lint      # oxlint
npm run preview   # Preview production build
```

Default login: `admin` / `Password123`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Backend Architecture

### Modular monolith with strict encapsulation
Each business module (`auth`, `users`, `categories`, `products`, `inventory`, `customers`, `suppliers`, `purchases`, `sales`, `billing`, `reports`, `settings`, `stores`, `legal`, `support`, `notifications`, `storage`, `backoffice`) is a vertical slice under `com.sciencebot.pos`; each has a `backend/docs/modules/<module>/spec.md`. `billing` is the one exception to the package layout: its public API lives in `billing/api`, `billing/dto` and `billing/exception` (decision pending, see `backend/docs/_roadmap.md`).

- **Module root** — the public API: `[Module]Facade` interface, output DTOs (`[Module]Dto`), input commands (`Create[Module]Command`), and domain events. These are the *only* types other modules may reference.
- **`internal/`** — private implementation, split into `controllers/`, `services/`, `repositories/`, `entities/`, `mappers/`. Classes here should be package-private wherever possible and are invisible to other modules.

**Cross-module rules (enforced by convention — see `backend/docs/_conventions.md`):**
1. A module may only inject *another* module's public `Facade` — never anything under another module's `internal/`.
2. JPA `@Entity` classes never leave their module. All cross-module and external communication uses immutable `record` DTOs.
3. Entity↔DTO mapping lives in dedicated `@Component` classes in `internal/mappers/` — never as private methods inside services.
4. Services: `@Transactional(readOnly = true)` for reads, `@Transactional` for writes; they implement the module's `Facade`.
5. Controllers map `/api/v1/[module]` and talk only to their own module's service.

### CQRS read models
The `reports` module bypasses JPA and uses `JdbcClient` with native SQL aggregations (`SUM`/`COUNT`/`GROUP BY`) to avoid ORM overhead and N+1 problems. Follow this pattern for new read-heavy analytics rather than loading domain entities.

### Concurrency
Stock changes go through `ProductFacade.adjustStock` → one atomic conditional `UPDATE` (`ProductRepository.adjustStockInStore`), so concurrent sales/purchases of the same product never lose an update or oversell. **Do not** reimplement it as `SELECT … FOR UPDATE` + mutate the entity: inside the sale transaction the product is already loaded and Hibernate returns the stale managed instance (this was a real bug; `StockConcurrencyTest` guards it). Billing documents are locked by `reference_code`/sale. Outbound HTTP (Factus) must use `OutboundHttp.restClient()` (connect/read timeouts), never `RestClient.create()`.

### Multi-tenancy (store scoping)
Requests are scoped to a store. `JwtAuthenticationFilter` extracts `storeId` from the JWT and stores it in `TenantContext` (a `ThreadLocal`) for the request, clearing it afterward. When adding tenant-scoped queries, read the current store via `TenantContext.getStoreId()`. Isolation is manual per service (no Hibernate filter/RLS), and billing/storage operations **require** a store — never fall back to store 1. The `stores` module handles store onboarding/documents; `legal` and `support` are platform back-office modules. `categories` is the one business catalog that is deliberately **global** (no `store_id`) — shared across every store, writable only by `SUPER_ADMIN`.

### Billing adapter strategy
Electronic invoicing is decoupled behind `ElectronicInvoicingProvider` in `billing/internal/adapters/`. Implementation is selected by the `billing.provider` property (`factus` → `FactusBillingAdapter`, `mock` → `MockBillingAdapter`, only registered when the property is `mock`). An unknown value fails fast; there is no silent fallback to `mock`. Add new providers as adapters selected by this property; don't hardcode a provider in services. Sales never call billing: `sales` publishes `SaleCompletedEvent` and `billing` emits asynchronously after commit.

### Billing credentials are per store, never global
Factus credentials live **encrypted in `store_billing_config`** (one row per store, one credential set per environment), loaded only by `SUPER_ADMIN` through `/api/v1/backoffice/stores/{storeId}/billing/credentials`. There are **no** `FACTUS_CLIENT_ID`/`SECRET`/`USERNAME`/`PASSWORD` env vars anymore and **no fallback** to a platform account: `FactusCredentialsResolver` throws `BillingNotEnabledException` when a store isn't `ACTIVE` or lacks credentials, so a store can never consume another store's DIAN document quota. `BILLING_ENCRYPTION_KEY` (32-byte Base64, AES-256-GCM) is the only billing secret outside the DB. Numbering-range writes are guarded (range in use protected, one active range per document type, consecutive never goes backwards) and audited in `numbering_range_audit`. The active range per environment is chosen by the store's `ADMINISTRATOR` (`POST /api/v1/billing/numbering-ranges/{id}/set-default`), not by `SUPER_ADMIN`. See `backend/docs/modules/billing/spec.md`.

### Database & migrations
- Schema changes go through **Flyway** SQL migrations in `backend/src/main/resources/db/migration/` (`V1__Initial_Schema.sql` … `V26__User_Deletion_Audit.sql`; the next free version is `V27`). Add the next `V#__Description.sql` — never edit an applied migration.
- Flyway owns the schema in **both** `dev` and `prod`: `ddl-auto=validate` everywhere, so JPA never generates tables and any drift between entities and the migrated schema fails startup. Only the `test` profile lets Hibernate build the schema (`create-drop`, Flyway disabled); the `V*MigrationSqlTest` classes run Flyway by hand against their own in-memory H2.
- Migrations that touch data must also be safe on H2 (the `dev` default): H2 2.4.x has a known bug validating `CHECK … IN (…)` constraints from a connection other than the one that created them.

## Frontend Architecture

- `src/pages/` — one component per route: local pages (`/`, `/pos`, `/sales`, `/cash`, `/products`, `/inventory` with suppliers/returns tabs, `/customers`, `/invoicing`, `/documents`, `/settings`), `pages/backoffice/` (`SUPER_ADMIN`, under `/backoffice`) and `pages/onboarding/` (`/onboarding/store`); public `/login`, `/register`, `/verify-email`.
- `src/services/http.js` — single HTTP client, plus one `*Api.js` per module on top of it (`services/backoffice/` for back-office calls). Sends cookies (`credentials: include`) and, right after login, a `Bearer` header from an **in-memory** token. Tokens are never written to `localStorage` (the `HttpOnly` cookies `jwt_token`/`refresh_token` carry the session); only the non-sensitive profile (`pos_user`) is persisted. Refreshes transparently on 401; when it cannot, it fires `auth:session-expired` and `AuthContext` closes the UI session. Route all backend calls through these.
- `src/context/` — `AuthContext` (session), `ThemeContext` (light/dark, persisted in `localStorage`; Tailwind class-based dark mode) and `ModalContext`.
- **Styling**: use the semantic Tailwind tokens defined in `src/index.css` (`bg-card`, `bg-page`, `border-line`, `text-ink`, `text-ink-soft`, `text-ink-muted`, `bg-brand`, `bg-solid`) instead of hardcoded hex + `dark:` pairs — they follow the theme automatically. Build new screens with the shared components in `src/components/ui/` (`Button`, `Badge`, `Card`, `Modal`, `Input`/`Select`, `EmptyState`, `PageHeader`). Visual spec: `DESIGN.md`.
- **Routing**: pages are lazy-loaded in `App.jsx`; role-restricted routes use `<RequireRole roles={[...]}>` and must mirror the backend `@PreAuthorize` rules (also hide the sidebar link). Public pages (`/legal/:slug`, `/support`) live in `src/pages/public/`.
- Barcodes are rendered client-side with JsBarcode (Code 128).

## Environment

Backend config is env-var driven (see README "Variables de Entorno"): `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_*`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `JWT_COOKIE_SECURE`, `APP_FRONTEND_URL` (also the CORS origin), `EMAIL_PROVIDER`/`SMTP_*`, `SEED_SUPERADMIN_PASSWORD`, `BILLING_PROVIDER`, `BILLING_ENCRYPTION_KEY`, `FACTUS_URL_SANDBOX`/`FACTUS_URL_PRODUCTION` (public URLs only — credentials are per store, in the DB), `R2_*`. See `backend/docs/_deployment.md` for the full list. Only the `dev` profile seeds the `admin` user and demo data. The frontend needs no env vars.

Roles: `SUPER_ADMIN` (platform/back-office level, no `store_id` — SaaS metrics, store lifecycle/KYC, legal docs, support, and the only role allowed to write the global `categories` catalog), `ADMINISTRATOR` (full control of one store), `SUPERVISOR` (purchases, inventory, customers, invoice retries), `SELLER` (cash sales, catalog). All but `SUPER_ADMIN` carry a `store_id` and are scoped to their own tenant.
