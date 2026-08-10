#!/usr/bin/env bash
# Phase 4C2A: central master batch preparation (no TIFF copies, no processing).
#
# Usage:
#   ./prepare_master_batch.sh <all-tiffs-input-root> <portable-job-root> <machine-count>
#
set -euo pipefail

readonly EXPECTED_COMMIT='0c1c326fc07f7e049f1d359226e810c7b8db24ce'
readonly EXPECTED_COMMIT_SHORT='0c1c326'
readonly EXPECTED_TIFF_COUNT=80
readonly EXPECTED_FIRST_IMAGE_ID='010101'
readonly EXPECTED_LAST_IMAGE_ID='022002'
readonly LUMEN_RUNTIME_ROOT="${HOME}/LumenRuntime/${EXPECTED_COMMIT_SHORT}"

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3
readonly EXIT_PLAN=4
readonly EXIT_VERIFY=5

INPUT_ROOT=''
JOB_ROOT=''
MACHINE_COUNT=''
STAGING_ROOT=''
INSTALL_ROOT=''
JDK_ROOT=''
PROJECT_ROOT=''
CLASSES_DIR=''
DEFAULT_CONFIG=''
VERIFY_LAUNCHER=''

cleanup_staging() {
  local staging="${STAGING_ROOT:-}"
  if [[ -z "${staging}" ]]; then
    return 0
  fi
  if [[ ! -d "${staging}" ]]; then
    STAGING_ROOT=''
    return 0
  fi
  local base parent
  base="$(basename -- "${staging}")"
  parent="$(cd -P "$(dirname -- "${staging}")" && pwd)" || return 0
  case "${base}" in
    .*.staging_*)
      ;;
    *)
      return 0
      ;;
  esac
  # Never delete HOME, job root, or input root.
  local abs
  abs="$(cd -P -- "${staging}" && pwd)" || return 0
  if [[ "${abs}" == "${HOME}" \
     || "${abs}" == "${JOB_ROOT:-}" \
     || "${abs}" == "${INPUT_ROOT:-}" ]]; then
    return 0
  fi
  rm -rf -- "${abs}"
  STAGING_ROOT=''
}

on_exit() {
  cleanup_staging
}

fail() {
  local code="$1"
  shift
  echo "ERROR: $*" >&2
  cleanup_staging
  exit "${code}"
}

usage() {
  cat >&2 <<'EOF'
Usage: ./prepare_master_batch.sh <all-tiffs-input-root> <portable-job-root> <machine-count>

Creates one authoritative central batch plan for laboratory distribution.
Does not copy TIFFs, run Fiji, or process images.

machine-count must be an integer from 1 through 99.
EOF
}

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "${EXIT_VALIDATION}" "Required command not found: ${cmd}"
}

sha256_of() {
  local path="$1"
  sha256sum -- "${path}" | awk '{print $1}'
}

normalize_dir() {
  local path="$1"
  local label="$2"
  [[ -d "${path}" ]] || fail "${EXIT_VALIDATION}" "${label} is not a directory: ${path}"
  (cd -P -- "${path}" && pwd)
}

machine_id_for_index() {
  local index="$1"
  printf 'machine_%02d' "${index}"
}

require_file() {
  local path="$1"
  local label="$2"
  [[ -f "${path}" ]] || fail "${EXIT_VERIFY}" "Missing ${label}: ${path}"
}

require_dir() {
  local path="$1"
  local label="$2"
  [[ -d "${path}" ]] || fail "${EXIT_VERIFY}" "Missing ${label}: ${path}"
}

# Safe key=value reader. Does not source the file as shell.
read_required_sha256_property() {
  local file_path="$1"
  local key="$2"
  local label="$3"
  local line value found='' count=0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    case "${line}" in
      "${key}="*)
        value="${line#*=}"
        value="${value%%[[:space:]]*}"
        count=$((count + 1))
        found="${value}"
        ;;
    esac
  done <"${file_path}"
  if [[ "${count}" -ne 1 ]]; then
    fail "${EXIT_VERIFY}" \
      "${label}: expected exactly one ${key}= line, found ${count}"
  fi
  if [[ ! "${found}" =~ ^[0-9a-f]{64}$ ]]; then
    fail "${EXIT_VERIFY}" \
      "${label}: ${key} must be 64 lowercase hex characters, found '${found}'"
  fi
  printf '%s\n' "${found}"
}

csv_field() {
  # Extract 1-based CSV field; strip surrounding quotes. Assumes no embedded commas.
  local line="$1"
  local index="$2"
  awk -F',' -v idx="${index}" '{
    val=$idx
    gsub(/^"/, "", val)
    gsub(/"$/, "", val)
    print val
  }' <<<"${line}"
}

resolve_lumen_runtime() {
  INSTALL_ROOT="${LUMEN_RUNTIME_ROOT}"
  require_dir "${INSTALL_ROOT}" "LumenRuntime installation"
  require_file "${INSTALL_ROOT}/installation.properties" "installation.properties"
  VERIFY_LAUNCHER="${INSTALL_ROOT}/run_lumen_machine.sh"
  require_file "${VERIFY_LAUNCHER}" "run_lumen_machine.sh"
  [[ -x "${VERIFY_LAUNCHER}" ]] \
    || fail "${EXIT_VALIDATION}" "run_lumen_machine.sh is not executable: ${VERIFY_LAUNCHER}"

  JDK_ROOT="${INSTALL_ROOT}/runtime/jdk21"
  PROJECT_ROOT="${INSTALL_ROOT}/project/minimal-ij1-plugin-ellipse"
  DEFAULT_CONFIG="${INSTALL_ROOT}/config/default_batch.properties"
  CLASSES_DIR="${PROJECT_ROOT}/target/classes"

  require_dir "${JDK_ROOT}" "portable JDK"
  require_file "${JDK_ROOT}/bin/java" "portable java"
  require_dir "${PROJECT_ROOT}" "installed project"
  require_file "${DEFAULT_CONFIG}" "default_batch.properties"
}

validate_machine_count() {
  local count="$1"
  if [[ ! "${count}" =~ ^[1-9][0-9]?$ ]] || [[ "${count}" -lt 1 || "${count}" -gt 99 ]]; then
    usage
    fail "${EXIT_USAGE}" "Invalid machine-count '${count}'. Expected integer 1..99."
  fi
  MACHINE_COUNT="${count}"
}

write_production_config() {
  local dest="$1"
  local saw_complete=0
  local saw_machines=0
  local line
  : >"${dest}"
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    case "${line}" in
      machines.count=*)
        printf 'machines.count=%s\n' "${MACHINE_COUNT}" >>"${dest}"
        saw_machines=1
        ;;
      input.require_complete_expected_set=*)
        printf '%s\n' "${line}" >>"${dest}"
        if [[ "${line}" == "input.require_complete_expected_set=true" ]]; then
          saw_complete=1
        fi
        ;;
      *)
        printf '%s\n' "${line}" >>"${dest}"
        ;;
    esac
  done <"${DEFAULT_CONFIG}"

  if [[ "${saw_machines}" -ne 1 ]]; then
    fail "${EXIT_VALIDATION}" "Source config missing machines.count"
  fi
  if [[ "${saw_complete}" -ne 1 ]]; then
    fail "${EXIT_VALIDATION}" \
      "production config must keep input.require_complete_expected_set=true"
  fi
}

run_central_planner() {
  local plan_root="$1"
  local config_path="$2"
  local log_path="$3"
  mkdir -p -- "${plan_root}/machines"

  if [[ ! -d "${CLASSES_DIR}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Controller classes missing after verify-only: ${CLASSES_DIR}"
  fi

  if ! (
    "${JDK_ROOT}/bin/java" -cp "${CLASSES_DIR}" \
      vesselreconstruction.ellipse.BatchManifestPlannerCli \
      --input-root "${INPUT_ROOT}" \
      --batch-root "${plan_root}" \
      --config "${config_path}"
  ) >"${log_path}" 2>&1; then
    fail "${EXIT_PLAN}" \
      "Central batch planner failed. See preparation log: ${log_path}"
  fi
}

validate_plan_artifacts() {
  local plan_root="$1"
  require_file "${plan_root}/master_batch_manifest.csv" "master_batch_manifest.csv"
  require_file "${plan_root}/machine_assignment_summary.txt" "machine_assignment_summary.txt"
  require_file "${plan_root}/batch_plan_report.txt" "batch_plan_report.txt"

  local i mid path
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    path="${plan_root}/machines/${mid}_manifest.csv"
    require_file "${path}" "${mid}_manifest.csv"
  done
}

validate_master_and_machines() {
  local plan_root="$1"
  local production_config_canonical_sha256="$2"
  local master="${plan_root}/master_batch_manifest.csv"

  local total_lines
  total_lines="$(wc -l <"${master}" | tr -d ' ')"
  if [[ "${total_lines}" -ne $((EXPECTED_TIFF_COUNT + 1)) ]]; then
    fail "${EXIT_VERIFY}" \
      "master_batch_manifest.csv must have $((EXPECTED_TIFF_COUNT + 1)) lines (header+${EXPECTED_TIFF_COUNT}); found ${total_lines}"
  fi

  local header
  IFS= read -r header <"${master}" || true
  [[ -n "${header}" ]] || fail "${EXIT_VERIFY}" "master manifest header missing"

  local first_id='' last_id='' prev_id=''
  local -A seen_ids=()
  local -A seen_sources=()
  local -A machine_counts=()
  local line image_id source_rel assigned config_hash
  local data_rows=0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" ]] && continue
    if [[ "${line}" == "${header}" ]]; then
      continue
    fi

    image_id="$(csv_field "${line}" 3)"
    source_rel="$(csv_field "${line}" 8)"
    assigned="$(csv_field "${line}" 11)"
    config_hash="$(csv_field "${line}" 21)"

    if [[ -z "${first_id}" ]]; then
      first_id="${image_id}"
    fi
    last_id="${image_id}"

    if [[ -n "${prev_id}" && ! "${image_id}" > "${prev_id}" ]]; then
      fail "${EXIT_VERIFY}" \
        "master manifest image_id order not strictly ascending at ${image_id} (previous ${prev_id})"
    fi
    prev_id="${image_id}"

    if [[ -n "${seen_ids[${image_id}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "duplicate image_id in master manifest: ${image_id}"
    fi
    seen_ids["${image_id}"]=1

    if [[ -n "${seen_sources[${source_rel}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "duplicate source_relative_path in master manifest: ${source_rel}"
    fi
    seen_sources["${source_rel}"]=1

    if [[ ! "${assigned}" =~ ^machine_(0[1-9]|[1-9][0-9])$ ]]; then
      fail "${EXIT_VERIFY}" "invalid assigned_machine in master manifest: ${assigned}"
    fi
    local idx
    idx=$((10#${assigned#machine_}))
    if [[ "${idx}" -lt 1 || "${idx}" -gt "${MACHINE_COUNT}" ]]; then
      fail "${EXIT_VERIFY}" \
        "assigned_machine ${assigned} outside expected machine_01..$(machine_id_for_index "${MACHINE_COUNT}")"
    fi
    machine_counts["${assigned}"]=$(( ${machine_counts[${assigned}]:-0} + 1 ))

    if [[ "${config_hash}" != "${production_config_canonical_sha256}" ]]; then
      fail "${EXIT_VERIFY}" \
        "configuration_sha256 mismatch in master for ${image_id}: expected ${production_config_canonical_sha256}, found ${config_hash}"
    fi

    data_rows=$((data_rows + 1))
  done <"${master}"

  if [[ "${data_rows}" -ne "${EXPECTED_TIFF_COUNT}" ]]; then
    fail "${EXIT_VERIFY}" "master data rows expected ${EXPECTED_TIFF_COUNT}, found ${data_rows}"
  fi
  if [[ "${#seen_ids[@]}" -ne "${EXPECTED_TIFF_COUNT}" ]]; then
    fail "${EXIT_VERIFY}" "expected ${EXPECTED_TIFF_COUNT} distinct image_id values"
  fi
  if [[ "${#seen_sources[@]}" -ne "${EXPECTED_TIFF_COUNT}" ]]; then
    fail "${EXIT_VERIFY}" "expected ${EXPECTED_TIFF_COUNT} distinct source_relative_path values"
  fi
  if [[ "${first_id}" != "${EXPECTED_FIRST_IMAGE_ID}" ]]; then
    fail "${EXIT_VERIFY}" "first image_id expected ${EXPECTED_FIRST_IMAGE_ID}, found ${first_id}"
  fi
  if [[ "${last_id}" != "${EXPECTED_LAST_IMAGE_ID}" ]]; then
    fail "${EXIT_VERIFY}" "last image_id expected ${EXPECTED_LAST_IMAGE_ID}, found ${last_id}"
  fi

  local i mid mpath mrows mline m_image m_assigned m_hash machine_total=0
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    mpath="${plan_root}/machines/${mid}_manifest.csv"
    mrows=0
    while IFS= read -r mline || [[ -n "${mline}" ]]; do
      mline="${mline%$'\r'}"
      [[ -z "${mline}" ]] && continue
      if [[ "${mrows}" -eq 0 ]]; then
        # header
        mrows=1
        continue
      fi
      m_image="$(csv_field "${mline}" 3)"
      m_assigned="$(csv_field "${mline}" 11)"
      m_hash="$(csv_field "${mline}" 21)"
      if [[ "${m_assigned}" != "${mid}" ]]; then
        fail "${EXIT_VERIFY}" \
          "${mid}_manifest.csv contains row for ${m_assigned} (image ${m_image})"
      fi
      if [[ "${m_hash}" != "${production_config_canonical_sha256}" ]]; then
        fail "${EXIT_VERIFY}" \
          "configuration_sha256 mismatch in ${mid} for ${m_image}: expected ${production_config_canonical_sha256}, found ${m_hash}"
      fi
      mrows=$((mrows + 1))
    done <"${mpath}"
    local data_for_machine=$((mrows - 1))
    if [[ "${data_for_machine}" -lt 0 ]]; then
      data_for_machine=0
    fi
    if [[ "${data_for_machine}" -ne "${machine_counts[${mid}]:-0}" ]]; then
      fail "${EXIT_VERIFY}" \
        "${mid} row count ${data_for_machine} does not match master assignment count ${machine_counts[${mid}]:-0}"
    fi
    machine_total=$((machine_total + data_for_machine))
  done

  if [[ "${machine_total}" -ne "${EXPECTED_TIFF_COUNT}" ]]; then
    fail "${EXIT_VERIFY}" \
      "sum of machine manifest rows is ${machine_total}, expected ${EXPECTED_TIFF_COUNT}"
  fi

  if [[ "${MACHINE_COUNT}" -eq 3 ]]; then
    if [[ "${machine_counts[machine_01]:-0}" -ne 27 \
       || "${machine_counts[machine_02]:-0}" -ne 27 \
       || "${machine_counts[machine_03]:-0}" -ne 26 ]]; then
      fail "${EXIT_VERIFY}" \
        "for machine_count=3 expected 27/27/26, found ${machine_counts[machine_01]:-0}/${machine_counts[machine_02]:-0}/${machine_counts[machine_03]:-0}"
    fi
  fi

  # Export counts for success summary via globals.
  MASTER_FIRST_ID="${first_id}"
  MASTER_LAST_ID="${last_id}"
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    eval "MACHINE_COUNT_${i}=${machine_counts[${mid}]:-0}"
  done
}

write_preparation_properties() {
  local dest="$1"
  local production_config_canonical_sha256="$2"
  local production_config_file_sha256="$3"
  local timestamp_ms
  timestamp_ms="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"

  cat >"${dest}" <<EOF
schema_version=1
scientific_checkpoint=${EXPECTED_COMMIT}
scientific_checkpoint_short=${EXPECTED_COMMIT_SHORT}
machine_count=${MACHINE_COUNT}
input_root=${INPUT_ROOT}
job_root=${JOB_ROOT}
production_config_path=${JOB_ROOT}/production_batch.properties
production_config_sha256=${production_config_canonical_sha256}
production_config_canonical_sha256=${production_config_canonical_sha256}
production_config_file_sha256=${production_config_file_sha256}
master_manifest_path=${JOB_ROOT}/plan/master_batch_manifest.csv
valid_tiff_count=${EXPECTED_TIFF_COUNT}
first_image_id=${EXPECTED_FIRST_IMAGE_ID}
last_image_id=${EXPECTED_LAST_IMAGE_ID}
preparation_timestamp_epoch_ms=${timestamp_ms}
EOF
}

print_success() {
  local i mid
  echo "LUMEN MASTER BATCH PREPARATION SUCCESS"
  echo "TIFFs: ${EXPECTED_TIFF_COUNT}"
  echo "first: ${MASTER_FIRST_ID}"
  echo "last: ${MASTER_LAST_ID}"
  echo "machines: ${MACHINE_COUNT}"
  echo
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    local count_var="MACHINE_COUNT_${i}"
    echo "${mid}: ${!count_var}"
  done
  echo
  echo "job root: ${JOB_ROOT}"
  echo "configuration: verified"
  echo "configuration canonical SHA-256: ${PRODUCTION_CONFIG_CANONICAL_SHA256}"
  echo "configuration file SHA-256: ${PRODUCTION_CONFIG_FILE_SHA256}"
  echo "assignment order: verified"
  echo "source TIFFs modified: NO"
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run it as ./prepare_master_batch.sh ..." >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  trap on_exit EXIT

  if [[ "$#" -ne 3 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected exactly 3 arguments"
  fi

  require_cmd sha256sum
  require_cmd awk
  require_cmd grep
  require_cmd sed
  require_cmd wc
  require_cmd date
  require_cmd cp
  require_cmd mkdir
  require_cmd rm
  require_cmd mv

  validate_machine_count "$3"
  INPUT_ROOT="$(normalize_dir "$1" "input-root")"
  local requested_job="$2"
  if [[ -e "${requested_job}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Job root already exists; refusing to overwrite: ${requested_job}"
  fi

  local job_parent job_name
  job_parent="$(cd -P "$(dirname -- "${requested_job}")" && pwd)" \
    || fail "${EXIT_VALIDATION}" "Unable to resolve parent of job root: ${requested_job}"
  job_name="$(basename -- "${requested_job}")"
  JOB_ROOT="${job_parent}/${job_name}"

  if [[ -e "${JOB_ROOT}" ]]; then
    fail "${EXIT_VALIDATION}" "Job root already exists; refusing to overwrite: ${JOB_ROOT}"
  fi

  resolve_lumen_runtime

  echo "Verifying portable LumenRuntime..."
  "${VERIFY_LAUNCHER}" --verify-only >/dev/null

  STAGING_ROOT="${job_parent}/.${job_name}.staging_$$"
  rm -rf -- "${STAGING_ROOT}"
  mkdir -p -- \
    "${STAGING_ROOT}/plan/machines"

  echo "Writing authoritative production_batch.properties..."
  write_production_config "${STAGING_ROOT}/production_batch.properties"
  local production_config_file_sha256
  production_config_file_sha256="$(sha256_of "${STAGING_ROOT}/production_batch.properties")"

  echo "Running central BatchManifestPlannerCli..."
  run_central_planner \
    "${STAGING_ROOT}/plan" \
    "${STAGING_ROOT}/production_batch.properties" \
    "${STAGING_ROOT}/preparation.log"

  validate_plan_artifacts "${STAGING_ROOT}/plan"

  local production_config_canonical_sha256 summary_canonical
  production_config_canonical_sha256="$(read_required_sha256_property \
    "${STAGING_ROOT}/plan/batch_plan_report.txt" \
    "configuration_sha256" \
    "batch_plan_report.txt")"
  summary_canonical="$(read_required_sha256_property \
    "${STAGING_ROOT}/plan/machine_assignment_summary.txt" \
    "configuration_sha256" \
    "machine_assignment_summary.txt")"
  if [[ "${summary_canonical}" != "${production_config_canonical_sha256}" ]]; then
    fail "${EXIT_VERIFY}" \
      "configuration_sha256 mismatch between batch_plan_report.txt (${production_config_canonical_sha256}) and machine_assignment_summary.txt (${summary_canonical})"
  fi

  # Best-effort log cross-check; report is authoritative if the log line is absent.
  if grep -Eq 'configuration SHA-256:[[:space:]]*[0-9a-f]{64}' \
      "${STAGING_ROOT}/preparation.log"; then
    local log_canonical
    log_canonical="$(
      sed -n 's/.*configuration SHA-256:[[:space:]]*\([0-9a-f]\{64\}\).*/\1/p' \
        "${STAGING_ROOT}/preparation.log" | awk 'NR==1 {print; exit}'
    )"
    if [[ -n "${log_canonical}" \
       && "${log_canonical}" != "${production_config_canonical_sha256}" ]]; then
      fail "${EXIT_VERIFY}" \
        "preparation.log configuration SHA-256 (${log_canonical}) does not match batch_plan_report.txt (${production_config_canonical_sha256})"
    fi
  fi

  validate_master_and_machines \
    "${STAGING_ROOT}/plan" \
    "${production_config_canonical_sha256}"
  write_preparation_properties \
    "${STAGING_ROOT}/preparation.properties" \
    "${production_config_canonical_sha256}" \
    "${production_config_file_sha256}"

  PRODUCTION_CONFIG_CANONICAL_SHA256="${production_config_canonical_sha256}"
  PRODUCTION_CONFIG_FILE_SHA256="${production_config_file_sha256}"

  if [[ -e "${JOB_ROOT}" ]]; then
    fail "${EXIT_VALIDATION}" "Job root appeared during staging; refusing to publish: ${JOB_ROOT}"
  fi

  echo "Publishing authoritative job root..."
  mv -- "${STAGING_ROOT}" "${JOB_ROOT}"
  STAGING_ROOT=''

  print_success
}

main "$@"
