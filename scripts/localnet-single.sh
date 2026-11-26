#!/usr/bin/env bash
# One-validator LOCAL DEVELOPMENT network for nodelink-1. Wipes .localnet/node0 each run.
# Test keyring: keys are unencrypted on disk. Never use this genesis or these keys anywhere else.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"; source "$ROOT/scripts/lib/genesis.sh"
BIN="$ROOT/blockchain/bin/nodelinkd"; H="${NODELINK_HOME:-$ROOT/.localnet/node0}"; [ -x "$BIN" ] || { echo "Run scripts/build-chain.sh first"; exit 1; }
K=(--keyring-backend test --home "$H"); Z=000000000000000000; D="$BASE_DENOM"; A="$ROOT/blockchain/genesis/allocations.json"
nodelink_check_alloc "$A"; rm -rf "$H"
"$BIN" init node0 --chain-id "$CHAIN_ID" --home "$H" >/dev/null 2>&1
"$BIN" config set client chain-id "$CHAIN_ID" --home "$H"; "$BIN" config set client keyring-backend test --home "$H"
nodelink_genesis "$H/config/genesis.json"
nodelink_fund_reserve "$H" "$(jq -r '.[]|select(.name=="staking-rewards").ndl' "$A")$Z$D"
for n in $(jq -r '.[].name' "$A"); do [ "$n" = staking-rewards ] && continue
 "$BIN" keys add "alloc-$n" --algo eth_secp256k1 "${K[@]}" >/dev/null 2>&1
 "$BIN" genesis add-genesis-account "alloc-$n" "$(jq -r ".[]|select(.name==\"$n\").ndl" "$A")$Z$D" "${K[@]}"
done
"$BIN" genesis gentx alloc-genesis-validators "1000000$Z$D" --gas-prices "10000000$D" --chain-id "$CHAIN_ID" "${K[@]}" >/dev/null 2>&1
"$BIN" genesis collect-gentxs --home "$H" >/dev/null 2>&1
"$BIN" genesis validate-genesis --home "$H"
nodelink_app_config "$H"
echo "Genesis ready: 1,000,000,000 NODE in 10 allocation accounts. Starting validator (Ctrl+C to stop)…"
exec "$BIN" start --home "$H" --chain-id "$CHAIN_ID" --evm.evm-chain-id "$EVM_CHAIN_ID" --minimum-gas-prices "0$D" --evm.min-tip=0 --json-rpc.api eth,net,web3,txpool