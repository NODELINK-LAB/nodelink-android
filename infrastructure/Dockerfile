# NODELINK node image (phase 9). Build from the repository root:
#   docker build -f infrastructure/Dockerfile -t nodelink/nodelinkd:local .
FROM golang:1.25.9-bookworm AS build
RUN apt-get update && apt-get install -y --no-install-recommends git jq gcc && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
RUN bash scripts/build-chain.sh

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates jq curl && rm -rf /var/lib/apt/lists/* \
 && useradd -u 10001 -m -s /usr/sbin/nologin nodelink
COPY --from=build /src/blockchain/bin/nodelinkd /usr/local/bin/nodelinkd
USER nodelink
VOLUME /data
EXPOSE 26656 8545
ENTRYPOINT ["nodelinkd"]