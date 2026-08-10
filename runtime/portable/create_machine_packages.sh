#!/usr/bin/env bash
# Phase 4C2B: create machine-specific distribution packages from a validated
# central job. No Fiji, Maven, precheck, or image processing.
#
# Usage:
#   ./create_machine_packages.sh <job-root>
#
set -euo pipefail

readonly EXPECTED_COMMIT='0c1c326fc07f7e049f1d359226e810c7b8db24ce'
readonly EXPECTED_COMMIT_SHORT='0c1c326'
readonly EXPECTED_TIFF_COUNT=80
readonly EXPECTED_FIRST_IMAGE_ID='010101'
readonly EXPECTED_LAST_IMAGE_ID='022002'

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

# Per-machine summary globals: MACHINE_FIRST_N, MACHINE_LAST_N, MACHINE_COUNT_N
declare -A GLOBAL_IMAGE_OWNER=()

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

# Safe key=value reader. Does not source the file.
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

csv_field() {
  local line="$1"
  local index="$2"
  awk -F',' -v idx="${index}" '{
    val=$idx
    gsub(/^"/, "", val)
    gsub(/"$/, "", val)
    print val
  }' <<<"${line}"
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
  # Reject a symlink in the final path component (parent dirs already resolved via cd -P).
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
  local schema checkpoint short_ckpt valid_count first_id last_id count_raw

  schema="$(require_prop "${props}" "schema_version")"
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

  [[ "${schema}" == "1" ]] \
    || fail "${EXIT_VERIFY}" "schema_version must be 1, found ${schema}"
  [[ "${checkpoint}" == "${EXPECTED_COMMIT}" ]] \
    || fail "${EXIT_VERIFY}" "scientific_checkpoint mismatch"
  [[ "${short_ckpt}" == "${EXPECTED_COMMIT_SHORT}" ]] \
    || fail "${EXIT_VERIFY}" "scientific_checkpoint_short mismatch"
  [[ "${valid_count}" == "${EXPECTED_TIFF_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "valid_tiff_count must be ${EXPECTED_TIFF_COUNT}, found ${valid_count}"
  [[ "${first_id}" == "${EXPECTED_FIRST_IMAGE_ID}" ]] \
    || fail "${EXIT_VERIFY}" "first_image_id mismatch"
  [[ "${last_id}" == "${EXPECTED_LAST_IMAGE_ID}" ]] \
    || fail "${EXIT_VERIFY}" "last_image_id mismatch"

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

  mkdir -p -- \
    "${package_root}/input" \
    "${package_root}/batch/machines"

  local machine_manifest_sha
  machine_manifest_sha="$(sha256_of "${machine_manifest}")"

  local header line
  IFS= read -r header <"${machine_manifest}" || true
  [[ -n "${header}" ]] || fail "${EXIT_VERIFY}" "${mid} manifest header missing"

  local -a image_ids=()
  local -a rel_paths=()
  local -a source_shas=()
  local -a source_sizes=()
  local data_rows=0
  local image_id source_rel source_sha source_size assigned config_hash

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" ]] && continue
    if [[ "${line}" == "${header}" ]]; then
      continue
    fi

    image_id="$(csv_field "${line}" 3)"
    source_rel="$(csv_field "${line}" 8)"
    source_sha="$(csv_field "${line}" 9)"
    source_size="$(csv_field "${line}" 10)"
    assigned="$(csv_field "${line}" 11)"
    config_hash="$(csv_field "${line}" 21)"

    [[ "${assigned}" == "${mid}" ]] \
      || fail "${EXIT_VERIFY}" "${mid} manifest contains assigned_machine=${assigned}"
    [[ "${config_hash}" == "${CANONICAL_CONFIG_SHA}" ]] \
      || fail "${EXIT_VERIFY}" \
        "${mid} manifest configuration_sha256 mismatch for ${image_id}"

    is_safe_relative_path "${source_rel}" \
      || fail "${EXIT_VERIFY}" \
        "${mid}: unsafe source_relative_path '${source_rel}' for ${image_id}"

    image_ids+=("${image_id}")
    rel_paths+=("${source_rel}")
    source_shas+=("${source_sha}")
    source_sizes+=("${source_size}")
    data_rows=$((data_rows + 1))
  done <"${machine_manifest}"

  if [[ "${data_rows}" -lt 1 ]]; then
    fail "${EXIT_VERIFY}" \
      "${mid}_manifest.csv has zero data rows; refusing empty package"
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

  # No extra TIFF files under input/
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
  local timestamp_ms
  timestamp_ms="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"

  cat >"${package_root}/package.properties" <<EOF
package.schema_version=1
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
}

global_cross_checks() {
  local total=0 i mid count_var
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    count_var="MACHINE_COUNT_${i}"
    total=$((total + ${!count_var}))
  done
  [[ "${total}" -eq "${EXPECTED_TIFF_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "packaged TIFF sum ${total} != ${EXPECTED_TIFF_COUNT}"

  local package_dirs
  package_dirs="$(find "${STAGING_ROOT}" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')"
  [[ "${package_dirs}" -eq "${MACHINE_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "package directory count ${package_dirs} != machine_count ${MACHINE_COUNT}"

  # Every master-manifest image_id appears in exactly one package.
  local master="${JOB_ROOT}/plan/master_batch_manifest.csv"
  local header line image_id
  IFS= read -r header <"${master}" || true
  local master_rows=0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == "${header}" ]] && continue
    image_id="$(csv_field "${line}" 3)"
    if [[ -z "${GLOBAL_IMAGE_OWNER[${image_id}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "master image_id ${image_id} missing from all packages"
    fi
    master_rows=$((master_rows + 1))
  done <"${master}"
  [[ "${master_rows}" -eq "${EXPECTED_TIFF_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "master data rows ${master_rows} != ${EXPECTED_TIFF_COUNT}"
  [[ "${#GLOBAL_IMAGE_OWNER[@]}" -eq "${EXPECTED_TIFF_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" "unique packaged image_id count != ${EXPECTED_TIFF_COUNT}"

  if [[ "${MACHINE_COUNT}" -eq 3 ]]; then
    if [[ "${MACHINE_COUNT_1}" -ne 27 \
       || "${MACHINE_COUNT_2}" -ne 27 \
       || "${MACHINE_COUNT_3}" -ne 26 ]]; then
      fail "${EXIT_VERIFY}" \
        "for machine_count=3 expected 27/27/26, found ${MACHINE_COUNT_1}/${MACHINE_COUNT_2}/${MACHINE_COUNT_3}"
    fi
  fi
}

print_success() {
  local i mid count_var first_var last_var
  echo "LUMEN MACHINE PACKAGE CREATION SUCCESS"
  echo "machines: ${MACHINE_COUNT}"
  echo "TIFFs packaged: ${EXPECTED_TIFF_COUNT}"
  echo
  for ((i = 1; i <= MACHINE_COUNT; i++)); do
    mid="$(machine_id_for_index "${i}")"
    count_var="MACHINE_COUNT_${i}"
    first_var="MACHINE_FIRST_${i}"
    last_var="MACHINE_LAST_${i}"
    echo "${mid}: ${!count_var} | ${!first_var} -> ${!last_var}"
  done
  echo
  echo "machine packages: ${JOB_ROOT}/machine_packages"
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

  print_success
}

main "$@"
