# FEAT-020 — Resultados del spike en el sandbox de Alegra

> Ejecutado el 2026-11-04 contra `https://sandbox-api.alegra.com/e-provider/col/v1`. Ningún token ni id de empresa se guarda aquí.
> **Estado: parcial.** Respondidas 3 de las 6 preguntas; las otras 3 (PDF, notas, token en producción) siguen pendientes. Ver "Pendiente".

## Preparación que hubo que hacer (afecta a FEAT-022 y 029)
1. El token entregado es de **plataforma**: `GET /company` responde `404 AEP1002 Company not found` y `GET /companies` lista las empresas asociadas. Cada local será una empresa **asociada** con su propio `company.id` (ULID).
2. `POST /companies` con `{name, identification, dv, useAlegraCertificate}` crea la empresa (`type: associated`, `governmentStatus: {}`).
3. `POST /test-sets` con `{company:{id}, type:"invoices", governmentId:"a70562e0-631e-4ceb-aa65-36887b57dc17"}` (valor fijo del sandbox) responde 201 `status: ACCEPTED`; la empresa pasa a `governmentStatus: {invoices: AUTHORIZED}`. En producción el `governmentId` lo genera la DIAN.
4. La resolución de pruebas de la documentación (prefijo `SETP`, `resolutionNumber 18760000001`, rango 990000000-995000000) **solo la acepta una empresa creada con NIT 900559088 DV 2**. Con otro NIT la DIAN rechaza (código 89: "NIT ... no autorizado a enviar documentos para emisor con NIT ...").
5. Ese rango de pruebas es **compartido** entre todos los que prueban Alegra: un número fijo (990000201) fue rechazado con la regla 90 "Documento procesado anteriormente". En pruebas usar números aleatorios dentro del rango.

## Tabla pregunta → respuesta → tarea afectada

| Pregunta | Respuesta verificada | Afecta a |
|---|---|---|
| ¿Basta `company.id` o hay que enviar el bloque `company` completo? | **No basta.** Solo `id` → `400 AEP6012` (falta `organizationType`); con `organizationType` → `400 AEP6013` (falta `identificationType`). Se aceptó con `id`, `organizationType`, `identificationType` (`31`), `identificationNumber`, `dv`, `name`, `regimeCode`, `email` y `address{address,city,department,country}`. No se probó el conjunto mínimo exacto ni actualizar la empresa con `PUT /companies/{id}` para evitar enviar el bloque cada vez. | FEAT-022, 024, 025 |
| ¿`POST /invoices` acepta `Idempotency-Key`? | **Sí.** Header opcional (UUID v4). Repetir llave y cuerpo devuelve `201` con `Idempotent-Replayed: true`. Llave igual con cuerpo distinto (`422`) y `409` están documentados, no probados. | FEAT-025 |
| ¿`attachedDocument`/`zip`/correo traen PDF? | **Sí, dentro del zip (verificado 2026-11-04).** `files.xml` y `files.attachedDocument` son XML (enlaces S3); `files.zip` (`application/zip`, enlace firmado de `*.alegra.com`, sin token) trae un **PDF real** (~36 KB) y el XML del documento adjunto. El zip guarda las entradas sin comprimir y con descriptor de datos (hay que leerlo con `ZipFile`, no con `ZipInputStream`). Los enlaces caducan a los 60 min: se pide uno fresco con `GET /invoices/{id}`. El correo (`POST /send-email`) sigue sin probarse. | FEAT-028 (ruta A, hecha), FEAT-027 |
| ¿NC/ND exigen resolución o rango propio? | **No llevan resolución (verificado 2026-11-04).** `POST /credit-notes` y `/debit-notes` aceptaron prefijo propio (`NC`/`ND`) y número propio. Exigen `invoicePeriod` (fecha de la factura de origen) y `associatedDocuments` (fecha, tipo `01`, número y prefijo de la factura, `uuid` = su CUFE). Concepto 2 (anulación) en la crédito, 1 en la débito. La respuesta trae `creditNote`/`debitNote` con `cude` (no `cufe`). Ambas salieron `ACCEPTED_WITH_OBSERVATIONS`. | FEAT-023, 026 (hecha) |
| ¿Token distinto por entorno? ¿cómo se obtiene? | **Parcial.** Autenticación `Authorization: Bearer <token>` (sin usuario). Sandbox y producción son entornos independientes; si el token difiere por entorno y cómo se rota no se pudo comprobar. | FEAT-024, 029 |
| Formato exacto de errores | **Parcial.** `400`/`404`: `{"errors":[{"code":"AEP....","message":"..."}]}`. Un rechazo de la DIAN **no es un error HTTP**: llega como `201` con `invoice.legalStatus: REJECTED` y `governmentResponse{code,message,errorMessages[]}`. `422` y `409` sin probar. | FEAT-025 |

## Respuesta real de una emisión aceptada (resumida)
```json
{
  "invoice": {
    "id": "<ULID>",
    "status": "SENT",
    "legalStatus": "ACCEPTED_WITH_OBSERVATIONS",
    "fullNumber": "SETP993836500",
    "cufe": "<...>",
    "governmentResponse": {
      "code": "00",
      "message": "Procesado Correctamente.",
      "errorMessages": [
        "Regla: FAZ09, Notificación: Debe existir el grupo de información de identificación del bien o servicio",
        "Regla: FAJ43b, Notificación: Nombre informado No corresponde al registrado en el RUT con respecto al Nit suminstrado."
      ]
    }
  },
  "files": { "xml": "<enlace>", "attachedDocument": "<enlace>", "zip": "<enlace>" }
}
```
`FAZ09` desaparece enviando `standardCode` en los ítems; `FAJ43b` es solo aviso (el nombre de la empresa de prueba no coincide con el RUT).

## Reglas para el adaptador de Alegra
- **Nunca** tratar `201` como éxito: decidir por `legalStatus` (`ACCEPTED`, `ACCEPTED_WITH_OBSERVATIONS`, `REJECTED`) y `status` (`REGISTERED`, `WAITING_RESPONSE`, `FAILED`, `SENT`).
- Guardar `invoice.id` como `providerDocumentId` y `invoice.cufe`; las URLs de `files` caducan a los 60 min, descargar de inmediato o volver a consultar.
- Enviar `Idempotency-Key` (UUID v4) estable por documento para poder reintentar.
- Numeración y resolución las envía el sistema (confirmado): ver FEAT-022/023.
- Los mensajes de error propios no deben contener "409" (los servicios de notas lo leen como conflicto de Factus).

## Pendiente para cerrar el spike
- [x] Descargar `attachedDocument` y `zip` y listar su contenido: el zip trae un PDF (ver tabla).
- [ ] `POST /send-email` y ver qué adjunta.
- [x] `POST /credit-notes` y `POST /debit-notes` sobre una factura aceptada: sin resolución, ver tabla.
- [ ] Llave de idempotencia igual con cuerpo distinto (`422`).
- [ ] Confirmar con Alegra cómo se obtiene el token de producción y si cambia por entorno.
