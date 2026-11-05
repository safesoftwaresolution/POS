# Service categories + appointment overlaps
Objective: block overlapping appointments; separate service categories from product categories, linked to store categories.
Branch: feature/service-categories-and-overlaps (from develop). Strategy: ask-on-risk. Runner: backend `.\mvnw.cmd test`, frontend `npm test` + `npm run lint`.

## Specs
- S1 "si un servicio está programado de 9 a 10, no se puede agendar otra cita en ese intervalo." -> one agenda per store; two SCHEDULED appointments cannot overlap (touching ends are allowed); CANCELLED ignored; applies on create and reschedule; 409 on conflict.
- S2 "separar las categorías de productos de las de servicios" -> new global `service_categories` catalog (SUPER_ADMIN writes); services reference it, not `categories`; product category delete is no longer blocked by services.
- S3 "permitir categorías específicas por plantilla (ej. maquillaje, boutique, peluquería, corte)" + "cada plantilla puede tener sus propias categorías según el tipo de local" -> user chose option 1: each service category is linked to one or more store categories; a store only sees/uses service categories linked to its store category.
- S4 Existing services keep working: migration copies the categories they use into `service_categories` (linked to the store category of each store that uses them) and remaps `services.category_id`.

## Tasks
- [x] T1 S1 inline: overlap validation in AppointmentServiceImpl + repository query + tests (e4b6909)
- [x] T2 S2-S4 inline: backend service categories (V37, module `services`, facade, controllers, tests) (3070547)
- [x] T3 S2-S3 inline: frontend (services form select, backoffice management, conflict message)
- [x] T4 docs (specs, schema, roadmap, CLAUDE.md migration number)

## Log
L1 (user, 2026-11-05):
- Validaciones de citas: si un servicio está programado de 9 a 10, no se puede agendar otra cita en ese intervalo.
- Categorías de servicios: separar las categorías de productos de las de servicios; permitir categorías específicas por plantilla (ej. maquillaje, boutique, peluquería, corte).
- Plantillas de citas: cada plantilla puede tener sus propias categorías según el tipo de local (mini market, tienda, etc.).
L2 (user): "La opción 1" (service categories linked to store categories).

L3 Verified: backend 885+ tests green; frontend 79 tests, lint 0 warnings, build ok; E2E on dev profile (H2 in-memory): store without category sees only "Otros servicios", service in a non-offered category -> 400, appointments 9:00 ok / 9:30 -> 409 / 10:00 (touching) ok.
