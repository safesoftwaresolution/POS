#!/bin/bash
# ============================================================================
# init.sh - Validacion del entorno para el arnes de agentes IA (paridad con init.ps1)
# USO:  bash .ai/init.sh [--skip-tests] [--frontend]
#   --skip-tests  no ejecuta la suite de Maven (validacion rapida del arnes y los docs)
#   --frontend    ejecuta ademas "npm run lint" en frontend/
# EXIT CODES: 0 = entorno sano | 1 = error critico, el agente DEBE detenerse
# ============================================================================

SKIP_TESTS=0
CHECK_FRONTEND=0
for arg in "$@"; do
    case "$arg" in
        --skip-tests) SKIP_TESTS=1 ;;
        --frontend)   CHECK_FRONTEND=1 ;;
    esac
done

# Funciona desde cualquier directorio: se ancla a la raiz del repo (padre de .ai/)
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

ERRORS=0
WARNINGS=0
ok()   { echo "[OK]  $1"; }
fail() { echo "[XX]  $1"; ERRORS=$((ERRORS + 1)); }
warn() { echo "[!!]  $1"; WARNINGS=$((WARNINGS + 1)); }
info() { echo "[ii]  $1"; }

BACKEND_DIR="."
[ -f "backend/pom.xml" ] && BACKEND_DIR="backend"
DOC_BASE="docs"
[ -d "$BACKEND_DIR/docs" ] && DOC_BASE="$BACKEND_DIR/docs"

echo "============================================"
echo "  Harness Init - Validacion de Entorno"
echo "============================================"

# ---------------------------------------------------------------- Fase 1
info "Fase 1: Verificando estructura del arnes..."
for f in .ai/agents.md .ai/init.sh .ai/init.ps1 .ai/features.json .ai/progress/history.md \
         .ai/roles/leader.md .ai/roles/implementer.md .ai/roles/reviewer.md .ai/roles/committer.md; do
    if [ -f "$f" ]; then ok "Encontrado: $f"; else fail "Falta archivo critico: $f"; fi
done
echo

# ---------------------------------------------------------------- Fase 2
info "Fase 2: Verificando suite de documentacion (SSOT)..."
for f in _index.md _project.md _architecture.md _conventions.md _security.md _database-schema.md \
         _deployment.md _glossary.md _roadmap.md; do
    if [ -f "$DOC_BASE/$f" ]; then ok "Documento global verificado: $DOC_BASE/$f"; else fail "Falta documento global obligatorio: $DOC_BASE/$f"; fi
done

# La lista de modulos se DERIVA del codigo: un modulo es un directorio de com/sciencebot/pos con subcarpeta internal/.
# Asi un modulo nuevo sin spec (o un spec huerfano) rompe la validacion en vez de pasar inadvertido.
SRC_BASE="$BACKEND_DIR/src/main/java/com/sciencebot/pos"
MODULES=()
if [ -d "$SRC_BASE" ]; then
    for d in "$SRC_BASE"/*/; do
        [ -d "${d}internal" ] && MODULES+=("$(basename "$d")")
    done
fi
if [ ${#MODULES[@]} -eq 0 ]; then
    fail "No se detectaron modulos en $SRC_BASE"
fi
for mod in "${MODULES[@]}"; do
    if [ -f "$DOC_BASE/modules/$mod/spec.md" ]; then ok "Especificacion de modulo verificada: $DOC_BASE/modules/$mod/spec.md"; else fail "Falta especificacion del modulo: $DOC_BASE/modules/$mod/spec.md"; fi
done
if [ -d "$DOC_BASE/modules" ]; then
    for d in "$DOC_BASE"/modules/*/; do
        name="$(basename "$d")"
        [ -d "$SRC_BASE/$name/internal" ] || warn "Spec huerfano (no hay modulo en el codigo): $DOC_BASE/modules/$name"
    done
fi
echo

# ---------------------------------------------------------------- Fase 3
info "Fase 3: Verificando estructura del proyecto..."
if [ -f "$BACKEND_DIR/pom.xml" ]; then ok "Encontrado: $BACKEND_DIR/pom.xml"; else fail "Falta elemento del proyecto: pom.xml"; fi
if [ -d "$BACKEND_DIR/src" ]; then ok "Encontrado: $BACKEND_DIR/src/"; else fail "Falta elemento del proyecto: src/"; fi
if [ -d "$BACKEND_DIR/src/main" ]; then ok "Directorio $BACKEND_DIR/src/main/ existe"; else warn "Directorio $BACKEND_DIR/src/main/ no encontrado"; fi
if [ -d "$BACKEND_DIR/src/test" ]; then ok "Directorio $BACKEND_DIR/src/test/ existe"; else warn "Directorio $BACKEND_DIR/src/test/ no encontrado"; fi
echo

# ---------------------------------------------------------------- Fase 4
info "Fase 4: Verificando dependencias del sistema..."
if command -v java >/dev/null 2>&1; then ok "Java disponible: $(java -version 2>&1 | head -n 1)"; else fail "Java no encontrado en PATH"; fi

MVN_CMD=""
if [ -f "$BACKEND_DIR/mvnw" ]; then
    ok "Maven Wrapper disponible ($BACKEND_DIR/mvnw)"
    MVN_CMD="./mvnw"
elif command -v mvn >/dev/null 2>&1; then
    ok "Maven global disponible: $(mvn --version 2>&1 | head -n 1)"
    MVN_CMD="mvn"
else
    fail "Ni Maven Wrapper ni Maven global encontrados"
fi
if command -v git >/dev/null 2>&1; then ok "Git disponible: $(git --version)"; else warn "Git no encontrado"; fi
if command -v docker >/dev/null 2>&1; then ok "Docker disponible: $(docker --version | head -n 1)"; else warn "Docker no encontrado"; fi
echo

# ---------------------------------------------------------------- Fase 5
info "Fase 5: Validando features.json..."
if [ -f ".ai/features.json" ]; then
    if command -v node >/dev/null 2>&1; then
        if node -e 'const f=JSON.parse(require("fs").readFileSync(".ai/features.json","utf8")); const a=f.features||[]; console.log("[ii]  Tareas pendientes: "+a.filter(x=>x.status==="pending").length+" | Completadas: "+a.filter(x=>x.status==="done").length)' 2>/dev/null; then
            ok "features.json es JSON valido"
        else
            fail "features.json contiene JSON invalido"
        fi
    else
        warn "node no disponible: no se valida el JSON de features.json"
    fi
else
    fail "features.json no encontrado"
fi
echo

# ---------------------------------------------------------------- Fase 6
info "Fase 6: Ejecutando tests del proyecto..."
if [ "$SKIP_TESTS" -eq 1 ]; then
    warn "Tests omitidos (--skip-tests)"
elif [ -n "$MVN_CMD" ]; then
    TEST_LOG="$(mktemp)"
    if (cd "$BACKEND_DIR" && $MVN_CMD test -q) >"$TEST_LOG" 2>&1; then
        ok "Todos los tests pasaron"
    else
        fail "Fallaron tests en la suite (ultimas lineas de la salida):"
        tail -n 40 "$TEST_LOG"
    fi
    rm -f "$TEST_LOG"
else
    warn "No se pueden ejecutar tests (Maven no disponible)"
fi
echo

# ---------------------------------------------------------------- Fase 7 (opcional)
if [ "$CHECK_FRONTEND" -eq 1 ]; then
    info "Fase 7: Lint del frontend..."
    if [ -d "frontend/node_modules" ] && command -v npm >/dev/null 2>&1; then
        if (cd frontend && npm run lint --silent) >/dev/null 2>&1; then ok "npm run lint sin errores"; else fail "npm run lint fallo"; fi
    else
        warn "frontend/node_modules o npm no disponibles: lint omitido"
    fi
    echo
fi

echo "============================================"
if [ "$ERRORS" -gt 0 ]; then
    echo "  RESULTADO: FALLO"
    echo "  Errores: $ERRORS | Advertencias: $WARNINGS"
    echo
    echo "  AGENTE: DEBES DETENERTE. Corrige los errores antes de continuar."
    echo "============================================"
    exit 1
fi
echo "  RESULTADO: ENTORNO SANO"
echo "  Errores: 0 | Advertencias: $WARNINGS"
echo "============================================"
exit 0
