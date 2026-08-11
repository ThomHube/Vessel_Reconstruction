#!/usr/bin/env bash
# Lumen portable local runtime launcher.
#
# Installed location:
#   $HOME/LumenRuntime/0c1c326/run_lumen_machine.sh
#
# Supported modes:
#   ./run_lumen_machine.sh --verify-only
#   ./run_lumen_machine.sh --precheck-only
#   ./run_lumen_machine.sh --full-only <tile-profile-index>
#
# NOTE: Changing this file changes its SHA-256. The installer still pins the
# previous launcher hash until a separate deployment bite updates it.
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

# Imported job paths (precheck-only).
JOB_ROOT=''
JOB_INPUT=''
JOB_BATCH=''
JOB_CONFIG=''
JOB_IMPORT_PROPS=''
JOB_PACKAGE_PROPS=''
JOB_CHECKSUMS=''
JOB_MANIFEST=''

fail() {
  local code="$1"
  shift
  echo "ERROR: $*" >&2
  exit "${code}"
}

usage() {
  cat >&2 <<'EOF'
Usage:
  ./run_lumen_machine.sh --verify-only
  ./run_lumen_machine.sh --precheck-only
  ./run_lumen_machine.sh --full-only <tile-profile-index>

--verify-only
  Verify the portable LumenRuntime installation and offline controller classes.
  Does not require an imported job and launches no Fiji work.

--precheck-only
  Verify an imported machine job, then run BatchPrecheckSupervisorCli only.
  Classification and reconstruction are not enabled.

--full-only <tile-profile-index>
  Verify an imported machine job, then run BatchMachineSupervisorCli from the
  current canonical state. Does not automatically run precheck. Tile profile
  index is required (decimal integer >= 0); no silent default.
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

# Safe key=value parser. Does not source the file as shell.
load_installation_properties() {
  local props_path="$1"
  require_file "${props_path}" "installation.properties"

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
  local supervisor_class full_class precheck_class
  supervisor_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchMachineSupervisorCli.class"
  full_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchFullProcessingCli.class"
  precheck_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchPrecheckSupervisorCli.class"
  [[ -f "${supervisor_class}" && -f "${full_class}" && -f "${precheck_class}" ]]
}

ensure_controller_classes() {
  local supervisor_class full_class precheck_class build_log
  supervisor_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchMachineSupervisorCli.class"
  full_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchFullProcessingCli.class"
  precheck_class="${PROP_project_root}/target/classes/vesselreconstruction/ellipse/BatchPrecheckSupervisorCli.class"

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
      "Offline compile reported success but required .class files are missing (${supervisor_class}; ${full_class}; ${precheck_class}). See log: ${build_log}"
  fi
  echo "controller classes: offline build succeeded"
}

prepare_runtime() {
  require_cmd sha256sum
  require_cmd awk
  require_cmd grep
  require_cmd find

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
}

validate_checksum_paths() {
  local root="$1"
  local checksums="$2"
  local line path
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" ]] && continue
    if [[ ! "${line}" =~ ^[0-9a-f]{64}[[:space:]][\ *](.+)$ ]]; then
      fail "${EXIT_VERIFY}" "Malformed package_checksums.sha256 line: ${line}"
    fi
    path="${BASH_REMATCH[1]}"
    path="${path#\*}"
    is_safe_relative_path "${path}" \
      || fail "${EXIT_VERIFY}" "Unsafe path in package_checksums.sha256: ${path}"
    if [[ ! -e "${root}/${path}" ]]; then
      fail "${EXIT_VERIFY}" "Checksum path missing in job: ${path}"
    fi
  done <"${checksums}"
}

reject_symlinks_under() {
  local root="$1"
  local label="$2"
  local found
  found="$(find "${root}" -type l -print -quit)"
  if [[ -n "${found}" ]]; then
    fail "${EXIT_VERIFY}" "${label} contains a symbolic link: ${found}"
  fi
}

prepare_imported_job() {
  JOB_ROOT="${PROP_work_root}/job"
  require_dir "${JOB_ROOT}" "imported job root"
  JOB_INPUT="${JOB_ROOT}/input"
  JOB_BATCH="${JOB_ROOT}/batch"
  JOB_CONFIG="${JOB_ROOT}/production_batch.properties"
  JOB_IMPORT_PROPS="${JOB_ROOT}/import.properties"
  JOB_PACKAGE_PROPS="${JOB_ROOT}/package.properties"
  JOB_CHECKSUMS="${JOB_ROOT}/package_checksums.sha256"
  JOB_MANIFEST="${JOB_ROOT}/batch/machines/${PROP_machine_id}_manifest.csv"

  require_dir "${JOB_INPUT}" "job/input"
  require_dir "${JOB_BATCH}" "job/batch"
  require_file "${JOB_CONFIG}" "job/production_batch.properties"
  require_file "${JOB_IMPORT_PROPS}" "job/import.properties"
  require_file "${JOB_PACKAGE_PROPS}" "job/package.properties"
  require_file "${JOB_CHECKSUMS}" "job/package_checksums.sha256"
  require_file "${JOB_MANIFEST}" "job machine manifest"

  local import_machine package_machine import_checkpoint
  local config_file_sha manifest_sha actual_config_sha actual_manifest_sha
  import_machine="$(require_prop "${JOB_IMPORT_PROPS}" "machine_id")"
  package_machine="$(require_prop "${JOB_PACKAGE_PROPS}" "machine_id")"
  import_checkpoint="$(require_prop "${JOB_IMPORT_PROPS}" "scientific_checkpoint")"
  config_file_sha="$(require_prop "${JOB_IMPORT_PROPS}" "production_config_file_sha256")"
  manifest_sha="$(require_prop "${JOB_IMPORT_PROPS}" "machine_manifest_sha256")"

  [[ "${import_machine}" == "${PROP_machine_id}" ]] \
    || fail "${EXIT_VERIFY}" \
      "import.properties machine_id ${import_machine} != installed ${PROP_machine_id}"
  [[ "${package_machine}" == "${PROP_machine_id}" ]] \
    || fail "${EXIT_VERIFY}" \
      "package.properties machine_id ${package_machine} != installed ${PROP_machine_id}"
  [[ "${import_checkpoint}" == "${EXPECTED_COMMIT}" ]] \
    || fail "${EXIT_VERIFY}" "import.properties scientific_checkpoint mismatch"
  if [[ ! "${config_file_sha}" =~ ^[0-9a-f]{64}$ ]]; then
    fail "${EXIT_VERIFY}" "import.properties production_config_file_sha256 invalid"
  fi
  if [[ ! "${manifest_sha}" =~ ^[0-9a-f]{64}$ ]]; then
    fail "${EXIT_VERIFY}" "import.properties machine_manifest_sha256 invalid"
  fi

  validate_checksum_paths "${JOB_ROOT}" "${JOB_CHECKSUMS}"
  (
    cd -- "${JOB_ROOT}"
    sha256sum -c -- package_checksums.sha256 >/dev/null
  ) || fail "${EXIT_VERIFY}" "job package_checksums.sha256 verification failed"

  actual_config_sha="$(sha256_of "${JOB_CONFIG}")"
  [[ "${actual_config_sha}" == "${config_file_sha}" ]] \
    || fail "${EXIT_VERIFY}" \
      "job production_batch.properties SHA-256 mismatch: expected ${config_file_sha}, found ${actual_config_sha}"

  actual_manifest_sha="$(sha256_of "${JOB_MANIFEST}")"
  [[ "${actual_manifest_sha}" == "${manifest_sha}" ]] \
    || fail "${EXIT_VERIFY}" \
      "job machine manifest SHA-256 mismatch: expected ${manifest_sha}, found ${actual_manifest_sha}"

  reject_symlinks_under "${JOB_INPUT}" "job/input"
  reject_symlinks_under "${JOB_BATCH}/machines" "job/batch/machines"
}

interpret_precheck_exit() {
  local code="$1"
  case "${code}" in
    0) echo "LUMEN PRECHECK SUCCESS" ;;
    10) echo "LUMEN PRECHECK REVIEW REQUIRED" ;;
    11) echo "LUMEN PRECHECK FAILED IMAGES" ;;
    12) echo "LUMEN PRECHECK INVALID ARGUMENTS" ;;
    13) echo "LUMEN PRECHECK INVALID CONFIGURATION" ;;
    14) echo "LUMEN PRECHECK INVALID MANIFEST" ;;
    15) echo "LUMEN PRECHECK FIJI LAUNCH FAILURE" ;;
    16) echo "LUMEN PRECHECK UNEXPECTED SUPERVISOR FAILURE" ;;
    *) echo "LUMEN PRECHECK NONZERO EXIT" ;;
  esac
}

interpret_full_exit() {
  local code="$1"
  case "${code}" in
    0) echo "LUMEN FULL MACHINE RUN SUCCESS" ;;
    10) echo "LUMEN FULL MACHINE RUN COMPLETED WITH BLOCKED IMAGES" ;;
    11) echo "LUMEN FULL MACHINE RUN COMPLETED WITH FAILURES" ;;
    2) echo "LUMEN FULL MACHINE RUN CONFIGURATION FAILURE" ;;
    6) echo "LUMEN FULL MACHINE RUN UNEXPECTED FAILURE" ;;
    *) echo "LUMEN FULL MACHINE RUN UNKNOWN EXIT" ;;
  esac
}

run_verify_only() {
  prepare_runtime

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

run_precheck_only() {
  prepare_runtime
  prepare_imported_job

  local classes_dir fiji_launcher precheck_rc
  classes_dir="${PROP_project_root}/target/classes"
  fiji_launcher="${PROP_fiji_root}/ImageJ-linux64"
  require_file \
    "${classes_dir}/vesselreconstruction/ellipse/BatchPrecheckSupervisorCli.class" \
    "BatchPrecheckSupervisorCli.class"
  require_executable "${fiji_launcher}" "Fiji launcher"

  cat <<EOF
LUMEN PRECHECK START
machine: ${PROP_machine_id}
job root: ${JOB_ROOT}
input: ${JOB_INPUT}
batch: ${JOB_BATCH}
configuration: verified
imported package: verified
classification enabled: NO
reconstruction enabled: NO
EOF

  set +e
  "${PROP_jdk_root}/bin/java" -cp "${classes_dir}" \
    vesselreconstruction.ellipse.BatchPrecheckSupervisorCli \
    --input-root "${JOB_INPUT}" \
    --batch-root "${JOB_BATCH}" \
    --machine-id "${PROP_machine_id}" \
    --config "${JOB_CONFIG}" \
    --fiji-launcher "${fiji_launcher}"
  precheck_rc=$?
  set -e

  interpret_precheck_exit "${precheck_rc}"
  echo "machine: ${PROP_machine_id}"
  echo "precheck exit code: ${precheck_rc}"
  echo "classification launched: NO"
  echo "reconstruction launched: NO"
  exit "${precheck_rc}"
}

run_full_only() {
  local tile_profile_index="$1"

  if [[ ! "${tile_profile_index}" =~ ^[0-9]+$ ]]; then
    fail "${EXIT_USAGE}" \
      "--full-only requires a decimal integer tile-profile-index >= 0 (got: ${tile_profile_index})"
  fi

  prepare_runtime
  prepare_imported_job

  local classes_dir fiji_launcher supervisor_rc progress_file summary_file
  classes_dir="${PROP_project_root}/target/classes"
  fiji_launcher="${PROP_fiji_root}/ImageJ-linux64"
  require_file \
    "${classes_dir}/vesselreconstruction/ellipse/BatchMachineSupervisorCli.class" \
    "BatchMachineSupervisorCli.class"
  require_executable "${fiji_launcher}" "Fiji launcher"
  require_file "${PROP_classifier_path}" "classifier model"

  progress_file="${JOB_BATCH}/machine_supervisor/${PROP_machine_id}/machine_progress.properties"
  summary_file="${JOB_BATCH}/machine_supervisor/${PROP_machine_id}/machine_run_summary.csv"

  cat <<EOF
LUMEN FULL MACHINE RUN START
machine: ${PROP_machine_id}
job root: ${JOB_ROOT}
input: ${JOB_INPUT}
batch: ${JOB_BATCH}
classifier: verified
configuration: verified
imported package: verified
tile profile index: ${tile_profile_index}
precheck automatically launched: NO
machine supervisor: ENABLED
classification/reconstruction: supervisor-controlled
EOF

  set +e
  "${PROP_jdk_root}/bin/java" \
    "-Dvesselreconstruction.classes.dir=${classes_dir}" \
    -cp "${classes_dir}" \
    vesselreconstruction.ellipse.BatchMachineSupervisorCli \
    --input-root "${JOB_INPUT}" \
    --batch-root "${JOB_BATCH}" \
    --machine-id "${PROP_machine_id}" \
    --config "${JOB_CONFIG}" \
    --classifier "${PROP_classifier_path}" \
    --fiji-launcher "${fiji_launcher}" \
    --tile-profile-index "${tile_profile_index}"
  supervisor_rc=$?
  set -e

  interpret_full_exit "${supervisor_rc}"
  echo "machine: ${PROP_machine_id}"
  echo "supervisor exit code: ${supervisor_rc}"
  echo "progress file: ${progress_file}"
  echo "summary file: ${summary_file}"
  exit "${supervisor_rc}"
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./run_lumen_machine.sh --verify-only|--precheck-only|--full-only <tile-profile-index>" >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  if [[ "$#" -eq 1 && "$1" == "--verify-only" ]]; then
    run_verify_only
    exit 0
  fi

  if [[ "$#" -eq 1 && "$1" == "--precheck-only" ]]; then
    run_precheck_only
  fi

  if [[ "$#" -eq 2 && "$1" == "--full-only" ]]; then
    run_full_only "$2"
  fi

  usage
  echo >&2
  echo "ERROR: Unknown or unsupported arguments." >&2
  exit "${EXIT_USAGE}"
}

main "$@"
