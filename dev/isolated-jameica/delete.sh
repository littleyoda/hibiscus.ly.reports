#!/bin/bash
# Echter Loeschaufruf (ohne dryRun) an die Test-Instanz. Wartet, bis der Dialog in Hibiscus beantwortet
# ist oder ablaeuft, und gibt das Ergebnis aus. Aufruf: delete.sh <id> [<id> ...]
IDS=$(printf '"%s",' "$@"); IDS="[${IDS%,}]"
bash "$(dirname "$0")/mcp.sh" "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"hibiscus_transactions_delete\",\"arguments\":{\"ids\":$IDS}}}" \
  | jq '.result.structuredContent // .'
