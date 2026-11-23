#!/bin/bash
# Nur mit der GUI-Instanz (start.sh gui), ohne Klicks: Ein zweiter Loeschaufruf bei offenem Dialog muss
# sofort mit confirmError zurueckkommen; der offene Dialog muss sich nach etwa 50 s selbst schliessen und
# "nein" melden. Den Dialog dabei NICHT bestaetigen. Beide Umsaetze muessen danach noch da sein.
# Der zweite Aufruf folgt nach einer festen Pause; bei sehr langsamem Dialogaufbau die Pause verlaengern
# (PAUSE=10). Beendet sich mit der Anzahl fehlgeschlagener Pruefungen.
BASE="$(cd "$(dirname "$0")" && pwd)"
source "$BASE/iso-env.sh"
bash "$BASE/wait.sh" > /dev/null || exit 1
OUT="$RUN_DIR/out"; mkdir -p "$OUT"

id_of() { bash "$BASE/ids.sh" | awk -F'\t' -v p="$1" '$5==p {print $1}'; }
FIRST_ID=$(id_of "Miete Maerz")
SECOND_ID=$(id_of "Rechnung 4713")
[ -n "$FIRST_ID" ] && [ -n "$SECOND_ID" ] || { echo "Testumsaetze fehlen; neu bauen (build.sh)." >&2; exit 1; }

t0=$(date +%s)
bash "$BASE/delete.sh" "$FIRST_ID" > "$OUT/first.json" 2>&1 &
FIRST=$!
sleep "${PAUSE:-5}"
t1=$(date +%s)
bash "$BASE/delete.sh" "$SECOND_ID" > "$OUT/second.json" 2>&1
t2=$(date +%s)
ok "$([ $((t2 - t1)) -le 3 ] && echo true || echo false)" "second call returns at once ($((t2 - t1)) s)" ""
ok "$(jq '.confirmed == false and (.confirmError | contains("Es wartet bereits")) and .counts.deleted == 0' "$OUT/second.json")" \
  "second call: confirmError, nothing deleted" "$(jq -c . "$OUT/second.json")"
wait $FIRST
t3=$(date +%s)
ok "$([ $((t3 - t0)) -ge 45 ] && [ $((t3 - t0)) -le 55 ] && echo true || echo false)" \
  "first call ends by timeout after about 50 s ($((t3 - t0)) s)" ""
ok "$(jq '.confirmed == false and .counts.notConfirmed == 1 and .counts.deleted == 0' "$OUT/first.json")" \
  "first call: notConfirmed, nothing deleted" "$(jq -c . "$OUT/first.json")"
ok "$([ "$(id_of "Miete Maerz")" = "$FIRST_ID" ] && [ "$(id_of "Rechnung 4713")" = "$SECOND_ID" ] && echo true || echo false)" \
  "both transactions still present" ""
finish
