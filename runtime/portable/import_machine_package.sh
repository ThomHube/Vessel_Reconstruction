#!/usr/bin/env bash
# Phase 4C2C: import one verified machine package into an installed lab runtime.
# No Fiji, Maven, precheck, or image processing.
#
# Usage:
#   ./import_machine_package.sh <machine-package-root>
#
set -euo pipefail

readonly EXPECTED_COMMIT='0c1c326fc07f7e049f1d359226e810c7b8db24ce'
readonly EXPECTED_COMMIT_SHORT='0c1c326'
readonly LUMEN_RUNTIME_ROOT="${HOME}/LumenRuntime/${EXPECTED_COMMIT_SHORT}"

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3
readonly EXIT_VERIFY=5

PACKAGE_ROOT=''
INSTALL_ROOT=''
INSTALL_MACHINE_ID=''
WORK_ROOT=''
STAGING_ROOT=''
FINAL_JOB=''

# Package properties
PKG_MACHINE_ID=''
PKG_MACHINE_COUNT=''
PKG_ASSIGNED_COUNT=''
PKG_FIRST_ID=''
PKG_LAST_ID=''
PKG_CANONICAL_CONFIG_SHA=''
PKG_FILE_CONFIG_SHA=''
PKG_MASTER_MANIFEST_SHA=''
PKG_MACHINE_MANIFEST_SHA=''
PKG_PROPERTIES_SHA=''
PKG_CHECKSUMS_SHA=''

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
    .job_import_staging_*)
      ;;
    *)
      return 0
      ;;
  esac
  abs="$(cd -P -- "${staging}" && pwd)" || return 0
  parent="$(cd -P "$(dirname -- "${staging}")" && pwd)" || return 0
  if [[ -z "${WORK_ROOT:-}" || "${parent}" != "${WORK_ROOT}" ]]; then
    return 0
  fi
  if [[ "${abs}" == "${HOME}" \
     || "${abs}" == "${INSTALL_ROOT:-}" \
     || "${abs}" == "${WORK_ROOT}" \
     || "${abs}" == "${FINAL_JOB:-}" ]]; then
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
Usage: ./import_machine_package.sh <machine-package-root>

Imports one verified machine package into:
  $HOME/LumenRuntime/0c1c326/work/<machine-id>/job

Does not run precheck or processing.
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
    fail "${EXIT_VERIFY}" "$(basename -- "${props_path}") missing required key: ${key}"
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
  local part
  IFS='/' read -r -a parts <<<"${rel}"
  for part in "${parts[@]}"; do
    if [[ "${part}" == ".." ]]; then
      return 1
    fi
  done
  return 0
}

reject_any_symlink() {
  local root="$1"
  local label="$2"
  local found
  found="$(find "${root}" -type l -print -quit)"
  if [[ -n "${found}" ]]; then
    fail "${EXIT_VERIFY}" "${label} contains a symbolic link: ${found}"
  fi
}

validate_checksum_paths() {
  local package_root="$1"
  local checksums="$2"
  local line hash path
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" ]] && continue
    # sha256sum lines: "<hash>  <path>" or "<hash> *<path>"
    if [[ ! "${line}" =~ ^[0-9a-f]{64}[[:space:]][\ *](.+)$ ]]; then
      fail "${EXIT_VERIFY}" "Malformed package_checksums.sha256 line: ${line}"
    fi
    path="${BASH_REMATCH[1]}"
    path="${path#\*}"
    is_safe_relative_path "${path}" \
      || fail "${EXIT_VERIFY}" "Unsafe path in package_checksums.sha256: ${path}"
    if [[ ! -e "${package_root}/${path}" ]]; then
      fail "${EXIT_VERIFY}" "Checksum path missing in package: ${path}"
    fi
  done <"${checksums}"
}

run_checksums_in() {
  local root="$1"
  (
    cd -- "${root}"
    sha256sum -c -- package_checksums.sha256 >/dev/null
  ) || fail "${EXIT_VERIFY}" "package_checksums.sha256 verification failed in ${root}"
}

load_installation() {
  INSTALL_ROOT="$(cd -P -- "${LUMEN_RUNTIME_ROOT}" && pwd)" \
    || fail "${EXIT_VALIDATION}" "LumenRuntime installation missing: ${LUMEN_RUNTIME_ROOT}"
  local props="${INSTALL_ROOT}/installation.properties"
  require_file "${props}" "installation.properties"

  local schema commit short_ckpt install_root_prop work_root_prop
  schema="$(require_prop "${props}" "schema_version")"
  commit="$(require_prop "${props}" "production_commit")"
  short_ckpt="$(require_prop "${props}" "production_commit_short")"
  INSTALL_MACHINE_ID="$(require_prop "${props}" "machine_id")"
  install_root_prop="$(require_prop "${props}" "installation_root")"
  work_root_prop="$(require_prop "${props}" "work_root")"

  [[ "${schema}" == "1" ]] || fail "${EXIT_VERIFY}" "installation schema_version must be 1"
  [[ "${commit}" == "${EXPECTED_COMMIT}" ]] \
    || fail "${EXIT_VERIFY}" "installation production_commit mismatch"
  [[ "${short_ckpt}" == "${EXPECTED_COMMIT_SHORT}" ]] \
    || fail "${EXIT_VERIFY}" "installation production_commit_short mismatch"
  [[ "${install_root_prop}" == "${INSTALL_ROOT}" ]] \
    || fail "${EXIT_VERIFY}" \
      "installation_root mismatch: properties=${install_root_prop} actual=${INSTALL_ROOT}"
  if [[ ! "${INSTALL_MACHINE_ID}" =~ ^machine_(0[1-9]|[1-9][0-9])$ ]]; then
    fail "${EXIT_VERIFY}" "Invalid installation machine_id: ${INSTALL_MACHINE_ID}"
  fi
  WORK_ROOT="${INSTALL_ROOT}/work/${INSTALL_MACHINE_ID}"
  [[ "${work_root_prop}" == "${WORK_ROOT}" ]] \
    || fail "${EXIT_VERIFY}" \
      "work_root mismatch: properties=${work_root_prop} expected=${WORK_ROOT}"
  require_dir "${WORK_ROOT}" "installed work_root"
  FINAL_JOB="${WORK_ROOT}/job"
}

load_package_properties() {
  local props="${PACKAGE_ROOT}/package.properties"
  require_file "${props}" "package.properties"
  PKG_PROPERTIES_SHA="$(sha256_of "${props}")"

  local schema checkpoint short_ckpt config_sha
  schema="$(require_prop "${props}" "package.schema_version")"
  checkpoint="$(require_prop "${props}" "scientific_checkpoint")"
  short_ckpt="$(require_prop "${props}" "scientific_checkpoint_short")"
  PKG_MACHINE_ID="$(require_prop "${props}" "machine_id")"
  PKG_MACHINE_COUNT="$(require_prop "${props}" "machine_count")"
  PKG_ASSIGNED_COUNT="$(require_prop "${props}" "assigned_tiff_count")"
  PKG_FIRST_ID="$(require_prop "${props}" "first_image_id")"
  PKG_LAST_ID="$(require_prop "${props}" "last_image_id")"
  config_sha="$(require_prop "${props}" "production_config_sha256")"
  PKG_CANONICAL_CONFIG_SHA="$(require_prop "${props}" "production_config_canonical_sha256")"
  PKG_FILE_CONFIG_SHA="$(require_prop "${props}" "production_config_file_sha256")"
  PKG_MASTER_MANIFEST_SHA="$(require_prop "${props}" "master_manifest_sha256")"
  PKG_MACHINE_MANIFEST_SHA="$(require_prop "${props}" "machine_manifest_sha256")"

  [[ "${schema}" == "1" ]] || fail "${EXIT_VERIFY}" "package.schema_version must be 1"
  [[ "${checkpoint}" == "${EXPECTED_COMMIT}" ]] \
    || fail "${EXIT_VERIFY}" "package scientific_checkpoint mismatch"
  [[ "${short_ckpt}" == "${EXPECTED_COMMIT_SHORT}" ]] \
    || fail "${EXIT_VERIFY}" "package scientific_checkpoint_short mismatch"

  if [[ "${PKG_MACHINE_ID}" != "${INSTALL_MACHINE_ID}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Package machine_id ${PKG_MACHINE_ID} does not match installed machine_id ${INSTALL_MACHINE_ID}"
  fi
  if [[ ! "${PKG_MACHINE_COUNT}" =~ ^[1-9][0-9]?$ ]] \
     || [[ "${PKG_MACHINE_COUNT}" -lt 1 || "${PKG_MACHINE_COUNT}" -gt 99 ]]; then
    fail "${EXIT_VERIFY}" "package machine_count must be 1..99, found ${PKG_MACHINE_COUNT}"
  fi
  if [[ ! "${PKG_ASSIGNED_COUNT}" =~ ^[1-9][0-9]*$ ]]; then
    fail "${EXIT_VERIFY}" "assigned_tiff_count must be a positive integer, found ${PKG_ASSIGNED_COUNT}"
  fi
  if [[ ! "${PKG_FIRST_ID}" =~ ^[0-9]{6}$ || ! "${PKG_LAST_ID}" =~ ^[0-9]{6}$ ]]; then
    fail "${EXIT_VERIFY}" "first/last image_id must be exactly six digits"
  fi
  [[ "${config_sha}" == "${PKG_CANONICAL_CONFIG_SHA}" ]] \
    || fail "${EXIT_VERIFY}" \
      "production_config_sha256 must equal production_config_canonical_sha256"
}

validate_package_structure() {
  require_file "${PACKAGE_ROOT}/package.properties" "package.properties"
  require_file "${PACKAGE_ROOT}/package_checksums.sha256" "package_checksums.sha256"
  require_file "${PACKAGE_ROOT}/production_batch.properties" "production_batch.properties"
  require_dir "${PACKAGE_ROOT}/input" "package input/"
  require_dir "${PACKAGE_ROOT}/batch" "package batch/"
  require_dir "${PACKAGE_ROOT}/batch/machines" "package batch/machines/"

  PKG_CHECKSUMS_SHA="$(sha256_of "${PACKAGE_ROOT}/package_checksums.sha256")"

  local manifest_count
  manifest_count="$(find "${PACKAGE_ROOT}/batch/machines" -type f -name 'machine_*_manifest.csv' | wc -l | tr -d ' ')"
  [[ "${manifest_count}" -eq 1 ]] \
    || fail "${EXIT_VERIFY}" \
      "Expected exactly one machine manifest under batch/machines/, found ${manifest_count}"

  local expected_manifest="${PACKAGE_ROOT}/batch/machines/${PKG_MACHINE_ID}_manifest.csv"
  require_file "${expected_manifest}" "${PKG_MACHINE_ID}_manifest.csv"
}

validate_package_contents() {
  validate_checksum_paths "${PACKAGE_ROOT}" "${PACKAGE_ROOT}/package_checksums.sha256"
  run_checksums_in "${PACKAGE_ROOT}"

  local actual_config_sha
  actual_config_sha="$(sha256_of "${PACKAGE_ROOT}/production_batch.properties")"
  [[ "${actual_config_sha}" == "${PKG_FILE_CONFIG_SHA}" ]] \
    || fail "${EXIT_VERIFY}" \
      "production_batch.properties raw SHA-256 mismatch: expected ${PKG_FILE_CONFIG_SHA}, found ${actual_config_sha}"

  local manifest="${PACKAGE_ROOT}/batch/machines/${PKG_MACHINE_ID}_manifest.csv"
  local actual_manifest_sha
  actual_manifest_sha="$(sha256_of "${manifest}")"
  [[ "${actual_manifest_sha}" == "${PKG_MACHINE_MANIFEST_SHA}" ]] \
    || fail "${EXIT_VERIFY}" \
      "machine manifest SHA-256 mismatch: expected ${PKG_MACHINE_MANIFEST_SHA}, found ${actual_manifest_sha}"

  validate_manifest_and_inputs "${PACKAGE_ROOT}" "${manifest}"
}

validate_manifest_and_inputs() {
  local root="$1"
  local manifest="$2"
  local header line
  IFS= read -r header <"${manifest}" || true
  [[ -n "${header}" ]] || fail "${EXIT_VERIFY}" "machine manifest header missing"

  local -a image_ids=()
  local -a rel_paths=()
  local -a source_shas=()
  local -a source_sizes=()
  local -A seen_ids=()
  local data_rows=0
  local image_id source_rel source_sha source_size assigned config_hash

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == "${header}" ]] && continue

    image_id="$(csv_field "${line}" 3)"
    source_rel="$(csv_field "${line}" 8)"
    source_sha="$(csv_field "${line}" 9)"
    source_size="$(csv_field "${line}" 10)"
    assigned="$(csv_field "${line}" 11)"
    config_hash="$(csv_field "${line}" 21)"

    [[ "${assigned}" == "${PKG_MACHINE_ID}" ]] \
      || fail "${EXIT_VERIFY}" "manifest assigned_machine ${assigned} != ${PKG_MACHINE_ID}"
    [[ "${config_hash}" == "${PKG_CANONICAL_CONFIG_SHA}" ]] \
      || fail "${EXIT_VERIFY}" "manifest configuration_sha256 mismatch for ${image_id}"
    [[ "${image_id}" =~ ^[0-9]{6}$ ]] \
      || fail "${EXIT_VERIFY}" "manifest image_id not six digits: ${image_id}"
    if [[ -n "${seen_ids[${image_id}]+x}" ]]; then
      fail "${EXIT_VERIFY}" "duplicate image_id in manifest: ${image_id}"
    fi
    seen_ids["${image_id}"]=1
    is_safe_relative_path "${source_rel}" \
      || fail "${EXIT_VERIFY}" "unsafe source_relative_path: ${source_rel}"

    image_ids+=("${image_id}")
    rel_paths+=("${source_rel}")
    source_shas+=("${source_sha}")
    source_sizes+=("${source_size}")
    data_rows=$((data_rows + 1))
  done <"${manifest}"

  [[ "${data_rows}" -eq "${PKG_ASSIGNED_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" \
      "manifest data rows ${data_rows} != assigned_tiff_count ${PKG_ASSIGNED_COUNT}"
  [[ "${image_ids[0]}" == "${PKG_FIRST_ID}" ]] \
    || fail "${EXIT_VERIFY}" "first image_id mismatch"
  [[ "${image_ids[$((data_rows - 1))]}" == "${PKG_LAST_ID}" ]] \
    || fail "${EXIT_VERIFY}" "last image_id mismatch"

  local i path actual_sha actual_size
  for ((i = 0; i < data_rows; i++)); do
    path="${root}/input/${rel_paths[$i]}"
    [[ -f "${path}" && ! -L "${path}" ]] \
      || fail "${EXIT_VERIFY}" "missing or non-regular input file: input/${rel_paths[$i]}"
    actual_sha="$(sha256_of "${path}")"
    [[ "${actual_sha}" == "${source_shas[$i]}" ]] \
      || fail "${EXIT_VERIFY}" "TIFF SHA-256 mismatch for ${image_ids[$i]}"
    actual_size="$(wc -c <"${path}" | tr -d ' ')"
    [[ "${actual_size}" == "${source_sizes[$i]}" ]] \
      || fail "${EXIT_VERIFY}" "TIFF size mismatch for ${image_ids[$i]}"
  done

  local found rel_found expected
  while IFS= read -r -d '' found; do
    case "${found}" in
      *.tif|*.tiff|*.TIF|*.TIFF)
        ;;
      *)
        fail "${EXIT_VERIFY}" "non-TIFF regular file under input/: ${found#${root}/input/}"
        ;;
    esac
    rel_found="${found#${root}/input/}"
    expected=0
    for ((i = 0; i < data_rows; i++)); do
      if [[ "${rel_found}" == "${rel_paths[$i]}" ]]; then
        expected=1
        break
      fi
    done
    [[ "${expected}" -eq 1 ]] \
      || fail "${EXIT_VERIFY}" "extra TIFF under input/ not in manifest: ${rel_found}"
  done < <(find "${root}/input" -type f -print0)

  local input_count
  input_count="$(find "${root}/input" -type f | wc -l | tr -d ' ')"
  [[ "${input_count}" -eq "${PKG_ASSIGNED_COUNT}" ]] \
    || fail "${EXIT_VERIFY}" \
      "input file count ${input_count} != assigned_tiff_count ${PKG_ASSIGNED_COUNT}"
}

handle_existing_job() {
  local import_props="${FINAL_JOB}/import.properties"
  require_file "${import_props}" "existing job import.properties"

  local existing_machine existing_checkpoint existing_props_sha existing_checksums_sha
  local existing_count existing_file_sha existing_manifest_sha
  existing_machine="$(require_prop "${import_props}" "machine_id")"
  existing_checkpoint="$(require_prop "${import_props}" "scientific_checkpoint")"
  existing_props_sha="$(require_prop "${import_props}" "source_package_properties_sha256")"
  existing_checksums_sha="$(require_prop "${import_props}" "source_package_checksums_sha256")"
  existing_count="$(require_prop "${import_props}" "assigned_tiff_count")"
  existing_file_sha="$(require_prop "${import_props}" "production_config_file_sha256")"
  existing_manifest_sha="$(require_prop "${import_props}" "machine_manifest_sha256")"

  if [[ "${existing_machine}" != "${PKG_MACHINE_ID}" \
     || "${existing_checkpoint}" != "${EXPECTED_COMMIT}" \
     || "${existing_props_sha}" != "${PKG_PROPERTIES_SHA}" \
     || "${existing_checksums_sha}" != "${PKG_CHECKSUMS_SHA}" \
     || "${existing_count}" != "${PKG_ASSIGNED_COUNT}" \
     || "${existing_file_sha}" != "${PKG_FILE_CONFIG_SHA}" \
     || "${existing_manifest_sha}" != "${PKG_MACHINE_MANIFEST_SHA}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Existing job at ${FINAL_JOB} does not match the supplied package; refusing to mutate it"
  fi

  require_file "${FINAL_JOB}/package_checksums.sha256" "existing job package_checksums.sha256"
  validate_checksum_paths "${FINAL_JOB}" "${FINAL_JOB}/package_checksums.sha256"
  run_checksums_in "${FINAL_JOB}"

  cat <<EOF
LUMEN MACHINE PACKAGE ALREADY IMPORTED
machine: ${PKG_MACHINE_ID}
job root: ${FINAL_JOB}
No changes made.
EOF
  exit 0
}

write_import_properties() {
  local dest="$1"
  local timestamp_ms
  timestamp_ms="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"
  cat >"${dest}" <<EOF
import.schema_version=1
scientific_checkpoint=${EXPECTED_COMMIT}
scientific_checkpoint_short=${EXPECTED_COMMIT_SHORT}
machine_id=${PKG_MACHINE_ID}
machine_count=${PKG_MACHINE_COUNT}
assigned_tiff_count=${PKG_ASSIGNED_COUNT}
first_image_id=${PKG_FIRST_ID}
last_image_id=${PKG_LAST_ID}
production_config_sha256=${PKG_CANONICAL_CONFIG_SHA}
production_config_canonical_sha256=${PKG_CANONICAL_CONFIG_SHA}
production_config_file_sha256=${PKG_FILE_CONFIG_SHA}
master_manifest_sha256=${PKG_MASTER_MANIFEST_SHA}
machine_manifest_sha256=${PKG_MACHINE_MANIFEST_SHA}
source_package_properties_sha256=${PKG_PROPERTIES_SHA}
source_package_checksums_sha256=${PKG_CHECKSUMS_SHA}
import_timestamp_epoch_ms=${timestamp_ms}
EOF
}

perform_import() {
  STAGING_ROOT="${WORK_ROOT}/.job_import_staging_$$"
  rm -rf -- "${STAGING_ROOT}"
  mkdir -p -- "${STAGING_ROOT}"

  echo "Copying package into staging..."
  cp -a -- "${PACKAGE_ROOT}/input" "${STAGING_ROOT}/input"
  cp -a -- "${PACKAGE_ROOT}/batch" "${STAGING_ROOT}/batch"
  cp -f -- "${PACKAGE_ROOT}/production_batch.properties" "${STAGING_ROOT}/production_batch.properties"
  cp -f -- "${PACKAGE_ROOT}/package.properties" "${STAGING_ROOT}/package.properties"
  cp -f -- "${PACKAGE_ROOT}/package_checksums.sha256" "${STAGING_ROOT}/package_checksums.sha256"

  reject_any_symlink "${STAGING_ROOT}" "staging import"
  validate_checksum_paths "${STAGING_ROOT}" "${STAGING_ROOT}/package_checksums.sha256"
  run_checksums_in "${STAGING_ROOT}"

  [[ "$(sha256_of "${STAGING_ROOT}/production_batch.properties")" == "${PKG_FILE_CONFIG_SHA}" ]] \
    || fail "${EXIT_VERIFY}" "staged production_batch.properties SHA-256 mismatch"
  [[ "$(sha256_of "${STAGING_ROOT}/batch/machines/${PKG_MACHINE_ID}_manifest.csv")" == "${PKG_MACHINE_MANIFEST_SHA}" ]] \
    || fail "${EXIT_VERIFY}" "staged machine manifest SHA-256 mismatch"

  validate_manifest_and_inputs \
    "${STAGING_ROOT}" \
    "${STAGING_ROOT}/batch/machines/${PKG_MACHINE_ID}_manifest.csv"

  write_import_properties "${STAGING_ROOT}/import.properties"

  if [[ -e "${FINAL_JOB}" ]]; then
    fail "${EXIT_VALIDATION}" "Job root appeared during staging; refusing to publish: ${FINAL_JOB}"
  fi

  echo "Publishing imported job..."
  mv -- "${STAGING_ROOT}" "${FINAL_JOB}"
  STAGING_ROOT=''
}

print_success() {
  cat <<EOF
LUMEN MACHINE PACKAGE IMPORT SUCCESS
machine: ${PKG_MACHINE_ID}
TIFFs imported: ${PKG_ASSIGNED_COUNT}
first: ${PKG_FIRST_ID}
last: ${PKG_LAST_ID}
job root: ${FINAL_JOB}
package checksums: verified
machine manifest: verified
production config: verified
source package modified: NO
precheck launched: NO
processing launched: NO
EOF
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./import_machine_package.sh <machine-package-root>" >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  trap on_exit EXIT

  if [[ "$#" -ne 1 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected exactly one argument: <machine-package-root>"
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

  [[ -d "$1" ]] || fail "${EXIT_VALIDATION}" "machine-package-root is not a directory: $1"
  PACKAGE_ROOT="$(cd -P -- "$1" && pwd)"

  load_installation
  reject_any_symlink "${PACKAGE_ROOT}" "source package"
  load_package_properties
  validate_package_structure
  validate_package_contents

  if [[ -e "${FINAL_JOB}" ]]; then
    if [[ ! -d "${FINAL_JOB}" ]]; then
      fail "${EXIT_VALIDATION}" "job path exists but is not a directory: ${FINAL_JOB}"
    fi
    handle_existing_job
  fi

  perform_import
  print_success
}

main "$@"
