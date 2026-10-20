# FEAT-025 — Emisión de facturas electrónicas con Alegra

> Parte del plan "Alegra como proveedor de facturación". Solo backend.

## Objetivo
Que una venta con `sendToFactus=true` termine como factura `VALIDATED` (o `REJECTED`/`PENDING` con motivo) emitida a la DIAN a través de Alegra, con número consecutivo asignado por nosotros, sin duplicar documentos ante reintentos.

## Depende de
FEAT-023 (asignador), FEAT-024 (acceso/empresa/adaptador), FEAT-020 (resultados del spike) y **FEAT-018 (decidido: va antes)**. Esta tarea **asume** que `InvoiceServiceImpl.processElectronicInvoice` ya reserva `PENDING` en una transacción corta, llama al proveedor **sin transacción ni lock** y registra el resultado en otra; por eso el asignador (`REQUIRES_NEW`) y la llamada HTTP a Alegra no quedan dentro del lock de la fila de factura. No reimplementar ese flujo aquí: solo enchufar el cliente de Alegra y la conciliación en el gancho que deja FEAT-018.

## Datos de la API de Alegra
`POST /invoices` (Bearer; `Idempotency-Key` UUIDv4 opcional, a verificar en FEAT-020).
Requeridos: `documentType` (`01`), `number` (numérico), `resolution{resolutionNumber, minNumber, maxNumber, startDate, endDate, technicalKey}` (+ `prefix`), `company{id,…}`, `customer{name, identificationType, identificationNumber, dv?, organizationType, regimeCode, taxCode{id}, email, address{address, city, country}}`, `items[{description, price, quantity, unitCode, subtotal, taxAmount, total, taxes[{taxCode, taxAmount, taxPercentage (string "19.00"), taxableAmount}], standardCode{identificationId,id}}]`, `payments[{paymentForm, paymentMethod}]`, `totalAmounts{grossTotal, taxableTotal, taxTotal, discountTotal, chargeTotal, advanceTotal, payableTotal}`.
Respuesta 200: `invoice{id, status (REGISTERED|WAITING_RESPONSE|FAILED|SENT), legalStatus (ACCEPTED|ACCEPTED_WITH_OBSERVATIONS|REJECTED), cufe, prefix, number, fullNumber, governmentResponse{code,message,errorMessages[]}, qrCodeContent, errors[]}` + `files{xml, attachedDocument, zip}` (links de 60 min). `GET /invoices/{id}` devuelve lo mismo.

## Context Pack (R8)
- Docs (3): `backend/docs/modules/billing/spec.md`, `backend/docs/_conventions.md`, `backend/docs/_architecture.md`.
- Código a tocar por etapas (≤5 a la vez): (A) `billing/internal/adapters/dto/InvoiceResult.java` + `adapters/InvoiceProvider.java` + `entities/ElectronicInvoice.java`; (B) `services/InvoiceServiceImpl.java` + `mappers/BillingCanonicalMapper.java` + `adapters/dto/InvoiceItemData.java`; (C) nuevos `alegra/AlegraInvoiceMapper`, `alegra/AlegraInvoiceClient`, `alegra/AlegraBillingAdapter`.
- Patrones a imitar: `FactusInvoiceClient` (mapeo de errores a `InvoiceResult`), `FactusSaleMapper`.

## Qué se construye
1. `InvoiceResult` (y por simetría `CreditNoteResult`/`DebitNoteResult`) gana `providerDocumentId`; actualizar factorías y usos. `ElectronicInvoice.markAsValidated`/`markAsPending` guardan `providerDocumentId` (columna de FEAT-022). `qr_code` guarda el bloque `qrCodeContent` completo; `pdf_url` queda `null`.
2. `AlegraInvoiceMapper` (record de entrada `InvoiceRequest` + `AlegraCompany` + `Allocation` + rango): arma el JSON. Reglas: precio neto por ítem tal como lo deja `BillingCanonicalMapper`; por línea `subtotal = qty×price − descuento`, `taxAmount = round(subtotal×tasa)`, `total = subtotal + taxAmount` (HALF_UP, 2 decimales); `totalAmounts` = sumas de las líneas ya redondeadas; ítem exento o tarifa 0 → `taxes` con 0.00; `regimeCode` = responsabilidades unidas con `;`; consumidor final `222222222222` tipo `13`. **Cubrir con tests la diferencia de centavos frente a `sale.totalAmount`** y fijar la regla (la que decida el usuario tras FEAT-020).
3. `AlegraInvoiceClient.emitInvoice(InvoiceRequest)`: `access = resolver.resolve(storeId)` → `company = companyProvider.get(access)` → `allocation = allocator.allocate(store, env, "01", referenceCode)` con `referenceCode = <invoiceNumber>-<saleId>` → `POST /invoices` con `Idempotency-Key = allocation.idempotencyKey` → mapeo:
   - `legalStatus ACCEPTED|ACCEPTED_WITH_OBSERVATIONS` → `InvoiceResult.success(fullNumber, cufe, qrCodeContent, null)` + `providerDocumentId`; `allocator.markCompleted`.
   - `legalStatus REJECTED` → `rejected(errorMessages)`; `markCompleted` + el siguiente intento rota la llave (`rotateIdempotencyKey`) conservando el número.
   - `status WAITING_RESPONSE|REGISTERED|SENT` sin `legalStatus` → `pending("En espera de respuesta de la DIAN")` con `providerDocumentId`.
   - `status FAILED` o `errors[]` → `error`/`pending` según el motivo.
   - HTTP 400 → `rejected` con los mensajes; 401/403 → `pending` (credenciales); 5xx/timeout → `pending`; 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` → `pending`; 422 llave reutilizada → rotar llave y reportar `pending`. **Ningún mensaje de error debe contener el texto "409"** (los servicios de NC/ND lo interpretan como conflicto de Factus).
4. Refresco de `WAITING_RESPONSE`: `queryInvoice` por `providerDocumentId` (`GET /invoices/{id}`). `InvoiceProvider` gana `default InvoiceResult queryInvoiceByProviderId(String id)`. `InvoiceServiceImpl.getBySaleId` y `retryInvoice`: si la factura está `PENDING` con `providerDocumentId`, consultar y aplicar el resultado antes de reemitir (nunca emitir de nuevo una factura que Alegra ya recibió).
5. Cablear en `AlegraBillingAdapter` (`emitInvoice`, `queryInvoice…`).

## Criterios de aceptación
- [ ] Con `MockRestServiceServer`: 200 aceptada, aceptada con observaciones, rechazada, `WAITING_RESPONSE`, 400, 401, 5xx/timeout, replay idempotente → cada caso produce el `InvoiceResult` y la transición de `ElectronicInvoice` esperados.
- [ ] Reintentar una venta tras timeout reutiliza **el mismo número y la misma llave**; tras rechazo reutiliza el número con **llave nueva**; una factura `VALIDATED` nunca se reemite.
- [ ] Una factura `PENDING` con `providerDocumentId` se concilia por `GET` en vez de reemitirse.
- [ ] `AlegraInvoiceMapperTest`: IVA 19 %, exento, múltiples ítems, consumidor final, NIT con DV, redondeo.
- [ ] E2E sandbox (con token): venta → factura `VALIDATED` con CUFE y número consecutivo.
- [ ] `.\mvnw.cmd test` en verde; Factus y mock siguen pasando.

## Fuera de alcance
Notas crédito/débito (FEAT-026), email/XML (FEAT-027), PDF (FEAT-028), job programado de conciliación, `frontend/`.

## Riesgos
Cuerpo del bloque `company` (FEAT-020); centavos de diferencia en totales; ventana de idempotencia de 7 días; reconciliación manual hasta que exista job.
