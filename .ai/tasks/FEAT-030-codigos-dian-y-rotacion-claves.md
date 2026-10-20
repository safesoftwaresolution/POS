# FEAT-030 — Catálogo central de códigos DIAN y rotación de claves de cifrado

> Salió de FEAT-018 el 2026-10-20. **No bloquea** la integración con Alegra; conviene hacerla después de FEAT-025 para que el catálogo cubra también los mappers de Alegra. Solo backend.

## Parte 1 — Catálogo central de códigos DIAN

### Objetivo
Una sola fuente de verdad para los códigos DIAN que hoy están repartidos como literales en los mappers.

### Estado actual
- `BillingCanonicalMapper`: medios de pago (`TRANSFER→31`, `NEQUI/DAVIPLATA→42`, `CARD→48`, defecto `10`), forma de pago `"1"`, documento `"31"` NIT / `"13"` cédula, organización `"1"`/`"2"`, tributo `"01"`/`"ZZ"`, responsabilidades `O-13`/`R-99-PN`, unidad `"94"`, impuesto `"01"`, estándar `"999"`, municipio por defecto `"11001"`, consumidor final `"222222222222"`.
- `FactusSaleMapper` y los mappers de nota repiten defaults (`"13"`, `"2"`, `"ZZ"`, `"94"`, `"999"`, `"01"`).
- Los mappers de Alegra (FEAT-025/026) heredarán los mismos literales si no se centraliza.

### Context Pack (R8)
- Docs (3): `backend/docs/modules/billing/spec.md` (mapeo de medios de pago), `backend/docs/_conventions.md`, `backend/docs/_glossary.md`.
- Código (≤5): `mappers/BillingCanonicalMapper.java`, `adapters/factus/FactusSaleMapper.java`, `alegra/AlegraInvoiceMapper.java` (si ya existe), `adapters/dto/CustomerBillingData.java`, `adapters/dto/InvoiceItemData.java`.

### Qué se construye
- `billing/internal/mappers/DianCodes` (clase final con constantes y enums pequeños: `PaymentMethod`, `PaymentForm`, `IdentificationType`, `OrganizationType`, `TaxCode`, `Responsibility`, `UnitMeasure`, `StandardCode`, `DefaultConsumer`) y un `DianPaymentMethodResolver` (`@Component`) que reemplaza `resolveDianPaymentMethod`.
- Reemplazar los literales en `BillingCanonicalMapper`, `FactusSaleMapper`, los mappers de notas y los de Alegra por el catálogo, **sin cambiar ningún valor emitido**.
- Los códigos que dependen de tablas DIAN completas (municipios, unidades) siguen viniendo del dato del cliente/producto; el catálogo solo guarda los valores por defecto y las equivalencias propias del POS.

### Criterios de aceptación
- [ ] `git grep` de los literales listados no encuentra duplicados fuera de `DianCodes` en `billing/`.
- [ ] Tests existentes de mappers (`BillingCanonicalMapperTest`, `FactusSaleMapperTest`, mappers de Alegra) pasan sin cambiar los valores esperados; test nuevo del catálogo (cada medio de pago de POS → código DIAN).
- [ ] Comportamiento de Factus idéntico.

## Parte 2 — Rotación de claves de `SecretCryptoConverter`

### Objetivo
Poder cambiar la clave AES-256-GCM de `billing.encryption-key` sin perder los secretos ya guardados en `store_billing_config` (`client_secret`, `password` de Factus).

### Estado actual
`SecretCryptoConverter` cifra con AES-256-GCM, formato `v1:` + Base64(IV 12 bytes + texto cifrado + tag), con una sola clave (`BILLING_ENCRYPTION_KEY`). Cambiar la clave hace ilegibles todos los secretos. El token de Alegra **no** está en BD (variable de entorno), así que esto solo protege Factus.

### Context Pack (R8)
- Docs (3): `backend/docs/_security.md` (sección de credenciales), `backend/docs/_deployment.md`, `backend/docs/modules/billing/spec.md`.
- Código (≤5): `entities/SecretCryptoConverter.java`, `entities/StoreBillingConfig.java`, `entities/FactusCredentials.java`, `repositories/StoreBillingConfigRepository.java`, `SecretCryptoConverterTest`.

### Qué se construye
- Formato versionado con identificador de clave: `v2:<keyId>:<base64>`. Lectura compatible con `v1:` (clave "legacy" = la actual).
- Propiedades: `billing.encryption-keys.<id>=<base64 de 32 bytes>` (varias) y `billing.encryption-active-key=<id>`; `billing.encryption-key` se mantiene como alias de la clave `legacy` para no romper despliegues. Arranque falla si la clave activa no existe o no mide 32 bytes.
- Escritura siempre con la clave activa; lectura con la clave indicada en el prefijo.
- Comando de re-cifrado (`SUPER_ADMIN`, endpoint de backoffice **o** `ApplicationRunner` detrás de una propiedad `billing.encryption.reencrypt-on-startup`; elegir uno y documentarlo): recorre `store_billing_config`, descifra con la clave de cada valor, vuelve a cifrar con la activa en lotes y registra cuántos cambió, sin loguear secretos.
- Documentar el procedimiento de rotación (añadir clave nueva → activarla → re-cifrar → retirar la anterior) en `_security.md` y `_deployment.md`.

### Criterios de aceptación
- [ ] Valores `v1:` existentes se descifran igual que antes.
- [ ] Rotación completa en un test: cifrar con A, añadir B y activarla, leer valores viejos, re-cifrar, quitar A y seguir leyendo.
- [ ] Clave inexistente/inválida → error claro al arrancar, nunca texto plano ni `null` silencioso.
- [ ] `StoreBillingConfigPersistenceTest` (valores cifrados, sin texto plano) sigue verde.
- [ ] Ningún secreto ni clave aparece en logs ni en el diff.

## Fuera de alcance
Migrar los secretos de Factus a otro almacén (KMS/Vault); mover el token de Alegra a BD; `frontend/`.

## Riesgos
Rotar con valores que fallan al descifrar (clave perdida) dejaría locales sin credenciales: el re-cifrado debe ser todo-o-nada por fila y reportar las filas ilegibles sin tocarlas.
