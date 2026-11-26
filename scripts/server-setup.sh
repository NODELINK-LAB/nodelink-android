#!/usr/bin/env bash
# Phase 9: prepare a fresh Ubuntu 24.04 server (VPS) for a NODELINK node. Run once as root:
#   bash scripts/server-setup.sh            (RPC node: opens 22, 80, 443, 26656)
#   bash scripts/server-setup.sh validator  (validator: opens 22 and 26656 only, no web ports)
# Works from a VPS provider's browser console, so no computer is needed.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Run as root"; exit 1; }
ROLE="${1:-rpc}"
apt-get update && DEBIAN_FRONTEND=noninteractive apt-get -y upgrade
DEBIAN_FRONTEND=noninteractive apt-get install -y ufw fail2ban unattended-upgrades jq curl git chrony
dpkg-reconfigure -f noninteractive unattended-upgrades
systemctl enable --now chrony fail2ban                     # correct time matters for consensus
command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh
ufw default deny incoming; ufw default allow outgoing
ufw allow OpenSSH; ufw allow 26656/tcp
[ "$ROLE" = rpc ] && { ufw allow 80/tcp; ufw allow 443/tcp; }
ufw --force enable
# Docker publishes ports past ufw; the compose files publish only 26656 (and 80/443 for the RPC node).
sysctl -w fs.file-max=1000000 >/dev/null
id nodelink >/dev/null 2>&1 || { useradd -m -s /bin/bash nodelink; usermod -aG docker nodelink; }
echo "Done ($ROLE). Firewall:"; ufw status
echo "Next: add an SSH key for user nodelink and disable password login (PasswordAuthentication no) once the key works."