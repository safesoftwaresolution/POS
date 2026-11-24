# Voice sale preview
Stateless Spanish dictation for the POS; manual confirmation via existing sales. Branch: feature/billing-self-service. Delivery: ask-on-risk; no commit or publication without explicit approval.
Baseline: 446d917dfa68664886cc432fd5149199a1a07540. Runner: Java 21, backend Maven wrapper in offline mode. Forecast: ~1,000–1,600 authored lines including tests/docs; keep behavior with tests.

## Specs
S1. **La voz prepara la venta; el cajero la confirma.** Con un solo audio en español se cargan artículos, cantidades, cliente, medio de pago y efectivo recibido. El backend devuelve un borrador editable. Hasta que el cajero lo revise, **no se registra la venta, no se descuenta stock y no se solicita una factura a la DIAN**.

S2. Por ejemplo: *«Dos cafés grandes y una botella de agua para Ana Pérez. Paga en efectivo con cincuenta mil»*. Si hay dos clientas llamadas Ana Pérez, el borrador muestra ambas opciones de esa tienda. No elige una ni reemplaza silenciosamente a Ana por “Cliente General”.

S3. | **Dictar** | Nuevo `POST /api/v1/voice-sales/preview` | Recibe un archivo de audio y devuelve artículos, cantidades, cliente, pago, efectivo recibido y campos pendientes de revisión. No guarda el borrador. |

S4. | **Revisar** | Respuesta del borrador | Cada referencia indica si se resolvió, tiene varias coincidencias o no se encontró. El POS puede mostrar alternativas y permitir correcciones sin otra llamada a la IA. |

S5. | **Confirmar** | `POST /api/v1/sales` existente | El POS envía los datos revisados, reutiliza la misma `Idempotency-Key` si reintenta el cobro y pasa `sendElectronicInvoice` solo según la elección del cajero. |

S6. La primera entrega es **solo backend**: contrato OpenAPI y ejemplos para que el frontend pueda integrarse después. No incluye captura de micrófono, conversación por turnos, síntesis de voz ni tablas nuevas.

S7. - Crear un módulo `voicesales` con dos interfaces internas: `SpeechTranscriber` transforma audio en texto; `SaleIntentExtractor` transforma texto en campos de venta. Se configuran por separado: cambiar la transcripción no obliga a cambiar el modelo que interpreta el pedido.

S8. - Implementar Groq como primer adaptador de cada interfaz. Usar `whisper-large-v3-turbo` para transcribir en español y `openai/gpt-oss-20b` con esquema JSON estricto para extraer campos. Ambos modelos serán configurables. Otro proveedor implementará el mismo contrato sin tocar ventas ni facturación. [Transcripción de Groq](https://console.groq.com/docs/speech-to-text) · [Salidas estructuradas](https://console.groq.com/docs/structured-outputs).

S9. - **La IA extrae lo que escuchó; el POS decide qué existe.** El modelo devuelve menciones como “café grande”, nunca IDs autorizados, precios, impuestos ni totales. El módulo resuelve productos, servicios y clientes activos usando las fachadas de la tienda autenticada. Si no se dictó un importe o una coincidencia es dudosa, lo marca para revisión; no inventa el dato.

S10. - Aplicar los mismos roles que ventas: `SELLER`, `SUPERVISOR` y `ADMINISTRATOR`. Exigir tienda autenticada antes de llamar al proveedor; nunca aceptar `storeId` desde el audio o el formulario.

S11. - Mantener la función desactivada por defecto. Leer `GROQ_API_KEY` únicamente en el servidor; **no necesitás compartir la clave**. Aceptar archivos WebM/WAV de hasta 5 MB, sin URLs externas, con límites de uso por usuario y tienda y tiempos de espera acotados. Si Groq falla, la venta manual sigue disponible.

S12. - El audio puede contener nombres y documentos. No conservar audio ni transcripciones en el POS, no registrar cuerpos de petición y no enviar a Groq el catálogo ni el padrón de clientes. Antes del piloto con clientes reales, verificar la configuración de retención de la cuenta. [Controles de datos de Groq](https://console.groq.com/docs/your-data).

S13. 1. **Contrato y borrador con proveedor simulado.** Empezar por pruebas en rojo para el ejemplo anterior, un cliente homónimo, un artículo inexistente y un importe no dictado. Verificar que pedir un borrador no modifica ventas, inventario ni facturación.

S14. 2. **Adaptadores Groq.** Probar por HTTP simulado la transcripción, la extracción, respuestas inválidas, límites de uso y tiempos de espera. La suite debe correr sin internet ni claves reales. Verificar que se puede sustituir cada adaptador independientemente.

S15. 3. **Integración con ventas.** Probar aislamiento entre dos tiendas, rechazo por rol, corrección del borrador y doble confirmación con la misma clave: debe quedar **una sola venta y un solo descuento de stock**. Probar también que no se solicita emisión electrónica si el cajero no la eligió.

S16. 4. **Cierre.** Ejecutar la suite completa del backend, documentar el módulo y sus variables, y aplicar la revisión de Gentle AI a los cambios de seguridad. Verifiqué que está habilitada en este repositorio. Dejar la prueba real con Groq para un piloto autorizado con audios ficticios.

## Tasks
T1 | S1–S7, S10–S12 | inline: freeze ports and preview contract; protect endpoint and quota | done (52 voicesales tests green offline, 2026-11-23) | no commit
T2 | S1–S4, S9, S13 | delegated: independent store catalog resolution with simulated intent, focused tests | done (VoiceSaleResolverTest) | no commit
T3 | S7–S9, S11–S12, S14 | delegated: Groq transport and strict extraction with simulated HTTP, focused tests | done (29 Groq tests) | no commit
T4 | S1–S6, S10–S16 | inline integration and docs; delegated long suite and independent security verification | partial: full suite green after hardening (1200 run, 0 failures, 1 skipped); docs/config done; independent security verification done (no blockers, S1–S15 PASS); native review pending (needs commits or declared untracked scope) | no commit

## Log
L1 (2026-11-23), original request verbatim:

PLEASE IMPLEMENT THIS PLAN:
# Backend de facturación por voz: Groq como primer proveedor

**La voz prepara la venta; el cajero la confirma.** Con un solo audio en español se cargan artículos, cantidades, cliente, medio de pago y efectivo recibido. El backend devuelve un borrador editable. Hasta que el cajero lo revise, **no se registra la venta, no se descuenta stock y no se solicita una factura a la DIAN**.

Por ejemplo: *«Dos cafés grandes y una botella de agua para Ana Pérez. Paga en efectivo con cincuenta mil»*. Si hay dos clientas llamadas Ana Pérez, el borrador muestra ambas opciones de esa tienda. No elige una ni reemplaza silenciosamente a Ana por “Cliente General”.

## 1. Contrato con el POS

| Paso | Interfaz | Comportamiento |
|---|---|---|
| **Dictar** | Nuevo `POST /api/v1/voice-sales/preview` | Recibe un archivo de audio y devuelve artículos, cantidades, cliente, pago, efectivo recibido y campos pendientes de revisión. No guarda el borrador. |
| **Revisar** | Respuesta del borrador | Cada referencia indica si se resolvió, tiene varias coincidencias o no se encontró. El POS puede mostrar alternativas y permitir correcciones sin otra llamada a la IA. |
| **Confirmar** | `POST /api/v1/sales` existente | El POS envía los datos revisados, reutiliza la misma `Idempotency-Key` si reintenta el cobro y pasa `sendElectronicInvoice` solo según la elección del cajero. |

La primera entrega es **solo backend**: contrato OpenAPI y ejemplos para que el frontend pueda integrarse después. No incluye captura de micrófono, conversación por turnos, síntesis de voz ni tablas nuevas.

## 2. Arquitectura intercambiable

- Crear un módulo `voicesales` con dos interfaces internas: `SpeechTranscriber` transforma audio en texto; `SaleIntentExtractor` transforma texto en campos de venta. Se configuran por separado: cambiar la transcripción no obliga a cambiar el modelo que interpreta el pedido.
- Implementar Groq como primer adaptador de cada interfaz. Usar `whisper-large-v3-turbo` para transcribir en español y `openai/gpt-oss-20b` con esquema JSON estricto para extraer campos. Ambos modelos serán configurables. Otro proveedor implementará el mismo contrato sin tocar ventas ni facturación. [Transcripción de Groq](https://console.groq.com/docs/speech-to-text) · [Salidas estructuradas](https://console.groq.com/docs/structured-outputs).
- **La IA extrae lo que escuchó; el POS decide qué existe.** El modelo devuelve menciones como “café grande”, nunca IDs autorizados, precios, impuestos ni totales. El módulo resuelve productos, servicios y clientes activos usando las fachadas de la tienda autenticada. Si no se dictó un importe o una coincidencia es dudosa, lo marca para revisión; no inventa el dato.

## 3. Seguridad y operación

- Aplicar los mismos roles que ventas: `SELLER`, `SUPERVISOR` y `ADMINISTRATOR`. Exigir tienda autenticada antes de llamar al proveedor; nunca aceptar `storeId` desde el audio o el formulario.
- Mantener la función desactivada por defecto. Leer `GROQ_API_KEY` únicamente en el servidor; **no necesitás compartir la clave**. Aceptar archivos WebM/WAV de hasta 5 MB, sin URLs externas, con límites de uso por usuario y tienda y tiempos de espera acotados. Si Groq falla, la venta manual sigue disponible.
- El audio puede contener nombres y documentos. No conservar audio ni transcripciones en el POS, no registrar cuerpos de petición y no enviar a Groq el catálogo ni el padrón de clientes. Antes del piloto con clientes reales, verificar la configuración de retención de la cuenta. [Controles de datos de Groq](https://console.groq.com/docs/your-data).

## 4. Desarrollo y aceptación con Gentle AI

1. **Contrato y borrador con proveedor simulado.** Empezar por pruebas en rojo para el ejemplo anterior, un cliente homónimo, un artículo inexistente y un importe no dictado. Verificar que pedir un borrador no modifica ventas, inventario ni facturación.
2. **Adaptadores Groq.** Probar por HTTP simulado la transcripción, la extracción, respuestas inválidas, límites de uso y tiempos de espera. La suite debe correr sin internet ni claves reales. Verificar que se puede sustituir cada adaptador independientemente.
3. **Integración con ventas.** Probar aislamiento entre dos tiendas, rechazo por rol, corrección del borrador y doble confirmación con la misma clave: debe quedar **una sola venta y un solo descuento de stock**. Probar también que no se solicita emisión electrónica si el cajero no la eligió.
4. **Cierre.** Ejecutar la suite completa del backend, documentar el módulo y sus variables, y aplicar la revisión de Gentle AI a los cambios de seguridad. Verifiqué que está habilitada en este repositorio. Dejar la prueba real con Groq para un piloto autorizado con audios ficticios.

## Key Learnings:
1. El POS ya separa el cobro de la emisión electrónica. Reutilizar esa frontera evita crear una segunda ruta de facturación y mantiene a la IA fuera de las decisiones que mueven dinero o stock.

L2 (2026-11-23): User replied “Si” to substituting gpt-6.1-sol/high for voice-sales exploration/development only. No project configuration change.
L4 (2026-11-23): User asked to continue what Codex left. First full suite: 1189 run, 1 failure — VoiceCheckoutFlowTest got 403 because UserServiceImplTest left an Authentication on the thread (its tearDown cleared only TenantContext) and JwtAuthenticationFilter skips login when one is present. Fixed the polluter (tearDown now calls SecurityContextHolder.clearContext()) and made the new test start from a clean thread. Second full suite: BUILD SUCCESS, 1189 run, 0 failures, 1 skipped. Risk: item 2 (new authenticated endpoint, roles/tenant guards, streams user audio to an external provider) → independent verifier launched; native review due (high_risk) but needs a committed or declared candidate.
L5 (2026-11-23): Independent read-only security verification (52 voicesales tests + throwaway real-Tomcat probe in scratch, since removed): no blockers; S1–S15 PASS; S16 outside its scope. Advisories, none blocking: (1) quota is charged after the body is read, so concurrent 5 MiB uploads are not rate-limited and invalid uploads never hit a quota; (2) item/quantity/cash limits live only in the Groq adapter — service and resolver do not re-validate a substituted provider's SaleIntent, and a negative cashReceived is flagged pending but echoed (VoiceSaleResolver.java:75-79, parent-confirmed); (3) a single exact customer match hides partial matches ("Ana Pérez" vs "Ana Pérez Gómez"); (4) provider/DB failures are never logged (log class name only, never message); (5) multipart guard filter is POST-only; (6) resolver bean and guard filter are not conditional on voice-sales.enabled; (7) Groq timeouts are per-read, no total deadline; (8) declared-oversize bodies surface as connection reset to the client, so the future frontend should pre-check size. Not verifiable offline: Groq acceptance of the nullable-integer strict schema and chunked request bodies — belong to the authorized pilot.
L6 (2026-11-23): User answered "Sí, corrige" to fixing advisories 2, 4 and 5. RED first: VoiceSaleServiceTest (out-of-contract intents accepted, provider failures not logged) failed as expected; VoiceMultipartGuardTest and VoiceSaleControllerTest did not compile (missing VoiceMultipartGuard, unexpected(Exception)). Changes: SaleIntent now owns the contract limits (MAX_ITEMS 30, MAX_QUANTITY 1000, MAX_MENTION 200, MAX_PAYMENT 100, MAX_CASH) and withinContract(); VoiceSaleService rejects out-of-contract intents with VoiceProviderException (503) and logs provider failures at WARN by exception class name only; VoiceSaleController.unexpected logs ERROR by class name only; the multipart guard is now a testable VoiceMultipartGuard that rejects multipart for every HTTP method; the Groq adapter reuses the SaleIntent constants. Voicesales tests: 63 green (52 + 11). Docs: spec.md updated. Risk: item 2 (multipart guard widened from POST to all methods and a new validation gate on an unreleased endpoint; no existing consumers). Advisories 1, 3, 6, 7, 8 left open by decision.
L7 (2026-11-23): User asked to configure the variables in the application and answered "Sí, solo dev" to enabling the feature by default only in the dev profile. application.properties already held the voice-sales.* variables (lines 102-109, bound to env vars, default off); GROQ_API_KEY stays out of tracked files (backend/.env or deployment env). Added `voice-sales.enabled=${VOICE_SALES_ENABLED:true}` to application-dev.properties; docs (spec.md, groq.md, _deployment.md) updated. Without a key the endpoint answers 503 before any provider call. First combined test run had one transient context failure in VoiceSalesDisabledTest (Flyway tried a Postgres URL because main's application.properties was used instead of the test one; target/test-classes/application.properties was rewritten by a process other than my Maven runs); not reproducible in two later runs, cause unconfirmed. Full suite after the change: 1200 run, 0 failures, 1 skipped. Risk: none (dev-profile default only; production stays off).
L3: The servlet's multipart staging is not memory-only by default. Use a single binary HTTP request body, Content-Type audio/webm or audio/wav, capped while reading; no URL/metadata fields or disk staging. Keep the existing upload and sales endpoints untouched. Keep both quotas process-local and document that limit. No live provider calls.
