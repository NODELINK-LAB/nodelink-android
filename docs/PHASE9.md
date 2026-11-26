# Phase 9 report: infrastructure and public RPC

## Layout
- Validators (4 to start, ideally run by different people in different places): no public web ports, peer-to-peer 26656 only. Keys never leave the validator server.
- One or more RPC nodes: full nodes without validator keys, JSON-RPC behind HTTPS (Caddy, automatic Let's Encrypt certificate), only eth, net and web3 namespaces, 64 KB request limit.
- https://nodelink.base44.app/api/rpc: public HTTPS endpoint in the web app. It forwards a fixed list of read methods plus eth_sendRawTransaction (already-signed transactions) to the RPC node set in the app secret NODELINK_UPSTREAM_RPC, at most 20 calls per batch, 64 KB, 10 s timeout. GET returns status, chain id and height. Debug, admin, personal and txpool methods are refused.
- Explorer (phase 8) points at the RPC node.

## Built
- infrastructure/Dockerfile: builds nodelinkd with the pinned Go 1.25.9, runs as a non-root user.
- infrastructure/docker-compose.yml + Caddyfile: RPC node + HTTPS. JSON-RPC is not published to the internet directly.
- scripts/server-setup.sh: Ubuntu 24.04 setup: updates, automatic security updates, firewall (rpc or validator profile), fail2ban, time sync, Docker, a nodelink user.
- scripts/rpc-node.sh: init from the published genesis (checks chain id, prints genesis sha256), peers, app mempool, pruning, then up, status, logs, down.
- /api/rpc in the web app, described above.

## Laptop path (no server, no domain)
1. Windows: open PowerShell as administrator, run wsl --install -d Ubuntu-24.04, restart, open Ubuntu from the Start menu and create a user. Linux or macOS: open Terminal.
2. Run: curl -fsSL https://nodelink.base44.app/api/install | bash -s laptop
   It writes the kit to ~/nodelink, installs Go 1.25.9 and cloudflared in ~/.nodelink-tools, builds nodelinkd, starts the 4 validators, and opens a free Cloudflare quick tunnel to node0's EVM RPC. It prints https://<random>.trycloudflare.com.
3. Put that address in the Base44 secret NODELINK_UPSTREAM_RPC.
Later: bash scripts/laptop.sh start (after a reboot; prints a new address), stop, url, test (runs the phase 2, 3, 4 and 6 checks and saves the results). The network only runs while the laptop is on and awake. With a domain, a named Cloudflare tunnel gives a permanent address.

## No-cost server path (no computer, no domain)
1. Create an Oracle Cloud Always Free account (free for life; a card is asked once for identity, the free tier is not charged).
2. Create an instance: Ubuntu 24.04, shape VM.Standard.A1.Flex (Ampere, up to 4 CPU and 24 GB free), keep "assign public IP".
3. Networking > Virtual cloud networks > your VCN > Security lists > Default: add ingress TCP 80 and 443 from 0.0.0.0/0.
4. Open the instance's Cloud Shell or browser SSH console and run: curl -fsSL https://nodelink.base44.app/api/install | sudo bash
   It writes the kit to /opt/nodelink, installs Go 1.25.9 and Caddy, builds nodelinkd, starts the 4-validator test network as a service that survives reboots, and serves HTTPS at https://<ip-with-dashes>.sslip.io (free hostname, real certificate).
5. Put the printed https address in the Base44 secret NODELINK_UPSTREAM_RPC.
A domain can replace the sslip.io name later without changing anything else. Test only: all validators and the genesis keys are on one server.

## With your own servers later (from a phone is fine)
1. Rent one Ubuntu 24.04 VPS for the RPC node: 4 vCPU, 8 GB RAM, 160 GB SSD is enough for a testnet (Hetzner, DigitalOcean, Vultr, Contabo; about 10 to 40 USD per month).
2. Point a domain name at it, for example rpc.yourdomain.com (DNS A record to the server IP).
3. In the provider's browser console: git clone the repository, bash scripts/server-setup.sh, fill infrastructure/.env (RPC_DOMAIN, PEERS from the validators), bash scripts/rpc-node.sh init genesis.json, bash scripts/rpc-node.sh up.
4. Give the HTTPS address (https://rpc.yourdomain.com) to Base44 as the secret NODELINK_UPSTREAM_RPC. Then https://nodelink.base44.app/api/rpc works for MetaMask and the wallets.
The validators need their own servers (phase 14 decides who runs them). For a first public test, one server can run the 4-validator localnet plus the RPC node, but that is not decentralised and must be labelled test only.

## Not verified
Nothing in this phase has run: no server exists yet and this workspace has no Docker. /api/rpc answers "not configured" until the secret is set.

## Not covered yet
Monitoring and alerts (Prometheus and Grafana, block height and peer count alarms), backups of validator keys, a second RPC node with load balancing, DDoS protection (for example Cloudflare in front of the RPC domain), per-client rate limiting.