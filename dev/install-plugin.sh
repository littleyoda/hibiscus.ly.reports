#!/bin/bash
# Baut hibiscus.ly.reports.jar aus diesem Checkout und ersetzt damit das Jar eines installierten Plugins.
# Das bisherige Jar bleibt als hibiscus.ly.reports.jar.bak-<Datum> daneben liegen.
#
#   dev/install-plugin.sh <Plugin-Ordner>        z. B. ~/.jameica/plugins/hibiscus.ly.reports
#
# Bricht ab, solange eine Jameica laeuft (auch eine Test-Instanz), und wenn plugin.xml oder lib/ im Ziel
# vom Checkout abweichen; dann das Plugin von Hand aktualisieren.
set -euo pipefail
source "$(dirname "$0")/env.sh"

TARGET="${1:?Plugin-Ordner angeben, z. B. ~/.jameica/plugins/hibiscus.ly.reports}"
TARGET="${TARGET%/}"
JARNAME=hibiscus.ly.reports.jar
if [ ! -f "$TARGET/$JARNAME" ] || [ ! -f "$TARGET/plugin.xml" ]; then
  echo "Kein installiertes hibiscus.ly.reports in $TARGET" >&2; exit 1
fi

jameica_running() {
  pgrep -f 'java.*(de\.willuhn\.jameica\.Main|jameica[^/ ]*\.jar)' >/dev/null
}
if jameica_running; then
  echo "Jameica laeuft noch (auch Test-Instanzen zaehlen); erst beenden." >&2; exit 1
fi

if ! cmp -s "$REPO_DIR/plugin.xml" "$TARGET/plugin.xml" || ! diff -rq "$REPO_DIR/lib" "$TARGET/lib" >/dev/null; then
  echo "plugin.xml oder lib/ in $TARGET weichen vom Checkout ab; Plugin bitte von Hand aktualisieren." >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
build_plugin_jar "$TMP/classes" "$TMP/$JARNAME"

# Waehrend des Bauens koennte Jameica gestartet worden sein.
if jameica_running; then
  echo "Jameica wurde inzwischen gestartet; nichts ersetzt." >&2; exit 1
fi

BACKUP="$TARGET/$JARNAME.bak-$(date +%Y%m%d)"
[ -e "$BACKUP" ] && BACKUP="$BACKUP-$(date +%H%M%S)"
cp -p "$TARGET/$JARNAME" "$BACKUP"
cp "$TMP/$JARNAME" "$TARGET/.$JARNAME.new"
mv "$TARGET/.$JARNAME.new" "$TARGET/$JARNAME"
cmp -s "$TMP/$JARNAME" "$TARGET/$JARNAME"

echo "installiert: $TARGET/$JARNAME (Stand $(git -C "$REPO_DIR" describe --always --dirty))"
echo "vorheriges Jar: $BACKUP"
