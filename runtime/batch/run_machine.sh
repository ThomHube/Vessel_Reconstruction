#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CLASSES_DIR="${PROJECT_ROOT}/target/classes"

if [[ ! -d "${CLASSES_DIR}" ]]; then
  echo "ERROR: ${CLASSES_DIR} is missing. Run 'mvn package' first." >&2
  exit 2
fi

if [[ $# -ne 7 ]]; then
  echo "Usage: $0 <input-root> <batch-root> <machine-id> <config> <classifier> <fiji-launcher> <tile-profile-index>" >&2
  exit 2
fi

INPUT_ROOT="$1"
BATCH_ROOT="$2"
MACHINE_ID="$3"
CONFIG_PATH="$4"
CLASSIFIER_PATH="$5"
FIJI_LAUNCHER="$6"
TILE_PROFILE_INDEX="$7"

exec java -cp "${CLASSES_DIR}" vesselreconstruction.ellipse.BatchMachineSupervisorCli \
  --input-root "${INPUT_ROOT}" \
  --batch-root "${BATCH_ROOT}" \
  --machine-id "${MACHINE_ID}" \
  --config "${CONFIG_PATH}" \
  --classifier "${CLASSIFIER_PATH}" \
  --fiji-launcher "${FIJI_LAUNCHER}" \
  --tile-profile-index "${TILE_PROFILE_INDEX}"
