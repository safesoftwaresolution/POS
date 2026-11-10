# Preparación de facturación antes de producción

El objetivo es validar el recorrido funcional sin emitir documentos reales. El perfil Spring `prod` no selecciona el entorno fiscal: cada local tiene su propio entorno de facturación. Las credenciales de Alegra se mantienen fuera del repositorio.

## Dos recorridos de prueba

| Recorrido | Qué comprueba | Qué no demuestra |
| --- | --- | --- |
| Pruebas locales con HTTP simulado | Contratos, habilitación, importación de resoluciones, errores, recuperación, concurrencia y aislamiento | Aceptación real de Alegra o DIAN |
| Local QA en SANDBOX | Venta, emisión, consulta, notas, PDF/XML y correo contra el proveedor de pruebas | Habilitación ni numeración del emisor en producción |

La consulta de resoluciones de Alegra está disponible solo en producción. Por eso el asistente de un cliente conserva su recorrido de producción; sus pruebas previas al token usan respuestas contractuales locales. No se debe reemplazar su entorno por sandbox de forma indiscriminada.

Para documentos aceptados en sandbox, Alegra ofrece los datos del emisor de prueba NIT `900559088`, DV `2`. Un NIT propio requiere la habilitación correspondiente y la selección de Alegra. Usar numeración y referencias de QA sin reutilizar las de otros ensayos. La guía oficial describe la resolución de ejemplo y los datos de empresa necesarios.

Fuentes: [entornos](https://e-provider-docs.alegra.com/docs/entornos), [creación y habilitación](https://e-provider-docs.alegra.com/docs/gu%C3%ADa-creaci%C3%B3n-de-una-compa%C3%B1%C3%ADa-asociada), [resoluciones](https://e-provider-docs.alegra.com/reference/getresolutions).

## Bloqueo previo a cualquier despliegue

`main`, `develop`, `origin/main` y `origin/develop` usan las versiones V33–V37 para otros módulos. Esta rama usaba esos mismos números para facturación, con otro contenido, así que el 2026-11-10 sus siete migraciones se **renumeraron a V38–V44**, por decisión del dueño del producto y sin consultar el historial de producción. La evidencia indirecta era que producción corre `main` (su log muestra el proveedor `factus`, que solo existe en `main`) y que ninguna base persistente se conectó a esta rama.

| Versión | `main` / `develop` | Esta rama |
|---|---|---|
| V33–V37 | `Store_Category_Template`, `Appointments`, `Store_Category_Theme`, `Store_Multiple_Templates`, `Service_Categories` | (no existen aquí; llegan al unir con `main`) |
| V38 | — | `Alegra_Local_Numbering` (antes V33) |
| V39 | — | `Invoice_Emission_Lease` (antes V34) |
| V40 | — | `Note_Emission_Lease` (antes V35) |
| V41 | — | `Retire_Factus_Leftovers` (antes V36) |
| V42 | — | `Billing_Self_Service` (antes V37) |
| V43 | — | `Billing_Document_Context_And_Receipts` (antes V38) |
| V44 | — | `Billing_Company_Registration_Journal` (antes V39) |

Probado en H2 (modo PostgreSQL) sobre una base con la historia completa de `main` hasta V37 y datos sembrados:
- **Con las migraciones de `main` más V38–V44** (el estado tras unir las ramas): las siete se aplican sin error, los estados retirados pasan a `IN_SETUP`, `factus_number` queda como `legal_number` y el entorno de las facturas se completa.
- **Con solo esta rama**: Flyway rechaza arrancar (`Detected applied migration not resolved locally: 33`) antes de aplicar nada.

Una copia local que ya compiló la rama con la numeración vieja conserva en `target/classes/db/migration` los archivos V33–V39 junto a los nuevos, y Flyway falla por versiones duplicadas: hay que correr `mvn clean` (`.\mvnw.cmd clean`) una vez. Las imágenes Docker y la CI compilan desde cero y no se ven afectadas.

Por eso **esta rama solo se despliega después de unirla con `main`**. Antes del despliegue, conviene confirmar igualmente el historial real de cada base persistente:

```sql
SELECT installed_rank, version, description, script, checksum, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Si alguna base muestra `Alegra_Local_Numbering` u otra migración de facturación con número 33–39, esa base aplicó la numeración vieja y necesita una reparación específica sobre una copia restaurable antes de actualizar. No ejecutar `flyway repair` para ocultar la diferencia. Esta guía no autoriza conexiones al servidor ni cambios en producción.

## Recuperación y documentos históricos

- Cada documento nuevo conserva su entorno, proveedor y empresa. Cambiar el entorno del local no debe redirigir las operaciones de documentos anteriores.
- Conservar el token sandbox mientras existan documentos QA que deban consultarse; nunca usar el token de producción como respaldo.
- Un documento histórico sin contexto fiscal verificable requiere revisión. No completar su empresa o entorno desde la configuración actual por suposición.
  - Consecuencia conocida (revisión nativa 2026-11-10): las facturas y notas de Alegra emitidas antes de V43 no tienen `provider_name`, así que no se pueden reenviar por correo ni descargar su PDF o XML desde la app. Se acepta porque producción todavía factura con Factus (no hay documentos de Alegra anteriores a V38 fuera de los entornos de prueba). Si alguna base persistente sí los tiene, su contexto se completa con un script revisado a mano, documento por documento, nunca con un relleno masivo.
- Si el proveedor guardado en el contexto de una nota ya no está registrado, la emisión y el reintento la dejan en error y liberan la reserva; no queda bloqueada hasta que venza el lease.
- Una respuesta incierta o aceptada no autoriza una nueva emisión con otra llave. Primero se recupera la solicitud o el resultado persistido y se concilia.
- La creación incierta de una empresa no se repite a ciegas. Una recepción ya guardada se reutiliza; si no se puede demostrar la pertenencia de una empresa encontrada, debe revisarse mediante el back office autorizado.
- Una nota fiscal no reemplaza el registro operativo de una devolución, un reembolso o un ajuste de inventario.

## Verificación local

Desde `backend/`, sin tokens reales ni conexiones al proveedor:

```powershell
.\mvnw.cmd -o -B test "-Dtest=*,!AlegraSandboxInvoiceE2ETest"
```

Desde `frontend/`:

```powershell
npm test
npm run lint
npm run build
```

`AlegraSandboxInvoiceE2ETest` crea documentos externos y queda fuera de esta verificación. Su ejecución requiere autorización expresa para ese entorno y esas credenciales, aunque el token ya exista en el equipo.

### Criterios de cierre

- [ ] Una factura QA mantiene el contexto sandbox después de cambiar la configuración del local.
- [ ] Éxito externo seguido de fallo local se recupera sin nueva numeración ni nueva llave.
- [ ] Totales de venta, líneas, impuestos y notas coinciden, incluyendo cantidades altas y tasas 0/5/19 %.
- [ ] Las notas pendientes se concilian y sus archivos se descargan con aislamiento entre locales.
- [ ] El asistente se prueba sin token real; una creación de empresa incierta no se duplica.
- [ ] Pruebas locales y revisión independiente completadas; se distinguen de los ensayos reales pendientes.
- [ ] Historia Flyway de destino reconciliada y actualización probada sobre una copia restaurable.

## Activación real posterior

Después de cerrar las comprobaciones anteriores: configurar el token de producción, registrar o asociar la empresa correcta, completar su habilitación, importar y seleccionar su resolución vigente y numeración de notas, y verificar el entorno del local. No copiar identificadores de empresa o numeración de sandbox a producción.

La primera emisión real y la comprobación de sus archivos y estado requieren autorización y una prueba controlada. El objetivo es llegar a ese paso sin cambios de código, no afirmar que el token por sí solo habilita al emisor.
