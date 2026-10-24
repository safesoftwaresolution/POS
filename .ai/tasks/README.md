# Integración con Alegra — índice y orden de ejecución

Decisiones (2026-10-20): solo backend; Factus queda como alterno; token de plataforma + `company.id` por local; **FEAT-018 se hace antes de FEAT-025**.

## Grafo de dependencias

```
FEAT-020 spike ───────────────────────────────┐
FEAT-021 puertos ──────────────┐              │
FEAT-022 migración ──┬─ FEAT-023 asignador ─ FEAT-024 infraestructura ─┐
                     └─ FEAT-018 emisión fuera de la tx + reintento ───┤
                                                                       ▼
                                            FEAT-025 facturas (necesita 018, 020, 023, 024)
                                                    │
                           ┌────────────────────────┼────────────────────────┐
                      FEAT-026 notas          FEAT-027 correo/XML       FEAT-028 PDF (+020)
                           └────────────────────────┼────────────────────────┘
                                                    ▼
                                           FEAT-029 cierre
```

## Orden por olas

| Ola | Tareas | Se pueden hacer a la vez | Por qué en este orden |
|---|---|---|---|
| 0 | **FEAT-020** (usuario, sin código) · **FEAT-021** | Sí | No comparten archivos. FEAT-020 es lo único que necesita el token. |
| 1 | **FEAT-022** | — (sola) | Define el esquema y toca `ElectronicInvoice`; su migración debe tomar el número **después** de FEAT-019 si esta ya se hizo. |
| 2 | **FEAT-018** · **FEAT-023 → FEAT-024** | Sí (dos carriles) | FEAT-018 toca `InvoiceServiceImpl`, `ElectronicInvoiceEventListener` y `ElectronicInvoice`; el carril Alegra solo crea archivos en `adapters/alegra/`. Sin solape. |
| 3 | **FEAT-025** | — (sola) | Necesita el flujo ya fuera de la transacción (018), los resultados del spike (020), el asignador (023) y el acceso/empresa (024). |
| 4 | **FEAT-026** · **FEAT-027** · **FEAT-028** | Sí | Cada una añade su propio cliente/clase sobre FEAT-025. FEAT-028 espera además el resultado de FEAT-020 (ruta A o B). |
| 5 | **FEAT-029** | — | Cierre: default, docs, verificación E2E. |
| 6 (opcional) | **FEAT-030** | — | Catálogo DIAN y rotación de claves; mejor después de FEAT-025 y sin prisa. |

## Reglas de coordinación

- **Migraciones: una a la vez.** FEAT-019, FEAT-018 y FEAT-022 necesitan una cada una (V20 ya la usa main; hoy la siguiente libre es V25). El número se decide al implementar, mirando `backend/src/main/resources/db/migration/`; nunca se numeran en paralelo. Orden recomendado de merge: FEAT-019 (si se hace) → FEAT-022 → FEAT-018.
- **Un escritor por archivo.** Si dos tareas de la misma ola se ejecutan a la vez en la misma carpeta, cada una usa su propia rama/worktree.
- **Cada tarea termina** con `.\mvnw.cmd test` en verde, una entrada en `.ai/progress/history.md` y un plan de commits aprobado por el usuario (Committer). Nada de push sin pedirlo.

## Alcance de FEAT-018 y FEAT-030 (confirmado el 2026-10-20)

- **FEAT-018** (brief propio, bloquea FEAT-025): emisión en tres pasos con lease (reservar `PENDING` en transacción corta → llamar al proveedor sin transacción ni lock → registrar el resultado) para facturas y notas, y job de reintento con espera creciente para facturas (primer `@EnableScheduling` del proyecto; también es el lugar para conciliar `WAITING_RESPONSE` de Alegra).
- **FEAT-030** (brief propio, **no bloquea**): catálogo central de códigos DIAN y rotación de claves de cifrado. Conviene hacerla **después de FEAT-025** para que el catálogo cubra también los mappers de Alegra; la rotación solo protege los secretos de Factus (el token de Alegra va en variable de entorno).

Briefs: `FEAT-018-emision-tres-pasos-reintento.md`, `FEAT-030-codigos-dian-y-rotacion-claves.md`.
