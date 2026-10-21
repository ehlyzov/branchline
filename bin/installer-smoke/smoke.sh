#!/bin/sh
set -eu
[ "$(id -u)" != 0 ] || { echo 'Smoke must run without root' >&2; exit 1; }
for runtime in java node python3; do
  if command -v "$runtime" >/dev/null 2>&1; then echo "Unexpected preinstalled $runtime" >&2; exit 1; fi
done
printf 'Clean environment: uid=%s arch=%s; no Java, Node or Python\n' "$(id -u)" "$(uname -m)"
cat /etc/os-release
INSTALLER=/opt/branchline-install.sh
if [ -n "${BRANCHLINE_INSTALLER_URL:-}" ]; then
  case "$BRANCHLINE_INSTALLER_URL" in https://*) ;; *) exit 1 ;; esac
  curl -fsSL "$BRANCHLINE_INSTALLER_URL" -o /tmp/branchline-install.sh
  INSTALLER=/tmp/branchline-install.sh
fi
sha256sum "$INSTALLER"
case "${1:-jvm}" in jvm) set -- ;; node) set -- --runtime node ;; *) exit 1 ;; esac
sh "$INSTALLER" "$@"
BL=$HOME/.local/bin/bl
[ "$("$BL" --version)" = 'Branchline CLI v1.0.0-rc.1' ]
grep '/runtime/' "$BL"
cat > program.bl <<'BL'
TRANSFORM Shipment {
  OUTPUT {
    order_id: input.id,
    city: input.shipping.city ?? "Not provided",
    skus: [item.sku FOR EACH item IN input.items WHERE item.qty > 0]
  }
}
BL
printf '%s\n' '{"id":"A-104","shipping":{},"items":[{"sku":"BOOK","qty":2},{"sku":"PEN","qty":1},{"sku":"SKIP","qty":0}]}' > input.json
"$BL" program.bl --input input.json > result.json
grep -F '"city": "Not provided"' result.json >/dev/null
compact=$(tr -d ' \n\r\t' < result.json)
[ "$compact" = '{"order_id":"A-104","city":"Notprovided","skus":["BOOK","PEN"]}' ]
printf '{broken' > invalid.json
if "$BL" program.bl --input invalid.json > invalid-output.json 2> invalid-error.txt; then
  echo 'Invalid JSON must fail' >&2; exit 1
fi
[ ! -s invalid-output.json ] && [ -s invalid-error.txt ]
# Repeat the same installer: the managed launcher must remain usable.
sh "$INSTALLER" "$@"
"$BL" program.bl --input input.json > repeated.json
cmp result.json repeated.json
for runtime in java node; do
  if command -v "$runtime" >/dev/null 2>&1; then echo 'Installer modified global runtime lookup' >&2; exit 1; fi
done
printf 'PASS: clean non-root installation, private runtime, exact CLI version, independent transform, invalid-input rejection, repeat install\n'
