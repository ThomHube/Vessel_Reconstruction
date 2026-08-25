#!/usr/bin/env bash
# One-command automated hardware benchmark for the single-machine architecture.
#
# BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.
#
# Usage:
#   ./runtime/portable/run_hardware_benchmark.sh
#       full automated benchmark: optional synthetic sanity, then
#       REAL_LUMEN_CLASSIFICATION (authoritative)
#
#   ./runtime/portable/run_hardware_benchmark.sh --synthetic-only
#       SYNTHETIC_PROBE only. Never production-ready.
#
# The benchmark detects hardware and chooses threads/heap. Do not pass those.
# Optional:
#   --input-root <dir>     TEST-ONLY 9999A TIFF tree (originals are never modified)
#   --fiji-launcher <p>    existing Fiji.app ImageJ-linux64
#   --classifier <p>       existing Classifier9.model
#   --synthetic-only       skip the real Lumen classification stage
#
set -euo pipefail

REPO="$(cd -P "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO}"

EXPECTED_COMMIT_SHORT="6dd42df"
LUMEN_RUNTIME_ROOT="${HOME}/LumenRuntime/${EXPECTED_COMMIT_SHORT}"

if [[ ! -f "${REPO}/target/classes/vesselreconstruction/ellipse/HardwareBenchmarkCli.class" ]]; then
  echo "Compiling benchmark classes (mvn -DskipTests compile)..."
  mvn -q -DskipTests compile
fi

JAVA_BIN=""
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
else
  JAVA_BIN="$(command -v java)"
fi
if [[ -z "${JAVA_BIN}" || ! -x "${JAVA_BIN}" ]]; then
  echo "ERROR: java executable not found" >&2
  exit 3
fi

COMMIT="NA"
if command -v git >/dev/null 2>&1; then
  COMMIT="$(git -C "${REPO}" rev-parse HEAD 2>/dev/null || echo NA)"
fi

USER_HAS_INPUT_ROOT=0
USER_HAS_FIJI=0
USER_HAS_CLASSIFIER=0
SYNTHETIC_ONLY=0
i=1
while [[ "${i}" -le "$#" ]]; do
  eval "arg=\${${i}}"
  case "${arg}" in
    --synthetic-only) SYNTHETIC_ONLY=1 ;;
    --input-root) USER_HAS_INPUT_ROOT=1 ;;
    --fiji-launcher) USER_HAS_FIJI=1 ;;
    --classifier) USER_HAS_CLASSIFIER=1 ;;
  esac
  i=$((i + 1))
done

FIJI_LAUNCHER="${LUMEN_RUNTIME_ROOT}/runtime/Fiji.app/ImageJ-linux64"
CLASSIFIER="${LUMEN_CLASSIFIER:-${LUMEN_RUNTIME_ROOT}/models/Classifier9.model}"

discover_input_root() {
  if [[ -n "${LUMEN_BENCHMARK_INPUT_ROOT:-}" && -d "${LUMEN_BENCHMARK_INPUT_ROOT}" ]]; then
    printf '%s\n' "${LUMEN_BENCHMARK_INPUT_ROOT}"
    return 0
  fi
  local candidate
  for candidate in \
      "${REPO}/testdata/tiffs_9999A" \
      "${HOME}/LumenTestData/tiffs_9999A"
  do
    if [[ -f "${candidate}/9999A1/9999A1.tif" || -f "${candidate}/9999A1.tif" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done
  candidate="/media/thomas/ADATA CH94/Masterarbeit/Lumen/03_TEST_DATA_NOT_FOR_ANALYSIS/9999A_Two_Segment_Workflow_Test/LumenWorkflowTest/tiffs_9999A"
  if [[ -f "${candidate}/9999A1/9999A1.tif" ]]; then
    printf '%s\n' "${candidate}"
    return 0
  fi
  return 1
}

ARGS=(
  --output-root "${REPO}/hardware_benchmark"
  --classes-dir "${REPO}/target/classes"
  --java-executable "${JAVA_BIN}"
  --source-commit "${COMMIT}"
)
if [[ "${USER_HAS_FIJI}" -eq 0 && -x "${FIJI_LAUNCHER}" ]]; then
  ARGS+=(--fiji-launcher "${FIJI_LAUNCHER}")
fi
if [[ "${SYNTHETIC_ONLY}" -eq 0 && "${USER_HAS_CLASSIFIER}" -eq 0 && -f "${CLASSIFIER}" ]]; then
  ARGS+=(--classifier "${CLASSIFIER}")
fi
if [[ "${SYNTHETIC_ONLY}" -eq 0 && "${USER_HAS_INPUT_ROOT}" -eq 0 ]]; then
  DISCOVERED_INPUT=""
  if DISCOVERED_INPUT="$(discover_input_root)"; then
    ARGS+=(--input-root "${DISCOVERED_INPUT}")
  fi
fi

echo "LUMEN AUTOMATED HARDWARE BENCHMARK"
echo "BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS."
echo "DEVELOPMENT MACHINE unless this host is labelled otherwise."
echo "repository: ${REPO}"
echo "java: ${JAVA_BIN}"
echo "source.commit: ${COMMIT}"
echo "tile geometry: 1024x1024 (fixed)"
if [[ "${SYNTHETIC_ONLY}" -eq 1 ]]; then
  echo "mode: SYNTHETIC_PROBE (not production-ready)"
else
  echo "mode: REAL_LUMEN_CLASSIFICATION (authoritative when successful)"
fi
echo

# ImageJ is required in the parent JVM only to copy/crop the TEST TIFF.
# Use the existing Fiji ij.jar so the crop path matches the installed runtime.
CLASSPATH="${REPO}/target/classes"
FIJI_APP=""
if [[ "${USER_HAS_FIJI}" -eq 1 ]]; then
  i=1
  while [[ "${i}" -le "$#" ]]; do
    eval "arg=\${${i}}"
    if [[ "${arg}" == "--fiji-launcher" ]]; then
      i=$((i + 1))
      eval "FIJI_LAUNCHER=\${${i}}"
      break
    fi
    i=$((i + 1))
  done
fi
if [[ -n "${FIJI_LAUNCHER}" && -e "${FIJI_LAUNCHER}" ]]; then
  FIJI_APP="$(cd -P "$(dirname -- "${FIJI_LAUNCHER}")" && pwd)"
fi
IJ_JAR=""
if [[ -n "${FIJI_APP}" && -d "${FIJI_APP}/jars" ]]; then
  for candidate in "${FIJI_APP}/jars"/ij-*.jar; do
    if [[ -f "${candidate}" ]]; then
      IJ_JAR="${candidate}"
      break
    fi
  done
fi
if [[ -n "${IJ_JAR}" ]]; then
  CLASSPATH="${CLASSPATH}:${IJ_JAR}"
  echo "imagej.jar: ${IJ_JAR}"
fi
echo

exec "${JAVA_BIN}" -cp "${CLASSPATH}" \
  vesselreconstruction.ellipse.HardwareBenchmarkCli \
  "${ARGS[@]}" \
  "$@"
