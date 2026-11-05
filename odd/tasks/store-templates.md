# Feature: store-templates (plantillas por tipo de local)
Objetivo: adaptar el POS al tipo de local (como Cupo para barberías) con plantillas automáticas por categoría. Rama: feature/store-templates (desde develop). Entrega: ask-on-risk. Runner: backend `.\mvnw.cmd test`, frontend `npm test` + `npm run lint`.

## Specs
- S1: "La asignación de plantillas sea totalmente automática": la plantilla de un local se deriva de su categoría de local; sin categoría, plantilla por defecto RETAIL.
- S2: "desde el back office podamos editar qué tipo de plantilla tiene": el SUPER_ADMIN edita la plantilla de cada categoría de local (RETAIL, FOOD, APPOINTMENTS).
- S3: "si el local es barbería, colóquería, boutique o salón de uñas y pies": existen esas categorías de local y usan la plantilla APPOINTMENTS por defecto (assumption: "colóquería" = peluquería).
- S4: "el sistema se adapte a las funcionalidades específicas": el frontend muestra/oculta módulos según la plantilla (sidebar y rutas), sin lógica de negocio en el frontend.
- S5: "un apartado exclusivo con calendario, agendamiento de citas y demás funcionalidades": módulo de citas solo para APPOINTMENTS (FASE 2, alcance por definir).
- S7 (FASE 2, versión mínima): "calendario por día y semana, crear, cancelar y mover citas, con cliente, servicio y hora." (alcance elegido por el usuario: "si hagamos la minima").
- S8 (assumption): solo los locales con plantilla APPOINTMENTS usan las citas; el backend responde 403 a los demás y el frontend oculta el apartado.
- S9 (assumption): la cita dura lo que el servicio (`durationMinutes`; 30 min si el servicio no lo define); no hay profesionales ni control de solapes en esta versión.
- S10 (assumption): estados SCHEDULED y CANCELLED; una cita cancelada no se puede mover.
- S6: Las categorías existentes conservan su comportamiento (migración con default RETAIL; FOOD para Restaurante / Cafetería).

## Tasks
- [x] T1 (S1,S2,S3,S6) Backend: V33 + enum StoreTemplate + DTO/command + StoreDto.template + seeder + tests — inline — commit f4b4175; 837 backend tests green
- [x] T2 (S2) Backoffice UI: editar plantilla en Categorías de Locales — inline — commit ef2e699; lint, build, 41 frontend tests green
- [x] T3 (S1,S4) Frontend: hook useStoreTemplate + gating sidebar/rutas + tests — inline — commits 0132a6f (endpoint, 839 backend tests) + ef2e699; no visible effect yet: first consumer is the appointments page (T5)
- [x] T4 (S5,S7-S10) Backend módulo appointments: V34, API, docs — inline — backend commit 0a4c9b6, 851 tests green; docs in the docs commit
- [x] T5 (S5,S7-S10) Frontend calendario día/semana + ruta y sidebar solo con features.appointments — inline — commit 37a71b7; 49 frontend tests, lint 0, build ok
- [x] T6 Docs: stores/spec.md, _roadmap.md, _database-schema.md

## Log
- L1 (2026-11-04): "Vamos a hacer un GitFlow y una feature de adaptación de plantillas. Por ejemplo, en nuestro proyecto Cupo, que es un sistema pensado para barberías o cluquerías, se puede hacer el agendamiento de citas y demás. Adaptaremos esas plantillas al POS según el tipo de local. Tenemos categorías de locales y tiendas. Queremos diferenciar las plantillas para cada categoría, de modo que el sistema se adapte a las funcionalidades específicas. Por ejemplo, si el local es barbería, colóquería, boutique o salón de uñas y pies, el sistema tendrá un apartado exclusivo con calendario, agendamiento de citas y demás funcionalidades disponibles."
- L2 (2026-11-04): "Queremos que la asignación de plantillas sea totalmente automática. Lo único es que desde el back office podamos editar qué tipo de plantilla tiene: local, comida, etc."
- Nota: onboarding registra el local con storeCategoryId=null (StoreOnboardingBento.jsx:23); el SUPER_ADMIN asigna la categoría luego. develop estaba 130 commits detrás de main; sincronizada con merge -X theirs.
- L3 (2026-11-05): "si hagamos la minima" (respuesta a: mínima = calendario por día y semana, crear, cancelar y mover citas, con cliente, servicio y hora; la completa añadía profesionales y cobro desde la venta).
