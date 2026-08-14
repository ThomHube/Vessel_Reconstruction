#!/usr/bin/env bash
# Lumen portable machine-status monitor (read-only, one-shot snapshot).
#
# Installed location:
#   $HOME/LumenRuntime/6dd42df/monitor_lumen_machine.sh
#
# Usage:
#   ./monitor_lumen_machine.sh
#
# Optional external refresh (not built into this script):
#   watch -n 10 ./monitor_lumen_machine.sh
#
# This script never launches, stops, signals, or modifies processing.
#
set -euo pipefail

readonly EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
readonly EXPECTED_COMMIT_SHORT='6dd42df'

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3

SCRIPT_PATH=''
INSTALL_ROOT=''

PROP_schema_version=''
PROP_production_commit=''
PROP_production_commit_short=''
PROP_machine_id=''
PROP_installation_root=''
PROP_work_root=''

JOB_ROOT=''
SUPERVISOR_ROOT=''
PROGRESS_FILE=''
SUMMARY_FILE=''

fail() {
  local code="$1"
  shift
  echo "ERROR: $*" >&2
  exit "${code}"
}

usage() {
  cat >&2 <<'EOF'
Usage:
  ./monitor_lumen_machine.sh

Print one read-only status snapshot for the installed portable machine and exit.
Does not launch, stop, or modify processing.

Optional repeated display:
  watch -n 10 ./monitor_lumen_machine.sh
EOF
}

resolve_script_path() {
  local source="${BASH_SOURCE[0]}"
  local dir
  while [[ -L "${source}" ]]; do
    dir="$(cd -P "$(dirname -- "${source}")" && pwd)"
    source="$(readlink -- "${source}")"
    if [[ "${source}" != /* ]]; then
      source="${dir}/${source}"
    fi
  done
  SCRIPT_PATH="$(cd -P "$(dirname -- "${source}")" && pwd)/$(basename -- "${source}")"
  INSTALL_ROOT="$(cd -P "$(dirname -- "${SCRIPT_PATH}")" && pwd)"
}

reject_symlink() {
  local path="$1"
  local label="$2"
  if [[ -L "${path}" ]]; then
    fail "${EXIT_VALIDATION}" "${label} must not be a symbolic link: ${path}"
  fi
}

require_file() {
  local path="$1"
  local label="$2"
  [[ -f "${path}" ]] || fail "${EXIT_VALIDATION}" "Missing ${label}: ${path}"
}

prop_value() {
  local props_path="$1"
  local key="$2"
  local line
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    case "${line}" in
      "${key}="*)
        printf '%s\n' "${line#*=}"
        return 0
        ;;
    esac
  done <"${props_path}"
  return 1
}

load_installation_properties() {
  local props_path="$1"
  require_file "${props_path}" "installation.properties"
  reject_symlink "${props_path}" "installation.properties"

  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    case "${line}" in
      *=*)
        key="${line%%=*}"
        value="${line#*=}"
        ;;
      *)
        continue
        ;;
    esac
    case "${key}" in
      schema_version) PROP_schema_version="${value}" ;;
      production_commit) PROP_production_commit="${value}" ;;
      production_commit_short) PROP_production_commit_short="${value}" ;;
      machine_id) PROP_machine_id="${value}" ;;
      installation_root) PROP_installation_root="${value}" ;;
      work_root) PROP_work_root="${value}" ;;
    esac
  done <"${props_path}"

  local required_name
  for required_name in \
    schema_version \
    production_commit \
    production_commit_short \
    machine_id \
    installation_root \
    work_root
  do
    local varname="PROP_${required_name}"
    if [[ -z "${!varname}" ]]; then
      fail "${EXIT_VALIDATION}" "installation.properties missing required key: ${required_name}"
    fi
  done
}

verify_installation_identity() {
  if [[ "${PROP_installation_root}" != "${INSTALL_ROOT}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "installation_root mismatch: properties=${PROP_installation_root} actual=${INSTALL_ROOT}"
  fi
  if [[ "${PROP_production_commit}" != "${EXPECTED_COMMIT}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "production_commit mismatch: expected ${EXPECTED_COMMIT}, found ${PROP_production_commit}"
  fi
  if [[ "${PROP_production_commit_short}" != "${EXPECTED_COMMIT_SHORT}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "production_commit_short mismatch: expected ${EXPECTED_COMMIT_SHORT}, found ${PROP_production_commit_short}"
  fi
  if [[ ! "${PROP_machine_id}" =~ ^machine_[0-9]+$ ]]; then
    fail "${EXIT_VALIDATION}" "Invalid machine_id in installation.properties: ${PROP_machine_id}"
  fi
  local expected_work="${INSTALL_ROOT}/work/${PROP_machine_id}"
  if [[ "${PROP_work_root}" != "${expected_work}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "work_root mismatch: expected ${expected_work}, found ${PROP_work_root}"
  fi
  reject_symlink "${PROP_work_root}" "work_root"
}

is_nonneg_int() {
  [[ "${1:-}" =~ ^[0-9]+$ ]]
}

is_nonneg_number() {
  [[ "${1:-}" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

now_epoch_ms() {
  local ms
  ms="$(date +%s%3N 2>/dev/null || true)"
  if [[ "${ms}" =~ ^[0-9]+$ ]]; then
    printf '%s\n' "${ms}"
    return 0
  fi
  printf '%s\n' "$(($(date +%s) * 1000))"
}

file_mtime_epoch_ms() {
  local path="$1"
  local sec
  sec="$(stat -c '%Y' -- "${path}" 2>/dev/null || true)"
  if ! is_nonneg_int "${sec}"; then
    return 1
  fi
  printf '%s\n' "$((sec * 1000))"
}

format_duration_ms() {
  local ms="$1"
  if ! is_nonneg_int "${ms}"; then
    echo "unknown"
    return 0
  fi
  if (( ms < 1000 )); then
    echo "${ms} ms"
    return 0
  fi
  local total_s=$((ms / 1000))
  if (( total_s < 60 )); then
    echo "${total_s} s"
    return 0
  fi
  local minutes=$((total_s / 60))
  local seconds=$((total_s % 60))
  if (( minutes < 60 )); then
    printf '%d m %02d s\n' "${minutes}" "${seconds}"
    return 0
  fi
  local hours=$((minutes / 60))
  local rem_m=$((minutes % 60))
  if (( seconds == 0 )); then
    printf '%d h %02d m\n' "${hours}" "${rem_m}"
  else
    printf '%d h %02d m %02d s\n' "${hours}" "${rem_m}" "${seconds}"
  fi
}

# Parse one CSV row (quoted fields supported) into CSV_FIELDS array.
parse_csv_row() {
  local line="$1"
  CSV_FIELDS=()
  local field=''
  local in_quotes=0
  local i c next
  local len=${#line}
  for ((i = 0; i < len; i++)); do
    c="${line:i:1}"
    if (( in_quotes )); then
      if [[ "${c}" == '"' ]]; then
        next="${line:i+1:1}"
        if [[ "${next}" == '"' ]]; then
          field+='"'
          ((i++))
        else
          in_quotes=0
        fi
      else
        field+="${c}"
      fi
    else
      case "${c}" in
        '"')
          in_quotes=1
          ;;
        ',')
          CSV_FIELDS+=("${field}")
          field=''
          ;;
        *)
          field+="${c}"
          ;;
      esac
    fi
  done
  CSV_FIELDS+=("${field}")
}

select_highest_attempt_dir() {
  local full_root="$1"
  local best_n=-1
  local best_dir=''
  local d name n
  shopt -s nullglob
  for d in "${full_root}"/attempt_*; do
    [[ -d "${d}" ]] || continue
    [[ -L "${d}" ]] && continue
    name="$(basename -- "${d}")"
    if [[ ! "${name}" =~ ^attempt_([1-9][0-9]*)$ ]]; then
      continue
    fi
    n="${BASH_REMATCH[1]}"
    if (( n > best_n )); then
      best_n="${n}"
      best_dir="${d}"
    fi
  done
  shopt -u nullglob
  printf '%s\n' "${best_dir}"
}

detect_full_processing_stage() {
  local attempt_dir="$1"
  if [[ -z "${attempt_dir}" ]]; then
    echo "FULL PROCESSING STARTING"
    return 0
  fi
  if [[ -f "${attempt_dir}/result.properties" ]]; then
    echo "FULL PROCESSING COMPLETE"
  elif [[ -f "${attempt_dir}/reconstruction/result.properties" ]]; then
    echo "RECONSTRUCTION COMPLETE"
  elif [[ -f "${attempt_dir}/reconstruction/job.properties" ]]; then
    echo "RECONSTRUCTION RUNNING"
  elif [[ -f "${attempt_dir}/classification/result.properties" ]]; then
    echo "CLASSIFICATION COMPLETE / TRANSITION"
  elif [[ -f "${attempt_dir}/classification/job.properties" ]]; then
    echo "CLASSIFICATION RUNNING"
  else
    echo "FULL PROCESSING STARTING"
  fi
}

detect_processing_process() {
  if ! command -v ps >/dev/null 2>&1; then
    echo "UNKNOWN"
    return 0
  fi
  local out
  # Observational only: match portable install root / this machine job.
  if out="$(ps -ewwo args= 2>/dev/null || true)"; then
    if grep -F -- "${INSTALL_ROOT}" <<<"${out}" \
      | grep -E 'BatchMachineSupervisorCli|BatchFullProcessingCli|ImageJ-linux64' \
      | grep -Fq -- "${PROP_machine_id}"; then
      echo "YES"
      return 0
    fi
    if grep -F -- "${INSTALL_ROOT}" <<<"${out}" \
      | grep -Eq 'BatchMachineSupervisorCli|BatchFullProcessingCli|ImageJ-linux64'; then
      echo "YES"
      return 0
    fi
  fi
  echo "NO"
}

summarize_successful_timings() {
  # Sets: SAMPLE_COUNT MEAN_ELAPSED_MS
  SAMPLE_COUNT=0
  MEAN_ELAPSED_MS=0
  local summary="$1"
  [[ -f "${summary}" ]] || return 0
  reject_symlink "${summary}" "machine_run_summary.csv"

  local header_line
  IFS= read -r header_line <"${summary}" || true
  header_line="${header_line%$'\r'}"
  parse_csv_row "${header_line}"
  local header=("${CSV_FIELDS[@]}")
  local idx_attempted=-1 idx_status=-1 idx_elapsed=-1
  local i
  for i in "${!header[@]}"; do
    case "${header[$i]}" in
      processing_attempted) idx_attempted=$i ;;
      final_status) idx_status=$i ;;
      elapsed_ms) idx_elapsed=$i ;;
    esac
  done
  if (( idx_attempted < 0 || idx_status < 0 || idx_elapsed < 0 )); then
    return 0
  fi

  local line sum=0 count=0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" ]] && continue
    parse_csv_row "${line}"
    # Skip accidental re-read of header-like rows.
    if [[ "${CSV_FIELDS[0]:-}" == "schema_version" ]]; then
      continue
    fi
    local attempted="${CSV_FIELDS[$idx_attempted]:-}"
    local status="${CSV_FIELDS[$idx_status]:-}"
    local elapsed="${CSV_FIELDS[$idx_elapsed]:-}"
    if [[ "${attempted}" != "TRUE" ]]; then
      continue
    fi
    if [[ "${status}" != "FULL_SUCCESS" ]]; then
      continue
    fi
    if ! is_nonneg_int "${elapsed}"; then
      continue
    fi
    sum=$((sum + elapsed))
    count=$((count + 1))
  done <"${summary}"

  SAMPLE_COUNT="${count}"
  if (( count > 0 )); then
    MEAN_ELAPSED_MS=$((sum / count))
  fi
}

print_no_imported_job() {
  cat <<EOF
LUMEN MACHINE STATUS
machine: ${PROP_machine_id}
state: NO_IMPORTED_JOB
EOF
}

print_not_started() {
  cat <<EOF
LUMEN MACHINE STATUS
machine: ${PROP_machine_id}
state: NOT_STARTED
current stage: NOT_STARTED
EOF
}

compute_state() {
  # Uses progress fields; prints state to stdout.
  # completed==assigned means every assigned IMAGE is FULL_SUCCESS.
  # COMPLETE additionally requires status=MACHINE_SUCCESS, which means
  # physical-core coordinate assembly also succeeded. Count-complete
  # MACHINE_FAILED_UNEXPECTED / failure / blocked / missing status must
  # not be reported as COMPLETE.
  local completed="$1"
  local assigned="$2"
  local remaining="$3"
  local image_id="$4"
  local action="$5"
  local status="$6"
  if [[ "${status}" == "MACHINE_SUCCESS" \
      && "${completed}" == "${assigned}" \
      && "${remaining}" == "0" \
      && "${image_id}" == "NA" \
      && "${action}" == "NA" ]]; then
    echo "COMPLETE"
    return 0
  fi
  if [[ "${image_id}" != "NA" || "${action}" != "NA" ]]; then
    echo "RUNNING"
    return 0
  fi
  echo "IDLE_OR_INTERRUPTED"
}

print_status_snapshot() {
  local progress="$1"
  reject_symlink "${progress}" "machine_progress.properties"

  local schema machine_id timestamp status assigned completed percent
  local sequence image_id action attempted succeeded skipped blocked failed remaining elapsed
  schema="$(prop_value "${progress}" "schema_version" || true)"
  machine_id="$(prop_value "${progress}" "machine_id" || true)"
  timestamp="$(prop_value "${progress}" "timestamp_epoch_ms" || true)"
  status="$(prop_value "${progress}" "status" || true)"
  assigned="$(prop_value "${progress}" "assigned_count" || true)"
  completed="$(prop_value "${progress}" "completed_count" || true)"
  percent="$(prop_value "${progress}" "progress_percent" || true)"
  sequence="$(prop_value "${progress}" "current_sequence" || true)"
  image_id="$(prop_value "${progress}" "current_image_id" || true)"
  action="$(prop_value "${progress}" "current_action" || true)"
  attempted="$(prop_value "${progress}" "attempted_count" || true)"
  succeeded="$(prop_value "${progress}" "succeeded_this_run_count" || true)"
  skipped="$(prop_value "${progress}" "skipped_complete_count" || true)"
  blocked="$(prop_value "${progress}" "blocked_count" || true)"
  failed="$(prop_value "${progress}" "failed_count" || true)"
  remaining="$(prop_value "${progress}" "remaining_count" || true)"
  elapsed="$(prop_value "${progress}" "elapsed_ms" || true)"

  # Conservative defaults for missing/malformed optional numeric fields.
  local numeric_ok=1
  for v in assigned completed sequence attempted succeeded skipped blocked failed remaining elapsed timestamp; do
    local val="${!v}"
    if ! is_nonneg_int "${val}"; then
      numeric_ok=0
      break
    fi
  done
  if [[ -z "${image_id}" ]]; then image_id="NA"; fi
  if [[ -z "${action}" ]]; then action="NA"; fi
  if ! is_nonneg_number "${percent}"; then
    percent="?"
  fi
  if (( numeric_ok == 0 )); then
    cat <<EOF
LUMEN MACHINE STATUS
machine: ${PROP_machine_id}
state: IDLE_OR_INTERRUPTED
note: progress file present but some numeric fields are missing/malformed; reporting conservatively
persisted status: ${status:-unknown}
current image: ${image_id}
planned action: ${action}
processing process detected: $(detect_processing_process)
EOF
    return 0
  fi

  if [[ -n "${machine_id}" && "${machine_id}" != "${PROP_machine_id}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "progress machine_id ${machine_id} != installed ${PROP_machine_id}"
  fi

  local state
  state="$(compute_state "${completed}" "${assigned}" "${remaining}" "${image_id}" "${action}" "${status}")"

  local live_elapsed_ms
  if [[ "${state}" == "COMPLETE" ]]; then
    live_elapsed_ms="${elapsed}"
  else
    local supervisor_start_ms=$((timestamp - elapsed))
    if (( supervisor_start_ms < 0 )); then
      supervisor_start_ms=0
    fi
    local now_ms
    now_ms="$(now_epoch_ms)"
    live_elapsed_ms=$((now_ms - supervisor_start_ms))
    if (( live_elapsed_ms < 0 )); then
      live_elapsed_ms=0
    fi
  fi

  echo "LUMEN MACHINE STATUS"
  echo "machine: ${PROP_machine_id}"
  echo "state: ${state}"
  echo "persisted status: ${status:-unknown}"
  echo "overall: ${completed} / ${assigned} complete (${percent}%)"
  echo "successful this run: ${succeeded}"
  echo "already complete/skipped: ${skipped}"
  echo "blocked: ${blocked}"
  echo "failed: ${failed}"
  echo "remaining: ${remaining}"
  echo "run elapsed: $(format_duration_ms "${live_elapsed_ms}")"

  local current_image_elapsed_ms=''
  local current_stage=''
  local attempt_dir=''

  if [[ "${image_id}" != "NA" ]]; then
    echo "current image: ${image_id}"
    echo "sequence: ${sequence} / ${assigned}"
    echo "planned action: ${action}"

    if [[ "${action}" == "RUN_FULL_PROCESSING" ]]; then
      local full_root="${JOB_ROOT}/batch/full/${PROP_machine_id}/${image_id}"
      if [[ -d "${full_root}" && ! -L "${full_root}" ]]; then
        attempt_dir="$(select_highest_attempt_dir "${full_root}")"
      fi
      current_stage="$(detect_full_processing_stage "${attempt_dir}")"
      echo "current stage: ${current_stage}"

      local start_ms=''
      if [[ -n "${attempt_dir}" ]]; then
        if [[ -f "${attempt_dir}/classification/job.properties" ]]; then
          start_ms="$(file_mtime_epoch_ms "${attempt_dir}/classification/job.properties" || true)"
        fi
        if [[ -z "${start_ms}" ]]; then
          start_ms="$(file_mtime_epoch_ms "${attempt_dir}" || true)"
        fi
      fi
      if is_nonneg_int "${start_ms}"; then
        local now_ms2
        now_ms2="$(now_epoch_ms)"
        current_image_elapsed_ms=$((now_ms2 - start_ms))
        if (( current_image_elapsed_ms < 0 )); then
          current_image_elapsed_ms=0
        fi
        echo "current image elapsed: $(format_duration_ms "${current_image_elapsed_ms}") (approximate)"
      fi
    else
      echo "current stage: ${action}"
    fi
  elif [[ "${state}" == "NOT_STARTED" ]]; then
    echo "current stage: NOT_STARTED"
  fi

  summarize_successful_timings "${SUMMARY_FILE}"
  if (( SAMPLE_COUNT > 0 )); then
    echo "mean completed image: $(format_duration_ms "${MEAN_ELAPSED_MS}")"
    echo "ETA sample images: ${SAMPLE_COUNT}"
  else
    echo "mean completed image: not available"
    echo "ETA sample images: 0"
  fi

  if [[ "${state}" == "COMPLETE" ]]; then
    echo "estimated remaining: 0 s"
  elif [[ "${state}" == "RUNNING" && "${remaining}" != "0" && "${SAMPLE_COUNT}" -gt 0 ]]; then
    local estimated
    if is_nonneg_int "${current_image_elapsed_ms}"; then
      local current_remaining=$((MEAN_ELAPSED_MS - current_image_elapsed_ms))
      if (( current_remaining < 0 )); then
        current_remaining=0
      fi
      local other=$((remaining - 1))
      if (( other < 0 )); then
        other=0
      fi
      estimated=$((current_remaining + MEAN_ELAPSED_MS * other))
    else
      estimated=$((MEAN_ELAPSED_MS * remaining))
    fi
    echo "estimated remaining: ~$(format_duration_ms "${estimated}")"
  else
    echo "estimated remaining: not available yet"
  fi

  echo "processing process detected: $(detect_processing_process)"
  if [[ -n "${schema}" ]]; then
    echo "progress schema: ${schema}"
  fi
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./monitor_lumen_machine.sh" >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  if [[ "$#" -ne 0 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected no arguments"
  fi

  resolve_script_path
  load_installation_properties "${INSTALL_ROOT}/installation.properties"
  verify_installation_identity

  JOB_ROOT="${PROP_work_root}/job"
  if [[ ! -d "${JOB_ROOT}" ]]; then
    print_no_imported_job
    exit 0
  fi
  reject_symlink "${JOB_ROOT}" "imported job root"

  SUPERVISOR_ROOT="${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}"
  PROGRESS_FILE="${SUPERVISOR_ROOT}/machine_progress.properties"
  SUMMARY_FILE="${SUPERVISOR_ROOT}/machine_run_summary.csv"

  if [[ ! -f "${PROGRESS_FILE}" ]]; then
    print_not_started
    exit 0
  fi

  print_status_snapshot "${PROGRESS_FILE}"
  exit 0
}

main "$@"
