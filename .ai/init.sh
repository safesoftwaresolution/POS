#!/bin/bash
set -e

BACKEND_DIR="."
if [ -f "backend/pom.xml" ]; then
    BACKEND_DIR="backend"
fi

DOC_BASE="docs"
if [ -d "$BACKEND_DIR/docs" ]; then
    DOC_BASE="$BACKEND_DIR/docs"
fi

echo "============================================"
echo "  Harness Init - Validacion de Entorno"
echo "============================================"

# Fase 1
for f in .ai/agents.md .ai/init.sh .ai/init.ps1 .ai/features.json .ai/progress/history.md .ai/roles/leader.md .ai/roles/implementer.md .ai/roles/reviewer.md .ai/roles/committer.md; do
    [ -f "$f" ] && echo "[OK]  $f" || (echo "[XX]  Falta $f" && exit 1)
done

# Fase 2
for f in _index.md _project.md _architecture.md _conventions.md _security.md _database-schema.md _deployment.md _glossary.md; do
    [ -f "$DOC_BASE/$f" ] && echo "[OK]  $DOC_BASE/$f" || (echo "[XX]  Falta $DOC_BASE/$f" && exit 1)
done

MODULES=("auth" "users" "products" "categories" "inventory" "purchases" "sales" "customers" "suppliers" "reports" "settings" "billing" "stores" "legal" "support")
for mod in "${MODULES[@]}"; do
    [ -f "$DOC_BASE/modules/$mod/spec.md" ] && echo "[OK]  $DOC_BASE/modules/$mod/spec.md" || (echo "[XX]  Falta $DOC_BASE/modules/$mod/spec.md" && exit 1)
done

# Fase 3 & 4 & 6
cd "$BACKEND_DIR"
./mvnw test -q
echo "============================================"
echo "  RESULTADO: ENTORNO SANO"
echo "============================================"