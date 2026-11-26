#!/usr/bin/env bash
# Phase 8: Blockscout explorer for the running 4-validator localnet. Needs Docker with compose v2.
# Usage: bash scripts/explorer.sh start | stop | status | reset
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"; source "$ROOT/scripts/lib/multi.sh"
C=(docker compose -f "$ROOT/explorer/docker-compose.yml" -p nodelink-explorer)
KEYF="$ROOT/explorer/.secret_key_base"
case "${1:-start}" in
 start)
  command -v docker >/dev/null || { echo "Docker is required: https://docs.docker.com/get-docker/"; exit 1; }
  [ -f "$NET/node0.pid" ] || { echo "Start the localnet first: bash scripts/localnet-multi.sh start"; exit 1; }
  # Blockscout reads internal transactions with debug_traceTransaction: restart node0 with the debug namespace.
  stop_node 0; JSONRPC_API=eth,net,web3,txpool,debug start_node 0; sleep 8
  [ -f "$KEYF" ] || { openssl rand -hex 64 > "$KEYF"; chmod 600 "$KEYF"; }
  export SECRET_KEY_BASE="$(cat "$KEYF")" RPC_URL="http://host.docker.internal:$(evmport 0)" PUBLIC_RPC_URL="http://localhost:$(evmport 0)"
  "${C[@]}" up -d
  echo "Explorer starting: http://localhost:3000 (API http://localhost:4000/api/v2). First indexing takes 1-2 minutes.";;
 stop) "${C[@]}" down;;
 reset) "${C[@]}" down -v; echo "Explorer database removed. Use after localnet-multi.sh init (a new chain).";;
 status)
  "${C[@]}" ps
  curl -s --max-time 5 localhost:4000/api/v2/stats | jq '{total_blocks,total_transactions,total_addresses}' 2>/dev/null || echo "API not answering yet";;
 *) echo "usage: explorer.sh start|stop|status|reset"; exit 1;;
esac