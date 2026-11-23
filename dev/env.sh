# Gemeinsame Einstellungen fuer die Skripte unter dev/. Wird per "source" eingebunden.
#
#   JAMEICA_DIR   Jameica-Installation. Ohne JAMEICA_CP wird das Layout des Nix-Pakets erwartet
#                 (share/java/jameica.jar und share/jameica-*/<jars>).
#   HIBISCUS_DIR  Hibiscus-Plugin-Ordner (enthaelt hibiscus.jar und lib/), z. B. aus dem
#                 Jameica-Arbeitsverzeichnis. Wird nur gelesen; noetig zum Uebersetzen.
#   JAVA_HOME     optional: JDK (17 oder neuer); sonst java/javac/jar aus PATH.
#   JAMEICA_CP    optional: Classpath von Jameica, falls nicht im Nix-Layout.
#   JAMEICA_CWD   optional: Arbeitsverzeichnis beim Start von Jameica (Nix: share/java); nur fuer start.sh.
#
# Pfade mit Leerzeichen werden nicht unterstuetzt.

DEV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$DEV_DIR/.." && pwd)"

: "${JAMEICA_DIR:?JAMEICA_DIR setzen (Jameica-Installation)}"

BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
JAVA="${BIN}java"
JAVAC="${BIN}javac"
JAR="${BIN}jar"

if [ -z "${JAMEICA_CP:-}" ]; then
  [ -f "$JAMEICA_DIR/share/java/jameica.jar" ] || {
    echo "JAMEICA_DIR ist kein Nix-Layout; JAMEICA_CP und JAMEICA_CWD setzen" >&2; exit 1; }
  JAMEICA_CP="$JAMEICA_DIR/share/java/jameica.jar"
  for jar in $(LC_ALL=C ls "$JAMEICA_DIR"/share/jameica-*/*.jar); do JAMEICA_CP="$JAMEICA_CP:$jar"; done
  : "${JAMEICA_CWD:=$JAMEICA_DIR/share/java}"
fi

require_hibiscus() {
  : "${HIBISCUS_DIR:?HIBISCUS_DIR setzen (Hibiscus-Plugin-Ordner mit hibiscus.jar)}"
  [ -f "$HIBISCUS_DIR/hibiscus.jar" ] || { echo "Kein hibiscus.jar in $HIBISCUS_DIR" >&2; exit 1; }
}

# Classpath zum Uebersetzen von Plugin und Tests, wie compile.path in build/build.xml: die Jars aus lib/ des
# Repos zuerst, damit ihre Versionen gelten; von Hibiscus nur hibiscus.jar, swtchart und obantoo
# (Hibiscus bringt aeltere Versionen von z. B. jackson, guava und slf4j mit).
compile_cp() {
  require_hibiscus
  local cp="" jar
  for jar in $(find "$REPO_DIR/lib" -name '*.jar' | LC_ALL=C sort); do cp="$cp$jar:"; done
  cp="$cp$JAMEICA_CP:$HIBISCUS_DIR/hibiscus.jar"
  for jar in $(LC_ALL=C ls "$HIBISCUS_DIR"/lib/swtchart/*.jar "$HIBISCUS_DIR"/lib/obantoo-bin-*.jar 2>/dev/null); do
    cp="$cp:$jar"
  done
  echo "$cp"
}
