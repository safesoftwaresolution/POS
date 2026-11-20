# Feature: store-businesses (varias sucursales por dueño)
Objetivo: un mismo dueño con varias sucursales, con modo de NIT y de catálogo configurables, y estadísticas consolidadas. Rama: main (sin rama de feature todavía). Entrega: ask-on-risk. Runner: backend `.\mvnw.cmd test`, frontend `npm test` + `npm run lint`. Diseño completo: `backend/docs/modules/businesses/spec.md`.

## Specs
- S1: "que un mismo dueño tenga varias sucursales".
- S2: "con un nit, o mejor dicho que se pueda configurar eso, al momento de activarles lo de sucursales se pueda configurar si con el mismo nit o con diferentes nit".
- S3: "que eso tambien lo podamos configurar" (catálogo compartido o independiente). Fase 1 solo `INDEPENDENT`; `SHARED` queda para la fase 2.
- S4: "poder que el admin padre entre comillas pueda mirar las estadisticas en general de sus sucursales".
- S5: "Siempre pasa por revisión": una sucursal nueva siempre la aprueba el `SUPER_ADMIN`.

## Tasks
- [x] U1 (S1,S2,S3,S5) `V45`, módulo `businesses`, alta/consulta/edición de negocios y alta o baja de sucursales desde el back office, con auditoría — inline — sin commit; 1153 backend tests green; el arranque en `dev` sobre H2 en memoria aplicó `V45` y pasó `ddl-auto=validate`; los endpoints se probaron con HTTP real en U2
- [x] U2 (S1) Cambio de sucursal (`POST /auth/switch-store`) + `refresh_tokens.store_id` (`V46`) + revalidación en `JwtAuthenticationFilter` + `GET /business/me` — inline — sin commit; 1173 backend tests green; flujo verificado con 24 peticiones HTTP contra la app en dev sobre H2 en memoria (login, alta de negocio, cambio, aislamiento de catálogo, refresh, acceso denegado, baja de sucursal con token vigente); riesgo: auth/permisos, falta verificación independiente
- [x] U3 (S1,S5) Alta de sucursal por el dueño (`POST /business/branches`, tope con bloqueo, NIT heredado en modo compartido), `DELETE /backoffice/businesses/{id}` (disolver), protección de la sucursal principal y del dueño al eliminar — inline — sin commit; 1183 backend tests green; 17 peticiones HTTP reales contra la app en dev sobre H2 en memoria; riesgo: ninguno de los ítems altos (no toca auth ni formatos persistidos)
- [x] U4 (S2) Modo `SHARED_TAX_ID` en `billing`: fuente fijada en el documento (`billing_store_id`, `V47`), consecutivo bloqueando los rangos de la principal, reintentos, `PATCH …/billing-mode` con bloqueo por facturas validadas, guarda al eliminar la principal — inline — sin commit; 1204 backend tests green, incluidos 24 hilos entre dos sucursales sobre un mismo rango; 10 peticiones HTTP del cambio de modo; revisión independiente: 1 defecto corregido (guardas del rango miraban solo las asignaciones del local del rango), recomprobado con la suite completa pero sin segunda revisión; NO probado contra Alegra real; riesgo: items 3 y 4 (contrato de billing y concurrencia)
- [x] U5 (S4) Estadísticas consolidadas: `GET /business/stats/overview` y `/stats/branches` (`BusinessStatsDao` con SQL nativo, `BusinessStatsService`) — inline — sin commit; 1213 backend tests green; 20 peticiones HTTP reales con una venta real; riesgo: ninguno de los ítems altos (solo lectura, los locales salen del negocio del usuario autenticado)
- [x] U6 (S1,S2,S4) Frontend: `BusinessContext`, selector de sucursal (cabecera y pantalla de revisión), `/business` con estadísticas y alta de sucursal, `/backoffice/businesses` con alta, detalle, modo de facturación y disolución; y en backend `/stores/me` pasa a la sucursal activa — inline — sin commit; 137 frontend tests, lint 0, build ok, 1216 backend tests; verificado en el navegador contra la app real (login, selector, cambio de sucursal y recarga, alta de sucursal, pantalla de revisión de una sucursal pendiente y vuelta, back office: listado, detalle y cambio de modo; sin desbordamiento a 375 px); NO probado el alta de negocio desde su modal ni la disolución desde la UI

## Log
- Hallazgo U6: `RequireActiveStore` leía `/stores/me` (local de origen) y la API bloqueaba la sucursal activa; se corrigió en `stores` para que "mi local" sea la sucursal activa. El comparativo no recargaba al crear una sucursal (dependencia de `useLoad`); corregido.
- L1 (2026-11-20): "necesito que revisemos lo de temas de sucursales como esta actualmente"
- L2 (2026-11-20): "Quiero que un mismo dueño tenga varias sucursales"
- L3 (2026-11-20): "con un nit, o mejor dicho que se pueda configurar eso, al momento de activarles lo de sucursales se pueda configurar si con el mismo nit o con diferentes nit"
- L4 (2026-11-20): "que eso tambien lo podamos configurar, es que actualmente no podemos saber que es lo que quieren" y "otra cosa tambien es poder que el admin padre entre comillas pueda mirar las estadisticas en general de sus sucursales"
- L5 (2026-11-20): "Siempre pasa por revisión, empecemos con la U1"
- Hallazgo U2: el `storeId` del JWT no basta para una sucursal distinta del local del usuario (lo pueden sacar del negocio con el token vigente); el filtro lo revalida en cada petición. `TenantScopedRepositoriesTest` obligó a llamar `activeStoreId` al campo de `RefreshToken`.
- U3 reemplazó el `409` genérico de las FKs por `400` con mensaje claro, y agregó disolver el negocio porque sin eso la principal y el dueño no se podían liberar nunca.
- Pendiente tras U4 (advertencias de la revisión): guardia que impida a una sucursal con NIT compartido gestionar sus propios rangos o su activación; la pantalla de facturación de la sucursal debe mostrar el estado de la principal; el camino de notas sin contexto usa la empresa propia de la sucursal.
- Abierto para U4 (resuelto en parte): al disolver un negocio en modo `SHARED_TAX_ID`, las sucursales no tienen empresa de facturación propia. La carrera de `max_branches` en `addBranch` del back office (solo `SUPER_ADMIN`) no está bloqueada; sí la del dueño.
- El `BILLING_PROVIDER=factus` del entorno local impide arrancar la app (Factus se eliminó en `V41`); hay que ponerlo en `alegra` o `mock`.
