# Einstellungen und Helfer der isolierten Test-Instanz. Wird per "source" eingebunden.
# Alle Werte sind Testwerte; keiner gehoert zu einer echten Installation.
ISO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${ISO_RUN_DIR:-$ISO_DIR/run}"     # Laufzeitdaten (Arbeitsverzeichnis, Home, Logs); in .gitignore
ISO_CFG="$RUN_DIR/work/cfg/de.open4me.hibiscus.reports.mcp.McpSettings.properties"
# Eigener MCP-Port, damit eine laufende echte Instanz unberuehrt bleibt. Ohne ISO_PORT gilt der Port, mit dem
# build.sh die Instanz gebaut hat, sonst 37691.
if [ -z "${ISO_PORT:-}" ] && [ -f "$ISO_CFG" ]; then
  ISO_PORT="$(sed -n 's/^port=//p' "$ISO_CFG")"
fi
ISO_PORT="${ISO_PORT:-37691}"
ISO_TOKEN="isolated-test-token-4711"       # MCP-Passwort der Test-Instanz
ISO_MASTER_PW="isolated-master-4711"       # Jameica-Masterpasswort der Test-Instanz
ISO_URL="http://127.0.0.1:$ISO_PORT/mcp"

# Laeuft die Instanz aus diesem RUN_DIR?
iso_running() { pgrep -f -- "-f $RUN_DIR/work" > /dev/null; }

# Pruefhelfer fuer die check-*.sh: ok <true|false> <Name> [<Ausgabe bei Fehler>], am Ende finish.
ISO_FAILS=0
ok() {
  if [ "$1" = "true" ]; then echo "PASS $2"; else echo "FAIL $2"; echo "     got: $3" | cut -c1-600; ISO_FAILS=$((ISO_FAILS + 1)); fi
}
finish() {
  [ "$ISO_FAILS" -eq 0 ] && echo "alle Pruefungen bestanden" || echo "$ISO_FAILS Pruefung(en) fehlgeschlagen"
  exit "$ISO_FAILS"
}
