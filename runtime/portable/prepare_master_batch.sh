#!/usr/bin/env bash
# Phase 4C4B1: central master batch preparation (PPTTCS / schema-2).
# No TIFF copies, no Fiji processing.
#
# Usage:
#   ./prepare_master_batch.sh <all-tiffs-input-root> <portable-job-root> [machine-count]
#
# machine-count defaults to 1. Production assigns every TIFF to one analysis
# host (machine_01). Values other than 1 are rejected.
#
set -euo pipefail

readonly EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
readonly EXPECTED_COMMIT_SHORT='6dd42df'
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
MASTER_FIRST_ID=''
MASTER_LAST_ID=''
MASTER_VALID_COUNT=0
PRODUCTION_CONFIG_CANONICAL_SHA256=''
PRODUCTION_CONFIG_FILE_SHA256=''


readonly CANONICAL_MANIFEST_HEADER='schema_version,order_index,image_id,plot_id,tree_id,core_label,core_id,segment_order,source_filename,source_relative_path,source_sha256,source_size_bytes,assigned_machine,assignment_index,status,attempt_count,last_start_timestamp,last_end_timestamp,last_exit_code,last_message,tile_profile_index,dataset_shard_relative_path,configuration_sha256'
readonly MANIFEST_SCHEMA_VERSION='2'

# Populated by resolve_manifest_header / split_csv_simple.
CSV_FIELDS=()
declare -A CSV_IDX=()
DERIVED_PLOT_ID=''
DERIVED_TREE_ID=''
DERIVED_CORE_LABEL=''
DERIVED_CORE_ID=''
DERIVED_SEGMENT_ORDER=''

is_core_segment_image_id() {
  local id="${1-}"
  # Exact six characters; no trim, no case fold, no repair.
  [[ "${#id}" -eq 6 ]] || return 1
  [[ "${id}" =~ ^[0-9]{4}[AB][1-5]$ ]] || return 1
  local plot="${id:0:2}"
  local tree="${id:2:2}"
  [[ "${plot}" != "00" && "${tree}" != "00" ]] || return 1
  return 0
}

derive_core_segment_fields() {
  local id="$1"
  DERIVED_PLOT_ID="${id:0:2}"
  DERIVED_TREE_ID="${id:2:2}"
  DERIVED_CORE_LABEL="${id:4:1}"
  DERIVED_CORE_ID="${id:0:5}"
  DERIVED_SEGMENT_ORDER="${id:5:1}"
}

image_id_sort_key() {
  local id="$1"
  local core_rank=0
  [[ "${id:4:1}" == "B" ]] && core_rank=1
  printf '%s%s%d%s' "${id:0:2}" "${id:2:2}" "${core_rank}" "${id:5:1}"
}

split_csv_simple() {
  local line="$1"
  CSV_FIELDS=()
  local IFS=','
  # shellcheck disable=SC2206
  CSV_FIELDS=(${line})
  unset IFS
}

resolve_manifest_header() {
  local header_line="$1"
  local label="$2"
  header_line="${header_line%$'\r'}"
  [[ -n "${header_line}" ]] || fail "${EXIT_VERIFY}" "${label}: manifest header missing"
  if [[ "${header_line}" != "${CANONICAL_MANIFEST_HEADER}" ]]; then
    fail "${EXIT_VERIFY}" \
      "${label}: manifest header is not the exact schema-2 canonical header"
  fi
  split_csv_simple "${header_line}"
  CSV_IDX=()
  local i name
  for i in "${!CSV_FIELDS[@]}"; do
    name="${CSV_FIELDS[$i]}"
    if [[ -n "${CSV_IDX[${name}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "${label}: duplicate header field: ${name}"
    fi
    CSV_IDX["${name}"]=$i
  done
  local required
  for required in \
      schema_version order_index image_id plot_id tree_id core_label core_id \
      segment_order source_filename source_relative_path source_sha256 \
      source_size_bytes assigned_machine assignment_index configuration_sha256
  do
    if [[ -z "${CSV_IDX[${required}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "${label}: missing required header field: ${required}"
    fi
  done
}

csv_get() {
  local name="$1"
  printf '%s' "${CSV_FIELDS[${CSV_IDX[${name}]}]:-}"
}

validate_row_identity() {
  local label="$1"
  local image_id plot_id tree_id core_label core_id segment_order schema_version
  image_id="$(csv_get image_id)"
  plot_id="$(csv_get plot_id)"
  tree_id="$(csv_get tree_id)"
  core_label="$(csv_get core_label)"
  core_id="$(csv_get core_id)"
  segment_order="$(csv_get segment_order)"
  schema_version="$(csv_get schema_version)"

  [[ "${schema_version}" == "${MANIFEST_SCHEMA_VERSION}" ]] \
    || fail "${EXIT_VERIFY}" \
      "${label}: schema_version must be ${MANIFEST_SCHEMA_VERSION}, found ${schema_version} (image ${image_id})"

  is_core_segment_image_id "${image_id}" \
    || fail "${EXIT_VERIFY}" "${label}: invalid PPTTCS image_id: ${image_id}"

  derive_core_segment_fields "${image_id}"
  [[ "${plot_id}" == "${DERIVED_PLOT_ID}" ]] \
    || fail "${EXIT_VERIFY}" \
      "${label}: plot_id mismatch for ${image_id}: row=${plot_id} derived=${DERIVED_PLOT_ID}"
  [[ "${tree_id}" == "${DERIVED_TREE_ID}" ]] \
    || fail "${EXIT_VERIFY}" \
      "${label}: tree_id mismatch for ${image_id}: row=${tree_id} derived=${DERIVED_TREE_ID}"
  [[ "${core_label}" == "${DERIVED_CORE_LABEL}" ]] \
    || fail "${EXIT_VERIFY}" \
      "${label}: core_label mismatch for ${image_id}: row=${core_label} derived=${DERIVED_CORE_LABEL}"
  [[ "${core_id}" == "${DERIVED_CORE_ID}" ]] \
    || fail "${EXIT_VERIFY}" \
      "${label}: core_id mismatch for ${image_id}: row=${core_id} derived=${DERIVED_CORE_ID}"
  [[ "${segment_order}" == "${DERIVED_SEGMENT_ORDER}" ]] \
    || fail "${EXIT_VERIFY}" \
      "${label}: segment_order mismatch for ${image_id}: row=${segment_order} derived=${DERIVED_SEGMENT_ORDER}"
}


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
Usage: ./prepare_master_batch.sh <all-tiffs-input-root> <portable-job-root> [machine-count]

Creates one authoritative central batch plan for a single analysis host.
Does not copy TIFFs, run Fiji, or process images.

machine-count defaults to 1 and must be 1. All production TIFFs are assigned
to machine_01 (host identity). Physical multi-machine partitioning is not used.
Production TIFF count is variable (PPTTCS identity, schema-2 manifests).
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
  if [[ ! "${count}" =~ ^[1-9][0-9]?$ ]]; then
    usage
    fail "${EXIT_USAGE}" "Invalid machine-count '${count}'. Single-machine production requires 1."
  fi
  if [[ "${count}" -ne 1 ]]; then
    fail "${EXIT_USAGE}" \
      "Single-machine production requires machine-count=1 (one analysis host receives every TIFF). Received '${count}'. Physical multi-machine partitioning is not used."
  fi
  MACHINE_COUNT=1
}

write_production_config() {
  local dest="$1"
  local saw_complete=0
  local saw_machines=0
  local saw_recursive=0
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
        # PPTTCS has no fixed expected set; force false regardless of source.
        printf 'input.require_complete_expected_set=false\n' >>"${dest}"
        saw_complete=1
        ;;
      input.recursive=*)
        # Authoritative portable production TIFFs live at
        # tiffs/<segment_id>/<segment_id>.tif. Force recursive discovery
        # regardless of default_batch.properties.
        printf 'input.recursive=true\n' >>"${dest}"
        saw_recursive=1
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
    printf 'input.require_complete_expected_set=false\n' >>"${dest}"
  fi
  if [[ "${saw_recursive}" -ne 1 ]]; then
    printf 'input.recursive=true\n' >>"${dest}"
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
  local label="master_batch_manifest.csv"

  local header
  IFS= read -r header <"${master}" || true
  resolve_manifest_header "${header}" "${label}"

  local first_id='' last_id='' prev_key=''
  local -A seen_ids=()
  local -A seen_sources=()
  local -A machine_counts=()
  local -A core_to_machine=()
  local -A master_images=()
  local line image_id source_rel assigned config_hash core_id
  local data_rows=0
  local cur_key

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" ]] && continue
    if [[ "${line}" == "${header}" ]]; then
      continue
    fi

    split_csv_simple "${line}"
    validate_row_identity "${label}"

    image_id="$(csv_get image_id)"
    source_rel="$(csv_get source_relative_path)"
    assigned="$(csv_get assigned_machine)"
    config_hash="$(csv_get configuration_sha256)"
    core_id="$(csv_get core_id)"

    if [[ -z "${first_id}" ]]; then
      first_id="${image_id}"
    fi
    last_id="${image_id}"

    cur_key="$(image_id_sort_key "${image_id}")"
    if [[ -n "${prev_key}" && ! "${cur_key}" > "${prev_key}" ]]; then
      fail "${EXIT_VERIFY}" \
        "master manifest not in canonical PPTTCS order at ${image_id} (previous key ${prev_key})"
    fi
    prev_key="${cur_key}"

    if [[ -n "${seen_ids[${image_id}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "duplicate image_id in master manifest: ${image_id}"
    fi
    seen_ids["${image_id}"]=1
    master_images["${image_id}"]=1

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

    if [[ -n "${core_to_machine[${core_id}]+x}" \
       && "${core_to_machine[${core_id}]}" != "${assigned}" ]]; then
      fail "${EXIT_VERIFY}" \
        "core_id ${core_id} split across machines: ${core_to_machine[${core_id}]} and ${assigned}"
    fi
    core_to_machine["${core_id}"]="${assigned}"

    if [[ "${config_hash}" != "${production_config_canonical_sha256}" ]]; then
      fail "${EXIT_VERIFY}" \
        "configuration_sha256 mismatch in master for ${image_id}: expected ${production_config_canonical_sha256}, found ${config_hash}"
    fi

    data_rows=$((data_rows + 1))
  done <"${master}"

  if [[ "${data_rows}" -lt 1 ]]; then
    fail "${EXIT_VERIFY}" "master_batch_manifest.csv has no data rows"
  fi
  if [[ "${#seen_ids[@]}" -ne "${data_rows}" ]]; then
    fail "${EXIT_VERIFY}" "master image_id uniqueness mismatch"
  fi
  if [[ "${#seen_sources[@]}" -ne "${data_rows}" ]]; then
    fail "${EXIT_VERIFY}" "master source_relative_path uniqueness mismatch"
  fi

  local -A packaged_images=()
  local i mid mpath mlabel mheader mline
  local m_image m_assigned m_hash m_core m_source
  local machine_total=0
  local data_for_machine
  local -A machine_cores=()

  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    mpath="${plan_root}/machines/${mid}_manifest.csv"
    mlabel="${mid}_manifest.csv"
    machine_cores=()

    IFS= read -r mheader <"${mpath}" || true
    resolve_manifest_header "${mheader}" "${mlabel}"

    data_for_machine=0
    local m_prev_key=''
    while IFS= read -r mline || [[ -n "${mline}" ]]; do
      mline="${mline%$'\r'}"
      [[ -z "${mline}" || "${mline}" == "${mheader}" ]] && continue

      split_csv_simple "${mline}"
      validate_row_identity "${mlabel}"

      m_image="$(csv_get image_id)"
      m_assigned="$(csv_get assigned_machine)"
      m_hash="$(csv_get configuration_sha256)"
      m_core="$(csv_get core_id)"
      m_source="$(csv_get source_relative_path)"

      [[ "${m_assigned}" == "${mid}" ]] \
        || fail "${EXIT_VERIFY}" \
          "${mlabel} contains row for ${m_assigned} (image ${m_image})"
      [[ "${m_hash}" == "${production_config_canonical_sha256}" ]] \
        || fail "${EXIT_VERIFY}" \
          "configuration_sha256 mismatch in ${mid} for ${m_image}"

      if [[ -z "${master_images[${m_image}]+x}" ]]; then
        fail "${EXIT_VERIFY}" "${mlabel} contains image_id not in master: ${m_image}"
      fi
      if [[ -n "${packaged_images[${m_image}]+x}" ]]; then
        fail "${EXIT_VERIFY}" "image_id ${m_image} appears in multiple machine manifests"
      fi
      packaged_images["${m_image}"]=1

      if [[ -n "${core_to_machine[${m_core}]+x}" \
         && "${core_to_machine[${m_core}]}" != "${mid}" ]]; then
        fail "${EXIT_VERIFY}" \
          "machine ${mid} has core_id ${m_core} assigned to ${core_to_machine[${m_core}]} in master"
      fi
      machine_cores["${m_core}"]=1

      cur_key="$(image_id_sort_key "${m_image}")"
      if [[ -n "${m_prev_key}" && ! "${cur_key}" > "${m_prev_key}" ]]; then
        fail "${EXIT_VERIFY}" \
          "${mlabel} not in canonical PPTTCS order at ${m_image}"
      fi
      m_prev_key="${cur_key}"

      data_for_machine=$((data_for_machine + 1))
    done <"${mpath}"

    if [[ "${data_for_machine}" -ne "${machine_counts[${mid}]:-0}" ]]; then
      fail "${EXIT_VERIFY}" \
        "${mid} row count ${data_for_machine} does not match master assignment count ${machine_counts[${mid}]:-0}"
    fi
    machine_total=$((machine_total + data_for_machine))
    eval "MACHINE_COUNT_${i}=${data_for_machine}"
  done

  if [[ "${machine_total}" -ne "${data_rows}" ]]; then
    fail "${EXIT_VERIFY}" \
      "sum of machine manifest rows is ${machine_total}, expected ${data_rows}"
  fi
  if [[ "${#packaged_images[@]}" -ne "${data_rows}" ]]; then
    fail "${EXIT_VERIFY}" \
      "unique machine-manifest image_id count ${#packaged_images[@]} != master ${data_rows}"
  fi

  local img
  for img in "${!master_images[@]}"; do
    if [[ -z "${packaged_images[${img}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "master image_id missing from machine manifests: ${img}"
    fi
  done

  # Every master physical core belongs to exactly one machine (already enforced).
  MASTER_FIRST_ID="${first_id}"
  MASTER_LAST_ID="${last_id}"
  MASTER_VALID_COUNT="${data_rows}"
}

write_preparation_properties() {
  local dest="$1"
  local production_config_canonical_sha256="$2"
  local production_config_file_sha256="$3"
  local timestamp_ms
  timestamp_ms="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"

  cat >"${dest}" <<EOF
portable_plan_schema_version=2
manifest_schema_version=2
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
valid_tiff_count=${MASTER_VALID_COUNT}
first_image_id=${MASTER_FIRST_ID}
last_image_id=${MASTER_LAST_ID}
preparation_timestamp_epoch_ms=${timestamp_ms}
EOF
}

print_success() {
  local i mid
  echo "LUMEN MASTER BATCH PREPARATION SUCCESS"
  echo "TIFFs: ${MASTER_VALID_COUNT}"
  echo "first: ${MASTER_FIRST_ID}"
  echo "last: ${MASTER_LAST_ID}"
  echo "analysis host: $(machine_id_for_index 1)"
  echo "physical machines required: 1"
  echo
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    local count_var="MACHINE_COUNT_${i}"
    echo "${mid}: ${!count_var} (all assigned TIFFs)"
  done
  echo
  echo "job root: ${JOB_ROOT}"
  echo "configuration: verified"
  echo "configuration canonical SHA-256: ${PRODUCTION_CONFIG_CANONICAL_SHA256}"
  echo "configuration file SHA-256: ${PRODUCTION_CONFIG_FILE_SHA256}"
  echo "assignment order: verified"
  echo "core integrity: verified"
  echo "source TIFFs modified: NO"
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run it as ./prepare_master_batch.sh ..." >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  trap on_exit EXIT

  local machine_count_arg=1
  if [[ "$#" -eq 3 ]]; then
    machine_count_arg="$3"
  elif [[ "$#" -ne 2 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected <input-root> <job-root> [machine-count]"
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

  validate_machine_count "${machine_count_arg}"
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
