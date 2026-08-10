#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
  echo "Usage: $0 <batch-root> <machine-id> <image-id> <attempt-number>" >&2
  exit 21
fi

BATCH_ROOT="$1"
MACHINE_ID="$2"
IMAGE_ID="$3"
ATTEMPT="$4"

ATTEMPT_DIR="${BATCH_ROOT}/full/${MACHINE_ID}/${IMAGE_ID}/attempt_${ATTEMPT}"
COMBINED_RESULT="${ATTEMPT_DIR}/result.properties"

echo "attempt directory: ${ATTEMPT_DIR}"

if [[ ! -d "${ATTEMPT_DIR}" ]]; then
  echo "ERROR: missing attempt directory: ${ATTEMPT_DIR}" >&2
  exit 1
fi

print_stage() {
  local stage_name="$1"
  local stage_dir="${ATTEMPT_DIR}/${stage_name}"
  local job_properties="${stage_dir}/job.properties"
  local result_properties="${stage_dir}/result.properties"
  local stdout_log="${stage_dir}/stdout.log"
  local stderr_log="${stage_dir}/stderr.log"

  echo
  echo "############################################################"
  echo "# stage: ${stage_name}"
  echo "############################################################"

  if [[ ! -d "${stage_dir}" ]]; then
    echo "(no ${stage_name} directory — stage was reused from the production dataset and never launched)"
    return
  fi

  echo
  echo "=== ${stage_name}/job.properties ==="
  if [[ -f "${job_properties}" ]]; then
    cat "${job_properties}"
  else
    echo "(missing: ${job_properties})"
  fi

  echo
  echo "=== ${stage_name}/result.properties ==="
  if [[ -f "${result_properties}" ]]; then
    cat "${result_properties}"
    echo
    echo "--- ${stage_name} summary ---"
    awk -F'=' '
      $1=="status" {status=$2}
      $1=="reason_code" {reason=$2}
      $1=="out_of_memory_detected" {oom=$2}
      $1=="processing_time_ms" {ms=$2}
      END {
        printf "status: %s\n", status
        printf "reason_code: %s\n", reason
        printf "out_of_memory_detected: %s\n", oom
        printf "processing_time_ms: %s\n", ms
      }' "${result_properties}"
  else
    echo "(missing: ${result_properties}) — stage did not complete or Fiji has not run yet"
  fi

  echo
  echo "=== ${stage_name}/stdout.log (last 40 lines) ==="
  if [[ -f "${stdout_log}" ]]; then
    tail -n 40 "${stdout_log}"
  else
    echo "(missing: ${stdout_log})"
  fi

  echo
  echo "=== ${stage_name}/stderr.log (last 40 lines) ==="
  if [[ -f "${stderr_log}" ]]; then
    tail -n 40 "${stderr_log}"
  else
    echo "(missing: ${stderr_log})"
  fi
}

echo
echo "############################################################"
echo "# combined attempt result (written by the parent CLI process)"
echo "############################################################"
echo
echo "=== result.properties ==="
if [[ -f "${COMBINED_RESULT}" ]]; then
  cat "${COMBINED_RESULT}"
  echo
  echo "--- combined summary ---"
  awk -F'=' '
    $1=="status" {status=$2}
    $1=="reason_code" {reason=$2}
    $1=="failed_stage" {failed_stage=$2}
    $1=="classification_stage_reused" {reused=$2}
    $1=="classification_process_launched" {class_launched=$2}
    $1=="classification_exit_code" {class_exit=$2}
    $1=="reconstruction_process_launched" {recon_launched=$2}
    $1=="reconstruction_exit_code" {recon_exit=$2}
    $1=="out_of_memory_detected" {oom=$2}
    $1=="required_outputs_validated" {validated=$2}
    $1=="processing_time_ms" {ms=$2}
    END {
      printf "status: %s\n", status
      printf "reason_code: %s\n", reason
      printf "failed_stage: %s\n", failed_stage
      printf "classification_stage_reused: %s\n", reused
      printf "classification_process_launched: %s (exit_code=%s)\n", class_launched, class_exit
      printf "reconstruction_process_launched: %s (exit_code=%s)\n", recon_launched, recon_exit
      printf "out_of_memory_detected: %s\n", oom
      printf "required_outputs_validated: %s\n", validated
      printf "processing_time_ms: %s\n", ms
    }' "${COMBINED_RESULT}"
else
  echo "(missing: ${COMBINED_RESULT}) — the parent CLI process has not finished this attempt yet"
fi

print_stage "classification"
print_stage "reconstruction"
