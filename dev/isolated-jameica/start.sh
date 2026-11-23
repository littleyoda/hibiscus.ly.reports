#!/bin/bash
# Startet die isolierte Test-Instanz. Aufruf: start.sh server|gui
# Eigenes user.home (die echte ~/.jameica.properties wird weder gelesen noch geschrieben),
# eigenes Arbeitsverzeichnis, Testdaten werden beim ersten Start angelegt.
source "$(dirname "$0")/../env.sh"
source "$(dirname "$0")/iso-env.sh"
MODE="${1:-server}"
: "${JAMEICA_CWD:?JAMEICA_CWD setzen (Startverzeichnis von Jameica)}"
[ -d "$RUN_DIR/work" ] || { echo "Erst build.sh ausfuehren." >&2; exit 1; }
iso_running && { echo "Die Test-Instanz laeuft bereits." >&2; exit 1; }
# Alte Logs entfernen, damit wait.sh nur Meldungen dieses Starts sieht.
rm -f "$RUN_DIR"/jameica-*.log "$RUN_DIR/work/jameica.log"
ARGS=(-Duser.home="$RUN_DIR/home" -cp "$JAMEICA_CP")
if [ "$MODE" = "server" ]; then
  MAIN_ARGS=(-d -n -f "$RUN_DIR/work" -p "$ISO_MASTER_PW")
else
  [ "$(uname)" = "Darwin" ] && ARGS+=(-XstartOnFirstThread -Xdock:name=Jameica-TEST)
  MAIN_ARGS=(-f "$RUN_DIR/work" -p "$ISO_MASTER_PW")
fi
cd "$JAMEICA_CWD"
exec "$JAVA" "${ARGS[@]}" de.willuhn.jameica.Main "${MAIN_ARGS[@]}" > "$RUN_DIR/jameica-$MODE.log" 2>&1
