<!-- spec-version: 1.1 | last-updated: 2026-11-24 -->
# Dictado de ventas y asistente de caja

El dictado prepara una propuesta editable, no una factura emitida. Solo la confirmación explícita en el flujo normal de ventas registra el cobro, mueve stock y, si se solicita, inicia la emisión electrónica.

El POS usa el **asistente de caja** (sección siguiente): conversa por voz o texto y arma el ticket en el propio panel. El borrador de una sola vez (`/preview`) sigue disponible en el backend como contrato independiente, pero el POS ya no lo usa.

## Asistente de caja (agente conversacional)

El cajero habla o escribe («Hazme una factura con dos cafés grandes y una agua, paga con Nequi») y el asistente edita el ticket del POS con herramientas, en varios turnos si hace falta («quita el agua», «mejor tres cafés», «a nombre de Luis Gómez», «cóbralo»). Los artículos, el cliente, el medio de pago y el efectivo aparecen directo en el panel «Ticket de Venta», sin ventanas. Al pedir cobrar, el panel muestra **una confirmación en el lugar del botón de cobro**; hasta que el cajero la acepta no se registra la venta, no se mueve stock y no se emite nada.

- **«Factura» es una venta normal** dentro del sistema. El asistente nunca solicita factura electrónica DIAN (el POS confirma con `sendElectronicInvoice=false`) y no existe ninguna herramienta para emitirla.
- **Cliente por defecto:** si no se dicta, se usa el Cliente General de la tienda (identificación `9999999999`). Un nombre homónimo no se adivina: queda Cliente General y el asistente pregunta cuál es.
- **El modelo no decide datos:** solo pasa menciones literales. Artículos, clientes y medios de pago se resuelven contra la tienda autenticada (mismas reglas que el borrador), los precios y el stock salen del catálogo, y los totales los calcula el servidor. Un artículo ambiguo, inexistente o sin stock no se agrega; el modelo recibe las opciones y pregunta.
- **Herramientas** (`TicketTools`): `agregarArticulo`, `quitarArticulo`, `cambiarCantidad`, `asignarCliente`, `usarClienteGeneral`, `fijarMedioPago`, `fijarEfectivoRecibido`, `vaciarTicket` y `pedirConfirmacion`. Esta última solo levanta la bandera `confirmationRequested`; ninguna herramienta registra una venta. Cualquier cambio posterior al ticket retira la confirmación pendiente.
- **Sin estado en el servidor:** cada turno recibe el ticket actual (solo `kind`, `id` y `quantity` por línea, cliente, código de pago y efectivo) y un historial de hasta 6 turnos `user`/`assistant`. Las líneas se vuelven a leer del catálogo del local autenticado; una línea de otro local o que ya no existe se descarta con un aviso en `warnings`. El modelo solo ve el prompt del sistema, el historial y el texto: nunca local, usuario ni permisos.
- **El POS descarta la respuesta** si el ticket cambió mientras el asistente respondía, en lugar de pisar el cambio del cajero. La confirmación solo se muestra mientras el ticket sea idéntico al que se confirmó.

### Contrato

`POST /api/v1/voice-sales/agent/transcribe` — audio binario (mismos tipos, límite y rechazo de multipart que `/preview`); responde `{"text":"..."}`. No guarda el audio.

`POST /api/v1/voice-sales/agent/turn` — JSON, roles `ADMINISTRATOR`, `SUPERVISOR` y `SELLER` con tienda, respuestas `Cache-Control: no-store`.

```json
{
  "text": "dos cafés grandes, paga con Nequi y cobra",
  "ticket": {
    "lines": [{"kind": "PRODUCT", "id": 11, "quantity": 1}],
    "customerId": null, "paymentMethodCode": "CASH", "cashReceived": null
  },
  "history": [{"role": "user", "text": "hola"}, {"role": "assistant", "text": "¿Qué necesitas?"}]
}
```

```json
{
  "reply": "Listo: 2 cafés grandes, pago con Nequi. ¿Confirmas?",
  "ticket": {
    "lines": [{"kind": "PRODUCT", "id": 11, "code": "P-11", "name": "Café grande", "unitPrice": 3500, "quantity": 2}],
    "customer": {"id": 1, "name": "Cliente General"},
    "paymentMethod": {"code": "NEQUI", "label": "Nequi"},
    "cashReceived": null, "subtotal": 7000, "confirmationRequested": true
  },
  "warnings": []
}
```

Límites: texto de 1 a 500 caracteres (400), hasta 100 líneas de ticket y 50 turnos de historial en el cuerpo (413; el servidor considera solo las primeras 30 líneas, con un aviso, y los últimos 6 turnos). Errores: 403 sin sesión de tienda, 429 por cuota, 503 si el proveedor falla o no hay modelo configurado. Si el modelo agota las rondas sin terminar, responde 200 con un aviso y el ticket conserva lo que las herramientas alcanzaron a aplicar. Para confirmar, el POS sigue usando `POST /api/v1/sales` con `Idempotency-Key`, como en el flujo normal.

### Proveedor y modelo (Spring AI)

El ciclo modelo-herramientas se escribe a mano en `SpringAiAgentModel` sobre `ChatModel` de Spring AI 2.0.x (compatible con Spring Boot 4.0/4.1): el número de rondas es configurable (`VOICE_SALES_AGENT_MAX_ITERATIONS`, 6 por defecto) y se prueba con un modelo guionado. Cualquier proveedor compatible con el protocolo de OpenAI (Groq por defecto) se cambia solo con configuración (`spring.ai.openai.*`); otro proveedor de Spring AI se agrega con su starter. Con `spring.ai.model.chat=none` la aplicación arranca igual y el asistente responde 503. Solo se usa el modelo de chat: las demás autoconfiguraciones de OpenAI (audio, imagen, embeddings, moderación) se excluyen en `Application`, porque exigen clave al arrancar. La transcripción sigue siendo el adaptador de Groq descrito en [groq.md](groq.md).

### Pruebas y evaluación

- **Arnés determinista (siempre corre):** `AgentHarnessScriptedTest` reproduce 10 dictados reales en español (factura con productos y pago, cobro con efectivo, artículo ambiguo, sin stock, quitar, cambiar cantidad, cliente nombrado con servicio, cliente homónimo, vaciar, pedir factura electrónica) con un `ChatModel` guionado, a través del servicio, las herramientas y el ciclo reales. `AgentOpenAiProtocolTest` ejecuta el cliente real de Spring AI contra un servidor local con el protocolo de OpenAI, sin clave ni internet.
- **Evaluación en vivo (opcional):** `AgentHarnessLiveEval` repite los mismos casos con el modelo real. Requiere `VOICE_SALES_LIVE_EVAL=true` y una `GROQ_API_KEY` de prueba; exige una tasa de aciertos mínima (`VOICE_SALES_LIVE_MIN_PASS_RATE`, 0,8 por defecto) porque los modelos no son deterministas. Usa datos ficticios del propio arnés.

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
| `VOICE_SALES_USER_PER_MINUTE` / `VOICE_SALES_STORE_PER_MINUTE` | 6 por usuario y 30 por local, por instancia (solo `/preview`) |
| `VOICE_SALES_AGENT_ENABLED` | `false`; el perfil `dev` lo enciende. Requiere `VOICE_SALES_ENABLED=true` |
| `VOICE_SALES_AGENT_PROVIDER` | `none` (apagado). `openai` enciende el modelo de chat de Spring AI (Groq y otros compatibles con OpenAI) |
| `VOICE_SALES_AGENT_BASE_URL` | `https://api.groq.com/openai/v1` |
| `VOICE_SALES_AGENT_MODEL` / `VOICE_SALES_AGENT_TEMPERATURE` | `openai/gpt-oss-20b` / `0.1`; usa `GROQ_API_KEY` |
| `VOICE_SALES_AGENT_MAX_ITERATIONS` | 6 rondas modelo-herramientas por turno |
| `VOICE_SALES_AGENT_USER_PER_MINUTE` / `VOICE_SALES_AGENT_STORE_PER_MINUTE` | 30 por usuario y 120 por local, por instancia (transcripción y turnos juntos) |

Las cuotas se aplican juntas en ventanas fijas de un minuto; no son cuotas distribuidas. Antes de desplegar varias réplicas con un presupuesto compartido, aplicar una cuota agregada en infraestructura. Para desactivar, establecer `VOICE_SALES_ENABLED=false` y reiniciar. Para cambiar de proveedor, implementar y registrar el puerto correspondiente y seleccionar su adaptador; cambiar el nombre del modelo por sí solo no implementa un proveedor nuevo.

El piloto real requiere autorización separada y audios ficticios. Verificar [los controles de retención de Groq](https://console.groq.com/docs/your-data) en la cuenta antes de enviar datos personales. En CI utilizar proveedores simulados sin credenciales ni tráfico a Groq.
