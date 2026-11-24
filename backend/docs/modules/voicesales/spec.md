<!-- spec-version: 1.0 | last-updated: 2026-11-23 -->
# Dictado de ventas

El dictado prepara una propuesta editable, no una factura emitida. Solo la confirmación explícita en el flujo normal de ventas registra el cobro, mueve stock y, si se solicita, inicia la emisión electrónica.

## Contrato y ejemplo

`POST /api/v1/voice-sales/preview` — autenticación del POS, un único audio en el cuerpo binario. Tipos: `audio/webm` (incluido `audio/webm;codecs=opus`) y `audio/wav`; máximo 5 MiB (5 242 880 bytes). No acepta enlaces, JSON ni formularios multipart (se rechazan con 415 para cualquier método HTTP, antes de que el servidor los almacene en disco). El local se obtiene de la sesión; no es un campo de entrada.

```http
POST /api/v1/voice-sales/preview HTTP/1.1
Authorization: Bearer <token-de-la-sesion>
Content-Type: audio/webm;codecs=opus

<bytes de la grabación>
```

Ejemplo ilustrativo para «Dos cafés grandes para Ana Pérez. En efectivo con cincuenta mil». El cajero debe elegir la persona correcta u omitirla explícitamente si corresponde; una sugerencia no es autorización.

```json
{
  "items": [{
    "item": {"mention":"café grande","status":"RESOLVED","candidates":[
      {"kind":"PRODUCT","id":7,"code":"CAF-G","label":"Café grande"}]},
    "quantity":2
  }],
  "customer":{"mention":"Ana Pérez","status":"AMBIGUOUS","candidates":[
    {"kind":"CUSTOMER","id":14,"code":null,"label":"Ana Pérez (••••1234; #14)"},
    {"kind":"CUSTOMER","id":22,"code":null,"label":"Ana Pérez (••••6789; #22)"}
  ]},
  "paymentMethod":{"mention":"efectivo","status":"RESOLVED","candidates":[
    {"kind":"PAYMENT_METHOD","id":3,"code":"CASH","label":"Efectivo"}
  ]},
  "cashReceived":50000,
  "pendingFields":["customer"]
}
```

`RESOLVED` indica coincidencia inequívoca, `AMBIGUOUS` varias alternativas, `NEEDS_REVIEW` una sugerencia no concluyente, `NOT_FOUND` ninguna coincidencia y `NOT_PROVIDED` un dato no dictado. Un campo pendiente no equivale a “Cliente General”, cantidad uno ni pago en efectivo. Los importes y cantidades ausentes se devuelven como `null`. La lista de candidatos es orientativa y acotada; no sustituye el buscador normal.

**Confirmación (sin otra llamada a la IA):** tras revisar o corregir, enviar `POST /api/v1/sales` con un UUID en `Idempotency-Key`. Reutilizar ese UUID solo para los reintentos del mismo cobro. Por ejemplo:

```json
{"items":[{"productId":7,"quantity":2}],"customerId":22,"paymentMethod":"CASH",
 "cashReceived":50000,"sendElectronicInvoice":false}
```

Para servicios, enviar `serviceId` en lugar de `productId`. Ventas valida catálogo, precios, pago y stock en el momento de confirmar. La elección de facturación electrónica es del cajero, nunca de la IA.

## Dependencias, datos y permisos

- Transcripción → extracción de menciones → consultas a las fachadas públicas de productos, servicios, clientes y parámetros. Solo opciones activas; catálogo comercial y clientes del local autenticado. Los métodos de pago activos pertenecen al catálogo global `PAYMENT_METHODS`.
- Roles: `ADMINISTRATOR`, `SUPERVISOR` y `SELLER` con tienda. No se permite `SUPER_ADMIN`. El endpoint usa las protecciones de sesión del POS.
- Sin tablas, borradores persistidos, auditorías con contenido ni grabaciones en disco. No se devuelve la transcripción. El proveedor de transcripción recibe el audio; el de extracción, el texto. Ninguno recibe precios, opciones de clientes ni el catálogo. No habilitar trazas de cuerpos HTTP en servidores, proxies ni clientes.
- Contrato de salida de cualquier proveedor: el servicio valida, sin importar el adaptador, un máximo de 30 artículos, cantidades de 1 a 1000, menciones de hasta 200 caracteres (medio de pago, 100) y un efectivo de 0 a 999 999 999 999,99 con a lo sumo dos decimales. Una salida fuera de contrato responde 503, igual que una respuesta inválida.
- Registro: los fallos del proveedor o inesperados se registran únicamente con el nombre de la clase de la excepción; nunca con mensajes, transcripciones, menciones ni audio.
- Errores: 401/403 para acceso no autorizado; 413 para tamaño excesivo, 415 para tipo no admitido, 429 para cuota local y 503 para indisponibilidad del proveedor o salida inválida. La venta manual sigue disponible. No reenviar automáticamente datos personales a otro proveedor.

## Configuración y piloto

Detalles del [adaptador Groq y su contrato de extracción](groq.md).

| Variable de backend | Valor por defecto |
|---|---|
| `VOICE_SALES_ENABLED` | `false`; el perfil `dev` lo enciende (`true`) para trabajar en local, aún sin clave el endpoint responde 503 |
| `VOICE_SALES_TRANSCRIPTION_PROVIDER` / `VOICE_SALES_EXTRACTION_PROVIDER` | `groq` para cada etapa |
| `VOICE_SALES_TRANSCRIPTION_MODEL` | `whisper-large-v3-turbo` |
| `VOICE_SALES_EXTRACTION_MODEL` | `openai/gpt-oss-20b` |
| `GROQ_API_KEY` | Sin valor; inyectar como secreto en el servidor |
| `VOICE_SALES_USER_PER_MINUTE` / `VOICE_SALES_STORE_PER_MINUTE` | 6 por usuario y 30 por local, por instancia |

Las cuotas se aplican juntas en ventanas fijas de un minuto; no son cuotas distribuidas. Antes de desplegar varias réplicas con un presupuesto compartido, aplicar una cuota agregada en infraestructura. Para desactivar, establecer `VOICE_SALES_ENABLED=false` y reiniciar. Para cambiar de proveedor, implementar y registrar el puerto correspondiente y seleccionar su adaptador; cambiar el nombre del modelo por sí solo no implementa un proveedor nuevo.

El piloto real requiere autorización separada y audios ficticios. Verificar [los controles de retención de Groq](https://console.groq.com/docs/your-data) en la cuenta antes de enviar datos personales. En CI utilizar proveedores simulados sin credenciales ni tráfico a Groq.
