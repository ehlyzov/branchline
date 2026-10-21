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
          'Defaults: published RC; Java 21+ preferred, otherwise Node 22/24.' \
          'Paths: BRANCHLINE_INSTALL_ROOT (~/.local/share/branchline), BRANCHLINE_BIN_DIR (~/.local/bin).' \
          'No sudo, runtime installation, or shell-profile changes.'
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
  for tool in curl awk grep sed mktemp; do
    command -v "$tool" >/dev/null 2>&1 || fail "Required tool not found: $tool"
  done
  if command -v sha256sum >/dev/null 2>&1; then HASH=sha256sum
  elif command -v shasum >/dev/null 2>&1; then HASH=shasum
  else fail 'SHA-256 verification needs sha256sum or shasum'; fi

  JAVA_MAJOR=0
  if command -v java >/dev/null 2>&1; then
    JAVA_MAJOR=$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1)
  fi
  case "$JAVA_MAJOR" in ''|*[!0-9]*) JAVA_MAJOR=0 ;; esac
  NODE_MAJOR=0
  if command -v node >/dev/null 2>&1; then
    NODE_MAJOR=$(node --version 2>/dev/null | sed -n 's/^v\([0-9][0-9]*\).*/\1/p')
  fi
  case "$NODE_MAJOR" in ''|*[!0-9]*) NODE_MAJOR=0 ;; esac
  if [ "$RUNTIME" = auto ]; then
    if [ "$JAVA_MAJOR" -ge 21 ]; then RUNTIME=jvm
    elif [ "$NODE_MAJOR" = 22 ] || [ "$NODE_MAJOR" = 24 ]; then RUNTIME=node
    else fail 'Install Java 21/25 or Node 22/24, then rerun. https://ehlyzov.github.io/branchline/guides/install/'; fi
  fi
  if [ "$RUNTIME" = jvm ]; then
    [ "$JAVA_MAJOR" -ge 21 ] || fail 'JVM installation needs Java 21+ (recommended: 21 or 25).'
    EXECUTABLE=$(command -v java)
    ASSET=branchline-cli-$VERSION-all.jar
  else
    [ "$NODE_MAJOR" = 22 ] || [ "$NODE_MAJOR" = 24 ] || fail 'Node installation needs Node 22 or 24. Use --runtime jvm for Java.'
    command -v tar >/dev/null 2>&1 || fail 'Node installation needs tar'
    EXECUTABLE=$(command -v node)
    ASSET=branchline-cli-js-$VERSION.tgz
    progress 'Node selected: JS numbers do not provide the JVM exact-decimal profile.'
  fi
  case "$EXECUTABLE" in /*) ;; *) fail 'Runtime must resolve to an absolute executable path; use an absolute PATH entry.' ;; esac
  progress "Installing $VERSION using $RUNTIME ($EXECUTABLE)"
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
  progress 'Downloading published CLI and SHA256SUMS...'
  download "$RELEASE_URL/$ASSET" "$TMP_ROOT/$ASSET" || fail 'CLI download failed; existing installation unchanged.'
  download "$RELEASE_URL/SHA256SUMS" "$TMP_ROOT/SHA256SUMS" || fail 'Checksum download failed; existing installation unchanged.'
  EXPECTED=$(awk -v name="$ASSET" '$2 == name { print $1 }' "$TMP_ROOT/SHA256SUMS")
  [ "${#EXPECTED}" -eq 64 ] || fail 'Missing, duplicate or invalid release checksum'
  case "$EXPECTED" in *[!0-9a-f]*) fail 'Invalid release checksum' ;; esac
  if [ "$HASH" = sha256sum ]; then ACTUAL=$(sha256sum "$TMP_ROOT/$ASSET" | awk '{print $1}')
  else ACTUAL=$(shasum -a 256 "$TMP_ROOT/$ASSET" | awk '{print $1}'); fi
  [ "$ACTUAL" = "$EXPECTED" ] || fail 'SHA-256 mismatch; existing installation unchanged.'
  progress 'Checksum verified. Preparing launcher...'
  STAGE=$(mktemp -d "$INSTALL_ROOT/releases/$VERSION-$RUNTIME.XXXXXX")
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
    'To update or change runtime, rerun the installer. Previous payloads are retained.'
}
main "$@"
