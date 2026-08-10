#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CLASSES_DIR="${PROJECT_ROOT}/target/classes"

if [[ ! -d "${CLASSES_DIR}" ]]; then
  echo "ERROR: ${CLASSES_DIR} is missing. Run 'mvn compile' or 'mvn package' first." >&2
  exit 1
fi

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <input-root> <batch-root> <config-path>" >&2
  exit 2
fi

INPUT_ROOT="$1"
BATCH_ROOT="$2"
CONFIG_PATH="$3"

exec java -cp "${CLASSES_DIR}" vesselreconstruction.ellipse.BatchManifestPlannerCli \
  --input-root "${INPUT_ROOT}" \
  --batch-root "${BATCH_ROOT}" \
  --config "${CONFIG_PATH}"
