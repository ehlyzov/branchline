#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <profile.jfr> <out.json>" >&2
  exit 1
fi

profile="$1"
out="$2"

jfr print --json --events jdk.ExecutionSample --stack-depth 20 "$profile" > "$out"
