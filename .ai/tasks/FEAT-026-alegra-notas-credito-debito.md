# FEAT-026 — Notas crédito y débito con Alegra

> Parte del plan "Alegra como proveedor de facturación". Solo backend.

## Objetivo
Emitir notas crédito y débito sobre una factura ya `VALIDATED` mediante Alegra, respetando el contrato REST actual (`CreateCreditNoteRequest`/`CreateDebitNoteRequest`, snake_case estilo Factus).

## Depende de
FEAT-025 (emisión de facturas, `providerDocumentId`, asignador) y FEAT-020 (cómo se numeran NC/ND).

## Datos de la API de Alegra
- `POST /credit-notes` — requeridos: `number`, `conceptCode` (`1`..`6`: 1 devolución parcial, 2 anulación de la factura, 3 descuento/rebaja, 4 ajuste de precio, 5 descuento por pronto pago, 6 descuento por volumen), `company`, `customer`, `items`, `totalAmounts`, `payments`; `prefix` opcional (≤4); **`associatedDocuments`** con exactamente 1 elemento `{date, documentType, number, prefix?, uuid=CUFE}` (obligatorio en estándar/exportación; solo una factura del mismo cliente). **No lleva objeto `resolution`.**
- `POST /debit-notes` — mismos requeridos más `associatedDocuments` (≥1); `conceptCode` según la tabla DIAN de notas débito (el ejemplo usa `"1"`).
- Respuestas: `creditNote`/`debitNote` con `id, status, legalStatus, cude, prefix, number, fullNumber, governmentResponse, qrCodeContent, errors` + `files`.
- Header `Idempotency-Key` UUIDv4 (misma semántica que facturas).

## Context Pack (R8)
- Docs (3): `backend/docs/modules/billing/spec.md`, `backend/docs/_conventions.md`, `backend/docs/_architecture.md`.
- Código (≤5): `services/CreditNoteServiceImpl.java`, `services/DebitNoteServiceImpl.java`, `adapters/factus/FactusCreditNoteClient.java` (patrón), `billing/dto/CreateCreditNoteRequest.java`, `billing/dto/CreateDebitNoteRequest.java`. Etapa 2: `AlegraInvoiceMapper` y `AlegraInvoiceClient` de FEAT-025.

## Qué se construye
1. `AlegraCreditNoteClient` / `AlegraDebitNoteClient` (clases simples en `alegra/`):
   - `emitCreditNote(request, storeId)` / `emitDebitNote(...)` → `allocation = allocator.allocate(store, env, "91"|"92", request.referenceCode())`.
   - Factura original: `ElectronicInvoiceRepository` por `(storeId, factusNumber == request.billNumber())` → CUFE, `validatedAt`, `providerDocumentId`; separar prefijo y número del `fullNumber` (regex `^([A-Za-z]{1,4})(\d+)$`); si no está `VALIDATED` → `Result.error`.
   - Cliente: la solicitud llega con `customer = null` → resolverlo de la venta original (`ElectronicInvoice.saleId` → `SaleFacade.getById` → `BillingCanonicalMapper.toInvoiceRequest(sale).customer()`), no desde `internal/` de otro módulo.
   - Ítems/totales: reutilizar la aritmética de `AlegraInvoiceMapper` (extraer a un componente común si hace falta) partiendo de `items[].price`, `quantity`, `taxes[].rate`; `payments` desde `payment_details` (o contado/efectivo por defecto).
   - `conceptCode` ← `correction_concept_code`; `customization_id` se ignora.
   - Mapeo de resultado idéntico al de facturas (`*Result.success(number, cude, qr, null)` con `providerDocumentId`).
2. `deleteUnvalidated*`: no existe en Alegra → mantener el `default` del puerto. **Evitar** mensajes con "409" para no disparar el reintento-borrado de `CreditNoteServiceImpl`/`DebitNoteServiceImpl`.
3. Cablear `AlegraBillingAdapter`. `CreditNoteServiceImpl.createFromSale` y `DebitNoteServiceImpl.createForSale` siguen usando `invoice.getFactusNumber()` como `bill_number` (nombre heredado; no renombrar aquí).
4. Persistir `providerDocumentId` en `electronic_credit_notes`/`electronic_debit_notes` (columna de FEAT-022) al aplicar el resultado.

## Criterios de aceptación
- [ ] `AlegraCreditNoteClientTest` / `AlegraDebitNoteClientTest` (`MockRestServiceServer`): payload con `associatedDocuments`, `conceptCode`, `number/prefix` del asignador; aceptada, rechazada, pendiente, 400, 5xx.
- [ ] Idempotencia por `reference_code`: reintento reutiliza número y llave (mismo comportamiento que facturas).
- [ ] Si la factura de origen no existe o no está validada, error claro sin consumir número.
- [ ] Tests existentes de servicios NC/ND (con proveedor mock/Factus) siguen verdes.
- [ ] E2E sandbox: NC (concepto 2) y ND sobre una factura validada.

## Fuera de alcance
Descarga de PDF/XML (FEAT-027/028); `frontend/`; renombrar `bill_number`.

## Riesgos
FEAT-020 puede revelar que las notas necesitan rango propio con prefijo distinto o resolución; ajustar el asignador (documentos `91/92`) sin tocar las tablas.
