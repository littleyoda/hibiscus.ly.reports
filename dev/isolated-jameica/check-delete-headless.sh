#!/bin/bash
# Prueft hibiscus_transactions_delete gegen die Test-Instanz im Server-Modus (start.sh server, frisch gebaut).
# Gibt PASS/FAIL je Pruefung aus. Im Server-Modus verweigert der Bestaetigungsdialog, daher wird nichts
# geloescht. NICHT gegen die GUI-Instanz laufen lassen: dort oeffnet der echte Loeschaufruf einen Dialog.
# Beendet sich mit der Anzahl fehlgeschlagener Pruefungen.
BASE="$(cd "$(dirname "$0")" && pwd)"
source "$BASE/iso-env.sh"
bash "$BASE/wait.sh" > /dev/null || exit 1
call() { bash "$BASE/mcp.sh" "$1"; }
tool() { call "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"$1\",\"arguments\":$2}}"; }

TOOLS=$(call '{"jsonrpc":"2.0","id":1,"method":"tools/list"}')
ok "$(jq '[.result.tools[].name] | index("hibiscus_transactions_delete") != null' <<<"$TOOLS")" "delete tool listed while setting on" "$TOOLS"
DESC=$(jq -r '.result.tools[] | select(.name=="hibiscus_transactions_delete") | .description' <<<"$TOOLS")
ok "$(jq -n --arg d "$DESC" '$d | contains("bis zu 50 Sekunden") and contains("Hoechstens 200 IDs")')" "description: 50 s + 200 limit" "$DESC"

ACCOUNTS=$(tool hibiscus_accounts_list '{}')
EUR=$(jq -r '.result.structuredContent.accounts[] | select(.name=="Testkonto EUR") | .id' <<<"$ACCOUNTS")
CHF=$(jq -r '.result.structuredContent.accounts[] | select(.name=="Testkonto CHF") | .id' <<<"$ACCOUNTS")
echo "info accounts: EUR=$EUR CHF=$CHF"

LIST=$(tool hibiscus_transactions_list "{\"accountId\":\"$EUR\",\"all\":true,\"limit\":500}")
ok "$(jq '[.result.structuredContent.transactions[] | .id | type=="string" and length>0] | all' <<<"$LIST")" "list: every transaction has a string id" "$(jq -c '.result.structuredContent.transactions[0]' <<<"$LIST")"
PAIR=$(jq -r '[.result.structuredContent.transactions[] | select(.zweck=="Rechnung 4711") | .id] | join(",")' <<<"$LIST")
INS=$(jq -r '.result.structuredContent.transactions[] | select(.zweck=="Beitrag Februar") | .id' <<<"$LIST")
RENT=$(jq -r '.result.structuredContent.transactions[] | select(.zweck=="Miete Maerz") | .id' <<<"$LIST")
RENTCAT=$(jq -r '.result.structuredContent.transactions[] | select(.zweck=="Miete Maerz") | .kategorie' <<<"$LIST")
CHFTX=$(tool hibiscus_transactions_list "{\"accountId\":\"$CHF\",\"all\":true}" | jq -r '.result.structuredContent.transactions[0].id')
echo "info ids: pair=$PAIR insurance=$INS rent=$RENT (list shows category '$RENTCAT' via rule) chf=$CHFTX"
P1=${PAIR%%,*}; P2=${PAIR##*,}

DRY=$(tool hibiscus_transactions_delete "{\"ids\":[\"$P1\",\"$P2\",\"$INS\",\"$RENT\",\"$CHFTX\",\"0$P1\",\"99999999999\",\"x\"],\"dryRun\":true}")
S=$(jq -c '.result.structuredContent' <<<"$DRY")
ok "$(jq '[.results[].status] == ["wouldDelete","wouldDelete","wouldDelete","wouldDelete","wouldDelete","unknown","unknown","unknown"]' <<<"$S")" "dryRun statuses (leading zero, out of range, text -> unknown)" "$S"
ok "$(jq '.confirmed == null and .counts.wouldDelete == 5 and .counts.unknown == 3' <<<"$S")" "dryRun: no confirmed field, counts" "$S"
ok "$(jq --arg id "$INS" '.results[] | select(.id==$id) | .links == ["kategorie: Versicherung","kommentar","geprueft"]' <<<"$S")" "links: stored category, comment, checked" "$(jq -c --arg id "$INS" '.results[] | select(.id==$id)' <<<"$S")"
ok "$(jq --arg id "$RENT" '.results[] | select(.id==$id) | .links == []' <<<"$S")" "links: rule-only category not reported" "$(jq -c --arg id "$RENT" '.results[] | select(.id==$id)' <<<"$S")"
ok "$(jq --arg a "$P1" --arg b "$P2" '[.results[] | select(.id==$a or .id==$b) | .transaction | select(.kontoName=="Testkonto EUR") | .gegenkontoName] | sort == ["BEISPIEL GMBH NIEDERLASSUNG","Beispiel GmbH"]' <<<"$S")" "dryRun transaction summary (both copies, account name)" "$S"

REAL=$(tool hibiscus_transactions_delete "{\"ids\":[\"$P2\"]}")
S=$(jq -c '.result.structuredContent' <<<"$REAL")
ok "$(jq '.confirmed == false and (.confirmError | contains("ohne Oberfläche")) and .results[0].status == "notConfirmed" and .counts.deleted == 0' <<<"$S")" "server mode: real delete refused, nothing deleted" "$S"
AFTER=$(tool hibiscus_transactions_list "{\"accountId\":\"$EUR\",\"all\":true,\"limit\":500}")
ok "$(jq --arg id "$P2" '[.result.structuredContent.transactions[].id] | index($id) != null' <<<"$AFTER")" "server mode: transaction still present" ""

BAD=$(tool hibiscus_transactions_delete '{"ids":[{"id":"1"}]}')
ok "$(jq '(.error.data // "") | contains("nur Umsatz-IDs als Text oder Zahl")' <<<"$BAD")" "non-scalar id rejected" "$BAD"
BAD=$(tool hibiscus_transactions_delete '{"ids":["1"],"dryRun":"yes"}')
ok "$(jq '(.error.data // "") | contains("dryRun: true oder false")' <<<"$BAD")" "dryRun 'yes' rejected" "$BAD"
MANY=$(jq -cn '[range(1;202) | tostring]')
BAD=$(tool hibiscus_transactions_delete "{\"ids\":$MANY,\"dryRun\":true}")
ok "$(jq '(.error.data // "") | contains("hoechstens 200")' <<<"$BAD")" "201 ids rejected" "$BAD"
finish
