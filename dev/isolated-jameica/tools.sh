#!/bin/bash
# Zeigt die Tools, die der MCP-Server der Test-Instanz gerade anbietet.
bash "$(dirname "$0")/mcp.sh" '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | jq -r '[.result.tools[].name] | join(", ")'
