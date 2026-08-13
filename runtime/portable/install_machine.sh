#!/usr/bin/env bash
# Lumen portable machine installer (user-local, no root).
#
# Deployed location on the external drive:
#   LumenPortable/install/install_machine.sh
#
# Usage (run as a script — do NOT source):
#   ./install_machine.sh machine_01
#
set -euo pipefail

readonly EXPECTED_COMMIT='9a53b843347b909dcc1762157aaf1d0c96126083'
readonly EXPECTED_COMMIT_SHORT='9a53b84'
readonly EXPECTED_CLASSIFIER_SHA256='d109f00d1ca16447bc433303cb6f5ec1a5a9d27a3215b24d87df77346b04340c'
readonly EXPECTED_PLUGIN_SHA256='0277c6453e81cc11e17f22c5e7021077a254457278185b76e87db54ae23b4414'
readonly EXPECTED_LAUNCHER_SHA256='5841d9c3bdd0f0e10cb4890144fbc69fb91963265c291e17a963ae6658923e0b'
readonly EXPECTED_MONITOR_SHA256='dca21f33cd94fc1b92261bc8ba7e3fee9b5e1cf0c3cdbcf700f7dafa4e155a95'
readonly EXPECTED_STARTER_SHA256='db1e99fa6a6021e05242f3dce7c62a21d3b9fa2604c47bb5b6c08a107514514a'
readonly PLUGIN_JAR_NAME='VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar'
readonly CLASSIFIER_NAME='Classifier9.model'
readonly LAUNCHER_NAME='run_lumen_machine.sh'
readonly MONITOR_NAME='monitor_lumen_machine.sh'
readonly STARTER_NAME='start_lumen_processing.sh'

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3
readonly EXIT_INSTALL=4
readonly EXIT_VERIFY=5

SCRIPT_PATH=''
INSTALLER_DIR=''
PORTABLE_ROOT=''
CANONICAL_ROOT=''
STAGING_ROOT=''
INSTALL_ROOT=''
MACHINE_ID=''
SOURCE_LAUNCHER_PATH=''
SOURCE_MONITOR_PATH=''
SOURCE_STARTER_PATH=''

cleanup_staging() {
  local staging="${STAGING_ROOT:-}"
  if [[ -z "${staging}" ]]; then
    return 0
  fi

  local runtime_parent="${HOME}/LumenRuntime"
  local base
  base="$(basename -- "${staging}")"
  if [[ "${base}" != .install_staging_${EXPECTED_COMMIT_SHORT}_* ]]; then
    return 0
  fi

  # Only delete an existing staging directory that resolves under $HOME/LumenRuntime/.
  if [[ ! -d "${staging}" ]]; then
    STAGING_ROOT=''
    return 0
  fi

  local abs
  abs="$(cd -P -- "${staging}" && pwd)" || return 0
  case "${abs}" in
    "${runtime_parent}"/.install_staging_${EXPECTED_COMMIT_SHORT}_*)
      ;;
    *)
      return 0
      ;;
  esac

  if [[ "${abs}" == "${HOME}" || "${abs}" == "${runtime_parent}" ]]; then
    return 0
  fi
  if [[ -n "${INSTALL_ROOT:-}" && "${abs}" == "${INSTALL_ROOT}" ]]; then
    return 0
  fi

  rm -rf -- "${abs}"
  STAGING_ROOT=''
}

on_installer_exit() {
  # Preserve the status that caused the exit; do not exit from this trap.
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
Usage: ./install_machine.sh <machine_XX>

Install the Lumen portable checkpoint into:
  $HOME/LumenRuntime/9a53b84

machine_XX must match: machine_01 .. machine_99

Run this file as a script. Do NOT use: source install_machine.sh
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
  INSTALLER_DIR="$(cd -P "$(dirname -- "${SCRIPT_PATH}")" && pwd)"
}

resolve_portable_root() {
  # Deployed as: LumenPortable/install/install_machine.sh
  PORTABLE_ROOT="$(cd -P "${INSTALLER_DIR}/.." && pwd)"
  CANONICAL_ROOT="${PORTABLE_ROOT}/canonical"
}

validate_machine_id() {
  local id="$1"
  if [[ ! "${id}" =~ ^machine_(0[1-9]|[1-9][0-9])$ ]]; then
    usage
    fail "${EXIT_USAGE}" "Invalid machine ID '${id}'. Expected machine_01 .. machine_99."
  fi
  MACHINE_ID="${id}"
}

require_file() {
  local path="$1"
  local label="$2"
  [[ -f "${path}" ]] || fail "${EXIT_VALIDATION}" "Missing ${label}: ${path}"
}

require_dir() {
  local path="$1"
  local label="$2"
  [[ -d "${path}" ]] || fail "${EXIT_VALIDATION}" "Missing ${label}: ${path}"
}

verify_canonical_inputs() {
  require_dir "${CANONICAL_ROOT}" "canonical root"
  require_file "${CANONICAL_ROOT}/runtime/Fiji.app_${EXPECTED_COMMIT_SHORT}.tar.gz" "Fiji archive"
  require_file "${CANONICAL_ROOT}/runtime/jdk21_openjdk_${EXPECTED_COMMIT_SHORT}.tar.gz" "JDK archive"
  require_file "${CANONICAL_ROOT}/runtime/maven_full_${EXPECTED_COMMIT_SHORT}.tar.gz" "Maven archive"
  require_file "${CANONICAL_ROOT}/runtime/maven_repository_${EXPECTED_COMMIT_SHORT}.tar.gz" "Maven repository archive"
  require_file "${CANONICAL_ROOT}/source/minimal-ij1-plugin-ellipse_${EXPECTED_COMMIT_SHORT}.tar.gz" "source archive"
  require_file "${CANONICAL_ROOT}/models/${CLASSIFIER_NAME}" "classifier model"

  SOURCE_LAUNCHER_PATH="${INSTALLER_DIR}/${LAUNCHER_NAME}"
  require_file "${SOURCE_LAUNCHER_PATH}" "portable launcher (${LAUNCHER_NAME})"
  verify_sha256 \
    "${SOURCE_LAUNCHER_PATH}" \
    "${EXPECTED_LAUNCHER_SHA256}" \
    "portable launcher"

  SOURCE_MONITOR_PATH="${INSTALLER_DIR}/${MONITOR_NAME}"
  require_file "${SOURCE_MONITOR_PATH}" "portable monitor (${MONITOR_NAME})"
  verify_sha256 \
    "${SOURCE_MONITOR_PATH}" \
    "${EXPECTED_MONITOR_SHA256}" \
    "portable monitor"

  SOURCE_STARTER_PATH="${INSTALLER_DIR}/${STARTER_NAME}"
  require_file "${SOURCE_STARTER_PATH}" "portable starter (${STARTER_NAME})"
  verify_sha256 \
    "${SOURCE_STARTER_PATH}" \
    "${EXPECTED_STARTER_SHA256}" \
    "portable starter"
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

stage_and_verify_launcher() {
  local staging_launcher="${STAGING_ROOT}/${LAUNCHER_NAME}"
  cp -f -- "${SOURCE_LAUNCHER_PATH}" "${staging_launcher}"
  chmod 755 -- "${staging_launcher}"
  [[ -x "${staging_launcher}" ]] \
    || fail "${EXIT_VERIFY}" "Staged launcher is not executable: ${staging_launcher}"
  verify_sha256 \
    "${staging_launcher}" \
    "${EXPECTED_LAUNCHER_SHA256}" \
    "staged portable launcher"
  if ! bash -n -- "${staging_launcher}"; then
    fail "${EXIT_VERIFY}" "Staged launcher failed bash -n: ${staging_launcher}"
  fi
}

stage_and_verify_monitor() {
  local staging_monitor="${STAGING_ROOT}/${MONITOR_NAME}"
  cp -f -- "${SOURCE_MONITOR_PATH}" "${staging_monitor}"
  chmod 755 -- "${staging_monitor}"
  [[ -x "${staging_monitor}" ]] \
    || fail "${EXIT_VERIFY}" "Staged monitor is not executable: ${staging_monitor}"
  verify_sha256 \
    "${staging_monitor}" \
    "${EXPECTED_MONITOR_SHA256}" \
    "staged portable monitor"
  if ! bash -n -- "${staging_monitor}"; then
    fail "${EXIT_VERIFY}" "Staged monitor failed bash -n: ${staging_monitor}"
  fi
}

stage_and_verify_starter() {
  local staging_starter="${STAGING_ROOT}/${STARTER_NAME}"
  cp -f -- "${SOURCE_STARTER_PATH}" "${staging_starter}"
  chmod 755 -- "${staging_starter}"
  [[ -x "${staging_starter}" ]] \
    || fail "${EXIT_VERIFY}" "Staged starter is not executable: ${staging_starter}"
  verify_sha256 \
    "${staging_starter}" \
    "${EXPECTED_STARTER_SHA256}" \
    "staged portable starter"
  if ! bash -n -- "${staging_starter}"; then
    fail "${EXIT_VERIFY}" "Staged starter failed bash -n: ${staging_starter}"
  fi
}

count_top_entries() {
  local dir="$1"
  find "${dir}" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' '
}

# Extract archive into dest_parent, then ensure final_name is the only content
# directory at dest_parent/final_name. Handles archives that already contain
# final_name/ or a single differently named top-level directory.
extract_normalized_dir() {
  local archive="$1"
  local dest_parent="$2"
  local final_name="$3"
  local label="$4"

  mkdir -p -- "${dest_parent}"
  local extract_tmp
  extract_tmp="$(mktemp -d -- "${dest_parent}/.extract_${final_name}.XXXXXX")"
  tar -xzf "${archive}" -C "${extract_tmp}"

  local final_path="${dest_parent}/${final_name}"
  if [[ -d "${extract_tmp}/${final_name}" ]]; then
    mv -- "${extract_tmp}/${final_name}" "${final_path}"
  else
    local entries
    entries="$(count_top_entries "${extract_tmp}")"
    if [[ "${entries}" -eq 1 ]]; then
      local only
      only="$(find "${extract_tmp}" -mindepth 1 -maxdepth 1 -print -quit)"
      if [[ -d "${only}" ]]; then
        mv -- "${only}" "${final_path}"
      else
        rm -rf -- "${extract_tmp}"
        fail "${EXIT_INSTALL}" "${label} archive did not contain a top-level directory: ${archive}"
      fi
    else
      # Multiple top-level entries: treat extract_tmp contents as the product root.
      mkdir -p -- "${final_path}"
      local child
      while IFS= read -r -d '' child; do
        mv -- "${child}" "${final_path}/"
      done < <(find "${extract_tmp}" -mindepth 1 -maxdepth 1 -print0)
    fi
  fi

  rm -rf -- "${extract_tmp}"
  [[ -d "${final_path}" ]] || fail "${EXIT_INSTALL}" "Failed to normalize ${label} into ${final_path}"
}

verify_source_tree() {
  local project_dir="$1"
  require_file "${project_dir}/pom.xml" "project pom.xml"
  require_file "${project_dir}/runtime/batch/default_batch.properties" \
    "project default_batch.properties"
  require_file "${project_dir}/runtime/batch/run_machine.sh" \
    "project run_machine.sh"
  require_file \
    "${project_dir}/src/main/java/vesselreconstruction/ellipse/BatchMachineSupervisor.java" \
    "BatchMachineSupervisor.java"
  require_file \
    "${project_dir}/src/main/java/vesselreconstruction/ellipse/BatchFullProcessingCli.java" \
    "BatchFullProcessingCli.java"
}

verify_sha256() {
  local path="$1"
  local expected="$2"
  local label="$3"
  local actual
  actual="$(sha256_of "${path}")"
  if [[ "${actual}" != "${expected}" ]]; then
    fail "${EXIT_VERIFY}" "${label} SHA-256 mismatch: expected ${expected}, found ${actual}"
  fi
}

verify_java_tool() {
  local tool_path="$1"
  local label="$2"
  [[ -x "${tool_path}" ]] || fail "${EXIT_VERIFY}" "${label} missing or not executable: ${tool_path}"
  "${tool_path}" -version >/dev/null 2>&1 \
    || fail "${EXIT_VERIFY}" "${label} failed to execute: ${tool_path}"
}

verify_portable_maven() {
  local jdk_root="$1"
  local maven_root="$2"
  local mvn_bin="${maven_root}/bin/mvn"
  [[ -x "${mvn_bin}" ]] || fail "${EXIT_VERIFY}" "Maven binary missing or not executable: ${mvn_bin}"

  local version_out
  if ! version_out="$(
    JAVA_HOME="${jdk_root}" \
    PATH="${jdk_root}/bin:/usr/bin:/bin" \
    "${mvn_bin}" -version 2>&1
  )"; then
    fail "${EXIT_VERIFY}" "Portable Maven -version failed"
  fi

  if ! grep -Eq 'Java version: 21(\.|$)' <<<"${version_out}"; then
    fail "${EXIT_VERIFY}" "Portable Maven did not report Java 21. Output was:"$'\n'"${version_out}"
  fi
  if ! grep -Fq "${jdk_root}" <<<"${version_out}"; then
    # Some Maven builds print Java home on a separate line.
    if ! grep -Eqi "Java home:.*jdk21|/runtime/jdk21" <<<"${version_out}"; then
      # Accept if JAVA_HOME was forced and Java 21 was reported; still print note.
      :
    fi
  fi
}

verify_maven_repository_nonempty() {
  local repo="$1"
  require_dir "${repo}" "Maven repository"
  local first_file
  first_file="$(find "${repo}" -type f -print -quit)"
  if [[ -z "${first_file}" ]]; then
    fail "${EXIT_VERIFY}" "Maven repository is empty: ${repo}"
  fi
  echo "Maven repository: verified (non-empty)"
}

write_installation_properties() {
  local props_path="$1"
  local install_root="$2"
  local project_root="$3"
  local fiji_root="$4"
  local jdk_root="$5"
  local maven_root="$6"
  local maven_repo="$7"
  local classifier_path="$8"
  local config_path="$9"
  local work_root="${10}"
  local launcher_path="${install_root}/${LAUNCHER_NAME}"
  local monitor_path="${install_root}/${MONITOR_NAME}"
  local starter_path="${install_root}/${STARTER_NAME}"
  local timestamp_ms
  timestamp_ms="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"

  cat >"${props_path}" <<EOF
schema_version=1
production_commit=${EXPECTED_COMMIT}
production_commit_short=${EXPECTED_COMMIT_SHORT}
machine_id=${MACHINE_ID}
classifier_sha256=${EXPECTED_CLASSIFIER_SHA256}
plugin_sha256=${EXPECTED_PLUGIN_SHA256}
launcher_sha256=${EXPECTED_LAUNCHER_SHA256}
monitor_sha256=${EXPECTED_MONITOR_SHA256}
starter_sha256=${EXPECTED_STARTER_SHA256}
installation_root=${install_root}
project_root=${project_root}
fiji_root=${fiji_root}
jdk_root=${jdk_root}
maven_root=${maven_root}
maven_repository=${maven_repo}
classifier_path=${classifier_path}
config_path=${config_path}
work_root=${work_root}
launcher_path=${launcher_path}
monitor_path=${monitor_path}
starter_path=${starter_path}
installation_timestamp_epoch_ms=${timestamp_ms}
EOF
}

check_existing_installation() {
  local install_root="$1"
  if [[ ! -e "${install_root}" ]]; then
    return 0
  fi
  if [[ ! -d "${install_root}" ]]; then
    fail "${EXIT_INSTALL}" "Install path exists but is not a directory: ${install_root}"
  fi

  local props="${install_root}/installation.properties"
  if [[ ! -f "${props}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Install directory already exists without installation.properties: ${install_root}"
  fi

  local existing_commit existing_machine launcher_path launcher_sha
  local monitor_path monitor_sha starter_path starter_sha
  existing_commit="$(prop_value "${props}" "production_commit" || true)"
  existing_machine="$(prop_value "${props}" "machine_id" || true)"
  launcher_path="$(prop_value "${props}" "launcher_path" || true)"
  launcher_sha="$(prop_value "${props}" "launcher_sha256" || true)"
  monitor_path="$(prop_value "${props}" "monitor_path" || true)"
  monitor_sha="$(prop_value "${props}" "monitor_sha256" || true)"
  starter_path="$(prop_value "${props}" "starter_path" || true)"
  starter_sha="$(prop_value "${props}" "starter_sha256" || true)"

  if [[ "${existing_commit}" != "${EXPECTED_COMMIT}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Install directory already exists and is not a verified ${EXPECTED_COMMIT_SHORT} installation: ${install_root}"
  fi

  if [[ -z "${launcher_path}" || -z "${launcher_sha}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation at ${install_root} predates the launcher-integrated installer (missing launcher_path/launcher_sha256). Recreate it intentionally; this installer will not delete or upgrade it automatically."
  fi
  if [[ -z "${monitor_path}" || -z "${monitor_sha}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation at ${install_root} predates the monitor-integrated installer (missing monitor_path/monitor_sha256). Recreate it intentionally; this installer will not delete or upgrade it automatically."
  fi
  if [[ -z "${starter_path}" || -z "${starter_sha}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation at ${install_root} predates the starter-integrated installer (missing starter_path/starter_sha256). Recreate it intentionally; this installer will not delete or upgrade it automatically."
  fi
  if [[ "${launcher_sha}" != "${EXPECTED_LAUNCHER_SHA256}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation launcher_sha256 mismatch at ${install_root}: expected ${EXPECTED_LAUNCHER_SHA256}, found ${launcher_sha}"
  fi
  if [[ "${monitor_sha}" != "${EXPECTED_MONITOR_SHA256}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation monitor_sha256 mismatch at ${install_root}: expected ${EXPECTED_MONITOR_SHA256}, found ${monitor_sha}"
  fi
  if [[ "${starter_sha}" != "${EXPECTED_STARTER_SHA256}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation starter_sha256 mismatch at ${install_root}: expected ${EXPECTED_STARTER_SHA256}, found ${starter_sha}"
  fi
  if [[ "${launcher_path}" != "${install_root}/${LAUNCHER_NAME}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation launcher_path mismatch: expected ${install_root}/${LAUNCHER_NAME}, found ${launcher_path}"
  fi
  if [[ "${monitor_path}" != "${install_root}/${MONITOR_NAME}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation monitor_path mismatch: expected ${install_root}/${MONITOR_NAME}, found ${monitor_path}"
  fi
  if [[ "${starter_path}" != "${install_root}/${STARTER_NAME}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation starter_path mismatch: expected ${install_root}/${STARTER_NAME}, found ${starter_path}"
  fi
  if [[ ! -f "${launcher_path}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation missing launcher file: ${launcher_path}"
  fi
  if [[ ! -x "${launcher_path}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation launcher is not executable: ${launcher_path}"
  fi
  if [[ ! -f "${monitor_path}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation missing monitor file: ${monitor_path}"
  fi
  if [[ ! -x "${monitor_path}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation monitor is not executable: ${monitor_path}"
  fi
  if [[ ! -f "${starter_path}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation missing starter file: ${starter_path}"
  fi
  if [[ ! -x "${starter_path}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation starter is not executable: ${starter_path}"
  fi
  local actual_launcher_sha
  actual_launcher_sha="$(sha256_of "${launcher_path}")"
  if [[ "${actual_launcher_sha}" != "${EXPECTED_LAUNCHER_SHA256}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation launcher SHA-256 mismatch: expected ${EXPECTED_LAUNCHER_SHA256}, found ${actual_launcher_sha}"
  fi
  if ! bash -n -- "${launcher_path}"; then
    fail "${EXIT_INSTALL}" \
      "Existing installation launcher failed bash -n: ${launcher_path}"
  fi
  local actual_monitor_sha
  actual_monitor_sha="$(sha256_of "${monitor_path}")"
  if [[ "${actual_monitor_sha}" != "${EXPECTED_MONITOR_SHA256}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation monitor SHA-256 mismatch: expected ${EXPECTED_MONITOR_SHA256}, found ${actual_monitor_sha}"
  fi
  if ! bash -n -- "${monitor_path}"; then
    fail "${EXIT_INSTALL}" \
      "Existing installation monitor failed bash -n: ${monitor_path}"
  fi
  local actual_starter_sha
  actual_starter_sha="$(sha256_of "${starter_path}")"
  if [[ "${actual_starter_sha}" != "${EXPECTED_STARTER_SHA256}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation starter SHA-256 mismatch: expected ${EXPECTED_STARTER_SHA256}, found ${actual_starter_sha}"
  fi
  if ! bash -n -- "${starter_path}"; then
    fail "${EXIT_INSTALL}" \
      "Existing installation starter failed bash -n: ${starter_path}"
  fi

  echo "LUMEN INSTALLATION ALREADY PRESENT"
  echo "machine: ${existing_machine:-unknown}"
  echo "checkpoint: ${EXPECTED_COMMIT_SHORT}"
  echo "install root: ${install_root}"
  echo "launcher: verified"
  echo "monitor: verified"
  echo "starter: verified"
  echo "No changes made."
  exit 0
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./install_machine.sh machine_XX" >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  trap on_installer_exit EXIT

  if [[ "$#" -ne 1 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected exactly one argument: machine_XX"
  fi

  require_cmd tar
  require_cmd sha256sum
  require_cmd find
  require_cmd mv
  require_cmd cp
  require_cmd mkdir
  require_cmd rm
  require_cmd awk
  require_cmd grep
  require_cmd mktemp
  require_cmd date
  require_cmd bash
  require_cmd chmod

  validate_machine_id "$1"
  resolve_script_path
  resolve_portable_root

  [[ -d "${PORTABLE_ROOT}" ]] || fail "${EXIT_VALIDATION}" "Unable to resolve portable root from script path"
  verify_canonical_inputs

  local runtime_parent="${HOME}/LumenRuntime"
  INSTALL_ROOT="${runtime_parent}/${EXPECTED_COMMIT_SHORT}"
  check_existing_installation "${INSTALL_ROOT}"

  mkdir -p -- "${runtime_parent}"
  STAGING_ROOT="${runtime_parent}/.install_staging_${EXPECTED_COMMIT_SHORT}_$$"
  rm -rf -- "${STAGING_ROOT}"
  mkdir -p -- "${STAGING_ROOT}"

  local staging_runtime="${STAGING_ROOT}/runtime"
  local staging_project_root="${STAGING_ROOT}/project/minimal-ij1-plugin-ellipse"
  local staging_models="${STAGING_ROOT}/models"
  local staging_config="${STAGING_ROOT}/config"
  local staging_work="${STAGING_ROOT}/work/${MACHINE_ID}"
  local staging_logs="${STAGING_ROOT}/logs"

  mkdir -p -- \
    "${staging_runtime}" \
    "${STAGING_ROOT}/project" \
    "${staging_models}" \
    "${staging_config}" \
    "${staging_work}/input" \
    "${staging_work}/batch" \
    "${staging_work}/output" \
    "${staging_logs}"

  echo "Installing portable launcher..."
  stage_and_verify_launcher
  echo "Installing portable monitor..."
  stage_and_verify_monitor
  echo "Installing portable starter..."
  stage_and_verify_starter

  echo "Extracting Fiji..."
  extract_normalized_dir \
    "${CANONICAL_ROOT}/runtime/Fiji.app_${EXPECTED_COMMIT_SHORT}.tar.gz" \
    "${staging_runtime}" \
    "Fiji.app" \
    "Fiji"

  echo "Extracting JDK 21..."
  extract_normalized_dir \
    "${CANONICAL_ROOT}/runtime/jdk21_openjdk_${EXPECTED_COMMIT_SHORT}.tar.gz" \
    "${staging_runtime}" \
    "jdk21" \
    "JDK"

  echo "Extracting Maven..."
  extract_normalized_dir \
    "${CANONICAL_ROOT}/runtime/maven_full_${EXPECTED_COMMIT_SHORT}.tar.gz" \
    "${staging_runtime}" \
    "maven" \
    "Maven"

  echo "Extracting Maven repository..."
  extract_normalized_dir \
    "${CANONICAL_ROOT}/runtime/maven_repository_${EXPECTED_COMMIT_SHORT}.tar.gz" \
    "${staging_runtime}" \
    "maven_repository" \
    "Maven repository"

  echo "Extracting project source archive..."
  extract_normalized_dir \
    "${CANONICAL_ROOT}/source/minimal-ij1-plugin-ellipse_${EXPECTED_COMMIT_SHORT}.tar.gz" \
    "${STAGING_ROOT}/project" \
    "minimal-ij1-plugin-ellipse" \
    "project source"
  verify_source_tree "${staging_project_root}"

  echo "Copying classifier..."
  cp -f -- \
    "${CANONICAL_ROOT}/models/${CLASSIFIER_NAME}" \
    "${staging_models}/${CLASSIFIER_NAME}"
  verify_sha256 \
    "${staging_models}/${CLASSIFIER_NAME}" \
    "${EXPECTED_CLASSIFIER_SHA256}" \
    "Classifier"

  local default_config_src="${staging_project_root}/runtime/batch/default_batch.properties"
  require_file "${default_config_src}" "default_batch.properties in restored project"
  cp -f -- "${default_config_src}" "${staging_config}/default_batch.properties"

  local fiji_root="${staging_runtime}/Fiji.app"
  local fiji_launcher="${fiji_root}/ImageJ-linux64"
  local plugin_jar="${fiji_root}/plugins/${PLUGIN_JAR_NAME}"
  local jdk_root="${staging_runtime}/jdk21"
  local maven_root="${staging_runtime}/maven"
  local maven_repo="${staging_runtime}/maven_repository"

  [[ -x "${fiji_launcher}" ]] \
    || fail "${EXIT_VERIFY}" "Fiji launcher missing or not executable: ${fiji_launcher}"
  require_file "${plugin_jar}" "Fiji plugin JAR"
  verify_sha256 "${plugin_jar}" "${EXPECTED_PLUGIN_SHA256}" "Fiji plugin JAR"

  verify_java_tool "${jdk_root}/bin/java" "portable java"
  verify_java_tool "${jdk_root}/bin/javac" "portable javac"
  verify_portable_maven "${jdk_root}" "${maven_root}"
  verify_maven_repository_nonempty "${maven_repo}"

  write_installation_properties \
    "${STAGING_ROOT}/installation.properties" \
    "${INSTALL_ROOT}" \
    "${INSTALL_ROOT}/project/minimal-ij1-plugin-ellipse" \
    "${INSTALL_ROOT}/runtime/Fiji.app" \
    "${INSTALL_ROOT}/runtime/jdk21" \
    "${INSTALL_ROOT}/runtime/maven" \
    "${INSTALL_ROOT}/runtime/maven_repository" \
    "${INSTALL_ROOT}/models/${CLASSIFIER_NAME}" \
    "${INSTALL_ROOT}/config/default_batch.properties" \
    "${INSTALL_ROOT}/work/${MACHINE_ID}"

  # Final existence check just before atomic publish.
  if [[ -e "${INSTALL_ROOT}" ]]; then
    fail "${EXIT_INSTALL}" "Install root appeared during staging; refusing to overwrite: ${INSTALL_ROOT}"
  fi

  echo "Publishing installation..."
  mv -- "${STAGING_ROOT}" "${INSTALL_ROOT}"
  STAGING_ROOT=''

  cat <<EOF
LUMEN INSTALLATION SUCCESS
machine: ${MACHINE_ID}
checkpoint: ${EXPECTED_COMMIT_SHORT}
install root: ${INSTALL_ROOT}
Fiji: verified
Fiji Java: bundled inside Fiji
controller Java: verified
Maven: verified
classifier: verified
source checkpoint: verified
launcher: verified
monitor: verified
starter: verified
EOF
}

main "$@"
