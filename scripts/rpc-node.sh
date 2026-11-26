#!/usr/bin/env bash
# Phase 9: run a NODELINK public RPC node on a Linux server with Docker.
# Usage: bash scripts/rpc-node.sh init <genesis.json> | up | down | status | logs
# infrastructure/.env must set RPC_DOMAIN, PEERS (id@host:26656,...) and optionally CHAIN_ID, MONIKER, PUBLIC_IP.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; I="$ROOT/infrastructure"; D="$I/data"
[ -f "$I/.env" ] || { echo "Create infrastructure/.env from infrastructure/.env.example first"; exit 1; }
set -a; source "$I/.env"; set +a; CHAIN_ID="${CHAIN_ID:-nodelink-1}"
C=(docker compose -f "$I/docker-compose.yml" --env-file "$I/.env" -p nodelink-rpc)
case "${1:-}" in
 init)
  G="${2:?usage: rpc-node.sh init <genesis.json>}"; [ -f "$D/config/genesis.json" ] && { echo "Already initialised: $D"; exit 1; }
  [ "$(jq -r .chain_id "$G")" = "$CHAIN_ID" ] || { echo "genesis chain_id is not $CHAIN_ID"; exit 1; }
  "${C[@]}" build node; mkdir -p "$D"; sudo chown 10001:10001 "$D"
  "${C[@]}" run --rm node init "${MONIKER:-nodelink-rpc}" --chain-id "$CHAIN_ID" --home /data >/dev/null
  sudo cp "$G" "$D/config/genesis.json"; sudo chown 10001:10001 "$D/config/genesis.json"
  CT="$D/config/config.toml"; AT="$D/config/app.toml"
  sudo sed -i "s|^persistent_peers = .*|persistent_peers = \"${PEERS:?set PEERS in .env}\"|" "$CT"
  sudo sed -i 's/^type = "flood"/type = "app"/' "$CT"            # cosmos/evm needs the app mempool
  [ -n "${PUBLIC_IP:-}" ] && sudo sed -i "s|^external_address = .*|external_address = \"$PUBLIC_IP:26656\"|" "$CT"
  sudo sed -i 's/^pruning = .*/pruning = "custom"/; s/^pruning-keep-recent = .*/pruning-keep-recent = "100000"/; s/^pruning-interval = .*/pruning-interval = "100"/' "$AT"
  sudo sed -i "s/^evm-chain-id = .*/evm-chain-id = 778001/" "$AT"
  echo "Initialised. Genesis sha256: $(sha256sum "$G" | cut -c1-64) (must match the one the validators published). Next: bash scripts/rpc-node.sh up";;
 up) "${C[@]}" up -d --build; echo "Starting. Check: curl -s https://$RPC_DOMAIN -X POST -H 'content-type: application/json' -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"eth_chainId\",\"params\":[]}'";;
 down) "${C[@]}" down;;
 logs) "${C[@]}" logs --tail 100 -f node;;
 status)
  "${C[@]}" ps
  curl -s --max-time 5 "https://$RPC_DOMAIN" -X POST -H 'content-type: application/json' \
   -d '[{"jsonrpc":"2.0","id":1,"method":"eth_chainId","params":[]},{"jsonrpc":"2.0","id":2,"method":"eth_blockNumber","params":[]},{"jsonrpc":"2.0","id":3,"method":"eth_syncing","params":[]}]' | jq . || echo "RPC not answering yet";;
 *) echo "usage: rpc-node.sh init <genesis.json> | up | down | status | logs"; exit 1;;
esac