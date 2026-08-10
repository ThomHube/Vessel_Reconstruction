#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <batch-root> <machine-id>" >&2
  exit 12
fi

BATCH_ROOT="$1"
MACHINE_ID="$2"
CSV="${BATCH_ROOT}/precheck/${MACHINE_ID}/precheck_results.csv"

if [[ ! -f "${CSV}" ]]; then
  echo "ERROR: missing precheck results CSV: ${CSV}" >&2
  exit 1
fi

echo "precheck CSV: ${CSV}"
TOTAL=$(($(wc -l < "${CSV}") - 1))
if [[ ${TOTAL} -lt 0 ]]; then TOTAL=0; fi
echo "total assigned/result rows: ${TOTAL}"

SUCCESS=$(awk -F',' 'NR>1 {gsub(/"/,""); if ($9=="PRECHECK_SUCCESS") c++} END{print c+0}' "${CSV}")
MASK=$(awk -F',' 'NR>1 {gsub(/"/,""); if ($9=="REVIEW_REQUIRED_MASK") c++} END{print c+0}' "${CSV}")
SCALE=$(awk -F',' 'NR>1 {gsub(/"/,""); if ($9=="REVIEW_REQUIRED_SCALE_BAR") c++} END{print c+0}' "${CSV}")
FAIL=$(awk -F',' 'NR>1 {gsub(/"/,""); if ($9!="PRECHECK_SUCCESS" && $9!="REVIEW_REQUIRED_MASK" && $9!="REVIEW_REQUIRED_SCALE_BAR") c++} END{print c+0}' "${CSV}")

echo "PRECHECK_SUCCESS: ${SUCCESS}"
echo "REVIEW_REQUIRED_MASK: ${MASK}"
echo "REVIEW_REQUIRED_SCALE_BAR: ${SCALE}"
echo "failure count: ${FAIL}"
echo
echo "=== per-image summary ==="
awk -F',' 'NR>1 {
  gsub(/"/,"");
  printf "%s\t%s\t%s\tmask=%s\tscale=%s\n", $3, $9, $10, $36, $37
}' "${CSV}"
