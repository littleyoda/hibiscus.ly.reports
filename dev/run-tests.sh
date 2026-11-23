#!/bin/bash
# Uebersetzt src/ und test/ mit javac und startet Tests ohne Apache Ant.
# Aufruf: dev/run-tests.sh                      -> CoreTests (alle registrierten Suiten)
#         dev/run-tests.sh <Klasse> [<Klasse>...] -> static run() jeder Klasse, z. B.
#         dev/run-tests.sh de.open4me.hibiscus.reports.mcp.McpJsonRpcHandlerTests
set -e
source "$(dirname "$0")/env.sh"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
CP="$(compile_cp)"
"$JAVAC" -nowarn -encoding UTF-8 --release 17 -cp "$CP" -d "$OUT/classes" $(find "$REPO_DIR/src" -name '*.java')
"$JAVAC" -nowarn -encoding UTF-8 --release 17 -cp "$CP:$OUT/classes" -d "$OUT/test" \
  $(find "$REPO_DIR/test" -name '*.java') "$DEV_DIR/SelectedTests.java"
cp -R "$REPO_DIR/src/help" "$OUT/classes/help" 2>/dev/null || true
cd "$REPO_DIR"
if [ $# -eq 0 ]; then
  "$JAVA" -cp "$CP:$OUT/classes:$OUT/test" de.open4me.hibiscus.reports.CoreTests
else
  "$JAVA" -cp "$CP:$OUT/classes:$OUT/test" SelectedTests "$@"
fi
