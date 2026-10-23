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
.\mvnw.cmd spring-boot:run                    # Run API on :8080 (default 'dev' profile = in-memory H2, no Docker needed)
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
Each business module (`auth`, `users`, `categories`, `products`, `inventory`, `customers`, `suppliers`, `purchases`, `sales`, `billing`, `reports`, `settings`, `stores`, `legal`, `support`, `notifications`, `storage`) is a vertical slice under `com.sciencebot.pos`. The last two (`notifications`, `storage`) have a spec but still no entry in `.ai/init.ps1`/`init.sh`'s module list (nor does `backoffice`) — known harness gap, see `backend/docs/_roadmap.md`.

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
Inventory stock deduction uses **pessimistic locking** to prevent race conditions on concurrent sales. Preserve this when touching inventory/sales flows.

### Multi-tenancy (store scoping)
Requests are scoped to a store. `JwtAuthenticationFilter` extracts `storeId` from the JWT and stores it in `TenantContext` (a `ThreadLocal`) for the request, clearing it afterward. When adding tenant-scoped queries, read the current store via `TenantContext.getStoreId()`. The `stores` module handles store onboarding/documents; `legal` and `support` are platform back-office modules. `categories` is the one business catalog that is deliberately **global** (no `store_id`) — shared across every store, writable only by `SUPER_ADMIN`.

### Billing adapter strategy
Electronic invoicing is decoupled behind `ElectronicInvoicingProvider` in `billing/internal/adapters/`. Implementation is selected by the `billing.provider` property (`factus` → real DIAN sandbox via `FactusBillingAdapter`, `mock` → `MockBillingAdapter`). Add new providers as adapters selected by this property; don't hardcode a provider in services.

### Billing credentials are per store, never global
Factus credentials live **encrypted in `store_billing_config`** (one row per store, one credential set per environment), loaded only by `SUPER_ADMIN` through `/api/v1/backoffice/stores/{storeId}/billing/credentials`. There are **no** `FACTUS_CLIENT_ID`/`SECRET`/`USERNAME`/`PASSWORD` env vars anymore and **no fallback** to a platform account: `FactusCredentialsResolver` throws `BillingNotEnabledException` when a store isn't `ACTIVE` or lacks credentials, so a store can never consume another store's DIAN document quota. `BILLING_ENCRYPTION_KEY` (32-byte Base64, AES-256-GCM) is the only billing secret outside the DB. Numbering-range writes are guarded (range in use protected, one active range per document type, consecutive never goes backwards) and audited in `numbering_range_audit`. The active range per environment is chosen by the store's `ADMINISTRATOR` (`POST /api/v1/billing/numbering-ranges/{id}/set-default`), not by `SUPER_ADMIN`. See `backend/docs/modules/billing/spec.md`.

### Database & migrations
- Schema changes go through **Flyway** SQL migrations in `backend/src/main/resources/db/migration/` (`V1__Initial_Schema.sql` … `V19__Password_Reset_Tokens.sql`). Add the next `V#__Description.sql` — never edit an applied migration.
- Flyway owns the schema in **both** `dev` and `prod`: `ddl-auto=validate` everywhere, so JPA never generates tables and any drift between entities and the migrated schema fails startup. Only the `test` profile lets Hibernate build the schema (`create-drop`, Flyway disabled); the `V*MigrationSqlTest` classes run Flyway by hand against their own in-memory H2.
- Migrations that touch data must also be safe on H2 (the `dev` default): H2 2.4.x has a known bug validating `CHECK … IN (…)` constraints from a connection other than the one that created them.

## Frontend Architecture

- `src/pages/` — one component per route: local pages (`/`, `/pos`, `/products`, `/inventory`, `/customers`, `/invoicing`, `/reports`, `/documents`, `/settings`), `pages/backoffice/` (`SUPER_ADMIN`, under `/backoffice`) and `pages/onboarding/`; public `/login`, `/register`, `/verify-email`.
- `src/services/http.js` — shared HTTP client (JWT + refresh token in `localStorage`, transparent refresh on 401); one `*Api.js` per module on top of it (`services/backoffice/` for back-office calls). Route all backend calls through these.
- `src/context/` — `AuthContext` (session), `ThemeContext` (light/dark, persisted in `localStorage`; Tailwind class-based dark mode) and `ModalContext`.
- **Styling**: use the semantic Tailwind tokens defined in `src/index.css` (`bg-card`, `bg-page`, `border-line`, `text-ink`, `text-ink-soft`, `text-ink-muted`, `bg-brand`, `bg-solid`) instead of hardcoded hex + `dark:` pairs — they follow the theme automatically. Build new screens with the shared components in `src/components/ui/` (`Button`, `Badge`, `Card`, `Modal`, `Input`/`Select`, `EmptyState`, `PageHeader`). Visual spec: `DESIGN.md`.
- **Routing**: pages are lazy-loaded in `App.jsx`; role-restricted routes use `<RequireRole roles={[...]}>` and must mirror the backend `@PreAuthorize` rules (also hide the sidebar link). Public pages (`/legal/:slug`, `/support`) live in `src/pages/public/`.
- Barcodes are rendered client-side with JsBarcode (Code 128).

## Environment

Backend config is env-var driven (see README "Variables de Entorno"): `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_*`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `BILLING_PROVIDER`, `BILLING_ENCRYPTION_KEY`, `FACTUS_URL_SANDBOX`/`FACTUS_URL_PRODUCTION` (public URLs only — credentials are per store, in the DB), `R2_*`. The frontend needs no env vars.

Roles: `SUPER_ADMIN` (platform/back-office level, no `store_id` — SaaS metrics, store lifecycle/KYC, legal docs, support, and the only role allowed to write the global `categories` catalog), `ADMINISTRATOR` (full control of one store), `SUPERVISOR` (purchases, inventory, customers, invoice retries), `SELLER` (cash sales, catalog). All but `SUPER_ADMIN` carry a `store_id` and are scoped to their own tenant.
