#!/usr/bin/env bash
set -euo pipefail

SCHEME="${1:-VolunteersAppiOS}"
SIMULATOR="${2:-iPhone 16}"

echo "Phase 26 Build Check"
echo "Scheme: ${SCHEME}"
echo "Simulator: ${SIMULATOR}"

echo "1) Debug simulator build"
xcodebuild \
  -scheme "${SCHEME}" \
  -configuration Debug \
  -destination "platform=iOS Simulator,name=${SIMULATOR}" \
  build

echo "2) Release archive build"
xcodebuild \
  -scheme "${SCHEME}" \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  archive

echo "Build checks completed."
