FROM debian:bookworm-slim@sha256:7ff6ba31527b9e336162fa061746c366c4b2032f4d2cff2078bea43f78efe3f8
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl tar gzip libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 1000 tester \
    && ! command -v java && ! command -v node && ! command -v python3
COPY install.sh /opt/branchline-install.sh
COPY smoke.sh /opt/smoke.sh
USER tester
WORKDIR /home/tester
ENTRYPOINT ["sh", "/opt/smoke.sh"]
