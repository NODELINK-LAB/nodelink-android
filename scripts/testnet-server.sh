#!/usr/bin/env bash
# One-command NODELINK public test network on a single free server (Oracle Cloud Always Free, Ubuntu 22.04/24.04).
# Installed and started by:  curl -fsSL https://nodelink.base44.app/api/install | sudo bash
# Runs the 4-validator localnet on ONE machine (test only, not decentralised) and serves node0's EVM JSON-RPC over HTTPS
# at https://<ip-with-dashes>.sslip.io (free hostname, no domain needed; Let's Encrypt certificate via Caddy).
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Run with sudo"; exit 1; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
log(){ echo; echo "== $*"; }
ARCH=$(dpkg --print-architecture); case "$ARCH" in amd64|arm64) ;; *) echo "Unsupported CPU: $ARCH"; exit 1;; esac

log "1/7 System packages"
apt-get update -y && DEBIAN_FRONTEND=noninteractive apt-get install -y git jq gcc curl gnupg debian-keyring debian-archive-keyring apt-transport-https chrony iptables-persistent python3
systemctl enable --now chrony

log "2/7 Memory: swap for the Go build on small servers"
if [ "$(free -m | awk '/^Mem/{print $2}')" -lt 8000 ] && ! swapon --show | grep -q /swapfile; then
 fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab; fi

log "3/7 Go $GO_VERSION"
if ! /usr/local/go/bin/go version 2>/dev/null | grep -q "go$GO_VERSION "; then
 rm -rf /usr/local/go && curl -fsSL "https://go.dev/dl/go$GO_VERSION.linux-$ARCH.tar.gz" | tar -C /usr/local -xz; fi
export PATH="/usr/local/go/bin:$PATH"

log "4/7 Build nodelinkd (10 to 40 minutes the first time)"
id nodelink >/dev/null 2>&1 || useradd -m -s /bin/bash nodelink
chown -R nodelink:nodelink "$ROOT"
sudo -u nodelink env PATH="$PATH" HOME=/home/nodelink bash "$ROOT/scripts/build-chain.sh"

log "5/7 Test network (4 validators on this server)"
[ -d "$ROOT/.localnet4/node0" ] || sudo -u nodelink env PATH="$PATH" HOME=/home/nodelink bash "$ROOT/scripts/localnet-multi.sh" init
cat > /etc/systemd/system/nodelink-testnet.service <<EOF
[Unit]
Description=NODELINK test network (4 validators)
After=network-online.target
[Service]
Type=oneshot
RemainAfterExit=yes
User=nodelink
Environment=PATH=/usr/local/go/bin:/usr/bin:/bin
ExecStart=/bin/bash $ROOT/scripts/localnet-multi.sh start
ExecStop=/bin/bash $ROOT/scripts/localnet-multi.sh stop
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload; systemctl enable nodelink-testnet; systemctl restart nodelink-testnet

log "6/7 HTTPS (Caddy) and firewall"
if ! command -v caddy >/dev/null; then
 curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
 curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list
 apt-get update -y && apt-get install -y caddy; fi
IP=$(curl -fsS https://api.ipify.org); HOST="${IP//./-}.sslip.io"
sed "s/{\$RPC_DOMAIN}/$HOST/; s/node:8545/127.0.0.1:8545/" "$ROOT/infrastructure/Caddyfile" > /etc/caddy/Caddyfile
# Oracle images reject everything except SSH in iptables: open 80 and 443 only. JSON-RPC 8545 stays private.
for p in 80 443; do iptables -C INPUT -p tcp --dport $p -j ACCEPT 2>/dev/null || iptables -I INPUT 5 -p tcp --dport $p -j ACCEPT; done
netfilter-persistent save >/dev/null 2>&1 || true
command -v ufw >/dev/null && ufw status | grep -q active && { ufw allow 80/tcp; ufw allow 443/tcp; }
systemctl enable caddy; systemctl restart caddy

log "7/7 Check"
for s in $(seq 1 60); do
 R=$(curl -s --max-time 5 "https://$HOST" -X POST -H 'content-type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"eth_chainId","params":[]}' | jq -r .result 2>/dev/null || true)
 [ "$R" = "0xbdf11" ] && break; sleep 5; done
echo
if [ "$R" = "0xbdf11" ]; then
 echo "NODELINK test network is live."
 echo "Public RPC: https://$HOST"
 echo "Give this address to Base44 as NODELINK_UPSTREAM_RPC; then https://nodelink.base44.app/api/rpc works for MetaMask and the wallets."
else
 echo "The chain is running but https://$HOST did not answer from the internet."
 echo "In Oracle Cloud: Networking > Virtual cloud networks > your VCN > Security lists > Default: add ingress rules TCP 80 and 443 from 0.0.0.0/0, then run: sudo systemctl restart caddy"
fi
echo "TEST ONLY: all 4 validators and the genesis allocation keys (keyring 'test', unencrypted) are on this one server."