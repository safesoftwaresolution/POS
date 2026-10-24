# 🔧 Harness de Control para Agentes de IA

> **ESTE ARCHIVO ES EL PUNTO DE ENTRADA OBLIGATORIO.**
> Todo agente de IA que interactúe con este repositorio **DEBE** leer este documento
> completo antes de ejecutar cualquier acción.

---

> **Rutas en este monorepo.** La documentación (`docs/` en el resto de este arnés) vive en **`backend/docs/`**, y el código Java en `backend/src/main/java/com/sciencebot/pos/`. Cuando un rol o este archivo diga `docs/...`, léelo como `backend/docs/...`. `init.sh`/`init.ps1` resuelven esa ruta solos.

## Boot Sequence

### Todo agente DEBE:
1. Leer `.ai/agents.md` (este archivo).
2. Leer su archivo de rol en `.ai/roles/<rol>.md`.

### Solo el Leader DEBE además:
3. Leer `backend/docs/_index.md` (para resolver rutas a módulos).
4. Leer `.ai/features.json` (para identificar la tarea pendiente).

### Ningún agente debe:
- Leer todos los archivos de `backend/docs/` al arrancar.
- Cargar más de 4 documentos de dominio simultáneamente (ver R1).
- Leer documentos que no estén en su Context Pack.

---

## Protocolo de Validación

Dependiendo del entorno, ejecutar el script correspondiente:

```bash
# Linux / MacOS / WSL / Git Bash
bash .ai/init.sh

# Windows nativo (PowerShell)
powershell -ExecutionPolicy Bypass -File .ai/init.ps1
```

Ambos scripts son equivalentes (mismas fases, mismo veredicto) y funcionan desde cualquier directorio. Banderas: `--skip-tests` / `-SkipTests` (valida solo arnés, docs y estructura, sin correr Maven) y `--frontend` / `-Frontend` (añade `npm run lint`). La lista de módulos **se deriva del código** (cada carpeta de `com/sciencebot/pos` con `internal/`): un módulo sin `spec.md` hace fallar la validación, y un spec sin módulo da una advertencia. Un código de salida distinto de `0` significa que el arnés, los docs o los tests están rotos; la salida muestra las últimas líneas de Maven si fallaron los tests.

Si el script retorna un código distinto de `0`, el agente **DEBE**:
1. Leer la salida para identificar el problema.
2. Reportar el error en `.ai/progress/history.md`.
3. **NO continuar** hasta que el entorno pase la validación en verde.

---

## Entorno de Desarrollo Contenedorizado (Docker)

El proyecto cuenta con integración completa de Docker para el desarrollo local y producción.

### Comandos de Control de Desarrollo

Para controlar el ciclo de vida del contenedor de desarrollo, utiliza el script de utilidad disponible en `backend/`:
- **En Windows (PowerShell)**: `.\backend\docker-dev.ps1 <comando>`
- **En Linux/WSL/MacOS (Bash)**: `./backend/docker-dev.sh <comando>`

#### Comandos Disponibles:
- `start`   - Levanta el backend y la base de datos PostgreSQL en segundo plano.
- `stop`    - Detiene y remueve los contenedores de desarrollo.
- `rebuild` - Reconstruye la imagen del backend y recarga los contenedores (usar tras cambiar dependencias en `pom.xml`).
- `restart` - Reinicia los contenedores sin reconstruir.
- `logs`    - Visualiza la salida de logs en tiempo real para verificar los arranques y depurar excepciones.
- `shell`   - Abre una terminal interactiva en el contenedor del backend para inspección.
- `status`  - Muestra el estado actual de los contenedores.

### Flujo de Trabajo y Pruebas
1. **Modificación**: Edita el código de la aplicación.
2. **Reflejar cambios**:
   - El código se sincroniza mediante el volumen montado (`. -> /app`), pero `/app/target` es un volumen anónimo: el `target/` del host queda enmascarado y DevTools solo reinicia con lo que se compila **dentro del contenedor**.
   - Si no ves tus cambios, ejecuta `.\backend\docker-dev.ps1 restart`.
   - Si modificas dependencias (`pom.xml`), ejecuta `.\backend\docker-dev.ps1 rebuild`.
3. **Pruebas de Endpoints**: Realiza solicitudes a `http://localhost:8080`.


---

## Reglas Generales

### R1 — Contexto mínimo
- Leer **solo** los archivos del Context Pack asignado por el Leader.
- Máximo de archivos de documentación de dominio simultáneos en contexto: **4** (spec del módulo, `_conventions.md`, y hasta dos más según la tarea: `_architecture.md`, `_security.md`, `_database-schema.md`, otro spec). **No cuentan** los archivos de arranque (`agents.md`, tu rol, `_index.md`, `features.json`).
- Máximo de archivos de código simultáneos en contexto: **5**.

### R2 — Validación continua
- Ejecutar el script de validación **antes** y **después** de cada cambio de código.
- No commitear ni dar por finalizado código que no pase la validación en verde.

### R3 — Trazabilidad obligatoria
- Cada acción relevante debe registrarse en `.ai/progress/history.md`.
- Formato: `[YYYY-MM-DD HH:MM] [ROL] — Descripción breve del resultado`.
- Nunca borrar entradas anteriores del historial (solo se corrige formato roto, y se anota en una entrada nueva).
- Si el trabajo lo hizo un agente o una persona **fuera** del protocolo, se registra igual con una entrada retroactiva marcada como tal (rol `[EXTERNO]`).

### R4 — Roles definidos

| Rol | Archivo | Responsabilidad principal |
|---|---|---|
| **Leader** | `.ai/roles/leader.md` | Orquestar, priorizar, delegar, construir Context Packs |
| **Implementer** | `.ai/roles/implementer.md` | Escribir código y tests |
| **Reviewer** | `.ai/roles/reviewer.md` | Revisar, validar, aprobar/rechazar |
| **Committer** | `.ai/roles/committer.md` | Agrupar el diff en commits atómicos y ejecutarlos tras aprobación (sin push) |

### R5 — Comunicación explícita
- Los agentes **NUNCA** asumen lo que otro agente hizo.
- Toda decisión y resultado se documenta en `progress/history.md`.

### R6 — Auto-mejora controlada
- Solo el rol **Reviewer** puede modificar las **reglas del arnés**: `.ai/agents.md`, `.ai/roles/*` e `.ai/init.*`.
- Los archivos de **estado** sí los actualizan los roles que lo indican: `features.json` (Leader) y `progress/history.md` (todos, solo añadiendo).
- Cualquier cambio a las reglas del arnés debe documentarse con motivo y diff en el historial.
- `.claude/agents/*.md` son envoltorios de estos roles para Claude Code: la fuente de verdad es `.ai/roles/*.md`; si difieren, manda `.ai`.

### R7 — Adherencia a la SSOT (Single Source of Truth)
- Todo desarrollo debe respetar los specs modulares en `backend/docs/modules/<modulo>/spec.md` y las convenciones de Monolito Modular de `backend/docs/_conventions.md`.
- Si se detecta discrepancia entre documentación y código → detenerse e informar.

### R8 — Context Pack obligatorio
- El Leader **DEBE** incluir un Context Pack explícito al delegar una tarea.
- El agente delegado solo lee los archivos listados en el Context Pack.

---

## Estructura del Arnés y Código

```
.ai/
├── agents.md              ← Punto de entrada (este archivo)
├── init.sh / init.ps1     ← Scripts de validación del entorno
├── features.json          ← Backlog de tareas
├── roles/
│   ├── leader.md          ← Instrucciones del orquestador
│   ├── implementer.md     ← Instrucciones del implementador
│   ├── reviewer.md        ← Instrucciones del revisor
│   └── committer.md       ← Instrucciones del integrador de commits
└── progress/
    └── history.md         ← Registro cronológico de acciones

backend/docs/
├── _index.md              ← Router de navegación
├── _project.md            ← Contexto mínimo del proyecto
├── _architecture.md       ← Capas y patrones (Monolito Modular)
├── _conventions.md        ← Estándares y estructura de paquetes de Java
├── _security.md           ← JWT, BCrypt, RBAC
├── _database-schema.md    ← Diagrama ER y convenciones
├── _deployment.md         ← Variables de entorno y despliegue
├── _glossary.md           ← Terminología del dominio
├── _roadmap.md            ← Estado por módulo, deuda técnica y plan
└── (modules/ abajo)
└── modules/
    └── [modulo]/spec.md   ← Un spec por módulo del código (lo exige init.*)
```

La estructura de paquetes Java debe seguir el Vertical Split en `src/main/java/com/sciencebot/pos/[modulo]/` con subpaquetes `internal/` para lógica privada, siguiendo las convenciones de `backend/docs/_conventions.md`. El frontend (`frontend/`) no tiene rol propio en el arnés: lo cubre el Leader y se valida con `--frontend`.

---

## Flujo de Trabajo Estándar

```
┌─────────┐     ┌──────────────┐     ┌────────────┐     ┌────────────┐
│ Leader   │────▶│ Implementer  │────▶│  Reviewer   │────▶│ Committer  │
│ Lee task │     │ Lee Context  │     │  Valida     │     │ Propone el │
│ Construye│     │ Pack del     │     │  Aprueba/   │     │ plan de    │
│ Context  │     │ Leader       │     │  Rechaza    │     │ commits y  │
│ Pack     │     │ Escribe code │     │             │     │ espera OK  │
│ Delega   │     │ Corre init   │     │             │     │ humano     │
└─────────┘     └──────────────┘     └────────────┘     └────────────┘
     ▲                                      │
     └──────────────────────────────────────┘
              (ciclo hasta aprobación)
```

El Committer nunca hace `push` ni reescribe historia, y solo commitea con aprobación humana explícita.