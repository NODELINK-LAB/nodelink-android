#!/usr/bin/env bash
# 4-validator LOCAL DEVELOPMENT network on one machine. Usage: localnet-multi.sh init | start [i..] | stop [i..] | status
# Each validator: its own home, keys, ports and process. Test keyring: development only.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"; source "$ROOT/scripts/lib/genesis.sh"; source "$ROOT/scripts/lib/multi.sh"
[ -x "$BIN" ] || { echo "Run scripts/build-chain.sh first"; exit 1; }
Z=000000000000000000; D="$BASE_DENOM"; A="$ROOT/blockchain/genesis/allocations.json"; T="$NET/genesis-build"; ALL=$(seq 0 $((N-1)))
init(){
 nodelink_check_alloc "$A"; for i in $ALL; do stop_node "$i"; done; rm -rf "$NET"
 # Node keys, validator keys, per-node ports and persistent peers come from the chain's own testnet tool; its genesis is replaced below.
 "$BIN" testnet init-files --validator-count "$N" --single-host --output-dir "$NET" --node-daemon-home home --chain-id "$CHAIN_ID" --keyring-backend test >/dev/null 2>&1
 "$BIN" init build --chain-id "$CHAIN_ID" --home "$T" >/dev/null 2>&1; nodelink_genesis "$T/config/genesis.json"; KT=(--keyring-backend test --home "$T")
 nodelink_fund_reserve "$T" "$(jq -r '.[]|select(.name=="staking-rewards").ndl' "$A")$Z$D"
 for n in $(jq -r '.[].name' "$A"); do case "$n" in genesis-validators|staking-rewards) continue;; esac
  "$BIN" keys add "alloc-$n" --algo eth_secp256k1 "${KT[@]}" >/dev/null 2>&1
  "$BIN" genesis add-genesis-account "alloc-$n" "$(jq -r ".[]|select(.name==\"$n\").ndl" "$A")$Z$D" "${KT[@]}"; done
 per=$(( $(jq -r '.[]|select(.name=="genesis-validators").ndl' "$A") / N ))   # 25,000,000 NODE each
 for i in $ALL; do "$BIN" genesis add-genesis-account "$("$BIN" keys show "node$i" -a --keyring-backend test --home "$(nh "$i")")" "$per$Z$D" --home "$T"; done
 rm -rf "$T/config/gentx"; mkdir -p "$T/config/gentx"
 for i in $ALL; do H="$(nh "$i")"; cp "$T/config/genesis.json" "$H/config/genesis.json"
  "$BIN" genesis gentx "node$i" "1000000$Z$D" --gas-prices "10000000$D" --chain-id "$CHAIN_ID" --keyring-backend test --home "$H" --output-document "$T/config/gentx/node$i.json" >/dev/null 2>&1; done
 "$BIN" genesis collect-gentxs --home "$T" >/dev/null 2>&1; "$BIN" genesis validate-genesis --home "$T"
 for i in $ALL; do cp "$T/config/genesis.json" "$(nh "$i")/config/genesis.json"; nodelink_app_config "$(nh "$i")"
  # The testnet tool gives every node proxy_app 26658, which is also node1's RPC port: give each its own.
  sed -i.bak "s|^proxy_app = .*|proxy_app = \"tcp://127.0.0.1:$((36658 + i))\"|" "$(nh "$i")/config/config.toml"; done
 echo "Genesis: $(jq -r '.app_state.genutil.gen_txs|length' "$T/config/genesis.json") validators, 1,000,000,000 NODE. Peers of node0: $(grep '^persistent_peers' "$(nh 0)/config/config.toml")"
}
case "${1:-}" in
 init) init;;
 start) shift; for i in ${*:-$ALL}; do start_node "$i"; echo "node$i started, RPC $(node "$i"), EVM localhost:$(evmport "$i"), log $NET/logs/node$i.log"; done;;
 stop) shift; for i in ${*:-$ALL}; do stop_node "$i"; echo "node$i stopped"; done;;
 status) for i in $ALL; do echo "node$i height $(height "$i")"; done;;
 *) echo "Usage: $0 init | start [i..] | stop [i..] | status"; exit 1;;
esac