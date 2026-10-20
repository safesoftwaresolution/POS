# FEAT-029 — Cierre: configuración por defecto, documentación y verificación E2E

> Parte del plan "Alegra como proveedor de facturación". Solo backend.

## Objetivo
Dejar Alegra como proveedor por defecto, con configuración, documentación y pruebas finales coherentes; Factus sigue siendo seleccionable.

## Depende de
FEAT-021..028 (las que se hayan hecho). Es la última tarea.

## Context Pack (R8)
- Docs a actualizar (4 a la vez como máximo; hacerlo por tandas): `backend/docs/modules/billing/spec.md`, `backend/docs/_security.md`, `backend/docs/_deployment.md`, `backend/docs/_database-schema.md`; luego `backend/docs/_roadmap.md`, `backend/docs/modules/settings/spec.md` (nota del sync de logo), `CLAUDE.md`, `README.md`.
- Config: `backend/src/main/resources/application.properties`, `backend/.env.example`, `backend/docker-compose.prod.yml`.
- Arnés: `.ai/features.json`, `.ai/progress/history.md`.

## Qué se hace
1. `application.properties`: `billing.provider=${BILLING_PROVIDER:alegra}`. `.env.example` y `docker-compose.prod.yml`: `BILLING_PROVIDER`, `ALEGRA_URL_SANDBOX|PRODUCTION`, `ALEGRA_API_TOKEN_SANDBOX|PRODUCTION`; los `FACTUS_*` quedan marcados "solo si `BILLING_PROVIDER=factus`". **No** escribir valores reales de tokens en ningún archivo versionado; `backend/.env` está en `.gitignore`.
2. Documentación (fuente de verdad, en español):
   - `billing/spec.md`: proveedores soportados, flujo Alegra (empresa → asignación local → emisión → estados `status`/`legalStatus` → conciliación), numeración local (tablas, asignador, reglas), tabla de mapeo de estados, endpoints nuevos (`company-id`), PDF, riesgos conocidos.
   - `_security.md`: el token de Alegra es una variable de entorno de plataforma; el aislamiento entre locales es por `company.id` + `TenantContext` (nunca por token); no loguear tokens. Ajustar la frase "sin cuenta global" aclarando que aplica a Factus.
   - `_deployment.md` / `README.md`: variables nuevas y cómo obtener el token y el `company.id`.
   - `_database-schema.md`: tablas y columnas de FEAT-022.
   - `_roadmap.md`: estado del módulo billing (qué queda: conciliación programada/webhook, renombres neutros, pantallas), y la nota de que FEAT-018 reordena la emisión.
   - `CLAUDE.md`: secciones "Billing adapter strategy" y "Billing credentials are per store" — Alegra por defecto, cómo seleccionarlo, dónde viven las credenciales y la numeración. Detectar y avisar (no corregir en silencio) cualquier drift entre `features.json`/`init.ps1` y los módulos reales.
3. `.ai/features.json`: marcar FEAT-020..029 según su estado real, con `completed_at`; `.ai/progress/history.md`: entrada por cada tarea cerrada (solo añadir).
4. Verificación final (ver abajo) y registro del resultado.

## Verificación E2E (checklist)
- [ ] `.\mvnw.cmd test` completo en verde (Factus y mock intactos).
- [ ] `BILLING_PROVIDER=mock|factus|alegra` arrancan; `siigo` no arranca.
- [ ] Sandbox de Alegra: `company-id` → `test-connection` → rango de prueba → venta con `sendToFactus=true` → `VALIDATED` con CUFE y número consecutivo → reintento sin duplicado → NC y ND → XML → correo → PDF.
- [ ] `git grep -n "ALEGRA_API_TOKEN"` no encuentra valores reales; `backend/.env` no está en el diff.
- [ ] Documentación sin contradicciones con el código (revisar `spec.md` contra las clases reales).

## Fuera de alcance
Renombrar `factus_number`/`factusNumber`/`sendToFactus`; job/webhook de conciliación; `frontend/`; push o PR (solo tras aprobación explícita del usuario).
