# shellcheck shell=bash
# Shared NODELINK genesis rules, used by every local network script. Needs versions.env loaded.
nodelink_check_alloc(){ [ "$(jq '[.[].ndl]|add' "$1")" = "1000000000" ] || { echo "Allocations must total exactly 1,000,000,000 NODE"; exit 1; }; }
nodelink_genesis(){ local G="$1" D="$BASE_DENOM"
 j(){ jq "$1" "$G" > "$G.tmp" && mv "$G.tmp" "$G"; }
 j ".app_state.staking.params.bond_denom=\"$D\" | .app_state.gov.params.min_deposit[0].denom=\"$D\" | .app_state.gov.params.expedited_min_deposit[0].denom=\"$D\" | .app_state.evm.params.evm_denom=\"$D\""
 # Fixed supply: the binary has no mint module (phase 3), so genesis carries no mint state.
 j 'del(.app_state.mint)'
 j ".app_state.bank.denom_metadata=[{\"description\":\"NODE, native coin of the NODELINK chain\",\"denom_units\":[{\"denom\":\"$D\",\"exponent\":0,\"aliases\":[]},{\"denom\":\"$DISPLAY_DENOM\",\"exponent\":$DECIMALS,\"aliases\":[]}],\"base\":\"$D\",\"display\":\"$DISPLAY_DENOM\",\"name\":\"NODE\",\"symbol\":\"$DISPLAY_DENOM\",\"uri\":\"$LOGO_URI\",\"uri_hash\":\"\"}]"
 j '.app_state.evm.params.active_static_precompiles=["0x0000000000000000000000000000000000000100","0x0000000000000000000000000000000000000400","0x0000000000000000000000000000000000000800","0x0000000000000000000000000000000000000801","0x0000000000000000000000000000000000000802","0x0000000000000000000000000000000000000803","0x0000000000000000000000000000000000000804","0x0000000000000000000000000000000000000805","0x0000000000000000000000000000000000000806","0x0000000000000000000000000000000000000807"]'
 j ".app_state.erc20.native_precompiles=[\"0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE\"] | .app_state.erc20.token_pairs=[{contract_owner:1,erc20_address:\"0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE\",denom:\"$D\",enabled:true}]"
 j '.consensus.params.block.max_gas="10000000"'
 # Phase 4 slashing. LOCAL TEST values so downtime shows within minutes; the public testnet genesis (phase 14) uses
 # window 10000, 5% minimum signed, 10 minute jail. Double signing: 5% burned and permanent ban (tombstone).
 j '.app_state.slashing.params.signed_blocks_window="50" | .app_state.slashing.params.min_signed_per_window="0.500000000000000000" | .app_state.slashing.params.downtime_jail_duration="60s" | .app_state.slashing.params.slash_fraction_downtime="0.010000000000000000" | .app_state.slashing.params.slash_fraction_double_sign="0.050000000000000000"'
}
# Address of the rewardreserve module account: first 20 bytes of sha256("rewardreserve").
nodelink_reserve_addr(){ local s="sha256sum"; command -v sha256sum >/dev/null || s="shasum -a 256"
 "$BIN" debug addr "$(printf rewardreserve | $s | cut -c1-40)" | awk '/^Bech32 Acc/{print $3}'; }
# Puts the staking-rewards allocation in the reserve. No key exists for it; permissions are cleared so it can never mint or burn.
nodelink_fund_reserve(){ # <home> <amount+denom>
 "$BIN" genesis add-genesis-account "$(nodelink_reserve_addr)" "$2" --module-name rewardreserve --home "$1"
 local G="$1/config/genesis.json"; jq '(.app_state.auth.accounts[]|select(.name?=="rewardreserve").permissions)=[]' "$G" > "$G.tmp" && mv "$G.tmp" "$G"; }
nodelink_app_config(){ # <home>
 sed -i.bak 's/timeout_commit = "5s"/timeout_commit = "2s"/' "$1/config/config.toml"
 # cosmos/evm's EVM mempool refuses to start unless CometBFT hands the mempool to the app.
 sed -i.bak 's/^type = "flood"/type = "app"/' "$1/config/config.toml"
 sed -i.bak 's/enable = false/enable = true/g; s/enabled = false/enabled = true/g' "$1/config/app.toml"
 sed -i.bak "s/^evm-chain-id = .*/evm-chain-id = $EVM_CHAIN_ID/" "$1/config/app.toml"
}