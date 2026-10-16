# ============================================================================
# init.ps1 — Script de validacion del entorno para el arnes de agentes IA
# ============================================================================
# USO: powershell -ExecutionPolicy Bypass -File .ai/init.ps1 [-SkipTests] [-Frontend]
#   -SkipTests  no ejecuta la suite de Maven (validacion rapida del arnes y los docs)
#   -Frontend   ejecuta ademas "npm run lint" en frontend/
# Paridad con init.sh: ambos scripts hacen las mismas fases y devuelven el mismo veredicto.
# EXIT CODES:
#   0  — Entorno sano, listo para trabajar
#   1  — Error critico, el agente DEBE detenerse
# ============================================================================

param(
    [switch]$SkipTests,
    [switch]$Frontend
)

$ErrorActionPreference = "Continue"

# Funciona desde cualquier directorio: se ancla a la raiz del repo (padre de .ai/)
Set-Location (Split-Path -Parent $PSScriptRoot)

$script:Errors = 0
$script:Warnings = 0

function Log-Ok($msg)   { Write-Host "[OK]  $msg" -ForegroundColor Green }
function Log-Fail($msg)  { Write-Host "[XX]  $msg" -ForegroundColor Red; $script:Errors++ }
function Log-Warn($msg)  { Write-Host "[!!]  $msg" -ForegroundColor Yellow; $script:Warnings++ }
function Log-Info($msg)  { Write-Host "[ii]  $msg" -ForegroundColor Cyan }

Write-Host ""
Write-Host "============================================"
Write-Host "  Harness Init - Validacion de Entorno"
Write-Host "============================================"
Write-Host ""

# Detectar subdirectorio backend si existe
$backendDir = if (Test-Path "backend/pom.xml") { "backend" } else { "." }
$docBase = if (Test-Path "$backendDir/docs") { "$backendDir/docs" } else { "docs" }

# ─────────────────────────────────────────────
# FASE 1: Verificar estructura del arnes
# ─────────────────────────────────────────────
Log-Info "Fase 1: Verificando estructura del arnes..."

$harnessFiles = @(
    ".ai/agents.md",
    ".ai/init.sh",
    ".ai/init.ps1",
    ".ai/features.json",
    ".ai/progress/history.md",
    ".ai/roles/leader.md",
    ".ai/roles/implementer.md",
    ".ai/roles/reviewer.md",
    ".ai/roles/committer.md"
)

foreach ($file in $harnessFiles) {
    if (Test-Path $file) {
        Log-Ok "Encontrado: $file"
    } else {
        Log-Fail "Falta archivo critico: $file"
    }
}

Write-Host ""

# ─────────────────────────────────────────────
# FASE 2: Verificar suite de documentacion (SSOT)
# ─────────────────────────────────────────────
Log-Info "Fase 2: Verificando suite de documentacion (SSOT)..."

$docFiles = @(
    "$docBase/_index.md",
    "$docBase/_project.md",
    "$docBase/_architecture.md",
    "$docBase/_conventions.md",
    "$docBase/_security.md",
    "$docBase/_database-schema.md",
    "$docBase/_deployment.md",
    "$docBase/_glossary.md",
    "$docBase/_roadmap.md"
)

foreach ($doc in $docFiles) {
    if (Test-Path $doc) {
        Log-Ok "Documento global verificado: $doc"
    } else {
        Log-Fail "Falta documento global obligatorio: $doc"
    }
}

# La lista de modulos se DERIVA del codigo: un modulo es un directorio de com/sciencebot/pos con subcarpeta internal/.
# Asi un modulo nuevo sin spec (o un spec huerfano) rompe la validacion en vez de pasar inadvertido.
$srcBase = "$backendDir/src/main/java/com/sciencebot/pos"
$modules = @()
if (Test-Path $srcBase) {
    $modules = @(Get-ChildItem $srcBase -Directory | Where-Object { Test-Path (Join-Path $_.FullName "internal") } | ForEach-Object { $_.Name })
}
if ($modules.Count -eq 0) {
    Log-Fail "No se detectaron modulos en $srcBase"
}
foreach ($mod in $modules) {
    $specPath = "$docBase/modules/$mod/spec.md"
    if (Test-Path $specPath) {
        Log-Ok "Especificacion de modulo verificada: $specPath"
    } else {
        Log-Fail "Falta especificacion del modulo: $specPath"
    }
}
if (Test-Path "$docBase/modules") {
    foreach ($dir in (Get-ChildItem "$docBase/modules" -Directory)) {
        if (-not (Test-Path "$srcBase/$($dir.Name)/internal")) {
            Log-Warn "Spec huerfano (no hay modulo en el codigo): $docBase/modules/$($dir.Name)"
        }
    }
}

Write-Host ""

# ─────────────────────────────────────────────
# FASE 3: Verificar estructura del proyecto
# ─────────────────────────────────────────────
Log-Info "Fase 3: Verificando estructura del proyecto..."

if (Test-Path "$backendDir/pom.xml") {
    Log-Ok "Encontrado: $backendDir/pom.xml"
} else {
    Log-Fail "Falta elemento del proyecto: pom.xml"
}

if (Test-Path "$backendDir/src") {
    Log-Ok "Encontrado: $backendDir/src/"
} else {
    Log-Fail "Falta elemento del proyecto: src/"
}

if (Test-Path "$backendDir/src/main") {
    Log-Ok "Directorio $backendDir/src/main/ existe"
} else {
    Log-Warn "Directorio $backendDir/src/main/ no encontrado"
}

if (Test-Path "$backendDir/src/test") {
    Log-Ok "Directorio $backendDir/src/test/ existe"
} else {
    Log-Warn "Directorio $backendDir/src/test/ no encontrado"
}

Write-Host ""

# ─────────────────────────────────────────────
# FASE 4: Verificar dependencias del sistema
# ─────────────────────────────────────────────
Log-Info "Fase 4: Verificando dependencias del sistema..."

try {
    $javaVersion = & java -version 2>&1 | Select-Object -First 1
    Log-Ok "Java disponible: $javaVersion"
} catch {
    Log-Fail "Java no encontrado en PATH"
}

$mvnCmd = $null
if (Test-Path "$backendDir/mvnw.cmd") {
    Log-Ok "Maven Wrapper disponible ($backendDir/mvnw.cmd)"
    $mvnCmd = (Resolve-Path "$backendDir/mvnw.cmd").Path
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnVersion = & mvn --version 2>&1 | Select-Object -First 1
    Log-Ok "Maven global disponible: $mvnVersion"
    $mvnCmd = "mvn"
} else {
    Log-Fail "Ni Maven Wrapper ni Maven global encontrados"
}

if (Get-Command git -ErrorAction SilentlyContinue) {
    $gitVersion = & git --version 2>&1
    Log-Ok "Git disponible: $gitVersion"
} else {
    Log-Warn "Git no encontrado"
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    $dockerVersion = & docker --version 2>&1 | Select-Object -First 1
    Log-Ok "Docker disponible: $dockerVersion"
} else {
    Log-Warn "Docker no encontrado"
}

Write-Host ""

# ─────────────────────────────────────────────
# FASE 5: Validar features.json
# ─────────────────────────────────────────────
Log-Info "Fase 5: Validando features.json..."

if (Test-Path ".ai/features.json") {
    try {
        $featuresContent = Get-Content ".ai/features.json" -Raw | ConvertFrom-Json
        Log-Ok "features.json es JSON valido"

        $pending = @($featuresContent.features | Where-Object { $_.status -eq "pending" }).Count
        $done = @($featuresContent.features | Where-Object { $_.status -eq "done" }).Count
        Log-Info "Tareas pendientes: $pending | Completadas: $done"
    } catch {
        Log-Fail "features.json contiene JSON invalido: $_"
    }
} else {
    Log-Fail "features.json no encontrado"
}

Write-Host ""

# ─────────────────────────────────────────────
# FASE 6: Ejecutar tests del proyecto
# ─────────────────────────────────────────────
Log-Info "Fase 6: Ejecutando tests del proyecto..."

if ($SkipTests) {
    Log-Warn "Tests omitidos (-SkipTests)"
} elseif ($mvnCmd) {
    try {
        Push-Location $backendDir
        $testOutput = & $mvnCmd test -q 2>&1
        $exitCode = $LASTEXITCODE
        Pop-Location
        if ($exitCode -eq 0) {
            Log-Ok "Todos los tests pasaron"
        } else {
            Log-Fail "Fallaron tests en la suite (ultimas lineas de la salida):"
            $testOutput | Select-Object -Last 40 | ForEach-Object { Write-Host "      $_" }
        }
    } catch {
        Pop-Location -ErrorAction SilentlyContinue
        Log-Warn "Error ejecutando Maven: $_"
    }
} else {
    Log-Warn "No se pueden ejecutar tests (Maven no disponible)"
}

Write-Host ""

# ─────────────────────────────────────────────
# FASE 7 (opcional): lint del frontend
# ─────────────────────────────────────────────
if ($Frontend) {
    Log-Info "Fase 7: Lint del frontend..."
    if ((Test-Path "frontend/node_modules") -and (Get-Command npm -ErrorAction SilentlyContinue)) {
        Push-Location "frontend"
        & npm run lint --silent *> $null
        $lintExit = $LASTEXITCODE
        Pop-Location
        if ($lintExit -eq 0) { Log-Ok "npm run lint sin errores" } else { Log-Fail "npm run lint fallo" }
    } else {
        Log-Warn "frontend/node_modules o npm no disponibles: lint omitido"
    }
    Write-Host ""
}

# ─────────────────────────────────────────────
# RESULTADO FINAL
# ─────────────────────────────────────────────
Write-Host "============================================"
if ($script:Errors -gt 0) {
    Write-Host "  RESULTADO: FALLO" -ForegroundColor Red
    Write-Host "  Errores: $($script:Errors) | Advertencias: $($script:Warnings)" -ForegroundColor Red
    Write-Host ""
    Write-Host "  AGENTE: DEBES DETENERTE." -ForegroundColor Red
    Write-Host "  Corrige los errores antes de continuar." -ForegroundColor Red
    Write-Host "============================================"
    Write-Host ""
    exit 1
} else {
    Write-Host "  RESULTADO: ENTORNO SANO" -ForegroundColor Green
    Write-Host "  Errores: 0 | Advertencias: $($script:Warnings)" -ForegroundColor Green
    Write-Host ""
    Write-Host "  AGENTE: Puedes proceder con la tarea." -ForegroundColor Green
    Write-Host "============================================"
    Write-Host ""
    exit 0
}