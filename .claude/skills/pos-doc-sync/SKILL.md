---
name: pos-doc-sync
description: Audita y actualiza la documentación de backend/docs (specs por módulo + _roadmap.md) contra el estado real del código y del historial de git. Usar después de implementar features/fixes en un módulo, o cuando se pida "poner la documentación al día".
---

# Mantener backend/docs al día

La documentación en español (`backend/docs/`) es la fuente de verdad de negocio para este proyecto. Se desactualiza fácilmente porque nada la valida automáticamente — esta skill es el procedimiento para detectarlo y corregirlo.

## 1. Detectar specs desactualizados

Cada `backend/docs/modules/[modulo]/spec.md` empieza con:
```
<!-- spec-version: X.Y | last-updated: YYYY-MM-DD -->
```

Para un módulo dado:
1. `git log --since=<last-updated del spec> -- backend/src/main/java/com/sciencebot/pos/[modulo]/` — si hay commits después de esa fecha que tocan lógica de negocio (no solo refactors triviales), el spec probablemente está desactualizado.
2. Compara puntualmente: reglas de negocio (`RN-*`) y requerimientos (`RF-*`) del spec contra lo que el código realmente hace hoy (servicios, validaciones, entidades). Presta atención a: campos nuevos en la entidad no reflejados en "Modelo de Datos", endpoints nuevos no listados, reglas de permisos cambiadas.
3. Revisa también `backend/docs/_project.md` — su sección "Exclusiones Explícitas" en particular tiende a quedar obsoleta cuando una feature que antes estaba fuera de alcance se termina implementando (ya pasó con multi-sucursal, devoluciones y métodos de pago).

## 2. Actualizar

- Al editar un spec, sube el `spec-version` (patch si es un ajuste menor, minor si agregas RF/RN nuevos) y el `last-updated` a la fecha actual.
- No reescribas el spec completo: edita solo las secciones que cambiaron, manteniendo el formato existente (tablas, RN-/RF- numerados).
- Si el cambio afecta el resumen de `backend/docs/_index.md` (nuevo endpoint base, nueva tabla), actualízalo también.

## 3. `backend/docs/_roadmap.md`

Este archivo consolida, por módulo, tres columnas: **Estado actual**, **Deuda técnica / faltante**, **Próximas ideas**, más una lista general de "Valores agregados propuestos" y las fases del plan de trabajo vigente.

- Cuando termines una feature que resuelve un ítem de "Deuda técnica" o "Próximas ideas" de un módulo, muévelo o márcalo como hecho ahí mismo.
- Cuando identifiques deuda técnica nueva mientras trabajas en un módulo (ej. algo que dejaste pendiente a propósito, un caso borde no cubierto), agrégalo en la fila de ese módulo en vez de dejarlo solo mencionado en un mensaje de chat — es lo que hace que el roadmap siga siendo útil en la próxima sesión.
- No dupliques contenido entre los `spec.md` individuales y `_roadmap.md`: los specs describen el comportamiento *actual* (qué hace el sistema hoy), el roadmap describe *huecos y planes* (qué falta, qué se rompe, qué se podría agregar).
