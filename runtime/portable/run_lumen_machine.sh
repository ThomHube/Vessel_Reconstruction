#!/usr/bin/env bash
# Lumen portable local runtime launcher (Phase 4C1 foundation).
#
# Installed location:
#   $HOME/LumenRuntime/0c1c326/run_lumen_machine.sh
#
# Phase 4C1 supports ONLY:
#   ./run_lumen_machine.sh --verify-only
#
set -euo pipefail

readonly EXPECTED_COMMIT='0c1c326fc07f7e049f1d359226e810c7b8db24ce'
readonly EXPECTED_COMMIT_SHORT='0c1c326'
readonly EXPECTED_CLASSIFIER_SHA256='d109f00d1ca16447bc433303cb6f5ec1a5a9d27a3215b24d87df77346b04340c'
readonly EXPECTED_PLUGIN_SHA256='81fa019d40ceb085d28ba6b82a5972615919a115a28014e6bbdebed3750a6cde'
readonly PLUGIN_JAR_NAME='VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar'
readonly CLASSIFIER_NAME='Classifier9.model'

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3
readonly EXIT_VERIFY=5
readonly EXIT_BUILD=6
readonly EXIT_NOT_ENABLED=7

SCRIPT_PATH=''
INSTALL_ROOT=''

# Parsed installation.properties values (never sourced as shell).
PROP_production_commit=''
PROP_production_commit_short=''
PROP_machine_id=''
PROP_installation_root=''
PROP_project_root=''
PROP_fiji_root=''
PROP_jdk_root=''
PROP_maven_root=''
PROP_maven_repository=''
PROP_classifier_path=''
PROP_config_path=''
PROP_work_root=''
PROP_classifier_sha256=''
PROP_plugin_sha256=''

fail() {
  local code="$1"
  shift
  echo "ERROR: $*" >&2
  exit "${code}"
}

usage() {
  cat >&2 <<'EOF'
Usage: ./run_lumen_machine.sh --verify-only

Phase 4C1 verifies the portable LumenRuntime installation and, if needed,
compiles controller classes offline. Image processing is not enabled yet.
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
  INSTALL_ROOT="$(cd -P "$(dirname -- "${SCRIPT_PATH}")" && pwd)"
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

require_executable() {
  local path="$1"
  local label="$2"
  [[ -x "${path}" ]] || fail "${EXIT_VERIFY}" "${label} missing or not executable: ${path}"
}

# Safe key=value parser. Does not source the file as shell.
load_installation_properties() {
  local props_path="$1"
  require_file "${props_path}" "installation.properties"

  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    # Trim CR and skip blanks/comments.
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
      production_commit) PROP_production_commit="${value}" ;;
      production_commit_short) PROP_production_commit_short="${value}" ;;
      machine_id) PROP_machine_id="${value}" ;;
      installation_root) PROP_installation_root="${value}" ;;
      project_root) PROP_project_root="${value}" ;;
      fiji_root) PROP_fiji_root="${value}" ;;
      jdk_root) PROP_jdk_root="${value}" ;;
      maven_root) PROP_maven_root="${value}" ;;
      maven_repository) PROP_maven_repository="${value}" ;;
      classifier_path) PROP_classifier_path="${value}" ;;
      config_path) PROP_config_path="${value}" ;;
      work_root) PROP_work_root="${value}" ;;
      classifier_sha256) PROP_classifier_sha256="${value}" ;;
      plugin_sha256) PROP_plugin_sha256="${value}" ;;
    esac
  done <"${props_path}"

  local required_name
  for required_name in \
    production_commit \
    production_commit_short \
    machine_id \
    installation_root \
    project_root \
    fiji_root \
    jdk_root \
    maven_root \
    maven_repository \
    classifier_path \
    config_path \
    work_root \
    classifier_sha256 \
    plugin_sha256
  do
    local varname="PROP_${required_name}"
    if [[ -z "${!varname}" ]]; then
      fail "${EXIT_VERIFY}" "installation.properties missing required key: ${required_name}"
    fi
  done
}

verify_fixed_expectations() {
  if [[ "${PROP_installation_root}" != "${INSTALL_ROOT}" ]]; then
    fail "${EXIT_VERIFY}" \
      "installation_root mismatch: properties=${PROP_installation_root} actual=${INSTALL_ROOT}"
  fi
  if [[ "${PROP_production_commit}" != "${EXPECTED_COMMIT}" ]]; then
    fail "${EXIT_VERIFY}" \
      "production_commit mismatch: expected ${EXPECTED_COMMIT}, found ${PROP_production_commit}"
  fi
  if [[ "${PROP_production_commit_short}" != "${EXPECTED_COMMIT_SHORT}" ]]; then
    fail "${EXIT_VERIFY}" \
      "production_commit_short mismatch: expected ${EXPECTED_COMMIT_SHORT}, found ${PROP_production_commit_short}"
  fi
  if [[ ! "${PROP_machine_id}" =~ ^machine_(0[1-9]|[1-9][0-9])$ ]]; then
    fail "${EXIT_VERIFY}" "Invalid machine_id in installation.properties: ${PROP_machine_id}"
  fi
}

verify_layout() {
  require_executable "${INSTALL_ROOT}/runtime/Fiji.app/ImageJ-linux64" "Fiji launcher"
  require_executable "${INSTALL_ROOT}/runtime/jdk21/bin/java" "portable java"
  require_executable "${INSTALL_ROOT}/runtime/jdk21/bin/javac" "portable javac"
  require_executable "${INSTALL_ROOT}/runtime/maven/bin/mvn" "portable Maven"
  require_dir "${INSTALL_ROOT}/runtime/maven_repository" "Maven repository"
  require_file "${INSTALL_ROOT}/project/minimal-ij1-plugin-ellipse/pom.xml" "project pom.xml"
  require_file \
    "${INSTALL_ROOT}/project/minimal-ij1-plugin-ellipse/runtime/batch/run_machine.sh" \
    "run_machine.sh"
  require_file "${INSTALL_ROOT}/models/${CLASSIFIER_NAME}" "classifier model"
  require_file "${INSTALL_ROOT}/config/default_batch.properties" "default batch config"
  require_dir "${INSTALL_ROOT}/work/${PROP_machine_id}/input" "machine input workspace"
  require_dir "${INSTALL_ROOT}/work/${PROP_machine_id}/batch" "machine batch workspace"
  require_dir "${INSTALL_ROOT}/work/${PROP_machine_id}/output" "machine output workspace"

  # Cross-check absolute paths recorded in installation.properties.
  [[ -x "${PROP_fiji_root}/ImageJ-linux64" ]] \
    || fail "${EXIT_VERIFY}" "fiji_root launcher missing: ${PROP_fiji_root}/ImageJ-linux64"
  [[ -x "${PROP_jdk_root}/bin/java" ]] \
    || fail "${EXIT_VERIFY}" "jdk_root java missing: ${PROP_jdk_root}/bin/java"
  [[ -x "${PROP_maven_root}/bin/mvn" ]] \
    || fail "${EXIT_VERIFY}" "maven_root mvn missing: ${PROP_maven_root}/bin/mvn"
  [[ -d "${PROP_maven_repository}" ]] \
    || fail "${EXIT_VERIFY}" "maven_repository missing: ${PROP_maven_repository}"
  [[ -f "${PROP_project_root}/pom.xml" ]] \
    || fail "${EXIT_VERIFY}" "project_root pom.xml missing: ${PROP_project_root}/pom.xml"
  [[ -f "${PROP_classifier_path}" ]] \
    || fail "${EXIT_VERIFY}" "classifier_path missing: ${PROP_classifier_path}"
  [[ -f "${PROP_config_path}" ]] \
    || fail "${EXIT_VERIFY}" "config_path missing: ${PROP_config_path}"
  [[ -d "${PROP_work_root}/input" && -d "${PROP_work_root}/batch" && -d "${PROP_work_root}/output" ]] \
    || fail "${EXIT_VERIFY}" "work_root incomplete: ${PROP_work_root}"
}

verify_hashes() {
  local classifier_actual plugin_path plugin_actual

  classifier_actual="$(sha256_of "${PROP_classifier_path}")"
  if [[ "${classifier_actual}" != "${EXPECTED_CLASSIFIER_SHA256}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Classifier SHA-256 mismatch vs fixed expectation: expected ${EXPECTED_CLASSIFIER_SHA256}, found ${classifier_actual}"
  fi
  if [[ "${classifier_actual}" != "${PROP_classifier_sha256}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Classifier SHA-256 mismatch vs installation.properties: expected ${PROP_classifier_sha256}, found ${classifier_actual}"
  fi

  plugin_path="${PROP_fiji_root}/plugins/${PLUGIN_JAR_NAME}"
  require_file "${plugin_path}" "Fiji plugin JAR"
  plugin_actual="$(sha256_of "${plugin_path}")"
  if [[ "${plugin_actual}" != "${EXPECTED_PLUGIN_SHA256}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Plugin SHA-256 mismatch vs fixed expectation: expected ${EXPECTED_PLUGIN_SHA256}, found ${plugin_actual}"
  fi
  if [[ "${plugin_actual}" != "${PROP_plugin_sha256}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Plugin SHA-256 mismatch vs installation.properties: expected ${PROP_plugin_sha256}, found ${plugin_actual}"
  fi
}

verify_portable_tools() {
  "${PROP_jdk_root}/bin/java" -version >/dev/null 2>&1 \
    || fail "${EXIT_VERIFY}" "portable java -version failed: ${PROP_jdk_root}/bin/java"
  "${PROP_jdk_root}/bin/javac" -version >/dev/null 2>&1 \
    || fail "${EXIT_VERIFY}" "portable javac -version failed: ${PROP_jdk_root}/bin/javac"

  local version_out
  if ! version_out="$(
    JAVA_HOME="${PROP_jdk_root}" \
    PATH="${PROP_jdk_root}/bin:/usr/bin:/bin" \
    "${PROP_maven_root}/bin/mvn" -version 2>&1
  )"; then
    fail "${EXIT_VERIFY}" "portable Maven -version failed"
  fi
  if ! grep -Eq 'Java version: 21(\.|$)' <<<"${version_out}"; then
    fail "${EXIT_VERIFY}" "portable Maven did not report Java 21:"$'\n'"${version_out}"
  fi
}

controller_classes_present() {
  local supervisor_class full_class
  supervisor_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchMachineSupervisorCli.class"
  full_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchFullProcessingCli.class"
  [[ -f "${supervisor_class}" && -f "${full_class}" ]]
}

ensure_controller_classes() {
  local supervisor_class full_class build_log
  supervisor_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchMachineSupervisorCli.class"
  full_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchFullProcessingCli.class"

  if controller_classes_present; then
    echo "controller classes: already built"
    return 0
  fi

  echo "controller classes: offline build required"
  mkdir -p -- "${INSTALL_ROOT}/logs"
  build_log="${INSTALL_ROOT}/logs/controller_offline_build.log"

  if ! (
    cd -- "${PROP_project_root}"
    JAVA_HOME="${PROP_jdk_root}" \
    PATH="${PROP_jdk_root}/bin:/usr/bin:/bin" \
    "${PROP_maven_root}/bin/mvn" \
      -o \
      "-Dmaven.repo.local=${PROP_maven_repository}" \
      -DskipTests \
      compile
  ) >"${build_log}" 2>&1; then
    fail "${EXIT_BUILD}" \
      "Offline controller compile failed. See log: ${build_log}"
  fi

  if ! controller_classes_present; then
    fail "${EXIT_BUILD}" \
      "Offline compile reported success but required .class files are missing (${supervisor_class}; ${full_class}). See log: ${build_log}"
  fi
  echo "controller classes: offline build succeeded"
}

run_verify_only() {
  require_cmd sha256sum
  require_cmd awk
  require_cmd grep

  resolve_script_path
  load_installation_properties "${INSTALL_ROOT}/installation.properties"
  verify_fixed_expectations
  verify_layout
  verify_hashes
  verify_portable_tools
  ensure_controller_classes

  if ! controller_classes_present; then
    fail "${EXIT_VERIFY}" "controller classes missing after preparation"
  fi

  cat <<EOF
LUMEN RUNTIME VERIFY SUCCESS
machine: ${PROP_machine_id}
checkpoint: ${PROP_production_commit_short}
install root: ${INSTALL_ROOT}
Fiji: verified
controller Java: verified
Maven: verified
classifier: verified
plugin: verified
controller classes: verified
workspace: verified
processing launched: NO
EOF
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./run_lumen_machine.sh --verify-only" >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  if [[ "$#" -eq 1 && "$1" == "--verify-only" ]]; then
    run_verify_only
    exit 0
  fi

  usage
  echo >&2
  echo "ERROR: Processing orchestration is intentionally not enabled in Phase 4C1." >&2
  echo "Use: ./run_lumen_machine.sh --verify-only" >&2
  exit "${EXIT_NOT_ENABLED}"
}

main "$@"
