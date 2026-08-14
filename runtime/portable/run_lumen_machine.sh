#!/usr/bin/env bash
# Lumen portable local runtime launcher.
#
# Installed location:
#   $HOME/LumenRuntime/6dd42df/run_lumen_machine.sh
#
# Supported modes:
#   ./run_lumen_machine.sh --verify-only
#   ./run_lumen_machine.sh --precheck-only
#   ./run_lumen_machine.sh --full-only <tile-profile-index>
#   ./run_lumen_machine.sh --export-results <completed-results-root>
#
# NOTE: Changing this file changes its SHA-256. install_machine.sh must pin
# the matching launcher hash for this scientific checkpoint.
#
set -euo pipefail

readonly EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
readonly EXPECTED_COMMIT_SHORT='6dd42df'
readonly EXPECTED_CLASSIFIER_SHA256='d109f00d1ca16447bc433303cb6f5ec1a5a9d27a3215b24d87df77346b04340c'
readonly EXPECTED_PLUGIN_SHA256='8c74d803a286dcd1a83e27c7f33a92d99adb2638cc69dbb301356865e198ae54'
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

# Export-results staging (never a local job/dataset path).
EXPORT_STAGING_ROOT=''
EXPORT_DEST_ROOT=''
EXPORT_FINAL_ROOT=''

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
  ./run_lumen_machine.sh --export-results <completed-results-root>

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

--export-results <completed-results-root>
  Copy the completed local machine dataset and provenance to an existing
  collection directory. Does not launch Java, Fiji, Weka, Maven, or processing.
  Destination must be an absolute, already-existing writable directory.
  Does not create the destination root. Does not run automatically after
  processing.
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

is_positive_int() {
  [[ "${1:-}" =~ ^[1-9][0-9]*$ ]]
}

is_nonneg_int() {
  [[ "${1:-}" =~ ^[0-9]+$ ]]
}

path_is_within() {
  local inner="$1"
  local outer="$2"
  [[ "${inner}" == "${outer}" || "${inner}" == "${outer}/"* ]]
}

resolve_existing_dir() {
  local path="$1"
  local label="$2"
  [[ -d "${path}" ]] || fail "${EXIT_VALIDATION}" "${label} is not a directory: ${path}"
  (cd -P -- "${path}" && pwd)
}

cleanup_export_staging() {
  local staging="${EXPORT_STAGING_ROOT:-}"
  if [[ -z "${staging}" ]]; then
    return 0
  fi
  if [[ ! -d "${staging}" ]]; then
    EXPORT_STAGING_ROOT=''
    return 0
  fi
  local base parent dest
  base="$(basename -- "${staging}")"
  case "${base}" in
    ."${PROP_machine_id}"_export_staging_*)
      ;;
    *)
      return 0
      ;;
  esac
  dest="${EXPORT_DEST_ROOT:-}"
  [[ -n "${dest}" ]] || return 0
  parent="$(cd -P "$(dirname -- "${staging}")" && pwd)" || return 0
  if [[ "${parent}" != "${dest}" ]]; then
    return 0
  fi
  local abs
  abs="$(cd -P -- "${staging}" && pwd)" || return 0
  if [[ "${abs}" == "${HOME}" \
     || "${abs}" == "${INSTALL_ROOT:-}" \
     || "${abs}" == "${PROP_work_root:-}" \
     || "${abs}" == "${JOB_ROOT:-}" \
     || "${abs}" == "${EXPORT_FINAL_ROOT:-}" \
     || "${abs}" == "${dest}" ]]; then
    return 0
  fi
  rm -rf -- "${abs}"
  EXPORT_STAGING_ROOT=''
}

on_export_exit() {
  cleanup_export_staging
}

load_export_installation_properties() {
  local props_path="$1"
  require_file "${props_path}" "installation.properties"
  if [[ -L "${props_path}" ]]; then
    fail "${EXIT_VERIFY}" "installation.properties must not be a symbolic link: ${props_path}"
  fi

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
      work_root) PROP_work_root="${value}" ;;
    esac
  done <"${props_path}"

  local required_name
  for required_name in \
    production_commit \
    production_commit_short \
    machine_id \
    installation_root \
    work_root
  do
    local varname="PROP_${required_name}"
    if [[ -z "${!varname}" ]]; then
      fail "${EXIT_VERIFY}" "installation.properties missing required key: ${required_name}"
    fi
  done
}

prepare_export_installation() {
  require_cmd sha256sum
  require_cmd awk
  require_cmd find
  require_cmd cp
  require_cmd mkdir
  require_cmd rm
  require_cmd mv
  require_cmd wc
  require_cmd sort
  require_cmd cmp
  require_cmd kill
  require_cmd tr
  require_cmd sed

  resolve_script_path
  load_export_installation_properties "${INSTALL_ROOT}/installation.properties"
  verify_fixed_expectations

  local expected_work="${INSTALL_ROOT}/work/${PROP_machine_id}"
  if [[ "${PROP_work_root}" != "${expected_work}" ]]; then
    fail "${EXIT_VERIFY}" \
      "work_root mismatch: expected ${expected_work}, found ${PROP_work_root}"
  fi
  require_dir "${PROP_work_root}" "work_root"
  if [[ -L "${PROP_work_root}" ]]; then
    fail "${EXIT_VERIFY}" "work_root must not be a symbolic link: ${PROP_work_root}"
  fi

  JOB_ROOT="${PROP_work_root}/job"
  require_dir "${JOB_ROOT}" "imported job root"
  if [[ -L "${JOB_ROOT}" ]]; then
    fail "${EXIT_VERIFY}" "imported job root must not be a symbolic link: ${JOB_ROOT}"
  fi
  JOB_BATCH="${JOB_ROOT}/batch"
  JOB_CHECKSUMS="${JOB_ROOT}/package_checksums.sha256"
  JOB_MANIFEST="${JOB_ROOT}/batch/machines/${PROP_machine_id}_manifest.csv"
}

validate_export_destination() {
  local requested="$1"
  if [[ "${requested}" != /* ]]; then
    fail "${EXIT_VALIDATION}" \
      "completed-results-root must be an absolute path: ${requested}"
  fi
  if [[ ! -e "${requested}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "completed-results-root does not exist (refusing to create it): ${requested}"
  fi
  if [[ ! -d "${requested}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "completed-results-root is not a directory: ${requested}"
  fi
  if [[ ! -w "${requested}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "completed-results-root is not writable: ${requested}"
  fi

  EXPORT_DEST_ROOT="$(resolve_existing_dir "${requested}" "completed-results-root")"
  local dataset_root="${JOB_ROOT}/batch/output/lumen_dataset_${PROP_machine_id}"
  local dataset_abs=''
  if [[ -d "${dataset_root}" ]]; then
    dataset_abs="$(cd -P -- "${dataset_root}" && pwd)"
  fi

  if path_is_within "${EXPORT_DEST_ROOT}" "${INSTALL_ROOT}" \
     || path_is_within "${EXPORT_DEST_ROOT}" "${PROP_work_root}" \
     || path_is_within "${EXPORT_DEST_ROOT}" "${JOB_ROOT}" \
     || { [[ -n "${dataset_abs}" ]] && path_is_within "${EXPORT_DEST_ROOT}" "${dataset_abs}"; }; then
    fail "${EXIT_VALIDATION}" \
      "completed-results-root must not resolve inside the local installation, work, job, or dataset tree: ${EXPORT_DEST_ROOT}"
  fi
}

refuse_if_processing_active() {
  local pid_file="${PROP_work_root}/control/full_processing/runner.pid"
  [[ -f "${pid_file}" ]] || return 0
  if [[ -L "${pid_file}" ]]; then
    fail "${EXIT_VALIDATION}" "runner.pid must not be a symbolic link: ${pid_file}"
  fi
  local pid
  pid="$(tr -d '[:space:]' <"${pid_file}" || true)"
  if ! is_positive_int "${pid}"; then
    return 0
  fi
  if kill -0 "${pid}" 2>/dev/null; then
    fail "${EXIT_VALIDATION}" \
      "Results cannot be exported while processing is active (runner PID ${pid}). Stop processing first; this command will not kill it."
  fi
}

require_machine_success() {
  local progress="${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}/machine_progress.properties"
  require_file "${progress}" "machine_progress.properties"
  if [[ -L "${progress}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "machine_progress.properties must not be a symbolic link: ${progress}"
  fi

  local status assigned completed percent failed blocked remaining machine_id_prog timestamp
  status="$(prop_value "${progress}" "status" || true)"
  assigned="$(prop_value "${progress}" "assigned_count" || true)"
  completed="$(prop_value "${progress}" "completed_count" || true)"
  percent="$(prop_value "${progress}" "progress_percent" || true)"
  failed="$(prop_value "${progress}" "failed_count" || true)"
  blocked="$(prop_value "${progress}" "blocked_count" || true)"
  remaining="$(prop_value "${progress}" "remaining_count" || true)"
  machine_id_prog="$(prop_value "${progress}" "machine_id" || true)"
  timestamp="$(prop_value "${progress}" "timestamp_epoch_ms" || true)"

  if [[ -n "${machine_id_prog}" && "${machine_id_prog}" != "${PROP_machine_id}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "progress machine_id ${machine_id_prog} != installed ${PROP_machine_id}"
  fi
  if [[ "${status}" != "MACHINE_SUCCESS" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Export requires status=MACHINE_SUCCESS, found ${status:-MISSING}"
  fi
  if ! is_positive_int "${assigned}"; then
    fail "${EXIT_VALIDATION}" \
      "Export requires assigned_count to be a positive integer, found ${assigned:-MISSING}"
  fi
  if [[ "${completed}" != "${assigned}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Export requires completed_count == assigned_count (${assigned}), found ${completed:-MISSING}"
  fi
  if [[ "${percent}" != "100.0" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Export requires progress_percent=100.0, found ${percent:-MISSING}"
  fi
  if [[ "${failed}" != "0" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Export requires failed_count=0, found ${failed:-MISSING}"
  fi
  if [[ "${blocked}" != "0" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Export requires blocked_count=0, found ${blocked:-MISSING}"
  fi
  if [[ "${remaining}" != "0" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Export requires remaining_count=0, found ${remaining:-MISSING}"
  fi
  if ! is_nonneg_int "${timestamp}"; then
    fail "${EXIT_VALIDATION}" \
      "Export requires timestamp_epoch_ms to be a non-negative integer, found ${timestamp:-MISSING}"
  fi
}

require_regular_file() {
  local path="$1"
  local label="$2"
  [[ -f "${path}" && ! -L "${path}" ]] \
    || fail "${EXIT_VALIDATION}" "Missing ${label}: ${path}"
}

require_nonempty_file_dir() {
  local dir="$1"
  local label="$2"
  require_dir "${dir}" "${label}"
  if [[ -L "${dir}" ]]; then
    fail "${EXIT_VALIDATION}" "${label} must not be a symbolic link: ${dir}"
  fi
  local found
  found="$(find "${dir}" -type f -print -quit)"
  if [[ -z "${found}" ]]; then
    fail "${EXIT_VALIDATION}" "${label} contains no regular files: ${dir}"
  fi
}

validate_source_dataset() {
  local dataset="$1"
  require_dir "${dataset}" "local dataset root"
  if [[ -L "${dataset}" ]]; then
    fail "${EXIT_VALIDATION}" "dataset root must not be a symbolic link: ${dataset}"
  fi
  require_regular_file "${dataset}/data/data_dictionary.csv" "data/data_dictionary.csv"
  require_regular_file "${dataset}/data/manifests/image_manifest.csv" "data/manifests/image_manifest.csv"
  require_regular_file "${dataset}/data/manifests/segment_manifest.csv" "data/manifests/segment_manifest.csv"
  require_regular_file "${dataset}/data/manifests/processing_runs.csv" "data/manifests/processing_runs.csv"
  require_regular_file \
    "${dataset}/data/lumen_measurements/lumen_measurements_all.csv" \
    "data/lumen_measurements/lumen_measurements_all.csv"
  require_regular_file \
    "${dataset}/data/diagnostics/artifact_diagnostics_all.csv" \
    "data/diagnostics/artifact_diagnostics_all.csv"
  require_nonempty_file_dir \
    "${dataset}/data/lumen_measurements/per_segment" \
    "data/lumen_measurements/per_segment"
  require_nonempty_file_dir \
    "${dataset}/data/diagnostics/per_segment" \
    "data/diagnostics/per_segment"
  require_nonempty_file_dir "${dataset}/images/original_rgb" "images/original_rgb"
  require_nonempty_file_dir "${dataset}/images/approved_masks" "images/approved_masks"
  require_nonempty_file_dir "${dataset}/images/masked_rgb" "images/masked_rgb"
  require_nonempty_file_dir "${dataset}/images/masked_classified" "images/masked_classified"
  require_nonempty_file_dir \
    "${dataset}/images/reconstructed_classified" \
    "images/reconstructed_classified"
}

list_regular_rel_paths() {
  local root="$1"
  (
    cd -- "${root}"
    find . -type f | sed 's|^\./||' | LC_ALL=C sort
  )
}

verify_copied_tree() {
  local src="$1"
  local dest="$2"
  local label="$3"
  local src_list dest_list
  src_list="$(list_regular_rel_paths "${src}")"
  dest_list="$(list_regular_rel_paths "${dest}")"
  if [[ "${src_list}" != "${dest_list}" ]]; then
    fail "${EXIT_VERIFY}" "${label}: copied file set does not match source"
  fi
  local rel src_sha dest_sha
  while IFS= read -r rel || [[ -n "${rel}" ]]; do
    [[ -z "${rel}" ]] && continue
    src_sha="$(sha256_of "${src}/${rel}")"
    dest_sha="$(sha256_of "${dest}/${rel}")"
    if [[ "${src_sha}" != "${dest_sha}" ]]; then
      fail "${EXIT_VERIFY}" \
        "${label}: SHA-256 mismatch after copy: ${rel}"
    fi
  done <<<"${src_list}"
}

verify_copied_file() {
  local src="$1"
  local dest="$2"
  local label="$3"
  [[ -f "${dest}" && ! -L "${dest}" ]] \
    || fail "${EXIT_VERIFY}" "${label}: copied file missing: ${dest}"
  local src_sha dest_sha
  src_sha="$(sha256_of "${src}")"
  dest_sha="$(sha256_of "${dest}")"
  if [[ "${src_sha}" != "${dest_sha}" ]]; then
    fail "${EXIT_VERIFY}" "${label}: SHA-256 mismatch after copy"
  fi
}

copy_tree_verified() {
  local src="$1"
  local dest="$2"
  local label="$3"
  if [[ -L "${src}" ]]; then
    fail "${EXIT_VERIFY}" "${label}: source must not be a symbolic link: ${src}"
  fi
  mkdir -p -- "$(dirname -- "${dest}")"
  cp -a -- "${src}" "${dest}"
  reject_symlinks_under "${dest}" "copied ${label}"
  verify_copied_tree "${src}" "${dest}" "${label}"
}

copy_file_verified() {
  local src="$1"
  local dest="$2"
  local label="$3"
  if [[ -L "${src}" ]]; then
    fail "${EXIT_VERIFY}" "${label}: source must not be a symbolic link: ${src}"
  fi
  mkdir -p -- "$(dirname -- "${dest}")"
  cp -a -- "${src}" "${dest}"
  if [[ -L "${dest}" ]]; then
    fail "${EXIT_VERIFY}" "${label}: copied file is a symbolic link: ${dest}"
  fi
  verify_copied_file "${src}" "${dest}" "${label}"
}

sum_regular_file_bytes() {
  local root="$1"
  local total=0 rel sz
  while IFS= read -r rel || [[ -n "${rel}" ]]; do
    [[ -z "${rel}" ]] && continue
    sz="$(wc -c <"${root}/${rel}" | tr -d ' ')"
    if ! is_nonneg_int "${sz}"; then
      fail "${EXIT_VERIFY}" "Unable to measure size of ${root}/${rel}"
    fi
    total=$((total + sz))
  done < <(list_regular_rel_paths "${root}")
  printf '%s\n' "${total}"
}

count_regular_files() {
  local root="$1"
  local count=0 rel
  while IFS= read -r rel || [[ -n "${rel}" ]]; do
    [[ -z "${rel}" ]] && continue
    count=$((count + 1))
  done < <(list_regular_rel_paths "${root}")
  printf '%s\n' "${count}"
}

write_export_checksums() {
  local root="$1"
  local checksums="${root}/export_checksums.sha256"
  local rel
  : >"${checksums}"
  {
    while IFS= read -r rel || [[ -n "${rel}" ]]; do
      [[ -z "${rel}" ]] && continue
      [[ "${rel}" == "export_checksums.sha256" ]] && continue
      printf '%s  %s\n' "$(sha256_of "${root}/${rel}")" "${rel}"
    done < <(list_regular_rel_paths "${root}")
  } >"${checksums}"
}

verify_export_checksums_in() {
  local root="$1"
  require_regular_file "${root}/export_checksums.sha256" "export_checksums.sha256"
  (
    cd -- "${root}"
    sha256sum -c -- export_checksums.sha256 >/dev/null
  ) || fail "${EXIT_VERIFY}" "export_checksums.sha256 verification failed in ${root}"
}

write_export_properties() {
  local dest="$1"
  local assigned="$2"
  local completed="$3"
  local timestamp_ms="$4"
  local manifest_sha="$5"
  local progress_sha="$6"
  local package_checksums_sha="$7"
  local payload_files="$8"
  local payload_bytes="$9"
  cat >"${dest}" <<EOF
schema_version=1
status=EXPORT_SUCCESS
machine_id=${PROP_machine_id}
production_commit=${PROP_production_commit}
production_commit_short=${PROP_production_commit_short}
dataset_directory=lumen_dataset_${PROP_machine_id}
machine_status=MACHINE_SUCCESS
assigned_count=${assigned}
completed_count=${completed}
machine_completion_timestamp_epoch_ms=${timestamp_ms}
source_machine_manifest_sha256=${manifest_sha}
source_machine_progress_sha256=${progress_sha}
source_package_checksums_sha256=${package_checksums_sha}
payload_file_count=${payload_files}
payload_bytes=${payload_bytes}
EOF
}

stage_export_payload() {
  local staging="$1"
  local dataset_src="$2"
  local dataset_name="lumen_dataset_${PROP_machine_id}"

  mkdir -p -- "${staging}"

  echo "Copying scientific dataset..."
  copy_tree_verified \
    "${dataset_src}" \
    "${staging}/${dataset_name}" \
    "dataset ${dataset_name}"

  echo "Copying provenance..."
  copy_file_verified \
    "${INSTALL_ROOT}/installation.properties" \
    "${staging}/provenance/installation.properties" \
    "installation.properties"
  copy_file_verified \
    "${JOB_ROOT}/import.properties" \
    "${staging}/provenance/job/import.properties" \
    "import.properties"
  copy_file_verified \
    "${JOB_ROOT}/package.properties" \
    "${staging}/provenance/job/package.properties" \
    "package.properties"
  copy_file_verified \
    "${JOB_ROOT}/package_checksums.sha256" \
    "${staging}/provenance/job/package_checksums.sha256" \
    "package_checksums.sha256"
  copy_file_verified \
    "${JOB_ROOT}/production_batch.properties" \
    "${staging}/provenance/job/production_batch.properties" \
    "production_batch.properties"
  copy_file_verified \
    "${JOB_MANIFEST}" \
    "${staging}/provenance/job/batch/machines/${PROP_machine_id}_manifest.csv" \
    "machine manifest"
  copy_tree_verified \
    "${JOB_ROOT}/batch/precheck/${PROP_machine_id}" \
    "${staging}/provenance/job/batch/precheck/${PROP_machine_id}" \
    "precheck tree"
  copy_tree_verified \
    "${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}" \
    "${staging}/provenance/job/batch/machine_supervisor/${PROP_machine_id}" \
    "machine supervisor tree"
  copy_tree_verified \
    "${JOB_ROOT}/batch/full/${PROP_machine_id}" \
    "${staging}/provenance/job/batch/full/${PROP_machine_id}" \
    "full-processing tree"

  local control_src="${PROP_work_root}/control/full_processing"
  if [[ -d "${control_src}" ]]; then
    if [[ -L "${control_src}" ]]; then
      fail "${EXIT_VERIFY}" \
        "optional control directory must not be a symbolic link: ${control_src}"
    fi
    local control_file src_file
    for control_file in runner.log runner.exitcode runner.sh start.properties; do
      src_file="${control_src}/${control_file}"
      if [[ -f "${src_file}" ]]; then
        copy_file_verified \
          "${src_file}" \
          "${staging}/provenance/control/full_processing/${control_file}" \
          "optional control ${control_file}"
      fi
    done
  fi

  if [[ -d "${staging}/${dataset_name}/input" ]]; then
    fail "${EXIT_VERIFY}" "Refusing export that contains a dataset input/ tree"
  fi
  if [[ -e "${staging}/job/input" || -e "${staging}/provenance/job/input" ]]; then
    fail "${EXIT_VERIFY}" "Refusing export that contains job/input TIFF working copies"
  fi
}

run_export_results() {
  local requested_dest="$1"

  trap on_export_exit EXIT

  prepare_export_installation
  validate_export_destination "${requested_dest}"
  refuse_if_processing_active
  require_machine_success

  local dataset_src="${JOB_ROOT}/batch/output/lumen_dataset_${PROP_machine_id}"
  validate_source_dataset "${dataset_src}"

  require_regular_file "${INSTALL_ROOT}/installation.properties" "installation.properties"
  require_regular_file "${JOB_ROOT}/import.properties" "import.properties"
  require_regular_file "${JOB_ROOT}/package.properties" "package.properties"
  require_regular_file "${JOB_CHECKSUMS}" "package_checksums.sha256"
  require_regular_file "${JOB_ROOT}/production_batch.properties" "production_batch.properties"
  require_regular_file "${JOB_MANIFEST}" "machine manifest"
  local precheck_src="${JOB_ROOT}/batch/precheck/${PROP_machine_id}"
  local supervisor_src="${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}"
  local full_src="${JOB_ROOT}/batch/full/${PROP_machine_id}"
  require_dir "${precheck_src}" "precheck tree"
  require_dir "${supervisor_src}" "machine supervisor tree"
  require_dir "${full_src}" "full-processing tree"
  if [[ -L "${precheck_src}" ]]; then
    fail "${EXIT_VERIFY}" "precheck tree must not be a symbolic link: ${precheck_src}"
  fi
  if [[ -L "${supervisor_src}" ]]; then
    fail "${EXIT_VERIFY}" "machine supervisor tree must not be a symbolic link: ${supervisor_src}"
  fi
  if [[ -L "${full_src}" ]]; then
    fail "${EXIT_VERIFY}" "full-processing tree must not be a symbolic link: ${full_src}"
  fi

  reject_symlinks_under "${dataset_src}" "local dataset"
  reject_symlinks_under "${JOB_ROOT}/batch/precheck/${PROP_machine_id}" "precheck tree"
  reject_symlinks_under \
    "${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}" \
    "machine supervisor tree"
  reject_symlinks_under "${JOB_ROOT}/batch/full/${PROP_machine_id}" "full-processing tree"

  local progress="${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}/machine_progress.properties"
  local assigned completed timestamp_ms
  assigned="$(prop_value "${progress}" "assigned_count")"
  completed="$(prop_value "${progress}" "completed_count")"
  timestamp_ms="$(prop_value "${progress}" "timestamp_epoch_ms")"
  local manifest_sha progress_sha package_checksums_sha
  manifest_sha="$(sha256_of "${JOB_MANIFEST}")"
  progress_sha="$(sha256_of "${progress}")"
  package_checksums_sha="$(sha256_of "${JOB_CHECKSUMS}")"

  EXPORT_FINAL_ROOT="${EXPORT_DEST_ROOT}/${PROP_machine_id}"
  local existing_ok=0
  if [[ -e "${EXPORT_FINAL_ROOT}" ]]; then
    if [[ ! -d "${EXPORT_FINAL_ROOT}" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Existing export path is not a directory: ${EXPORT_FINAL_ROOT}"
    fi
    if [[ -L "${EXPORT_FINAL_ROOT}" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Existing export path must not be a symbolic link: ${EXPORT_FINAL_ROOT}"
    fi
    if [[ ! -f "${EXPORT_FINAL_ROOT}/export_checksums.sha256" ]]; then
      fail "${EXIT_VERIFY}" \
        "Existing export at ${EXPORT_FINAL_ROOT} is missing export_checksums.sha256; refusing to overwrite"
    fi
    if ! (
      cd -- "${EXPORT_FINAL_ROOT}"
      sha256sum -c -- export_checksums.sha256 >/dev/null
    ); then
      fail "${EXIT_VERIFY}" \
        "Existing export at ${EXPORT_FINAL_ROOT} failed checksum verification; refusing to overwrite or repair it"
    fi
    existing_ok=1
  fi

  EXPORT_STAGING_ROOT="${EXPORT_DEST_ROOT}/.${PROP_machine_id}_export_staging_$$"
  rm -rf -- "${EXPORT_STAGING_ROOT}"
  mkdir -p -- "${EXPORT_STAGING_ROOT}"

  stage_export_payload "${EXPORT_STAGING_ROOT}" "${dataset_src}"

  local payload_files payload_bytes
  payload_files="$(count_regular_files "${EXPORT_STAGING_ROOT}")"
  payload_bytes="$(sum_regular_file_bytes "${EXPORT_STAGING_ROOT}")"
  payload_files=$((payload_files + 1))

  local props_path="${EXPORT_STAGING_ROOT}/export.properties"
  local props_size=0
  local attempt
  for attempt in 1 2 3 4 5 6 7 8; do
    write_export_properties \
      "${props_path}" \
      "${assigned}" \
      "${completed}" \
      "${timestamp_ms}" \
      "${manifest_sha}" \
      "${progress_sha}" \
      "${package_checksums_sha}" \
      "${payload_files}" \
      "$((payload_bytes + props_size))"
    local new_size
    new_size="$(wc -c <"${props_path}" | tr -d ' ')"
    if [[ "${new_size}" == "${props_size}" ]]; then
      payload_bytes=$((payload_bytes + props_size))
      break
    fi
    props_size="${new_size}"
    if [[ "${attempt}" -eq 8 ]]; then
      fail "${EXIT_VERIFY}" "Unable to stabilize export.properties payload_bytes"
    fi
  done

  echo "Writing export checksums..."
  write_export_checksums "${EXPORT_STAGING_ROOT}"
  verify_export_checksums_in "${EXPORT_STAGING_ROOT}"

  if [[ "${existing_ok}" -eq 1 ]]; then
    if cmp -s -- \
        "${EXPORT_STAGING_ROOT}/export_checksums.sha256" \
        "${EXPORT_FINAL_ROOT}/export_checksums.sha256"; then
      cleanup_export_staging
      cat <<EOF
LUMEN RESULTS ALREADY EXPORTED
machine: ${PROP_machine_id}
destination: ${EXPORT_FINAL_ROOT}
checksums: verified
changes made: NO
EOF
      exit 0
    fi
    fail "${EXIT_VERIFY}" \
      "Existing valid export at ${EXPORT_FINAL_ROOT} differs from the current completed local result; refusing to overwrite either dataset"
  fi

  if [[ -e "${EXPORT_FINAL_ROOT}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "Export destination appeared during staging; refusing to publish: ${EXPORT_FINAL_ROOT}"
  fi

  echo "Publishing export..."
  mv -- "${EXPORT_STAGING_ROOT}" "${EXPORT_FINAL_ROOT}"
  EXPORT_STAGING_ROOT=''

  verify_export_checksums_in "${EXPORT_FINAL_ROOT}"

  cat <<EOF
LUMEN MACHINE RESULT EXPORT SUCCESS
machine: ${PROP_machine_id}
production checkpoint: ${PROP_production_commit_short}
source dataset: ${dataset_src}
destination: ${EXPORT_FINAL_ROOT}
payload files: ${payload_files}
payload bytes: ${payload_bytes}
checksum file: ${EXPORT_FINAL_ROOT}/export_checksums.sha256
source modified: NO
local cleanup: NO
EOF
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./run_lumen_machine.sh --verify-only|--precheck-only|--full-only <tile-profile-index>|--export-results <completed-results-root>" >&2
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

  if [[ "$#" -eq 2 && "$1" == "--export-results" ]]; then
    run_export_results "$2"
    exit 0
  fi

  usage
  echo >&2
  echo "ERROR: Unknown or unsupported arguments." >&2
  exit "${EXIT_USAGE}"
}

main "$@"
