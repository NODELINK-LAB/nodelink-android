# shellcheck shell=bash
# Helpers for the 4-validator local network in .localnet4 (one machine, separate ports per node).
BIN="$ROOT/blockchain/bin/nodelinkd"; NET="$ROOT/.localnet4"; N=4
nh(){ echo "$NET/node$1/home"; }
rpcport(){ sed -n '/^\[rpc\]/,/^\[/p' "$(nh "$1")/config/config.toml" | grep '^laddr' | grep -oE '[0-9]+"' | tr -d '"'; }
evmport(){ echo $((8545 + 10 * $1)); }
node(){ echo "tcp://127.0.0.1:$(rpcport "$1")"; }
height(){ curl -s --max-time 2 "localhost:$(rpcport "$1")/status" | jq -r '.result.sync_info.latest_block_height // 0' 2>/dev/null || echo 0; }
start_node(){ mkdir -p "$NET/logs"; nohup "$BIN" start --home "$(nh "$1")" --chain-id "$CHAIN_ID" --evm.evm-chain-id "$EVM_CHAIN_ID" --minimum-gas-prices "0$BASE_DENOM" --evm.min-tip=0 --json-rpc.api "${JSONRPC_API:-eth,net,web3,txpool}" >>"$NET/logs/node$1.log" 2>&1 & echo $! > "$NET/node$1.pid"; }
stop_node(){ [ -f "$NET/node$1.pid" ] && { kill "$(cat "$NET/node$1.pid")" 2>/dev/null || true; }; rm -f "$NET/node$1.pid"; sleep 1; }