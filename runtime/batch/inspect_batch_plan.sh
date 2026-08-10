#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <batch-root>" >&2
  exit 2
fi

BATCH_ROOT="$1"

if [[ ! -d "${BATCH_ROOT}" ]]; then
  echo "ERROR: batch root does not exist: ${BATCH_ROOT}" >&2
  exit 1
fi

MASTER="${BATCH_ROOT}/master_batch_manifest.csv"
SUMMARY="${BATCH_ROOT}/machine_assignment_summary.txt"
REPORT="${BATCH_ROOT}/batch_plan_report.txt"
MACHINES_DIR="${BATCH_ROOT}/machines"

echo "=== batch_plan_report.txt ==="
if [[ -f "${REPORT}" ]]; then
  cat "${REPORT}"
else
  echo "(missing)"
fi
echo

echo "=== machine_assignment_summary.txt ==="
if [[ -f "${SUMMARY}" ]]; then
  cat "${SUMMARY}"
else
  echo "(missing)"
fi
echo

echo "=== master_batch_manifest.csv header ==="
if [[ -f "${MASTER}" ]]; then
  head -n 1 "${MASTER}"
  MASTER_ROWS=$(($(wc -l < "${MASTER}") - 1))
  if [[ ${MASTER_ROWS} -lt 0 ]]; then
    MASTER_ROWS=0
  fi
  echo "master row count (excluding header): ${MASTER_ROWS}"
else
  echo "(missing)"
fi
echo

if [[ -d "${MACHINES_DIR}" ]]; then
  shopt -s nullglob
  for manifest in "${MACHINES_DIR}"/machine_*_manifest.csv; do
    echo "=== $(basename "${manifest}") ==="
    ROWS=$(($(wc -l < "${manifest}") - 1))
    if [[ ${ROWS} -lt 0 ]]; then
      ROWS=0
    fi
    echo "row count (excluding header): ${ROWS}"
    if [[ ${ROWS} -gt 0 ]]; then
      # image_id is column 3 (1-based)
      FIRST="$(awk -F',' 'NR==2 {print $3}' "${manifest}" | tr -d '"')"
      LAST="$(awk -F',' -v rows="${ROWS}" 'NR==rows+1 {print $3}' "${manifest}" | tr -d '"')"
      echo "first assigned image: ${FIRST}"
      echo "last assigned image: ${LAST}"
    else
      echo "first assigned image: NA"
      echo "last assigned image: NA"
    fi
    echo
  done
else
  echo "machines directory missing: ${MACHINES_DIR}"
fi
