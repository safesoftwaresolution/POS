# FEAT-028 — PDF de la factura (representación gráfica)

> Parte del plan "Alegra como proveedor de facturación". Solo backend.

## Objetivo
Que `GET /api/v1/billing/invoices/sales/{saleId}/pdf` (y las equivalentes de notas) devuelva un PDF legible con CUFE y QR válidos cuando el proveedor activo es Alegra.

## Depende de
FEAT-025 y **FEAT-020** (decide la ruta). Opcionalmente FEAT-026 para NC/ND.

## Decisión según FEAT-020
**Ruta A — Alegra entrega el PDF** (`files.attachedDocument` o un `.pdf` dentro de `files.zip`): `AlegraInvoiceClient.downloadInvoicePDF(legalNumber)` = `GET /invoices/{id}` → link → bytes (o extraer el `.pdf` del ZIP con `java.util.zip`). Fin de la tarea; documentar la caducidad de 60 min (se pide siempre un link fresco, nada de `pdf_url` persistido).

**Ruta B — Alegra no lo entrega → se genera en el servidor** (solo si FEAT-020 lo confirma).

## Context Pack (R8) — Ruta B
- Docs (4): `backend/docs/modules/billing/spec.md`, `backend/docs/_conventions.md`, `backend/docs/_architecture.md`, anexo técnico DIAN vigente de la representación gráfica (**verificar los campos obligatorios antes de maquetar**).
- Código (≤5): `billing/internal/entities/ElectronicInvoice.java`, `mappers/BillingCanonicalMapper.java`, `alegra/AlegraCompanyProvider.java`, `services/InvoiceServiceImpl.java` (`downloadInvoicePDF`), `backend/pom.xml`.

## Qué se construye (Ruta B)
1. `pom.xml`: **OpenPDF** (maquetación) y **ZXing core** (QR); PDFBox solo con alcance `test` para extraer texto en pruebas. Fijar versiones; revisar licencias (OpenPDF LGPL/MPL, ZXing Apache 2.0, PDFBox Apache 2.0).
2. `billing/internal/pdf/`: `InvoicePdfData` (record), `InvoicePdfAssembler` (arma los datos), `InvoicePdfRenderer` (genera `byte[]`). Agnóstico del proveedor.
   - Datos: `ElectronicInvoice` (número, CUFE, `qr_code`, `validatedAt`), `SaleFacade.getById` (ítems, totales, fecha), cliente vía `BillingCanonicalMapper`, emisor vía `AlegraCompanyProvider` (razón social, NIT-DV, dirección, responsabilidades), rango/resolución vía `billing_document_number.range_id` → `billing_numbering_range`, logo vía `SettingsFacade`/`StorageFacade` (sin tocar `internal/` de otros módulos).
   - Contenido mínimo: título "Factura electrónica de venta", prefijo+número, fecha/hora de generación y validación, emisor, adquiriente, forma/medio de pago, ítems (código, descripción, cantidad, unidad, precio, % IVA, total), totales, resolución de autorización (número, vigencia, prefijo, rango), **CUFE**, **QR** con el bloque `qrCodeContent` completo, leyenda del proveedor tecnológico.
3. `AlegraInvoiceClient.downloadInvoicePDF` llama al renderizador; lo mismo para NC/ND con su CUDE. Si la factura no está `VALIDATED` → error claro. Sin caché inicial (la factura `VALIDATED` es inmutable; cachear en R2 más adelante).
4. El contrato del controlador no cambia (`application/pdf`, `Content-Disposition: inline`).

## Criterios de aceptación
- [ ] Ruta A: `MockRestServiceServer` con link PDF directo y con ZIP; `Content-Type` correcto; link caducado → reconsulta.
- [ ] Ruta B: `InvoicePdfRendererTest` — el PDF no está vacío, el texto extraído contiene número, CUFE, NIT, ítems y totales; el QR decodificado con ZXing es igual a `qrCodeContent`.
- [ ] Una factura `PENDING`/`REJECTED` no genera PDF legal (409/400 con mensaje claro).
- [ ] E2E sandbox: abrir el PDF descargado y validar visualmente contra la lista del anexo técnico.
- [ ] `.\mvnw.cmd test` en verde.

## Fuera de alcance
Adjuntar el PDF al correo de la app; plantillas por local; `frontend/`.

## Riesgos
Los campos obligatorios exactos de la representación gráfica deben contrastarse con el anexo técnico DIAN vigente (no verificados aún). El logo puede no existir o ser grande: manejar su ausencia.
