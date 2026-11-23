#!/bin/bash
# Baut ein frisches Arbeitsverzeichnis fuer die isolierte Test-Instanz:
# Kopie des Hibiscus-Plugins, hibiscus.ly.reports aus diesem Checkout, Testdaten-Plugin, MCP-Einstellungen.
# HIBISCUS_DIR wird nur gelesen. Vorher eine laufende Test-Instanz beenden: der alte Stand wird geloescht.
set -e
source "$(dirname "$0")/../env.sh"
source "$(dirname "$0")/iso-env.sh"

require_hibiscus
if iso_running; then
  echo "Die Test-Instanz laeuft noch; erst beenden." >&2; exit 1
fi

WORK="$RUN_DIR/work"
BUILD="$RUN_DIR/build"
rm -rf "$WORK" "$BUILD" "$RUN_DIR/home"
mkdir -p "$WORK/plugins" "$WORK/cfg" "$BUILD/reports" "$BUILD/testdata" "$RUN_DIR/home"

cp -R "$HIBISCUS_DIR" "$WORK/plugins/hibiscus"
CP="$(compile_cp)"

# hibiscus.ly.reports aus dem aktuellen Checkout
"$JAVAC" -nowarn -encoding UTF-8 --release 17 -cp "$CP" -d "$BUILD/reports" $(find "$REPO_DIR/src" -name '*.java')
cp -R "$REPO_DIR/src/help" "$BUILD/reports/help"
P="$WORK/plugins/hibiscus.ly.reports"
mkdir -p "$P/lib"
(cd "$BUILD/reports" && "$JAR" cf "$P/hibiscus.ly.reports.jar" .)
cp "$REPO_DIR/plugin.xml" "$P/"
cp -R "$REPO_DIR/lib/." "$P/lib/"

# Wegwerf-Plugin mit erfundenen Testdaten
"$JAVAC" -nowarn -encoding UTF-8 --release 17 -cp "$CP" -d "$BUILD/testdata" $(find "$ISO_DIR/testdata-src" -name '*.java')
T="$WORK/plugins/testdata"
mkdir -p "$T"
(cd "$BUILD/testdata" && "$JAR" cf "$T/testdata.jar" .)
cp "$ISO_DIR/testdata-plugin.xml" "$T/plugin.xml"

# MCP-Einstellungen: eigener Port, Loeschen erlaubt, bekanntes Test-Passwort
# (der Schluessel "token" ist das alte Format und wird beim Start in die Wallet uebernommen).
cat > "$WORK/cfg/de.open4me.hibiscus.reports.mcp.McpSettings.properties" <<EOF
enabled=true
localNetworkEnabled=false
port=$ISO_PORT
writeEnabled=false
deleteEnabled=true
token=$ISO_TOKEN
EOF
echo "gebaut: $WORK"
