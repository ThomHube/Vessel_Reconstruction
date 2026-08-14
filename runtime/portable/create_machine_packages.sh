#!/usr/bin/env bash
# Phase 4C4B1: create machine-specific distribution packages (PPTTCS / schema-2).
# No Fiji, Maven, precheck, or image processing.
#
# Usage:
#   ./create_machine_packages.sh <job-root>
#
set -euo pipefail

readonly EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
readonly EXPECTED_COMMIT_SHORT='6dd42df'

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3
readonly EXIT_VERIFY=5

JOB_ROOT=''
STAGING_ROOT=''
MACHINE_COUNT=''
INPUT_ROOT=''
CANONICAL_CONFIG_SHA=''
FILE_CONFIG_SHA=''
MASTER_MANIFEST_SHA=''
PREP_VALID_COUNT=''
PREP_FIRST_ID=''
PREP_LAST_ID=''

declare -A GLOBAL_IMAGE_OWNER=()
declare -A GLOBAL_CORE_OWNER=()


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
  local base abs parent
  base="$(basename -- "${staging}")"
  case "${base}" in
    .machine_packages.staging_*)
      ;;
    *)
      return 0
      ;;
  esac
  abs="$(cd -P -- "${staging}" && pwd)" || return 0
  parent="$(cd -P "$(dirname -- "${staging}")" && pwd)" || return 0
  if [[ "${abs}" == "${HOME}" \
     || "${abs}" == "${JOB_ROOT:-}" \
     || "${abs}" == "${INPUT_ROOT:-}" \
     || "${parent}" != "${JOB_ROOT}" ]]; then
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
Usage: ./create_machine_packages.sh <job-root>

Creates <job-root>/machine_packages/ from an authoritative central job.
Does not modify source TIFFs or launch processing.
Requires portable_plan_schema_version=2 and manifest_schema_version=2.
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

machine_id_for_index() {
  printf 'machine_%02d' "$1"
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

require_prop() {
  local props_path="$1"
  local key="$2"
  local value
  value="$(prop_value "${props_path}" "${key}" || true)"
  if [[ -z "${value}" ]]; then
    fail "${EXIT_VERIFY}" "preparation.properties missing required key: ${key}"
  fi
  printf '%s\n' "${value}"
}

is_safe_relative_path() {
  local rel="$1"
  if [[ -z "${rel}" ]]; then
    return 1
  fi
  case "${rel}" in
    /*|\\*)
      return 1
      ;;
  esac
  if [[ "${rel}" == *'..'* ]]; then
    return 1
  fi
  return 0
}

resolve_under_root() {
  local root="$1"
  local rel="$2"
  local candidate abs_root abs_candidate
  candidate="${root}/${rel}"
  [[ -e "${candidate}" ]] || return 1
  [[ -L "${candidate}" ]] && return 1
  abs_root="$(cd -P -- "${root}" && pwd)"
  abs_candidate="$(cd -P -- "$(dirname -- "${candidate}")" && pwd)/$(basename -- "${candidate}")"
  case "${abs_candidate}" in
    "${abs_root}"|"${abs_root}"/*)
      printf '%s\n' "${abs_candidate}"
      return 0
      ;;
  esac
  return 1
}

validate_job_root() {
  local requested="$1"
  [[ -d "${requested}" ]] || fail "${EXIT_VALIDATION}" "job-root is not a directory: ${requested}"
  JOB_ROOT="$(cd -P -- "${requested}" && pwd)"

  require_file "${JOB_ROOT}/preparation.properties" "preparation.properties"
  require_file "${JOB_ROOT}/production_batch.properties" "production_batch.properties"
  require_file "${JOB_ROOT}/plan/master_batch_manifest.csv" "master_batch_manifest.csv"
  require_file "${JOB_ROOT}/plan/machine_assignment_summary.txt" "machine_assignment_summary.txt"
  require_file "${JOB_ROOT}/plan/batch_plan_report.txt" "batch_plan_report.txt"

  if [[ -e "${JOB_ROOT}/machine_packages" ]]; then
    fail "${EXIT_VALIDATION}" \
      "machine_packages already exists; refusing to overwrite: ${JOB_ROOT}/machine_packages"
  fi
}

load_and_validate_preparation() {
  local props="${JOB_ROOT}/preparation.properties"
  local plan_schema manifest_schema checkpoint short_ckpt valid_count first_id last_id count_raw

  plan_schema="$(require_prop "${props}" "portable_plan_schema_version")"
  manifest_schema="$(require_prop "${props}" "manifest_schema_version")"
  checkpoint="$(require_prop "${props}" "scientific_checkpoint")"
  short_ckpt="$(require_prop "${props}" "scientific_checkpoint_short")"
  count_raw="$(require_prop "${props}" "machine_count")"
  INPUT_ROOT="$(require_prop "${props}" "input_root")"
  CANONICAL_CONFIG_SHA="$(require_prop "${props}" "production_config_canonical_sha256")"
  FILE_CONFIG_SHA="$(require_prop "${props}" "production_config_file_sha256")"
  local production_config_sha
  production_config_sha="$(require_prop "${props}" "production_config_sha256")"
  valid_count="$(require_prop "${props}" "valid_tiff_count")"
  first_id="$(require_prop "${props}" "first_image_id")"
  last_id="$(require_prop "${props}" "last_image_id")"
  require_prop "${props}" "job_root" >/dev/null

  [[ "${plan_schema}" == "2" ]] \
    || fail "${EXIT_VERIFY}" "portable_plan_schema_version must be 2, found ${plan_schema}"
  [[ "${manifest_schema}" == "2" ]] \
    || fail "${EXIT_VERIFY}" "manifest_schema_version must be 2, found ${manifest_schema}"
  [[ "${checkpoint}" == "${EXPECTED_COMMIT}" ]] \
    || fail "${EXIT_VERIFY}" "scientific_checkpoint mismatch"
  [[ "${short_ckpt}" == "${EXPECTED_COMMIT_SHORT}" ]] \
    || fail "${EXIT_VERIFY}" "scientific_checkpoint_short mismatch"

  if [[ ! "${valid_count}" =~ ^[1-9][0-9]*$ ]]; then
    fail "${EXIT_VERIFY}" "valid_tiff_count must be a positive integer, found ${valid_count}"
  fi
  is_core_segment_image_id "${first_id}" \
    || fail "${EXIT_VERIFY}" "preparation first_image_id is not PPTTCS: ${first_id}"
  is_core_segment_image_id "${last_id}" \
    || fail "${EXIT_VERIFY}" "preparation last_image_id is not PPTTCS: ${last_id}"

  PREP_VALID_COUNT="${valid_count}"
  PREP_FIRST_ID="${first_id}"
  PREP_LAST_ID="${last_id}"

  if [[ ! "${count_raw}" =~ ^[1-9][0-9]?$ ]] || [[ "${count_raw}" -lt 1 || "${count_raw}" -gt 99 ]]; then
    fail "${EXIT_VERIFY}" "machine_count must be integer 1..99, found ${count_raw}"
  fi
  MACHINE_COUNT="${count_raw}"

  [[ "${production_config_sha}" == "${CANONICAL_CONFIG_SHA}" ]] \
    || fail "${EXIT_VERIFY}" \
      "production_config_sha256 must equal production_config_canonical_sha256"

  require_dir "${INPUT_ROOT}" "input_root from preparation.properties"
  INPUT_ROOT="$(cd -P -- "${INPUT_ROOT}" && pwd)"

  local actual_file_sha
  actual_file_sha="$(sha256_of "${JOB_ROOT}/production_batch.properties")"
  [[ "${actual_file_sha}" == "${FILE_CONFIG_SHA}" ]] \
    || fail "${EXIT_VERIFY}" \
      "production_batch.properties raw SHA-256 mismatch: expected ${FILE_CONFIG_SHA}, found ${actual_file_sha}"

  local i mid
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    require_file "${JOB_ROOT}/plan/machines/${mid}_manifest.csv" "${mid}_manifest.csv"
  done

  MASTER_MANIFEST_SHA="$(sha256_of "${JOB_ROOT}/plan/master_batch_manifest.csv")"
}

package_one_machine() {
  local index="$1"
  local mid machine_manifest package_root
  mid="$(machine_id_for_index "${index}")"
  machine_manifest="${JOB_ROOT}/plan/machines/${mid}_manifest.csv"
  package_root="${STAGING_ROOT}/${mid}"
  local label="${mid}_manifest.csv"

  mkdir -p -- \
    "${package_root}/input" \
    "${package_root}/batch/machines"

  local machine_manifest_sha
  machine_manifest_sha="$(sha256_of "${machine_manifest}")"

  local header line
  IFS= read -r header <"${machine_manifest}" || true
  resolve_manifest_header "${header}" "${label}"

  local -a image_ids=()
  local -a rel_paths=()
  local -a source_shas=()
  local -a source_sizes=()
  local data_rows=0
  local image_id source_rel source_sha source_size assigned config_hash core_id
  local prev_key='' cur_key

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == "${header}" ]] && continue

    split_csv_simple "${line}"
    validate_row_identity "${label}"

    image_id="$(csv_get image_id)"
    source_rel="$(csv_get source_relative_path)"
    source_sha="$(csv_get source_sha256)"
    source_size="$(csv_get source_size_bytes)"
    assigned="$(csv_get assigned_machine)"
    config_hash="$(csv_get configuration_sha256)"
    core_id="$(csv_get core_id)"

    [[ "${assigned}" == "${mid}" ]] \
      || fail "${EXIT_VERIFY}" "${mid} manifest contains assigned_machine=${assigned}"
    [[ "${config_hash}" == "${CANONICAL_CONFIG_SHA}" ]] \
      || fail "${EXIT_VERIFY}" \
        "${mid} manifest configuration_sha256 mismatch for ${image_id}"

    is_safe_relative_path "${source_rel}" \
      || fail "${EXIT_VERIFY}" \
        "${mid}: unsafe source_relative_path '${source_rel}' for ${image_id}"

    if [[ -n "${GLOBAL_CORE_OWNER[${core_id}]+x}" \
       && "${GLOBAL_CORE_OWNER[${core_id}]}" != "${mid}" ]]; then
      fail "${EXIT_VERIFY}" \
        "core_id ${core_id} split across ${GLOBAL_CORE_OWNER[${core_id}]} and ${mid}"
    fi
    GLOBAL_CORE_OWNER["${core_id}"]="${mid}"

    cur_key="$(image_id_sort_key "${image_id}")"
    if [[ -n "${prev_key}" && ! "${cur_key}" > "${prev_key}" ]]; then
      fail "${EXIT_VERIFY}" "${label} not in canonical PPTTCS order at ${image_id}"
    fi
    prev_key="${cur_key}"

    image_ids+=("${image_id}")
    rel_paths+=("${source_rel}")
    source_shas+=("${source_sha}")
    source_sizes+=("${source_size}")
    data_rows=$((data_rows + 1))
  done <"${machine_manifest}"

  if [[ "${data_rows}" -lt 1 ]]; then
    fail "${EXIT_VERIFY}" \
      "${mid}_manifest.csv has zero data rows; portable deployment requires every configured machine to receive at least one physical core. Reduce machines.count."
  fi

  local i src_abs dest_abs actual_sha actual_size dest_parent
  for ((i = 0; i < data_rows; i++)); do
    src_abs="$(resolve_under_root "${INPUT_ROOT}" "${rel_paths[$i]}")" \
      || fail "${EXIT_VERIFY}" \
        "${mid}: source TIFF missing or escapes input root: ${rel_paths[$i]}"
    [[ -f "${src_abs}" ]] \
      || fail "${EXIT_VERIFY}" "${mid}: source is not a regular file: ${src_abs}"

    actual_sha="$(sha256_of "${src_abs}")"
    [[ "${actual_sha}" == "${source_shas[$i]}" ]] \
      || fail "${EXIT_VERIFY}" \
        "${mid}: source SHA-256 mismatch for ${image_ids[$i]} before copy"

    actual_size="$(wc -c <"${src_abs}" | tr -d ' ')"
    [[ "${actual_size}" == "${source_sizes[$i]}" ]] \
      || fail "${EXIT_VERIFY}" \
        "${mid}: source size mismatch for ${image_ids[$i]}: expected ${source_sizes[$i]}, found ${actual_size}"

    dest_abs="${package_root}/input/${rel_paths[$i]}"
    dest_parent="$(dirname -- "${dest_abs}")"
    mkdir -p -- "${dest_parent}"
    cp -f -- "${src_abs}" "${dest_abs}"

    actual_sha="$(sha256_of "${dest_abs}")"
    [[ "${actual_sha}" == "${source_shas[$i]}" ]] \
      || fail "${EXIT_VERIFY}" \
        "${mid}: copied TIFF SHA-256 mismatch for ${image_ids[$i]}"
  done

  local found
  while IFS= read -r -d '' found; do
    local rel_found="${found#${package_root}/input/}"
    local expected=0
    for ((i = 0; i < data_rows; i++)); do
      if [[ "${rel_found}" == "${rel_paths[$i]}" ]]; then
        expected=1
        break
      fi
    done
    if [[ "${expected}" -ne 1 ]]; then
      fail "${EXIT_VERIFY}" "${mid}: unexpected file under input/: ${rel_found}"
    fi
  done < <(find "${package_root}/input" -type f -print0)

  local dest_manifest="${package_root}/batch/machines/${mid}_manifest.csv"
  cp -f -- "${machine_manifest}" "${dest_manifest}"
  [[ "$(sha256_of "${dest_manifest}")" == "${machine_manifest_sha}" ]] \
    || fail "${EXIT_VERIFY}" "${mid}: machine manifest copy SHA-256 mismatch"

  local dest_config="${package_root}/production_batch.properties"
  cp -f -- "${JOB_ROOT}/production_batch.properties" "${dest_config}"
  [[ "$(sha256_of "${dest_config}")" == "${FILE_CONFIG_SHA}" ]] \
    || fail "${EXIT_VERIFY}" "${mid}: production_batch.properties copy SHA-256 mismatch"

  local first_id="${image_ids[0]}"
  local last_id="${image_ids[$((data_rows - 1))]}"
  local first_core_id="${first_id:0:5}"
  local timestamp_ms
  timestamp_ms="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"

  cat >"${package_root}/package.properties" <<EOF
package.schema_version=2
manifest.schema_version=2
scientific_checkpoint=${EXPECTED_COMMIT}
scientific_checkpoint_short=${EXPECTED_COMMIT_SHORT}
machine_id=${mid}
machine_count=${MACHINE_COUNT}
assigned_tiff_count=${data_rows}
first_image_id=${first_id}
last_image_id=${last_id}
production_config_sha256=${CANONICAL_CONFIG_SHA}
production_config_canonical_sha256=${CANONICAL_CONFIG_SHA}
production_config_file_sha256=${FILE_CONFIG_SHA}
master_manifest_sha256=${MASTER_MANIFEST_SHA}
machine_manifest_sha256=${machine_manifest_sha}
package_timestamp_epoch_ms=${timestamp_ms}
EOF

  local checksums="${package_root}/package_checksums.sha256"
  : >"${checksums}"
  {
    printf '%s  %s\n' "$(sha256_of "${dest_config}")" "production_batch.properties"
    printf '%s  %s\n' "$(sha256_of "${package_root}/package.properties")" "package.properties"
    printf '%s  %s\n' "$(sha256_of "${dest_manifest}")" "batch/machines/${mid}_manifest.csv"
    for ((i = 0; i < data_rows; i++)); do
      printf '%s  %s\n' \
        "$(sha256_of "${package_root}/input/${rel_paths[$i]}")" \
        "input/${rel_paths[$i]}"
    done
  } >"${checksums}"

  (
    cd -- "${package_root}"
    sha256sum -c -- package_checksums.sha256 >/dev/null
  ) || fail "${EXIT_VERIFY}" "${mid}: package_checksums.sha256 verification failed"

  local input_file_count
  input_file_count="$(find "${package_root}/input" -type f | wc -l | tr -d ' ')"
  [[ "${input_file_count}" == "${data_rows}" ]] \
    || fail "${EXIT_VERIFY}" \
      "${mid}: input file count ${input_file_count} != assigned_tiff_count ${data_rows}"

  for ((i = 0; i < data_rows; i++)); do
    if [[ -n "${GLOBAL_IMAGE_OWNER[${image_ids[$i]}]+x}" ]]; then
      fail "${EXIT_VERIFY}" \
        "image_id ${image_ids[$i]} appears in both ${GLOBAL_IMAGE_OWNER[${image_ids[$i]}]} and ${mid}"
    fi
    GLOBAL_IMAGE_OWNER["${image_ids[$i]}"]="${mid}"
  done

  eval "MACHINE_COUNT_${index}=${data_rows}"
  eval "MACHINE_FIRST_${index}=${first_id}"
  eval "MACHINE_LAST_${index}=${last_id}"
  eval "MACHINE_CORE_${index}=${first_core_id}"
}

global_cross_checks() {
  local total=0 i mid count_var
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    count_var="MACHINE_COUNT_${i}"
    total=$((total + ${!count_var}))
  done
  [[ "${total}" -eq "${PREP_VALID_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "packaged TIFF sum ${total} != valid_tiff_count ${PREP_VALID_COUNT}"

  local package_dirs
  package_dirs="$(find "${STAGING_ROOT}" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')"
  [[ "${package_dirs}" -eq "${MACHINE_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "package directory count ${package_dirs} != machine_count ${MACHINE_COUNT}"

  local master="${JOB_ROOT}/plan/master_batch_manifest.csv"
  local header line image_id core_id
  IFS= read -r header <"${master}" || true
  resolve_manifest_header "${header}" "master_batch_manifest.csv"
  local master_rows=0
  local first_id='' last_id=''
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == "${header}" ]] && continue
    split_csv_simple "${line}"
    validate_row_identity "master_batch_manifest.csv"
    image_id="$(csv_get image_id)"
    core_id="$(csv_get core_id)"
    if [[ -z "${first_id}" ]]; then
      first_id="${image_id}"
    fi
    last_id="${image_id}"
    if [[ -z "${GLOBAL_IMAGE_OWNER[${image_id}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "master image_id ${image_id} missing from all packages"
    fi
    if [[ -z "${GLOBAL_CORE_OWNER[${core_id}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "master core_id ${core_id} missing from all packages"
    fi
    master_rows=$((master_rows + 1))
  done <"${master}"
  [[ "${master_rows}" -eq "${PREP_VALID_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "master data rows ${master_rows} != valid_tiff_count ${PREP_VALID_COUNT}"
  [[ "${#GLOBAL_IMAGE_OWNER[@]}" -eq "${PREP_VALID_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "unique packaged image_id count != ${PREP_VALID_COUNT}"
  [[ "${first_id}" == "${PREP_FIRST_ID}" ]] \
    || fail "${EXIT_VERIFY}" "master first_image_id mismatch vs preparation.properties"
  [[ "${last_id}" == "${PREP_LAST_ID}" ]] \
    || fail "${EXIT_VERIFY}" "master last_image_id mismatch vs preparation.properties"
}

write_one_click_launchers() {
  local helper src_dir
  src_dir="$(cd -P "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
  helper="${src_dir}/write_machine_start_launcher.sh"
  require_file "${helper}" "write_machine_start_launcher.sh"

  local launchers_dir="${JOB_ROOT}/launchers"
  if [[ -e "${launchers_dir}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "launchers directory already exists; refusing to overwrite: ${launchers_dir}"
  fi
  mkdir -p -- "${launchers_dir}"

  local i mid core_var core_id
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    core_var="MACHINE_CORE_${i}"
    core_id="${!core_var}"
    [[ -n "${core_id}" ]] || fail "${EXIT_VERIFY}" "${mid}: missing core_id for launcher"
    bash -- "${helper}" \
      "${launchers_dir}" \
      "${mid}" \
      "${core_id}" \
      "../machine_packages/${mid}"
  done
}

print_success() {
  local i mid count_var first_var last_var core_var
  echo "LUMEN MACHINE PACKAGE CREATION SUCCESS"
  echo "machines: ${MACHINE_COUNT}"
  echo "TIFFs packaged: ${PREP_VALID_COUNT}"
  echo
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    count_var="MACHINE_COUNT_${i}"
    first_var="MACHINE_FIRST_${i}"
    last_var="MACHINE_LAST_${i}"
    core_var="MACHINE_CORE_${i}"
    echo "${mid}: ${!count_var} | ${!first_var} -> ${!last_var} | core ${!core_var} | START_${mid}_${!core_var}.sh"
  done
  echo
  echo "machine packages: ${JOB_ROOT}/machine_packages"
  echo "one-click launchers: ${JOB_ROOT}/launchers"
  echo "source TIFFs modified: NO"
  echo "precheck launched: NO"
  echo "processing launched: NO"
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./create_machine_packages.sh <job-root>" >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  trap on_exit EXIT

  if [[ "$#" -ne 1 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected exactly one argument: <job-root>"
  fi

  require_cmd sha256sum
  require_cmd awk
  require_cmd find
  require_cmd cp
  require_cmd mkdir
  require_cmd rm
  require_cmd mv
  require_cmd wc
  require_cmd date

  validate_job_root "$1"
  load_and_validate_preparation

  STAGING_ROOT="${JOB_ROOT}/.machine_packages.staging_$$"
  rm -rf -- "${STAGING_ROOT}"
  mkdir -p -- "${STAGING_ROOT}"

  local i
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    echo "Packaging $(machine_id_for_index "${i}")..."
    package_one_machine "${i}"
  done

  global_cross_checks

  if [[ -e "${JOB_ROOT}/machine_packages" ]]; then
    fail "${EXIT_VALIDATION}" \
      "machine_packages appeared during staging; refusing to publish"
  fi

  echo "Publishing machine_packages..."
  mv -- "${STAGING_ROOT}" "${JOB_ROOT}/machine_packages"
  STAGING_ROOT=''

  echo "Writing one-click launchers..."
  write_one_click_launchers

  print_success
}

main "$@"
