# FEAT-027 — Envío por correo y descarga de XML con Alegra

> Parte del plan "Alegra como proveedor de facturación". Solo backend.

## Objetivo
Que `POST …/billing/invoices/sales/{saleId}/send-email` y `GET …/xml` funcionen con Alegra para facturas (y, donde aplique, notas).

## Depende de
FEAT-025 (`providerDocumentId`, `AlegraClientHelper`). Independiente de FEAT-026 y FEAT-028.

## Datos de la API de Alegra
- `POST /send-email` (Bearer): `{ documentType: "01"|"91"|"92", documentId: "<id de Alegra>", email?: { to, replyTo } }`; `replyTo` es obligatorio si se envía `email`; sin `email` toma el del documento. 200 → `message` + `data{company, receiver, document{type,number,code,date,link,value}}`. Solo para documentos aceptados por la DIAN.
- XML: `GET /invoices/{id}` → `files.xml` (link temporal 60 min, puede venir `null` → volver a consultar) → `GET` del link devuelve los bytes.

## Context Pack (R8)
- Docs (2): `backend/docs/modules/billing/spec.md`, `backend/docs/_conventions.md`.
- Código (≤5): `adapters/InvoiceProvider.java`, `services/InvoiceServiceImpl.java` (solo `sendInvoiceEmail`/`downloadInvoiceXML`), `alegra/AlegraInvoiceClient.java`, `alegra/AlegraClientHelper.java`, `billing/internal/controllers/BillingInvoiceController.java`.

## Qué se construye
1. `AlegraInvoiceClient.sendInvoiceEmail(legalNumber, email)`: resolver la factura por `(storeId, legalNumber)` → `providerDocumentId`; `POST /send-email` con `replyTo` = email del local (`StoreFacade`, sin tocar `internal/`). Devuelve `true/false` como el puerto; errores del proveedor registrados sin filtrar el token.
2. `AlegraInvoiceClient.downloadInvoiceXML(legalNumber)`: `GET /invoices/{id}` → si `files.xml` es `null`, un reintento corto; descargar el link con `RestClient` (sin enviar el Bearer a un host que no sea el de Alegra); devolver `null`/excepción clara si no hay XML.
3. Notas (si FEAT-026 ya existe): equivalentes `downloadCreditNoteXML`/`downloadDebitNoteXML` y envío con `documentType` `91`/`92`.
4. Los métodos del puerto de factura no reciben `storeId` (usan `TenantContext`): documentar y no cambiar la firma en esta tarea.
5. El `GET …/pdf` sigue devolviendo 501 hasta FEAT-028.

## Criterios de aceptación
- [ ] `MockRestServiceServer`: envío OK, 404 (documento inexistente), 400 por `replyTo`, `files.xml` null con reintento, descarga OK.
- [ ] Una factura no `VALIDATED` rechaza el envío (guarda `assertCanSendEmail` existente).
- [ ] El token Bearer no se envía a los links de descarga de terceros.
- [ ] E2E sandbox: llega el correo y se descarga un XML válido.
- [ ] `.\mvnw.cmd test` en verde.

## Fuera de alcance
PDF; el correo propio de la app (módulo `notifications`); `frontend/`.

## Riesgos
Si FEAT-020 muestra que `/send-email` no adjunta PDF, documentarlo en FEAT-028 (el PDF generado podría adjuntarse por el correo propio de la app, decisión aparte).
