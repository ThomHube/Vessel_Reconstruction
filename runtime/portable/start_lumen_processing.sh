#!/usr/bin/env bash
# Lumen portable production start wrapper (operator-facing).
#
# Installed location:
#   $HOME/LumenRuntime/6dd42df/start_lumen_processing.sh
#
# Usage:
#   ./start_lumen_processing.sh
#
# Starts the validated installed launcher detached:
#   <installation-root>/run_lumen_machine.sh --full-only 2
#
# Does NOT invoke Java/Fiji/Maven/supervisor CLIs directly.
# Does NOT run precheck. Every assigned image must already be PRECHECK_SUCCESS.
#
set -euo pipefail

readonly EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
readonly EXPECTED_COMMIT_SHORT='6dd42df'
readonly PRODUCTION_TILE_PROFILE_INDEX=2
readonly PRODUCTION_TILE_PROFILE='1024x1024'
readonly LAUNCHER_NAME='run_lumen_machine.sh'
readonly MONITOR_NAME='monitor_lumen_machine.sh'

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
PROP_launcher_path=''
PROP_launcher_sha256=''
PROP_monitor_path=''
PROP_monitor_sha256=''

JOB_ROOT=''
CONTROL_ROOT=''
ASSIGNED_COUNT=0
MANIFEST_IMAGE_IDS=()
declare -A MANIFEST_SOURCE_SHA=()
declare -A MANIFEST_CONFIG_SHA=()
DERIVED_PLOT_ID=''
DERIVED_TREE_ID=''
DERIVED_CORE_LABEL=''
DERIVED_CORE_ID=''
DERIVED_SEGMENT_ORDER=''

fail() {
  local code="$1"
  shift
  echo "ERROR: $*" >&2
  exit "${code}"
}

usage() {
  cat >&2 <<'EOF'
Usage:
  ./start_lumen_processing.sh

Validate the installed machine/job, require PRECHECK_SUCCESS for every assigned
image, then start the validated portable launcher detached:

  ./run_lumen_machine.sh --full-only 2

Production tile profile is fixed (index 2 = 1024x1024). No arguments accepted.
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

sha256_of() {
  local path="$1"
  sha256sum -- "${path}" | awk '{print $1}'
}

is_sha256() {
  [[ "${1:-}" =~ ^[0-9a-f]{64}$ ]]
}

is_positive_int() {
  [[ "${1:-}" =~ ^[1-9][0-9]*$ ]]
}

is_nonneg_int() {
  [[ "${1:-}" =~ ^[0-9]+$ ]]
}

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

now_epoch_ms() {
  local ms
  ms="$(date +%s%3N 2>/dev/null || true)"
  if is_nonneg_int "${ms}"; then
    printf '%s\n' "${ms}"
    return 0
  fi
  printf '%s\n' "$(($(date +%s) * 1000))"
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
    fail "${EXIT_VALIDATION}" "$(basename -- "${props_path}") missing required key: ${key}"
  fi
  printf '%s\n' "${value}"
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

reject_symlink() {
  local path="$1"
  local label="$2"
  if [[ -L "${path}" ]]; then
    fail "${EXIT_VALIDATION}" "${label} must not be a symbolic link: ${path}"
  fi
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
      launcher_path) PROP_launcher_path="${value}" ;;
      launcher_sha256) PROP_launcher_sha256="${value}" ;;
      monitor_path) PROP_monitor_path="${value}" ;;
      monitor_sha256) PROP_monitor_sha256="${value}" ;;
    esac
  done <"${props_path}"

  local required_name
  for required_name in \
    schema_version \
    production_commit \
    production_commit_short \
    machine_id \
    installation_root \
    work_root \
    launcher_path \
    launcher_sha256 \
    monitor_path \
    monitor_sha256
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

  if [[ "${PROP_launcher_path}" != "${INSTALL_ROOT}/${LAUNCHER_NAME}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "launcher_path mismatch: expected ${INSTALL_ROOT}/${LAUNCHER_NAME}, found ${PROP_launcher_path}"
  fi
  if [[ "${PROP_monitor_path}" != "${INSTALL_ROOT}/${MONITOR_NAME}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "monitor_path mismatch: expected ${INSTALL_ROOT}/${MONITOR_NAME}, found ${PROP_monitor_path}"
  fi

  if ! is_sha256 "${PROP_launcher_sha256}"; then
    fail "${EXIT_VALIDATION}" "installation.properties launcher_sha256 is not a valid SHA-256"
  fi
  if ! is_sha256 "${PROP_monitor_sha256}"; then
    fail "${EXIT_VALIDATION}" "installation.properties monitor_sha256 is not a valid SHA-256"
  fi

  require_file "${PROP_launcher_path}" "portable launcher"
  [[ -x "${PROP_launcher_path}" ]] \
    || fail "${EXIT_VALIDATION}" "portable launcher is not executable: ${PROP_launcher_path}"
  require_file "${PROP_monitor_path}" "portable monitor"
  [[ -x "${PROP_monitor_path}" ]] \
    || fail "${EXIT_VALIDATION}" "portable monitor is not executable: ${PROP_monitor_path}"

  local actual_launcher actual_monitor
  actual_launcher="$(sha256_of "${PROP_launcher_path}")"
  actual_monitor="$(sha256_of "${PROP_monitor_path}")"
  if [[ "${actual_launcher}" != "${PROP_launcher_sha256}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "launcher SHA-256 mismatch vs installation.properties: expected ${PROP_launcher_sha256}, found ${actual_launcher}"
  fi
  if [[ "${actual_monitor}" != "${PROP_monitor_sha256}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "monitor SHA-256 mismatch vs installation.properties: expected ${PROP_monitor_sha256}, found ${actual_monitor}"
  fi
}

verify_imported_job_present() {
  JOB_ROOT="${PROP_work_root}/job"
  if [[ ! -d "${JOB_ROOT}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "No imported job at ${JOB_ROOT}. Import the matching machine package before starting processing."
  fi
  reject_symlink "${JOB_ROOT}" "imported job root"
}

verify_job_metadata() {
  local import_props package_props config_path manifest_path
  import_props="${JOB_ROOT}/import.properties"
  package_props="${JOB_ROOT}/package.properties"
  config_path="${JOB_ROOT}/production_batch.properties"
  manifest_path="${JOB_ROOT}/batch/machines/${PROP_machine_id}_manifest.csv"

  require_file "${import_props}" "import.properties"
  require_file "${package_props}" "package.properties"
  require_file "${config_path}" "production_batch.properties"
  require_file "${manifest_path}" "machine manifest"

  local import_machine package_machine import_ckpt package_ckpt
  local package_schema manifest_schema import_schema
  import_machine="$(require_prop "${import_props}" "machine_id")"
  package_machine="$(require_prop "${package_props}" "machine_id")"
  import_ckpt="$(require_prop "${import_props}" "scientific_checkpoint")"
  package_ckpt="$(require_prop "${package_props}" "scientific_checkpoint")"
  package_schema="$(require_prop "${package_props}" "package.schema_version")"
  manifest_schema="$(require_prop "${package_props}" "manifest.schema_version")"
  import_schema="$(require_prop "${import_props}" "import.schema_version")"

  [[ "${package_schema}" == "2" ]] \
    || fail "${EXIT_VALIDATION}" "package.schema_version must be 2, found ${package_schema}"
  [[ "${manifest_schema}" == "2" ]] \
    || fail "${EXIT_VALIDATION}" "manifest.schema_version must be 2, found ${manifest_schema}"
  [[ "${import_schema}" == "2" ]] \
    || fail "${EXIT_VALIDATION}" "import.schema_version must be 2, found ${import_schema}"
  [[ "${import_machine}" == "${PROP_machine_id}" ]] \
    || fail "${EXIT_VALIDATION}" \
      "import.properties machine_id ${import_machine} != installed ${PROP_machine_id}"
  [[ "${package_machine}" == "${PROP_machine_id}" ]] \
    || fail "${EXIT_VALIDATION}" \
      "package.properties machine_id ${package_machine} != installed ${PROP_machine_id}"
  [[ "${import_ckpt}" == "${EXPECTED_COMMIT}" ]] \
    || fail "${EXIT_VALIDATION}" "import.properties scientific_checkpoint mismatch"
  [[ "${package_ckpt}" == "${EXPECTED_COMMIT}" ]] \
    || fail "${EXIT_VALIDATION}" "package.properties scientific_checkpoint mismatch"

  ASSIGNED_COUNT="$(require_prop "${package_props}" "assigned_tiff_count")"
  if ! is_positive_int "${ASSIGNED_COUNT}"; then
    fail "${EXIT_VALIDATION}" \
      "package.properties assigned_tiff_count must be a positive integer, found ${ASSIGNED_COUNT}"
  fi
}

verify_production_tile_profile() {
  local config_path profiles entry
  config_path="${JOB_ROOT}/production_batch.properties"
  profiles="$(require_prop "${config_path}" "classification.tile_profiles")"

  local IFS=';'
  local -a parts
  # shellcheck disable=SC2206
  parts=(${profiles})
  unset IFS

  if (( ${#parts[@]} <= PRODUCTION_TILE_PROFILE_INDEX )); then
    fail "${EXIT_VALIDATION}" \
      "classification.tile_profiles lacks index ${PRODUCTION_TILE_PROFILE_INDEX}: ${profiles}"
  fi
  entry="${parts[$PRODUCTION_TILE_PROFILE_INDEX]}"
  if [[ "${entry}" != "${PRODUCTION_TILE_PROFILE}" ]]; then
    fail "${EXIT_VALIDATION}" \
      "production tile profile index ${PRODUCTION_TILE_PROFILE_INDEX} must be ${PRODUCTION_TILE_PROFILE}, found ${entry}"
  fi
}

# Split a simple CSV line (no embedded commas in fields of interest).
split_csv_simple() {
  local line="$1"
  CSV_FIELDS=()
  local IFS=','
  # shellcheck disable=SC2206
  CSV_FIELDS=(${line})
  unset IFS
}

load_machine_manifest() {
  local manifest_path="$1"
  MANIFEST_IMAGE_IDS=()
  MANIFEST_SOURCE_SHA=()
  MANIFEST_CONFIG_SHA=()

  local header_line line image_id source_sha config_sha schema_version
  local plot_id tree_id core_label core_id segment_order
  local idx_image=-1 idx_source=-1 idx_config=-1
  local idx_schema=-1 idx_plot=-1 idx_tree=-1 idx_core_label=-1 idx_core_id=-1 idx_segment=-1
  local data_rows=0
  local i name
  local header=()
  declare -A seen_header=()

  # Read header once, then only subsequent lines as data (same file descriptor).
  {
    IFS= read -r header_line || true
    header_line="${header_line%$'\r'}"
    [[ -n "${header_line}" ]] || fail "${EXIT_VALIDATION}" "machine manifest is empty: ${manifest_path}"

    split_csv_simple "${header_line}"
    header=("${CSV_FIELDS[@]}")
    for i in "${!header[@]}"; do
      name="${header[$i]}"
      if [[ -n "${seen_header[${name}]+x}" ]]; then
        fail "${EXIT_VALIDATION}" "Duplicate header field in machine manifest: ${name}"
      fi
      seen_header["${name}"]=1
      case "${name}" in
        schema_version) idx_schema=$i ;;
        image_id) idx_image=$i ;;
        plot_id) idx_plot=$i ;;
        tree_id) idx_tree=$i ;;
        core_label) idx_core_label=$i ;;
        core_id) idx_core_id=$i ;;
        segment_order) idx_segment=$i ;;
        source_sha256) idx_source=$i ;;
        configuration_sha256) idx_config=$i ;;
      esac
    done
    if (( idx_schema < 0 || idx_image < 0 || idx_source < 0 || idx_config < 0 \
       || idx_plot < 0 || idx_tree < 0 || idx_core_label < 0 || idx_core_id < 0 \
       || idx_segment < 0 )); then
      fail "${EXIT_VALIDATION}" \
        "machine manifest missing required schema-2 identity/sha columns"
    fi

    while IFS= read -r line || [[ -n "${line}" ]]; do
      line="${line%$'\r'}"
      [[ -z "${line}" ]] && continue
      split_csv_simple "${line}"
      image_id="${CSV_FIELDS[$idx_image]:-}"
      source_sha="${CSV_FIELDS[$idx_source]:-}"
      config_sha="${CSV_FIELDS[$idx_config]:-}"
      schema_version="${CSV_FIELDS[$idx_schema]:-}"
      plot_id="${CSV_FIELDS[$idx_plot]:-}"
      tree_id="${CSV_FIELDS[$idx_tree]:-}"
      core_label="${CSV_FIELDS[$idx_core_label]:-}"
      core_id="${CSV_FIELDS[$idx_core_id]:-}"
      segment_order="${CSV_FIELDS[$idx_segment]:-}"

      [[ "${schema_version}" == "2" ]] \
        || fail "${EXIT_VALIDATION}" \
          "machine manifest schema_version must be 2, found ${schema_version} (image ${image_id})"
      if ! is_core_segment_image_id "${image_id}"; then
        fail "${EXIT_VALIDATION}" "Invalid or missing PPTTCS image_id in machine manifest: ${image_id}"
      fi
      derive_core_segment_fields "${image_id}"
      [[ "${plot_id}" == "${DERIVED_PLOT_ID}" \
         && "${tree_id}" == "${DERIVED_TREE_ID}" \
         && "${core_label}" == "${DERIVED_CORE_LABEL}" \
         && "${core_id}" == "${DERIVED_CORE_ID}" \
         && "${segment_order}" == "${DERIVED_SEGMENT_ORDER}" ]] \
        || fail "${EXIT_VALIDATION}" \
          "Identity decomposition mismatch for image_id ${image_id}"
      if [[ -n "${MANIFEST_SOURCE_SHA[${image_id}]+x}" ]]; then
        fail "${EXIT_VALIDATION}" "Duplicate image_id in machine manifest: ${image_id}"
      fi
      if ! is_sha256 "${source_sha}"; then
        fail "${EXIT_VALIDATION}" "Malformed source_sha256 for image ${image_id}"
      fi
      if ! is_sha256 "${config_sha}"; then
        fail "${EXIT_VALIDATION}" "Malformed configuration_sha256 for image ${image_id}"
      fi

      MANIFEST_IMAGE_IDS+=("${image_id}")
      MANIFEST_SOURCE_SHA["${image_id}"]="${source_sha}"
      MANIFEST_CONFIG_SHA["${image_id}"]="${config_sha}"
      data_rows=$((data_rows + 1))
    done
  } <"${manifest_path}"

  if (( data_rows == 0 )); then
    fail "${EXIT_VALIDATION}" "machine manifest has no data rows: ${manifest_path}"
  fi
  if (( data_rows != ASSIGNED_COUNT )); then
    fail "${EXIT_VALIDATION}" \
      "machine manifest data-row count ${data_rows} != assigned_tiff_count ${ASSIGNED_COUNT}"
  fi
}

verify_all_prechecks() {
  local image_id result_path status source_unchanged result_image result_machine
  local result_source result_config
  local ok=0

  for image_id in "${MANIFEST_IMAGE_IDS[@]}"; do
    result_path="${JOB_ROOT}/batch/precheck/${PROP_machine_id}/jobs/${image_id}/result.properties"
    if [[ ! -f "${result_path}" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Missing PRECHECK result for image ${image_id}: ${result_path}"
    fi
    reject_symlink "${result_path}" "precheck result for ${image_id}"

    status="$(prop_value "${result_path}" "status" || true)"
    source_unchanged="$(prop_value "${result_path}" "source_unchanged" || true)"
    result_image="$(prop_value "${result_path}" "image_id" || true)"
    result_machine="$(prop_value "${result_path}" "machine_id" || true)"
    result_source="$(prop_value "${result_path}" "source_sha256" || true)"
    result_config="$(prop_value "${result_path}" "configuration_sha256" || true)"

    if [[ "${status}" != "PRECHECK_SUCCESS" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Image ${image_id} precheck status is ${status:-MISSING}, required PRECHECK_SUCCESS"
    fi
    if [[ "${source_unchanged}" != "TRUE" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Image ${image_id} source_unchanged=${source_unchanged:-MISSING}, required TRUE"
    fi
    if [[ "${result_image}" != "${image_id}" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Image ${image_id} precheck image_id mismatch: found ${result_image:-MISSING}"
    fi
    if [[ "${result_machine}" != "${PROP_machine_id}" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Image ${image_id} precheck machine_id mismatch: found ${result_machine:-MISSING}"
    fi
    if [[ "${result_source}" != "${MANIFEST_SOURCE_SHA[${image_id}]}" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Image ${image_id} precheck source_sha256 mismatch vs machine manifest"
    fi
    if [[ "${result_config}" != "${MANIFEST_CONFIG_SHA[${image_id}]}" ]]; then
      fail "${EXIT_VALIDATION}" \
        "Image ${image_id} precheck configuration_sha256 mismatch vs machine manifest"
    fi
    ok=$((ok + 1))
  done

  if (( ok != ASSIGNED_COUNT )); then
    fail "${EXIT_VALIDATION}" "Internal precheck gate count mismatch"
  fi
}

is_already_complete() {
  # Image-count completion is not machine completion.
  # completed_count == assigned_count means every assigned IMAGE is FULL_SUCCESS.
  # status=MACHINE_SUCCESS means those images succeeded AND physical-core
  # coordinate assembly succeeded. Assembly-only MACHINE_FAILED_UNEXPECTED
  # must return false so a normal start rerun retries finalization.
  local progress="${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}/machine_progress.properties"
  [[ -f "${progress}" ]] || return 1

  local completed remaining image_id action assigned status percent failed blocked
  completed="$(prop_value "${progress}" "completed_count" || true)"
  remaining="$(prop_value "${progress}" "remaining_count" || true)"
  image_id="$(prop_value "${progress}" "current_image_id" || true)"
  action="$(prop_value "${progress}" "current_action" || true)"
  assigned="$(prop_value "${progress}" "assigned_count" || true)"
  status="$(prop_value "${progress}" "status" || true)"
  percent="$(prop_value "${progress}" "progress_percent" || true)"
  failed="$(prop_value "${progress}" "failed_count" || true)"
  blocked="$(prop_value "${progress}" "blocked_count" || true)"

  if ! is_nonneg_int "${completed}" || ! is_nonneg_int "${remaining}" || ! is_nonneg_int "${assigned}"; then
    return 1
  fi
  [[ "${image_id}" == "NA" && "${action}" == "NA" ]] || return 1
  [[ "${remaining}" == "0" ]] || return 1
  [[ "${assigned}" == "${ASSIGNED_COUNT}" ]] || return 1
  [[ "${completed}" == "${assigned}" ]] || return 1
  is_positive_int "${assigned}" || return 1
  [[ "${percent}" == "100.0" ]] || return 1
  [[ "${failed}" == "0" ]] || return 1
  [[ "${blocked}" == "0" ]] || return 1
  [[ "${status}" == "MACHINE_SUCCESS" ]] || return 1
  return 0
}

pid_is_alive() {
  local pid="$1"
  is_nonneg_int "${pid}" || return 1
  if [[ -d "/proc/${pid}" ]]; then
    return 0
  fi
  # Fallback when /proc is unavailable.
  kill -0 "${pid}" 2>/dev/null
}

pid_belongs_to_this_runner() {
  local pid="$1"
  local cmdline=''
  if [[ -r "/proc/${pid}/cmdline" ]]; then
    cmdline="$(tr '\0' ' ' <"/proc/${pid}/cmdline" 2>/dev/null || true)"
  elif command -v ps >/dev/null 2>&1; then
    cmdline="$(ps -p "${pid}" -o args= 2>/dev/null || true)"
  fi
  [[ -n "${cmdline}" ]] || return 1
  if grep -Fq -- "${CONTROL_ROOT}/runner.sh" <<<"${cmdline}"; then
    return 0
  fi
  if grep -Fq -- "${PROP_launcher_path}" <<<"${cmdline}" \
    && grep -Fq -- '--full-only' <<<"${cmdline}"; then
    return 0
  fi
  return 1
}

scan_active_processing() {
  # Returns 0 if an active process for THIS install/job is detected.
  if ! command -v ps >/dev/null 2>&1; then
    return 1
  fi
  local out
  out="$(ps -ewwo args= 2>/dev/null || true)"
  [[ -n "${out}" ]] || return 1

  if printf '%s\n' "${out}" | grep -F -- "${PROP_launcher_path}" \
    | grep -Fq -- '--full-only'; then
    return 0
  fi
  if printf '%s\n' "${out}" | grep -F -- "${INSTALL_ROOT}" \
    | grep -E 'BatchMachineSupervisorCli|BatchFullProcessingCli|BatchFullProcessingImagePlugin|ImageJ-linux64' \
    | grep -Fq -- "${PROP_machine_id}"; then
    return 0
  fi
  if printf '%s\n' "${out}" | grep -F -- "${JOB_ROOT}" \
    | grep -Eq 'BatchMachineSupervisorCli|BatchFullProcessingCli|BatchFullProcessingImagePlugin|ImageJ-linux64'; then
    return 0
  fi
  return 1
}

is_already_running() {
  CONTROL_ROOT="${PROP_work_root}/control/full_processing"
  local pid_file="${CONTROL_ROOT}/runner.pid"
  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(tr -d '[:space:]' <"${pid_file}" || true)"
    if is_nonneg_int "${pid}" && pid_is_alive "${pid}"; then
      if pid_belongs_to_this_runner "${pid}"; then
        return 0
      fi
      # Alive PID that does not look like our runner: be conservative.
      return 0
    fi
    # Stale PID file — allow replacement later.
  fi

  if scan_active_processing; then
    return 0
  fi
  return 1
}

print_ready_message() {
  cat <<EOF
LUMEN PROCESSING READY
machine: ${PROP_machine_id}
assigned images: ${ASSIGNED_COUNT}
precheck: ${ASSIGNED_COUNT} / ${ASSIGNED_COUNT} PRECHECK_SUCCESS
tile profile index: ${PRODUCTION_TILE_PROFILE_INDEX}
tile size: ${PRODUCTION_TILE_PROFILE}
classification: supervisor-controlled
reconstruction: supervisor-controlled
EOF
}

write_runner_script() {
  local runner_path="$1"
  local log_path="$2"
  local exitcode_path="$3"
  local launcher_path="$4"
  local home_value="$5"

  cat >"${runner_path}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
export HOME=$(printf '%q' "${home_value}")
cd /tmp 2>/dev/null || cd /
set +e
$(printf '%q' "${launcher_path}") --full-only 2 >$(printf '%q' "${log_path}") 2>&1
rc=\$?
set -e
tmp=$(printf '%q' "${exitcode_path}").tmp.\$\$
printf '%s\n' "\${rc}" >"\${tmp}"
mv -f -- "\${tmp}" $(printf '%q' "${exitcode_path}")
EOF
  chmod 755 -- "${runner_path}"
}

start_detached() {
  CONTROL_ROOT="${PROP_work_root}/control/full_processing"
  mkdir -p -- "${CONTROL_ROOT}"

  local runner_path="${CONTROL_ROOT}/runner.sh"
  local pid_file="${CONTROL_ROOT}/runner.pid"
  local log_path="${CONTROL_ROOT}/runner.log"
  local exitcode_path="${CONTROL_ROOT}/runner.exitcode"
  local start_props="${CONTROL_ROOT}/start.properties"

  # Clear previous completion marker; log is truncated by the runner redirect.
  rm -f -- "${exitcode_path}" "${pid_file}"

  write_runner_script \
    "${runner_path}" \
    "${log_path}" \
    "${exitcode_path}" \
    "${PROP_launcher_path}" \
    "${HOME}"

  nohup bash -- "${runner_path}" >/dev/null 2>&1 &
  local runner_pid=$!
  local pid_tmp="${pid_file}.tmp.$$"
  printf '%s\n' "${runner_pid}" >"${pid_tmp}"
  mv -f -- "${pid_tmp}" "${pid_file}"

  local ts
  ts="$(now_epoch_ms)"
  cat >"${start_props}" <<EOF
schema_version=1
scientific_checkpoint=${EXPECTED_COMMIT}
machine_id=${PROP_machine_id}
tile_profile_index=${PRODUCTION_TILE_PROFILE_INDEX}
tile_profile=${PRODUCTION_TILE_PROFILE}
assigned_tiff_count=${ASSIGNED_COUNT}
runner_pid=${runner_pid}
runner_log=${log_path}
runner_exitcode=${exitcode_path}
start_timestamp_epoch_ms=${ts}
EOF

  sleep 2

  if pid_is_alive "${runner_pid}"; then
    cat <<EOF
LUMEN PROCESSING STARTED
machine: ${PROP_machine_id}
assigned images: ${ASSIGNED_COUNT}
tile profile index: ${PRODUCTION_TILE_PROFILE_INDEX}
tile size: ${PRODUCTION_TILE_PROFILE}
runner PID: ${runner_pid}
log: ${log_path}
exit code file: ${exitcode_path}
classification: supervisor-controlled
reconstruction: supervisor-controlled
detached: YES

Monitor with:
  watch -n 10 ${INSTALL_ROOT}/${MONITOR_NAME}

Closing this terminal does not stop the detached processing.
EOF
    return 0
  fi

  # Runner already exited (fast failure or short run). Wait briefly for atomic exitcode.
  local wait_i
  for wait_i in 1 2 3 4 5 6 7 8 9 10; do
    if [[ -f "${exitcode_path}" ]]; then
      break
    fi
    sleep 0.2
  done

  # Runner already exited (fast failure or very short fake run).
  local rc='unknown'
  if [[ -f "${exitcode_path}" ]]; then
    rc="$(tr -d '[:space:]' <"${exitcode_path}" || true)"
  fi
  echo "LUMEN PROCESSING RUNNER EXITED EARLY"
  echo "machine: ${PROP_machine_id}"
  echo "runner PID: ${runner_pid}"
  echo "exit code: ${rc}"
  echo "log: ${log_path}"
  if [[ -f "${log_path}" ]]; then
    echo "----- runner.log (tail) -----"
    tail -n 40 -- "${log_path}" || true
  fi
  if is_nonneg_int "${rc}"; then
    exit "${rc}"
  fi
  exit "${EXIT_VALIDATION}"
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./start_lumen_processing.sh" >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  if [[ "$#" -ne 0 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected no arguments"
  fi

  require_cmd sha256sum
  require_cmd awk
  require_cmd nohup
  require_cmd bash
  require_cmd mv
  require_cmd mkdir
  require_cmd chmod
  require_cmd date
  require_cmd sleep
  require_cmd tail
  require_cmd tr
  require_cmd grep

  resolve_script_path
  load_installation_properties "${INSTALL_ROOT}/installation.properties"
  verify_installation_identity
  verify_imported_job_present
  verify_job_metadata
  verify_production_tile_profile
  load_machine_manifest "${JOB_ROOT}/batch/machines/${PROP_machine_id}_manifest.csv"
  verify_all_prechecks

  print_ready_message

  if is_already_complete; then
    local progress="${JOB_ROOT}/batch/machine_supervisor/${PROP_machine_id}/machine_progress.properties"
    local completed assigned
    completed="$(prop_value "${progress}" "completed_count")"
    assigned="$(prop_value "${progress}" "assigned_count")"
    cat <<EOF
LUMEN PROCESSING ALREADY COMPLETE
machine: ${PROP_machine_id}
completed: ${completed} / ${assigned}
processing launched: NO
EOF
    exit 0
  fi

  if is_already_running; then
    cat <<EOF
LUMEN PROCESSING ALREADY RUNNING
machine: ${PROP_machine_id}
processing launched: NO
EOF
    exit 0
  fi

  start_detached
}

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "${EXIT_VALIDATION}" "Required command not found: ${cmd}"
}

main "$@"
