#!/bin/sh
# Branchline release installer. Keep execution inside main so truncated pipes do not install.
set -eu

main() {
  umask 077
  VERSION=${BRANCHLINE_VERSION:-v1.0.0-rc.1}
  RUNTIME=${BRANCHLINE_RUNTIME:-auto}
  INSTALL_ROOT=${BRANCHLINE_INSTALL_ROOT:-"$HOME/.local/share/branchline"}
  BIN_DIR=${BRANCHLINE_BIN_DIR:-"$HOME/.local/bin"}
  fail() { printf 'Branchline installer: %s\n' "$*" >&2; exit 1; }
  progress() { printf '[Branchline] %s\n' "$*" >&2; }
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --runtime|--version)
        [ "$#" -ge 2 ] || fail "$1 needs a value"
        case "$1" in --runtime) RUNTIME=$2 ;; --version) VERSION=$2 ;; esac
        shift 2 ;;
      --help|-h)
        printf '%s\n' 'Install Branchline: sh install.sh [--runtime auto|jvm|node] [--version v1.0.0-rc.1]' \
          'Defaults: published RC with a private Temurin 21 JRE; --runtime node downloads private Node 24.' \
          'Paths: BRANCHLINE_INSTALL_ROOT (~/.local/share/branchline), BRANCHLINE_BIN_DIR (~/.local/bin).' \
          'No preinstalled Java/Node, sudo, or shell-profile changes. auto is an alias for jvm.'
        exit 0 ;;
      *) fail "Unknown option: $1 (see --help)" ;;
    esac
  done
  printf '%s\n' "$VERSION" | LC_ALL=C grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$' || fail 'Invalid release version'
  case "$RUNTIME" in auto|jvm|node) ;; *) fail 'Runtime must be auto, jvm or node' ;; esac
  case "$(uname -s)" in Darwin|Linux) ;; *) fail 'This installer requires macOS or Linux; see the manual installation guide for other systems.' ;; esac
  for directory in "$INSTALL_ROOT" "$BIN_DIR"; do
    case "$directory" in /*) ;; *) fail 'Installation paths must be absolute' ;; esac
    case "$directory" in *'
'*) fail 'Installation paths must not contain newlines' ;; esac
    [ ! -L "$directory" ] || fail "Installation directory is a symlink: $directory"
  done
  for tool in curl awk grep sed mktemp tar; do
    command -v "$tool" >/dev/null 2>&1 || fail "Required tool not found: $tool"
  done
  if command -v sha256sum >/dev/null 2>&1; then HASH=sha256sum
  elif command -v shasum >/dev/null 2>&1; then HASH=shasum
  else fail 'SHA-256 verification needs sha256sum or shasum'; fi

  # Keep the default deterministic: existing host runtimes never change semantics.
  [ "$RUNTIME" != auto ] || RUNTIME=jvm
  case "$(uname -s):$(uname -m)" in
    Darwin:arm64) OS=mac; NODE_OS=darwin; ARCH=aarch64; NODE_ARCH=arm64 ;;
    Darwin:x86_64) OS=mac; NODE_OS=darwin; ARCH=x64; NODE_ARCH=x64 ;;
    Linux:aarch64|Linux:arm64) OS=linux; NODE_OS=linux; ARCH=aarch64; NODE_ARCH=arm64 ;;
    Linux:x86_64) OS=linux; NODE_OS=linux; ARCH=x64; NODE_ARCH=x64 ;;
    *) fail 'Managed runtimes support macOS/Linux on arm64 or x86_64 only.' ;;
  esac
  if [ "$OS" = linux ]; then
    LIBC_VERSION=$(getconf GNU_LIBC_VERSION 2>/dev/null) || fail 'Managed Linux runtimes require glibc 2.28+; musl/Alpine is unsupported.'
    printf '%s\n' "$LIBC_VERSION" | awk '{split($2,v,"."); exit !(v[1]>2 || (v[1]==2 && v[2]>=28))}' || fail 'Managed Linux runtimes require glibc 2.28+.'
  fi
  if [ "$RUNTIME" = jvm ]; then
    ASSET=branchline-cli-$VERSION-all.jar
    RUNTIME_VERSION=21.0.12.1
    RUNTIME_ROOT=jdk-21.0.12.1+1-jre
    RUNTIME_ASSET=OpenJDK21U-jre_${ARCH}_${OS}_hotspot_21.0.12.1_1.tar.gz
    RUNTIME_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/$RUNTIME_ASSET
    case "$OS:$ARCH" in
      linux:aarch64) RUNTIME_SHA=14be1f35ebdbd1f6e8d57eb911a3ffb74d6d9aa255abc5daf2b1302002cf2cf2 ;;
      mac:aarch64) RUNTIME_SHA=dec50fc6f9fcd4fe3ae8cabf5a5fa68f6afc48841f7698e468e9aa5d54beed84 ;;
      linux:x64) RUNTIME_SHA=2413149700df0f7d440500a84a8f764c535f21e5a5e87d38328b64eec2c5b500 ;;
      mac:x64) RUNTIME_SHA=6717ec641fd9ce0bb209ca083ee23b42202ac68cb6fcc5753496e0e4a0f41989 ;;
    esac
    RUNTIME_BIN=bin/java
    [ "$OS" != mac ] || RUNTIME_BIN=Contents/Home/bin/java
  else
    ASSET=branchline-cli-js-$VERSION.tgz
    RUNTIME_VERSION=v24.22.0
    RUNTIME_ROOT=node-$RUNTIME_VERSION-$NODE_OS-$NODE_ARCH
    RUNTIME_ASSET=$RUNTIME_ROOT.tar.gz
    RUNTIME_URL=https://nodejs.org/dist/$RUNTIME_VERSION/$RUNTIME_ASSET
    case "$NODE_OS:$NODE_ARCH" in
      darwin:arm64) RUNTIME_SHA=6a9724d9b50a5f22b4816cded441b5a8d9ddae546191a5e5b7efa5a5e2ee26d5 ;;
      darwin:x64) RUNTIME_SHA=495e3f273c645f3c4677d438304cdfc701729ea5dec5728f5c8e76a2dece4b91 ;;
      linux:arm64) RUNTIME_SHA=b7e7991d5407815adfa74a2a9cd4db683552c8ce32ff4a295c22e972849c3f5d ;;
      linux:x64) RUNTIME_SHA=28ea0a438de9a13bd7891fd758eee6c7819ee8af5442dfd8d45bd65e56d4228c ;;
    esac
    RUNTIME_BIN=bin/node
    progress 'Node selected: JS numbers do not provide the JVM exact-decimal profile.'
  fi
  progress "Installing $VERSION with private $RUNTIME $RUNTIME_VERSION ($OS/$ARCH)"
  mkdir -p "$INSTALL_ROOT/releases" "$BIN_DIR"
  [ ! -L "$INSTALL_ROOT/releases" ] || fail 'Release directory must not be a symlink'
  LOCK=$BIN_DIR/.branchline-install-lock
  mkdir "$LOCK" 2>/dev/null || fail "Another installer is running (lock: $LOCK)."
  TMP_ROOT=''; STAGE=''; LAUNCHER=''
  cleanup() {
    [ -z "$TMP_ROOT" ] || rm -rf -- "$TMP_ROOT"
    [ -z "$STAGE" ] || rm -rf -- "$STAGE"
    [ -z "$LAUNCHER" ] || rm -f -- "$LAUNCHER"
    rmdir "$LOCK" 2>/dev/null || true
  }
  trap cleanup 0
  trap 'exit 1' HUP INT TERM
  if [ -e "$BIN_DIR/bl" ] || [ -L "$BIN_DIR/bl" ]; then
    [ -f "$BIN_DIR/bl" ] && [ ! -L "$BIN_DIR/bl" ] &&
      grep -qx '# Branchline managed launcher' "$BIN_DIR/bl" || fail "Refusing to replace unrelated $BIN_DIR/bl"
  fi
  TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/branchline-install.XXXXXX")
  RELEASE_URL=https://github.com/ehlyzov/branchline/releases/download/$VERSION
  download() {
    curl --fail --show-error --location --silent --retry 2 --connect-timeout 15 --max-time 300 \
      --proto '=https' --proto-redir '=https' --tlsv1.2 "$1" --output "$2"
  }
  verify_hash() {
    if [ "$HASH" = sha256sum ]; then ACTUAL=$(sha256sum "$1" | awk '{print $1}')
    else ACTUAL=$(shasum -a 256 "$1" | awk '{print $1}'); fi
    [ "$ACTUAL" = "$2" ] || fail 'SHA-256 mismatch; existing installation unchanged.'
  }
  progress 'Downloading published CLI and SHA256SUMS...'
  download "$RELEASE_URL/$ASSET" "$TMP_ROOT/$ASSET" || fail 'CLI download failed; existing installation unchanged.'
  download "$RELEASE_URL/SHA256SUMS" "$TMP_ROOT/SHA256SUMS" || fail 'Checksum download failed; existing installation unchanged.'
  EXPECTED=$(awk -v name="$ASSET" '$2 == name { print $1 }' "$TMP_ROOT/SHA256SUMS")
  [ "${#EXPECTED}" -eq 64 ] || fail 'Missing, duplicate or invalid release checksum'
  case "$EXPECTED" in *[!0-9a-f]*) fail 'Invalid release checksum' ;; esac
  verify_hash "$TMP_ROOT/$ASSET" "$EXPECTED"
  progress "Downloading private $RUNTIME $RUNTIME_VERSION..."
  download "$RUNTIME_URL" "$TMP_ROOT/runtime.tar.gz" || fail 'Runtime download failed; existing installation unchanged.'
  verify_hash "$TMP_ROOT/runtime.tar.gz" "$RUNTIME_SHA"
  progress 'CLI and runtime checksums verified. Preparing private installation...'
  STAGE=$(mktemp -d "$INSTALL_ROOT/releases/$VERSION-$RUNTIME.XXXXXX")
  mkdir "$STAGE/runtime"
  tar -tzf "$TMP_ROOT/runtime.tar.gz" > "$TMP_ROOT/runtime-entries"
  [ -s "$TMP_ROOT/runtime-entries" ] || fail 'Empty runtime archive'
  awk -v root="$RUNTIME_ROOT" 'index($0,root "/")!=1 || $0 ~ /(^|\/)\.\.(\/|$)/ {exit 1}' "$TMP_ROOT/runtime-entries" || fail 'Unexpected runtime archive layout'
  # These vendor archives are immutable and matched against embedded SHA-256 pins.
  # Preserve their internal symlinks, licenses and legal notices in a fresh directory.
  tar -xzf "$TMP_ROOT/runtime.tar.gz" -C "$STAGE/runtime" --strip-components=1
  EXECUTABLE=$STAGE/runtime/$RUNTIME_BIN
  [ -x "$EXECUTABLE" ] || fail 'Private runtime executable missing'
  if [ "$RUNTIME" = jvm ]; then
    RUNTIME_ACTUAL=$("$EXECUTABLE" -version 2>&1) || fail 'Private Java cannot run on this system; existing installation unchanged.'
    printf '%s\n' "$RUNTIME_ACTUAL" | grep -F "version \"$RUNTIME_VERSION\"" >/dev/null || fail 'Private Java version mismatch'
  else
    RUNTIME_ACTUAL=$("$EXECUTABLE" --version) || fail 'Private Node cannot run on this system; existing installation unchanged.'
    [ "$RUNTIME_ACTUAL" = "$RUNTIME_VERSION" ] || fail 'Private Node version mismatch'
  fi
  if [ "$RUNTIME" = jvm ]; then
    mv "$TMP_ROOT/$ASSET" "$STAGE/branchline.jar"
    PAYLOAD=$STAGE/branchline.jar
  else
    # Release archives contain regular files/directories only. Reject unsafe paths or links.
    tar -tzf "$TMP_ROOT/$ASSET" > "$TMP_ROOT/entries"
    [ -s "$TMP_ROOT/entries" ] || fail 'Empty Node release archive'
    if LC_ALL=C grep -Eq '(^/|(^|/)\.\.(/|$))' "$TMP_ROOT/entries"; then fail 'Unsafe archive path'; fi
    tar -tvzf "$TMP_ROOT/$ASSET" > "$TMP_ROOT/types"
    if LC_ALL=C grep -Ev '^[-d]' "$TMP_ROOT/types" >/dev/null; then fail 'Unsupported archive entry'; fi
    tar -xzf "$TMP_ROOT/$ASSET" -C "$STAGE"
    PAYLOAD=$STAGE/bin/bl.cjs
    [ -f "$PAYLOAD" ] || fail 'Node release launcher missing'
  fi
  quote() { printf "'"; printf '%s' "$1" | sed "s/'/'\\\\''/g"; printf "'"; }
  LAUNCHER=$(mktemp "$BIN_DIR/.bl-launcher.XXXXXX")
  {
    printf '%s\n' '#!/bin/sh' '# Branchline managed launcher'
    printf 'exec '; quote "$EXECUTABLE"
    [ "$RUNTIME" != jvm ] || printf ' -jar'
    printf ' '; quote "$PAYLOAD"; printf ' "$@"\n'
  } > "$LAUNCHER"
  chmod 700 "$LAUNCHER"
  CLI_VERSION=$("$LAUNCHER" --version) || fail 'CLI verification failed; existing installation unchanged.'
  [ "$CLI_VERSION" = "Branchline CLI $VERSION" ] || fail 'CLI version mismatch; existing installation unchanged.'
  mv -f "$LAUNCHER" "$BIN_DIR/bl"
  LAUNCHER=''; STAGE=''
  printf 'Branchline %s installed (%s).\nCommand: %s\n' "$VERSION" "$RUNTIME" "$BIN_DIR/bl"
  case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *) printf 'For this terminal, run:\n  export PATH='; quote "$BIN_DIR"; printf ':"$PATH"\n' ;;
  esac
  printf '%s\n' 'Then: bl --version' '      bl program.bl --input input.json' \
    'To update Branchline and its private runtime, rerun the installer. Previous installations are retained.'
}
main "$@"
