# FEAT-020 — Spike en sandbox de Alegra (sin código)

> Parte del plan "Alegra como proveedor de facturación (Factus queda como alterno)". Solo backend.
> **Bloquea:** FEAT-025 (facturas), FEAT-026 (notas), FEAT-028 (PDF). **No bloquea** FEAT-021..024.

## Objetivo
Confirmar contra el sandbox real de Alegra los supuestos que la documentación no deja claros, y dejarlos escritos para que las demás tareas no adivinen.

## Depende de
Nada de código. Requiere del usuario: **token Bearer del sandbox** y el **`company.id`** de una empresa asociada ya habilitada (o NIT de pruebas `900559088`, DV `2`, con prefijo único).

## Context Pack
- Docs: este archivo y `backend/docs/modules/billing/spec.md` (solo sección de riesgos).
- Código: ninguno.
- Referencia externa: `https://e-provider-docs.alegra.com/llms.txt` (índice). Base sandbox `https://sandbox-api.alegra.com/e-provider/col/v1`, producción `https://api.alegra.com/e-provider/col/v1`.

## Pasos
1. `GET /companies/{id}` con el token: guardar la respuesta (campos `identification`, `dv`, `name`, `regimeCode`, `organizationType`, `address{city,department}`, `email`, `governmentStatus`).
2. `POST /invoices` con el ejemplo `invoice` de la documentación (documentType `01`, `number` dentro del rango de pruebas, `resolution` completa, `company.id`, 1 ítem con IVA 19%). Repetirlo **enviando solo `company: {id}`** y luego con el bloque completo: ¿cuál acepta?
3. Repetir el `POST` con el header `Idempotency-Key` (UUIDv4): mismo cuerpo → ¿`Idempotent-Replayed: true`? Cuerpo distinto + misma llave → ¿422?
4. Con la respuesta, descargar `files.attachedDocument` y `files.zip` (ojo: caducan en 60 min). Anotar `Content-Type`, tamaño y **si contienen un PDF** (si es ZIP, listar su contenido). `GET /invoices/{id}` y comparar.
5. `POST /send-email` (`documentType "01"`, `documentId`, `email{to,replyTo}`): ¿llega el correo?, ¿trae PDF adjunto?
6. `POST /credit-notes` y `POST /debit-notes` sobre esa factura (`associatedDocuments` con CUFE, `conceptCode "1"`/`"2"`, `prefix` propio, `number` propio, sin `resolution`): ¿se aceptan sin resolución en sandbox? ¿qué prefijo/rango exige?
7. Provocar un rechazo (dato inválido) y un `400`: guardar el cuerpo exacto del error (array `{code,message}`) y un `status`/`legalStatus` final.
8. Anotar cómo se obtiene/rota el token y si es distinto entre sandbox y producción.

## Entregable
`.ai/tasks/FEAT-020-resultados.md` con: respuestas JSON reales (sin token ni datos sensibles), y una tabla **pregunta → respuesta → tarea afectada**:

| Pregunta | Afecta a |
|---|---|
| ¿Basta `company.id` o hay que enviar el bloque `company` completo? | FEAT-024, 025 |
| ¿`POST /invoices` acepta `Idempotency-Key`? | FEAT-025 |
| ¿`attachedDocument`/`zip`/correo traen PDF? | FEAT-028 |
| ¿NC/ND exigen resolución o rango propio? | FEAT-023, 026 |
| ¿Token distinto por entorno? ¿cómo se obtiene? | FEAT-024, 029 |
| Formato exacto de errores 400/422/409 | FEAT-025 |

## Criterios de aceptación
- [ ] El documento de resultados existe y responde las 6 preguntas de la tabla.
- [ ] Ningún token ni dato real quedó escrito en el repo.
- [ ] Quedan resueltos o reformulados estos riesgos: (1) bloque `company` completo vs solo `id`; (3) cómo se obtiene el token y si cambia por entorno; (4) numeración de notas sin resolución; (6) `Idempotency-Key` en `POST /invoices`.

## Fuera de alcance
Escribir código; tocar `frontend/`.

## Riesgos
Sin token no se puede ejecutar: las tareas FEAT-021..024 pueden avanzar probadas solo con `MockRestServiceServer`.
