#!/bin/bash
# Listet die Testumsaetze der Test-Instanz: ID, Datum, Betrag, Gegenkonto, Verwendungszweck.
D="$(dirname "$0")"
for account in $(bash "$D/mcp.sh" '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"hibiscus_accounts_list","arguments":{"scope":"all"}}}' \
    | jq -r '.result.structuredContent.accounts[].id'); do
  bash "$D/mcp.sh" "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"hibiscus_transactions_list\",\"arguments\":{\"accountId\":\"$account\",\"all\":true,\"limit\":500}}}" \
    | jq -r '.result.structuredContent.transactions[] | [.id, .datum, .betrag, .gegenkontoName, .zweck] | @tsv'
done
