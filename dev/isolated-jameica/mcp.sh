#!/bin/bash
# Schickt eine JSON-RPC-Anfrage an den MCP-Server der Test-Instanz (nur diese, eigener Port und Test-Passwort).
# Aufruf: mcp.sh '<json-rpc body>'
source "$(dirname "$0")/iso-env.sh"
exec curl -s --max-time "${MCP_TIMEOUT:-90}" -X POST "$ISO_URL" \
  -H "Authorization: Bearer $ISO_TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d "$1"
