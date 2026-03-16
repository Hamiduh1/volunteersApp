#!/usr/bin/env bash
set -euo pipefail

STARTER_DIR="${1:-ios-migration/starter}"
TARGET_SRC_DIR="${2:-}"
README_FILE="${STARTER_DIR}/README.md"

if [[ ! -f "${README_FILE}" ]]; then
  echo "README not found: ${README_FILE}"
  exit 1
fi

if [[ -z "${TARGET_SRC_DIR}" ]]; then
  echo "Usage: $0 <starter_dir> <target_source_dir>"
  echo "Example: $0 ios-migration/starter VolunteersAppIOS"
  exit 1
fi

if [[ ! -d "${TARGET_SRC_DIR}" ]]; then
  echo "Target source directory not found: ${TARGET_SRC_DIR}"
  exit 1
fi

mapfile -t EXPECTED < <(
  awk '
    /Copy these files into your iOS target:/ { in_list=1; next }
    /Recommended destination in Xcode:/ { in_list=0 }
    in_list && /^- `/ {
      line=$0
      sub(/^- `/, "", line)
      sub(/`.*$/, "", line)
      print line
    }
  ' "${README_FILE}"
)

missing_in_starter=()
missing_in_target=()

for rel in "${EXPECTED[@]}"; do
  if [[ ! -f "${STARTER_DIR}/${rel}" ]]; then
    missing_in_starter+=("${rel}")
  fi
  if [[ ! -f "${TARGET_SRC_DIR}/${rel}" ]]; then
    missing_in_target+=("${rel}")
  fi
done

echo "Expected files: ${#EXPECTED[@]}"
echo "Missing in starter: ${#missing_in_starter[@]}"
for f in "${missing_in_starter[@]:-}"; do
  [[ -n "${f}" ]] && echo "  - ${f}"
done

echo "Missing in iOS target: ${#missing_in_target[@]}"
for f in "${missing_in_target[@]:-}"; do
  [[ -n "${f}" ]] && echo "  - ${f}"
done

if [[ ${#missing_in_starter[@]} -eq 0 && ${#missing_in_target[@]} -eq 0 ]]; then
  echo "Coverage check passed."
  exit 0
fi

exit 2
