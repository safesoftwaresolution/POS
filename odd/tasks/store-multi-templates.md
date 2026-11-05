# Feature: store-multi-templates (varias plantillas por local)
Objetivo: un local puede tener varias plantillas funcionales: las de su categoría más extras asignadas por el SUPER_ADMIN. Rama: feature/store-multi-templates (desde develop). Entrega: ask-on-risk. Runner: backend `.\mvnw.cmd test`, frontend `npm test` + `npm run lint`.

## Specs
- S1: "que un local pueda tener varias plantillas".
- S2: "la opción 2, categoría más extras por local": cada categoría tiene un conjunto de plantillas (heredado por sus locales) y además el SUPER_ADMIN puede sumar plantillas extra a un local concreto desde la tabla de Locales.
- S3: las plantillas efectivas de un local = las de su categoría + sus extras; si no hay ninguna (sin categoría y sin extras) = RETAIL. El tema visual sigue saliendo de la categoría.
- S4: el SUPER_ADMIN edita el conjunto de plantillas de cada categoría (multi-selección, al menos una) en Categorías de Locales.
- S5: un módulo se habilita si CUALQUIERA de las plantillas efectivas lo habilita (citas: si incluye APPOINTMENTS); el backend decide (403 en citas si no la tiene).
- S6 (compat): `template` sigue en las respuestas como la plantilla principal (APPOINTMENTS > FOOD > RETAIL) y se sigue aceptando `template` en los comandos de categoría, porque un frontend en caché (PWA) puede seguir enviándolo.
- S7 (assumption): Boutique pasa a RETAIL + APPOINTMENTS (el ejemplo del usuario: una tienda de ropa con citas); las demás categorías conservan su plantilla única.
- S8: borrar un local elimina también sus extras y sus citas (y sus servicios, de los que dependen las citas).
- S9 (assumption): expand/contract: la columna `store_categories.template` (V33) queda sin uso y sin mapear; se elimina en una migración posterior.

## Tasks
- [x] T1+T2 (S2,S3,S4,S5,S6,S7,S8,S9) Backend: V36, plantillas efectivas, comandos de categoría con lista, endpoint de extras, citas, seeder, purga — inline — commit 154cde1; 867 backend tests green; flujo verificado contra un backend dev en H2 en memoria (incluye la validación de esquema de V36)
- [x] T3 (S4,S5) Frontend: multi-selección en categorías, plantillas extra en la tabla de Locales, provider/features con lista + tests — inline — commit e87ec46; 74 frontend tests, lint 0, build ok; NO probado en navegador
- [x] T4 Docs: stores spec RN-STORE-006, appointments spec, schema, CLAUDE.md

## Log
- L1 (2026-11-05): "que un local pueda tener varias plantillas"
- L2 (2026-11-05): "la opción 2, categoría más extras por local" (respuesta a: opción 1 solo por categoría u opción 2 categoría más extras por local).
- Hallazgo: `StorePurgeDao` no borra `appointments` (mío, V34); tampoco `services`, `credit_accounts`, `credit_movements` ni `audit_log`, que tienen FK a `stores` (anteriores a esta feature). Aquí se corrigen `appointments` y `services` (las citas dependen de ellos); crédito y auditoría quedan fuera y se reportan.
