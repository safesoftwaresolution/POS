# 🔧 Harness de Control para Agentes de IA

> **ESTE ARCHIVO ES EL PUNTO DE ENTRADA OBLIGATORIO.**
> Todo agente de IA que interactúe con este repositorio **DEBE** leer este documento
> completo antes de ejecutar cualquier acción.

---

## Boot Sequence

### Todo agente DEBE:
1. Leer `.ai/agents.md` (este archivo).
2. Leer su archivo de rol en `.ai/roles/<rol>.md`.

### Solo el Leader DEBE además:
3. Leer `docs/_index.md` (para resolver rutas a módulos).
4. Leer `.ai/features.json` (para identificar la tarea pendiente).

### Ningún agente debe:
- Leer todos los archivos de `docs/` al arrancar.
- Cargar más de 3 documentos de contexto simultáneamente.
- Leer documentos que no estén en su Context Pack.

---

## Protocolo de Validación

Dependiendo del entorno, ejecutar el script correspondiente:

```bash
# Linux / MacOS / WSL
bash .ai/init.sh

# Windows nativo (PowerShell)
powershell -ExecutionPolicy Bypass -File .ai/init.ps1
```

Si el script retorna un código distinto de `0`, el agente **DEBE**:
1. Leer la salida para identificar el problema.
2. Reportar el error en `.ai/progress/history.md`.
3. **NO continuar** hasta que el entorno pase la validación en verde.

---

## Entorno de Desarrollo Contenedorizado (Docker)

El proyecto cuenta con integración completa de Docker para el desarrollo local y producción.

### Comandos de Control de Desarrollo

Para controlar el ciclo de vida del contenedor de desarrollo, utiliza el script de utilidad disponible en la raíz:
- **En Windows (PowerShell)**: `.\docker-dev.ps1 <comando>`
- **En Linux/WSL/MacOS (Bash)**: `./docker-dev.sh <comando>`

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
   - Los cambios de código se sincronizan mediante el volumen montado (`. -> /app`). Spring Boot DevTools reiniciará la app si el IDE compila el código en `target/classes`.
   - Si no estás usando un IDE con compilación automática, ejecuta `.\docker-dev.ps1 restart` para reiniciar los servicios.
   - Si modificas dependencias (`pom.xml`), debes ejecutar `.\docker-dev.ps1 rebuild` para descargar las dependencias nuevas y reconstruir la imagen.
3. **Pruebas de Endpoints**: Realiza solicitudes a `http://localhost:8080`.


---

## Reglas Generales

### R1 — Contexto mínimo
- Leer **solo** los archivos del Context Pack asignado por el Leader.
- Máximo de archivos de documentación simultáneos en contexto: **3**.
- Máximo de archivos de código simultáneos en contexto: **5**.

### R2 — Validación continua
- Ejecutar el script de validación **antes** y **después** de cada cambio de código.
- No commitear ni dar por finalizado código que no pase la validación en verde.

### R3 — Trazabilidad obligatoria
- Cada acción relevante debe registrarse en `.ai/progress/history.md`.
- Formato: `[YYYY-MM-DD HH:MM] [ROL] — Descripción breve del resultado`.
- Nunca borrar entradas anteriores del historial.

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
- Solo el rol **Reviewer** puede modificar archivos dentro de `.ai/`.
- Cualquier cambio al arnés debe documentarse con motivo y diff en el historial.

### R7 — Adherencia a la SSOT (Single Source of Truth)
- Todo desarrollo debe respetar los specs modulares en `docs/modules/<modulo>/spec.md` y las convenciones de Monolito Modular de `docs/_conventions.md`.
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

docs/
├── _index.md              ← Router de navegación
├── _project.md            ← Contexto mínimo del proyecto
├── _architecture.md       ← Capas y patrones (Monolito Modular)
├── _conventions.md        ← Estándares y estructura de paquetes de Java
├── _security.md           ← JWT, BCrypt, RBAC
├── _database-schema.md    ← Diagrama ER y convenciones
├── _deployment.md         ← Variables de entorno
├── _glossary.md           ← Terminología del dominio
└── modules/
    ├── [modulo]/spec.md   ← Specs modulares (auth, sales, products...)
```

La estructura de paquetes Java debe seguir el Vertical Split en `src/main/java/com/sciencebot/pos/[modulo]/` con subpaquetes `internal/` para lógica privada, siguiendo las convenciones de `docs/_conventions.md`.

---

## Flujo de Trabajo Estándar

```
┌─────────┐     ┌──────────────┐     ┌────────────┐
│ Leader   │────▶│ Implementer  │────▶│  Reviewer   │
│ Lee task │     │ Lee Context  │     │  Valida     │
│ Construye│     │ Pack del     │     │  Aprueba/   │
│ Context  │     │ Leader       │     │  Rechaza    │
│ Pack     │     │ Escribe code │     │             │
│ Delega   │     │ Corre init   │     │             │
└─────────┘     └──────────────┘     └────────────┘
     ▲                                      │
     └──────────────────────────────────────┘
              (ciclo hasta aprobación)
```
