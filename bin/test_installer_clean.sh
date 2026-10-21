#!/bin/sh
# Each runtime is tested in a fresh container without mounted runtimes/caches.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CONTEXT=$(mktemp -d "${TMPDIR:-/tmp}/branchline-clean-build.XXXXXX")
IMAGE=branchline-installer-clean:acceptance
trap 'rm -rf -- "$CONTEXT"' 0
trap 'exit 1' HUP INT TERM
cp "$ROOT/docs/install.sh" "$CONTEXT/install.sh"
cp "$ROOT/bin/installer-smoke/Dockerfile" "$CONTEXT/Dockerfile"
cp "$ROOT/bin/installer-smoke/smoke.sh" "$CONTEXT/smoke.sh"
docker build -t "$IMAGE" "$CONTEXT"
for runtime in jvm node; do
  docker run --rm --memory=1g --cpus=2 \
    -e "BRANCHLINE_INSTALLER_URL=${BRANCHLINE_INSTALLER_URL:-}" "$IMAGE" "$runtime"
done
