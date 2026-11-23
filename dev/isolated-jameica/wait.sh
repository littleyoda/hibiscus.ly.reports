#!/bin/bash
# Wartet, bis die Test-Instanz ihre Testdaten angelegt hat (hoechstens etwa 80 Sekunden).
# Im Server-Modus schreibt Jameica sein Log nach work/jameica.log, mit Oberflaeche auch auf stdout.
source "$(dirname "$0")/iso-env.sh"
LOGS=("$RUN_DIR/work/jameica.log" "$RUN_DIR"/jameica-*.log)
for i in $(seq 1 40); do
  if grep -q "TESTDATA: \(created\|already present\)" "${LOGS[@]}" 2>/dev/null; then
    echo "bereit"; exit 0
  fi
  if grep -q "TESTDATA: gave up" "${LOGS[@]}" 2>/dev/null; then
    echo "Testdaten konnten nicht angelegt werden, siehe $RUN_DIR/work/jameica.log" >&2; exit 1
  fi
  sleep 2
done
echo "Zeitueberschreitung, siehe $RUN_DIR/work/jameica.log" >&2; exit 1
