#!/usr/bin/env bash
# NODELINK test network on your own laptop: Windows (inside WSL Ubuntu), Linux or macOS. No root install, no domain.
# Installed by:  curl -fsSL https://nodelink.base44.app/api/install | bash -s laptop
# Usage afterwards (in ~/nodelink):  bash scripts/laptop.sh [start | stop | url | test]
# The public RPC goes through a free Cloudflare quick tunnel (https://<random>.trycloudflare.com). The address changes
# every time the tunnel restarts; a permanent address needs a domain (later).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
NET="$ROOT/.localnet4"; TOOLS="$HOME/.nodelink-tools"; mkdir -p "$TOOLS" "$NET/logs"
export PATH="$TOOLS/go/bin:$TOOLS:$PATH"
log(){ echo; echo "== $*"; }
case "$(uname -s)" in Linux) OS=linux;; Darwin) OS=darwin;; *) echo "On Windows, run this inside WSL Ubuntu (see docs/PHASE9.md)"; exit 1;; esac
case "$(uname -m)" in x86_64|amd64) ARCH=amd64;; arm64|aarch64) ARCH=arm64;; *) echo "Unsupported CPU: $(uname -m)"; exit 1;; esac

tunnel_url(){ grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$NET/logs/tunnel.log" 2>/dev/null | tail -1; }
stop_tunnel(){ [ -f "$NET/tunnel.pid" ] && { kill "$(cat "$NET/tunnel.pid")" 2>/dev/null || true; rm -f "$NET/tunnel.pid"; }; }

tools(){
 log "1/5 Tools"
 if [ "$OS" = linux ]; then sudo apt-get update -y && sudo DEBIAN_FRONTEND=noninteractive apt-get install -y git jq gcc curl python3
 else xcode-select -p >/dev/null 2>&1 || { xcode-select --install; echo "Finish the Xcode tools install, then run this again"; exit 1; }
  command -v jq >/dev/null || { command -v brew >/dev/null && brew install jq || { curl -fsSL -o "$TOOLS/jq" "https://github.com/jqlang/jq/releases/download/jq-1.7.1/jq-macos-$ARCH" && chmod +x "$TOOLS/jq"; }; }; fi
 log "2/5 Go $GO_VERSION (private copy in $TOOLS)"
 go version 2>/dev/null | grep -q "go$GO_VERSION " || { rm -rf "$TOOLS/go"; curl -fsSL "https://go.dev/dl/go$GO_VERSION.$OS-$ARCH.tar.gz" | tar -C "$TOOLS" -xz; }
 log "3/5 Cloudflare tunnel tool"
 command -v cloudflared >/dev/null || if [ "$OS" = linux ]; then
  curl -fsSL -o "$TOOLS/cloudflared" "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-$ARCH" && chmod +x "$TOOLS/cloudflared"
 else curl -fsSL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-$ARCH.tgz" | tar -C "$TOOLS" -xz; fi
}

start(){
 [ -x "$ROOT/blockchain/bin/nodelinkd" ] || { tools; log "4/5 Build nodelinkd (10 to 40 minutes the first time)"; bash "$ROOT/scripts/build-chain.sh"; }
 log "5/5 Test network and public RPC"
 [ -d "$NET/node0" ] || bash "$ROOT/scripts/localnet-multi.sh" init
 bash "$ROOT/scripts/localnet-multi.sh" start
 stop_tunnel; : > "$NET/logs/tunnel.log"
 nohup cloudflared tunnel --no-autoupdate --url http://localhost:8545 >>"$NET/logs/tunnel.log" 2>&1 & echo $! > "$NET/tunnel.pid"
 for s in $(seq 1 30); do [ -n "$(tunnel_url)" ] && break; sleep 2; done
 U=$(tunnel_url); [ -n "$U" ] || { echo "Tunnel did not start; see $NET/logs/tunnel.log"; exit 1; }
 for s in $(seq 1 30); do
  R=$(curl -s --max-time 5 "$U" -X POST -H 'content-type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"eth_chainId","params":[]}' | jq -r .result 2>/dev/null || true)
  [ "$R" = "0xbdf11" ] && break; sleep 3; done
 echo
 [ "$R" = "0xbdf11" ] && echo "NODELINK test network is live on this laptop." || echo "Chain started; the tunnel may need another minute."
 echo "Public RPC: $U"
 echo "Give this address to Base44 as NODELINK_UPSTREAM_RPC. It changes each time you run start; keep the laptop on and awake."
 echo "TEST ONLY: all 4 validators and the genesis keys (test keyring, unencrypted) are on this laptop."
}

case "${1:-start}" in
 start) start;;
 stop) stop_tunnel; bash "$ROOT/scripts/localnet-multi.sh" stop; echo "Stopped.";;
 url) tunnel_url || true;;
 test) export SKIP_UNIT_TESTS=1
  for p in 2 3 4; do bash "$ROOT/scripts/verify-phase$p.sh" || true; done
  bash "$ROOT/scripts/verify-phase6.sh" || true
  echo "Results saved in $ROOT/tests/results";;
 *) echo "usage: laptop.sh start | stop | url | test"; exit 1;;
esac