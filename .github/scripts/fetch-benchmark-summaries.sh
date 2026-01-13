#!/usr/bin/env bash
set -euo pipefail

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

repo="${GITHUB_REPOSITORY:-}"
if [[ -z "$repo" ]]; then
  remote_url="$(git config --get remote.origin.url || true)"
  if [[ "$remote_url" =~ github.com[:/](.+/[^/.]+)(\.git)?$ ]]; then
    repo="${BASH_REMATCH[1]}"
  fi
fi
if [[ -z "$repo" ]]; then
  echo "Set GITHUB_REPOSITORY (owner/name) or ensure git remote points to GitHub." >&2
  exit 1
fi
if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "Set GH_TOKEN to a GitHub token with repo read access." >&2
  exit 1
fi

decode_base64() {
  if printf 'dGVzdA==' | base64 --decode >/dev/null 2>&1; then
    printf '%s' "$1" | base64 --decode
  else
    printf '%s' "$1" | base64 -D
  fi
}

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
docs_dir="${root_dir}/docs"
release_dir="${docs_dir}/benchmarks/releases"
jsonata_release_dir="${docs_dir}/benchmarks/jsonata/releases"
latest_path="${docs_dir}/benchmarks/latest.md"
index_path="${release_dir}/index.md"
list_path="${release_dir}/list.md"
jsonata_latest_path="${docs_dir}/benchmarks/jsonata/latest.md"
jsonata_index_path="${jsonata_release_dir}/index.md"
jsonata_list_path="${jsonata_release_dir}/list.md"

mkdir -p "$release_dir"
mkdir -p "$jsonata_release_dir"
: > "$index_path"
: > "$list_path"
: > "$jsonata_index_path"
: > "$jsonata_list_path"

releases="$(curl -sSL -H "Authorization: Bearer ${GH_TOKEN}" \
  "https://api.github.com/repos/${repo}/releases?per_page=100")"

latest_written=false
jsonata_latest_written=false
while IFS= read -r release; do
  decoded="$(decode_base64 "$release")"
  tag="$(jq -r '.tag_name' <<<"$decoded")"
  prerelease="$(jq -r '.prerelease' <<<"$decoded")"
  asset_url="$(jq -r --arg tag "$tag" \
    '.assets[]? | select(.name == ("branchline-jmh-summary-" + $tag + ".md")) | .browser_download_url' \
    <<<"$decoded" | head -1)"

  if [[ -z "$asset_url" || "$asset_url" == "null" ]]; then
    continue
  fi

  safe_tag="${tag//\//-}"
  safe_tag="${safe_tag// /-}"
  out_path="${release_dir}/${safe_tag}.md"
  curl -sSL -H "Authorization: Bearer ${GH_TOKEN}" -o "$out_path" "$asset_url"
  release_assets_dir="${release_dir}/${safe_tag}"
  mkdir -p "$release_assets_dir"
  csv_asset_url="$(jq -r --arg tag "$tag" \
    '.assets[]? | select(.name == ("branchline-jmh-summary-" + $tag + ".csv")) | .browser_download_url' \
    <<<"$decoded" | head -1)"
  if [[ -n "$csv_asset_url" && "$csv_asset_url" != "null" ]]; then
    curl -sSL -H "Authorization: Bearer ${GH_TOKEN}" -o "${release_assets_dir}/jmh-summary.csv" "$csv_asset_url"
  fi

  label="$tag"
  if [[ "$prerelease" == "true" ]]; then
    label="${tag} (prerelease)"
  fi
  echo "- [${label}](${safe_tag}/)" >> "$index_path"
  echo "- [${label}](releases/${safe_tag}/)" >> "$list_path"

  if [[ "$latest_written" == "false" ]]; then
    cp "$out_path" "$latest_path"
    latest_written=true
  fi

  jsonata_asset_url="$(jq -r --arg tag "$tag" \
    '.assets[]? | select(.name == ("branchline-jsonata-summary-" + $tag + ".md")) | .browser_download_url' \
    <<<"$decoded" | head -1)"
  if [[ -z "$jsonata_asset_url" || "$jsonata_asset_url" == "null" ]]; then
    continue
  fi

  jsonata_out_path="${jsonata_release_dir}/${safe_tag}.md"
  curl -sSL -H "Authorization: Bearer ${GH_TOKEN}" -o "$jsonata_out_path" "$jsonata_asset_url"
  jsonata_assets_dir="${jsonata_release_dir}/${safe_tag}"
  mkdir -p "$jsonata_assets_dir"
  jsonata_csv_asset_url="$(jq -r --arg tag "$tag" \
    '.assets[]? | select(.name == ("branchline-jsonata-summary-" + $tag + ".csv")) | .browser_download_url' \
    <<<"$decoded" | head -1)"
  if [[ -n "$jsonata_csv_asset_url" && "$jsonata_csv_asset_url" != "null" ]]; then
    curl -sSL -H "Authorization: Bearer ${GH_TOKEN}" -o "${jsonata_assets_dir}/jsonata-summary.csv" "$jsonata_csv_asset_url"
  fi
  echo "- [${label}](${safe_tag}/)" >> "$jsonata_index_path"
  echo "- [${label}](releases/${safe_tag}/)" >> "$jsonata_list_path"

  if [[ "$jsonata_latest_written" == "false" ]]; then
    cp "$jsonata_out_path" "$jsonata_latest_path"
    jsonata_latest_written=true
  fi
done < <(jq -r '.[] | select(.draft == false) | @base64' <<<"$releases")

if [[ "$latest_written" == "false" ]]; then
  echo "_No benchmark summaries found in releases._" > "$latest_path"
  echo "_No benchmark summaries found in releases._" >> "$index_path"
  echo "_No benchmark summaries found in releases._" >> "$list_path"
fi

if [[ "$jsonata_latest_written" == "false" ]]; then
  echo "_No JSONata benchmark summaries found in releases._" > "$jsonata_latest_path"
  echo "_No JSONata benchmark summaries found in releases._" >> "$jsonata_index_path"
  echo "_No JSONata benchmark summaries found in releases._" >> "$jsonata_list_path"
fi

echo "Updated:"
echo "  $latest_path"
echo "  $index_path"
echo "  $list_path"
echo "  $jsonata_latest_path"
echo "  $jsonata_index_path"
echo "  $jsonata_list_path"
