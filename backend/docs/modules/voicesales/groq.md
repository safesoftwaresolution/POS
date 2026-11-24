<!-- spec-version: 1.0 | last-updated: 2026-11-23 -->
# Adaptadores Groq

## Activación y sustitución

La función está desactivada por defecto (el perfil `dev` la enciende; sin `GROQ_API_KEY` el endpoint responde 503). El servidor resuelve `voice-sales.groq.api-key`
desde `GROQ_API_KEY`; no se comparte la clave con el frontend. Una clave vacía impide
la llamada y produce un error saneado; no activa un proveedor alternativo ni afecta
la venta manual.

| Propiedad | Valor predeterminado |
|---|---|
| `voice-sales.enabled` | `false` |
| `voice-sales.transcription-provider` | `groq` |
| `voice-sales.extraction-provider` | `groq` |
| `voice-sales.groq.transcription-model` | `whisper-large-v3-turbo` |
| `voice-sales.groq.extraction-model` | `openai/gpt-oss-20b` |

Cada selector controla solamente su puerto (`SpeechTranscriber` o
`SaleIntentExtractor`). Otro proveedor registra una implementación del puerto y
su selector correspondiente; no necesita modificar ventas, facturación ni el otro
adaptador. Los modelos son configurables, pero el modelo de extracción elegido
debe soportar `response_format.json_schema` con `strict: true`.

## Transporte y privacidad

- Destinos HTTPS fijos: `api.groq.com/openai/v1/audio/transcriptions` y
  `api.groq.com/openai/v1/chat/completions`. No se aceptan URLs ni se siguen redirecciones.
- Se reutiliza `OutboundHttp.noRedirectsRestClient()`: conexión de 5 s y lectura de
  45 s. No hay reintentos automáticos; una solicitud de borrador puede hacer dos
  llamadas secuenciales. El tiempo de lectura es un límite de inactividad, no un
  plazo total para ambas llamadas.
- La transcripción envía bytes originales en multipart, nombre fijo `audio.webm`
  o `audio.wav`, idioma `es` y respuesta JSON. Se rechazan archivos vacíos, otros
  tipos y más de 5 MiB. No se escriben archivos temporales.
- La extracción envía solamente instrucciones fijas y la transcripción: nunca
  catálogo, padrón, IDs autorizados, precios o configuración de la tienda.
- No se registran ni persisten audio, transcripciones ni respuestas. El JSON
  saliente evita convertidores de objetos que pueden imprimir cuerpos en DEBUG.
  No habilitar captura de cuerpos/credenciales en proxies, APM o trazas HTTP.
- Cada respuesta está limitada a 64 KiB. Los errores, 429, redirecciones y tiempos
  de espera se transforman en `VoiceProviderException` sin cuerpo ni causa externa.
  El límite de uso local por usuario/tienda se aplica antes del adaptador.
- Antes del piloto real, verificar la retención y los controles de datos de la
  cuenta Groq. No se realizaron llamadas reales en esta implementación.

## Contrato de extracción

El esquema estricto exige exactamente `items`, `customer`, `paymentMethod` y
`cashReceived`; cada artículo exige `mention` y `quantity`. No admite propiedades
adicionales. Los campos no dictados se devuelven como `null`, incluida la cantidad;
una lista vacía es válida. El medio de pago conserva una mención literal, por
ejemplo `Nequi` o `fiado`, y el POS la resuelve contra sus métodos activos.

También se valida localmente, sin coerción de tipos: máximo 30 artículos,
menciones y cliente de 1–200 caracteres, pago de 1–100, cantidades enteras de
1–1000 o `null`, efectivo de 0 a 999999999999.99 con hasta dos decimales o `null`,
transcripción no vacía de hasta 12000 caracteres. Se rechazan campos desconocidos,
faltantes o duplicados, JSON extra, rechazos del modelo y respuestas truncadas.
El esquema del proveedor es estructural; estos límites locales no dependen de su
adhesión al esquema. La resolución de referencias y la revisión del cajero siguen
siendo necesarias: una respuesta sintácticamente válida no prueba que fue dictada.

## Verificación sin red

Desde `backend`, con Java 21: `.\mvnw.cmd -o test '-Dtest=GroqVoice*Test'`.
Las pruebas HTTP simuladas usan audio y credenciales ficticios; verifican multipart,
esquema estricto, validación local, precisión monetaria, ausencia de datos inventados,
fallos saneados y sustitución independiente de cada puerto.

Referencias del proveedor: [transcripción](https://console.groq.com/docs/speech-to-text),
[salidas estructuradas](https://console.groq.com/docs/structured-outputs) y
[controles de datos](https://console.groq.com/docs/your-data).
