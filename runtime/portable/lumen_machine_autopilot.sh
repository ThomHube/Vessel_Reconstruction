#!/usr/bin/env bash
# Local Lumen machine autopilot (Issue 7H).
#
# Installed location (long-running):
#   $HOME/LumenRuntime/6dd42df/lumen_machine_autopilot.sh
#
# ADATA one-click launchers bootstrap into this script. After install/import,
# all long-running control runs from the local runtime so ADATA can be unplugged.
#
# Do NOT source this file.
#
set -euo pipefail

readonly EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
readonly EXPECTED_COMMIT_SHORT='6dd42df'
readonly AUTOPILOT_NAME='lumen_machine_autopilot.sh'
readonly INSTALLER_NAME='install_machine.sh'
readonly IMPORT_NAME='import_machine_package.sh'
readonly LAUNCHER_NAME='run_lumen_machine.sh'
readonly STARTER_NAME='start_lumen_processing.sh'

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3
readonly EXIT_INSTALL=4
readonly EXIT_VERIFY=5
readonly EXIT_PRECHECK=10
readonly EXIT_PROCESSING=11

MACHINE_ID=''
CORE_ID=''
PACKAGE_ROOT=''
PORTABLE_ROOT=''
LAUNCHER_PATH=''
LOCAL_WAIT_ONLY=0

INSTALL_ROOT=''
STATE_DIR=''
LOG_PATH=''
PID_PATH=''
LOCK_PATH=''
BOOTSTRAP_PID_PATH=''
PROPS_PATH=''
DRIVE_PROPS_PATH=''
COMPLETE_PATH=''

SCRIPT_PATH=''
RUNNING_FROM_LOCAL=0
LOCK_HELD=0

fail() {
  local code="$1"
  shift
  local msg="$*"
  echo "ERROR: ${msg}" >&2
  if [[ -n "${INSTALL_ROOT:-}" ]] && installation_present 2>/dev/null; then
    if [[ -n "${LOG_PATH:-}" ]]; then
      mkdir -p -- "${STATE_DIR}"
      printf '%s\n' "[$(log_ts)] ERROR: ${msg}" >>"${LOG_PATH}" || true
    fi
  fi
  exit "${code}"
}

log_ts() {
  date +'%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || date
}

log() {
  local msg="$*"
  if installation_present; then
    mkdir -p -- "${STATE_DIR}"
    printf '%s\n' "[$(log_ts)] ${msg}" >>"${LOG_PATH}"
  fi
  printf '%s\n' "${msg}"
}

usage() {
  cat >&2 <<'EOF'
Usage:
  ./lumen_machine_autopilot.sh --machine-id <machine_XX> --core-id <coreId>
      [--package-root <dir>] [--portable-root <dir>] [--launcher-path <file>]
      [--local-wait]
EOF
}

poll_seconds() {
  printf '%s\n' "${LUMEN_AUTOPILOT_POLL_SECONDS:-5}"
}

adata_poll_seconds() {
  printf '%s\n' "${LUMEN_AUTOPILOT_ADATA_POLL_SECONDS:-15}"
}

is_foreground() {
  [[ "${LUMEN_AUTOPILOT_FOREGROUND:-0}" == "1" ]]
}

atomic_write() {
  local dest="$1"
  local tmp="${dest}.tmp.$$"
  cat >"${tmp}"
  mv -f -- "${tmp}" "${dest}"
}

prop_value() {
  local props_path="$1"
  local key="$2"
  local line
  [[ -f "${props_path}" ]] || return 1
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

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "${EXIT_VALIDATION}" "Required command not found: ${cmd}"
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
}

sha256_of() {
  sha256sum -- "$1" | awk '{print $1}'
}

quoted_cmd() {
  local out=''
  local a
  for a in "$@"; do
    out+="$(printf '%q ' "${a}")"
  done
  printf '%s\n' "${out% }"
}

parse_args() {
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --machine-id)
        MACHINE_ID="$2"
        shift 2
        ;;
      --core-id)
        CORE_ID="$2"
        shift 2
        ;;
      --package-root)
        PACKAGE_ROOT="$2"
        shift 2
        ;;
      --portable-root)
        PORTABLE_ROOT="$2"
        shift 2
        ;;
      --launcher-path)
        LAUNCHER_PATH="$2"
        shift 2
        ;;
      --local-wait)
        LOCAL_WAIT_ONLY=1
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        usage
        fail "${EXIT_USAGE}" "Unknown argument: $1"
        ;;
    esac
  done
}

validate_machine_id() {
  [[ "${MACHINE_ID}" =~ ^machine_(0[1-9]|[1-9][0-9])$ ]] \
    || fail "${EXIT_USAGE}" "Invalid machine ID '${MACHINE_ID}'"
}

init_paths() {
  INSTALL_ROOT="${HOME}/LumenRuntime/${EXPECTED_COMMIT_SHORT}"
  STATE_DIR="${INSTALL_ROOT}/work/${MACHINE_ID}/autopilot"
  LOG_PATH="${STATE_DIR}/autopilot.log"
  PID_PATH="${STATE_DIR}/autopilot.pid"
  LOCK_PATH="${HOME}/LumenRuntime/.autopilot-${EXPECTED_COMMIT_SHORT}-${MACHINE_ID}.lock"
  BOOTSTRAP_PID_PATH="${HOME}/LumenRuntime/.autopilot-${EXPECTED_COMMIT_SHORT}-${MACHINE_ID}.pid"
  PROPS_PATH="${STATE_DIR}/autopilot.properties"
  DRIVE_PROPS_PATH="${STATE_DIR}/drive.properties"
  COMPLETE_PATH="${STATE_DIR}/AUTOPILOT_COMPLETE"
  if [[ -n "${SCRIPT_PATH}" && "${SCRIPT_PATH}" == "${INSTALL_ROOT}/${AUTOPILOT_NAME}" ]]; then
    RUNNING_FROM_LOCAL=1
  fi
}

installation_present() {
  [[ -f "${INSTALL_ROOT}/installation.properties" ]]
}

ensure_state_dir() {
  installation_present \
    || fail "${EXIT_INSTALL}" "Refusing to create autopilot state before a verified installation exists"
  mkdir -p -- "${STATE_DIR}"
}

current_status() {
  prop_value "${PROPS_PATH}" "status" 2>/dev/null || true
}

write_state() {
  local status="$1"
  shift || true
  local extra="${*:-}"
  ensure_state_dir
  local ts
  ts="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"
  {
    cat <<EOF
schema_version=1
machine_id=${MACHINE_ID}
core_id=${CORE_ID}
scientific_commit=${EXPECTED_COMMIT}
scientific_commit_short=${EXPECTED_COMMIT_SHORT}
status=${status}
updated_epoch_ms=${ts}
install_root=${INSTALL_ROOT}
log_path=${LOG_PATH}
EOF
    if [[ -n "${PACKAGE_ROOT}" ]]; then
      echo "package_root=${PACKAGE_ROOT}"
    fi
    if [[ -n "${PORTABLE_ROOT}" ]]; then
      echo "portable_root=${PORTABLE_ROOT}"
    fi
    if [[ -n "${extra}" ]]; then
      printf '%s\n' "${extra}"
    fi
  } | atomic_write "${PROPS_PATH}"
}

pid_is_alive() {
  local pid="$1"
  [[ "${pid}" =~ ^[1-9][0-9]*$ ]] || return 1
  kill -0 "${pid}" 2>/dev/null
}

pid_is_this_autopilot() {
  local pid="$1"
  pid_is_alive "${pid}" || return 1
  local cmdline=''
  if [[ -r "/proc/${pid}/cmdline" ]]; then
    cmdline="$(tr '\0' ' ' <"/proc/${pid}/cmdline" || true)"
  fi
  if [[ "${cmdline}" == *"${AUTOPILOT_NAME}"* && "${cmdline}" == *"${MACHINE_ID}"* ]]; then
    return 0
  fi
  # Conservative: an alive PID recorded by us is treated as ours.
  return 0
}

report_existing_instance() {
  local status
  status="$(current_status)"
  case "${status}" in
    AUTOPILOT_COMPLETE)
      echo "LUMEN AUTOPILOT ALREADY COMPLETE"
      echo "machine: ${MACHINE_ID}"
      echo "log: ${LOG_PATH}"
      if [[ -f "${COMPLETE_PATH}" ]]; then
        echo "complete marker: ${COMPLETE_PATH}"
      fi
      ;;
    WAITING_FOR_ADATA)
      echo "LUMEN AUTOPILOT WAITING_FOR_ADATA"
      echo "machine: ${MACHINE_ID}"
      echo "log: ${LOG_PATH}"
      echo "processing launched: NO (already MACHINE_SUCCESS; waiting to export)"
      ;;
    *)
      echo "LUMEN AUTOPILOT RUNNING"
      echo "machine: ${MACHINE_ID}"
      echo "status: ${status:-UNKNOWN}"
      echo "log: ${LOG_PATH}"
      echo "processing launched: NO (already controlled by a live autopilot)"
      ;;
  esac
}

maybe_report_complete() {
  local status
  status="$(current_status)"
  if [[ "${status}" == "AUTOPILOT_COMPLETE" && -f "${COMPLETE_PATH}" ]]; then
    echo "LUMEN AUTOPILOT ALREADY COMPLETE"
    echo "machine: ${MACHINE_ID}"
    echo "scientific_commit: ${EXPECTED_COMMIT}"
    echo "complete marker: ${COMPLETE_PATH}"
    echo "log: ${LOG_PATH}"
    return 0
  fi
  return 1
}

acquire_instance_lock() {
  mkdir -p -- "${HOME}/LumenRuntime"
  local existing
  if [[ -f "${PID_PATH}" ]]; then
    existing="$(tr -d '[:space:]' <"${PID_PATH}" || true)"
    if pid_is_this_autopilot "${existing}" && [[ "${existing}" != "$$" ]]; then
      report_existing_instance
      exit 0
    fi
    rm -f -- "${PID_PATH}"
  fi
  if [[ -f "${BOOTSTRAP_PID_PATH}" ]]; then
    existing="$(tr -d '[:space:]' <"${BOOTSTRAP_PID_PATH}" || true)"
    if pid_is_this_autopilot "${existing}" && [[ "${existing}" != "$$" ]]; then
      report_existing_instance
      exit 0
    fi
    rm -f -- "${BOOTSTRAP_PID_PATH}"
  fi

  exec 9>"${LOCK_PATH}"
  if ! flock -n 9; then
    report_existing_instance
    exit 0
  fi
  LOCK_HELD=1
  local tmp="${BOOTSTRAP_PID_PATH}.tmp.$$"
  printf '%s\n' "$$" >"${tmp}"
  mv -f -- "${tmp}" "${BOOTSTRAP_PID_PATH}"
  if installation_present; then
    ensure_state_dir
    tmp="${PID_PATH}.tmp.$$"
    printf '%s\n' "$$" >"${tmp}"
    mv -f -- "${tmp}" "${PID_PATH}"
  fi
}

release_pid_file() {
  if [[ "${LOCK_HELD}" != "1" ]]; then
    return 0
  fi
  local recorded
  if [[ -f "${PID_PATH}" ]]; then
    recorded="$(tr -d '[:space:]' <"${PID_PATH}" || true)"
    if [[ "${recorded}" == "$$" ]]; then
      rm -f -- "${PID_PATH}"
    fi
  fi
  if [[ -f "${BOOTSTRAP_PID_PATH}" ]]; then
    recorded="$(tr -d '[:space:]' <"${BOOTSTRAP_PID_PATH}" || true)"
    if [[ "${recorded}" == "$$" ]]; then
      rm -f -- "${BOOTSTRAP_PID_PATH}"
    fi
  fi
}

extract_expected_commit_from_script() {
  local file="$1"
  local line val
  line="$(grep -E "^readonly EXPECTED_COMMIT=" "${file}" | head -n1 || true)"
  [[ -n "${line}" ]] || return 1
  val="${line#readonly EXPECTED_COMMIT=}"
  val="${val#\'}"
  val="${val%\'}"
  val="${val#\"}"
  val="${val%\"}"
  printf '%s\n' "${val}"
}

relpath_from_mount() {
  local mountpoint="$1"
  local abs="$2"
  local prefix="${mountpoint%/}"
  if [[ "${abs}" == "${prefix}" ]]; then
    printf '%s\n' "."
    return 0
  fi
  if [[ "${abs}" == "${prefix}/"* ]]; then
    printf '%s\n' "${abs#"${prefix}"/}"
    return 0
  fi
  return 1
}

lookup_mountpoint() {
  local path="$1"
  findmnt -n -o TARGET --target "${path}" 2>/dev/null | head -n1
}

lookup_source() {
  local path="$1"
  findmnt -n -o SOURCE --target "${path}" 2>/dev/null | head -n1
}

lookup_uuid_for_path() {
  local path="$1"
  if [[ -n "${LUMEN_AUTOPILOT_TEST_UUID:-}" ]]; then
    printf '%s\n' "${LUMEN_AUTOPILOT_TEST_UUID}"
    return 0
  fi
  local uuid=''
  uuid="$(findmnt -n -o UUID --target "${path}" 2>/dev/null | head -n1 || true)"
  uuid="$(tr -d '[:space:]' <<<"${uuid}")"
  if [[ -n "${uuid}" ]]; then
    printf '%s\n' "${uuid}"
    return 0
  fi
  local source
  source="$(lookup_source "${path}")"
  if [[ -n "${source}" ]]; then
    uuid="$(lsblk -n -o UUID "${source}" 2>/dev/null | head -n1 || true)"
    uuid="$(tr -d '[:space:]' <<<"${uuid}")"
    if [[ -n "${uuid}" ]]; then
      printf '%s\n' "${uuid}"
      return 0
    fi
    uuid="$(lsblk -n -o PARTUUID "${source}" 2>/dev/null | head -n1 || true)"
    uuid="$(tr -d '[:space:]' <<<"${uuid}")"
    if [[ -n "${uuid}" ]]; then
      printf '%s\n' "${uuid}"
      return 0
    fi
  fi
  return 1
}

mountpoint_for_uuid() {
  local uuid="$1"
  if [[ -n "${LUMEN_AUTOPILOT_TEST_MOUNTPOINT:-}" ]]; then
    if [[ -n "${LUMEN_AUTOPILOT_TEST_UUID:-}" && "${LUMEN_AUTOPILOT_TEST_UUID}" != "${uuid}" ]]; then
      return 1
    fi
    printf '%s\n' "${LUMEN_AUTOPILOT_TEST_MOUNTPOINT}"
    return 0
  fi
  local mp
  mp="$(findmnt -nr -o TARGET -S "UUID=${uuid}" 2>/dev/null | head -n1 || true)"
  if [[ -n "${mp}" ]]; then
    printf '%s\n' "${mp}"
    return 0
  fi
  mp="$(lsblk -nr -o UUID,MOUNTPOINT 2>/dev/null | awk -v u="${uuid}" '$1==u && $2!="" {print $2; exit}')"
  if [[ -n "${mp}" ]]; then
    printf '%s\n' "${mp}"
    return 0
  fi
  return 1
}

record_drive_identity() {
  local portable="$1"
  local package="$2"
  require_dir "${portable}" "LumenPortable root"
  local mountpoint source uuid
  if [[ -n "${LUMEN_AUTOPILOT_TEST_MOUNTPOINT:-}" ]]; then
    mountpoint="${LUMEN_AUTOPILOT_TEST_MOUNTPOINT}"
  else
    mountpoint="$(lookup_mountpoint "${portable}")"
  fi
  [[ -n "${mountpoint}" ]] \
    || fail "${EXIT_VERIFY}" "Unable to determine filesystem mountpoint for ${portable}"
  source="$(lookup_source "${portable}" || true)"
  uuid="$(lookup_uuid_for_path "${portable}")" \
    || fail "${EXIT_VERIFY}" \
      "Unable to prove filesystem identity (no UUID/PARTUUID) for ${portable}. Refusing to continue."

  local portable_rel package_rel job_rel=''
  portable_rel="$(relpath_from_mount "${mountpoint}" "${portable}")" \
    || fail "${EXIT_VERIFY}" "LumenPortable is not on mount ${mountpoint}"
  if [[ -n "${package}" && -d "${package}" ]]; then
    package_rel="$(relpath_from_mount "${mountpoint}" "${package}")" \
      || fail "${EXIT_VERIFY}" "Package is not on the same filesystem as LumenPortable"
    local job_root
    job_root="$(cd -P -- "${package}/../.." && pwd)"
    job_rel="$(relpath_from_mount "${mountpoint}" "${job_root}")" || job_rel=''
  fi

  ensure_state_dir
  atomic_write "${DRIVE_PROPS_PATH}" <<EOF
schema_version=1
filesystem_uuid=${uuid}
device_source=${source}
initial_mountpoint=${mountpoint}
lumen_portable_relpath=${portable_rel}
package_relpath=${package_rel:-}
job_relpath=${job_rel}
machine_id=${MACHINE_ID}
core_id=${CORE_ID}
scientific_commit=${EXPECTED_COMMIT}
EOF
  log "Recorded drive identity uuid=${uuid} mount=${mountpoint}"
}

discover_adata_from_drive_props() {
  [[ -f "${DRIVE_PROPS_PATH}" ]] || return 1
  local uuid portable_rel job_rel package_rel
  uuid="$(prop_value "${DRIVE_PROPS_PATH}" "filesystem_uuid")"
  portable_rel="$(prop_value "${DRIVE_PROPS_PATH}" "lumen_portable_relpath")"
  job_rel="$(prop_value "${DRIVE_PROPS_PATH}" "job_relpath" || true)"
  package_rel="$(prop_value "${DRIVE_PROPS_PATH}" "package_relpath" || true)"
  [[ -n "${uuid}" && "${uuid}" != "" ]] || return 1

  local mountpoint
  mountpoint="$(mountpoint_for_uuid "${uuid}")" || return 1
  [[ -d "${mountpoint}" ]] || return 1

  local live_uuid
  live_uuid="$(lookup_uuid_for_path "${mountpoint}" || true)"
  if [[ -n "${live_uuid}" && "${live_uuid}" != "${uuid}" ]]; then
    log "Rejected candidate mount ${mountpoint}: UUID ${live_uuid} != recorded ${uuid}"
    return 1
  fi

  local portable="${mountpoint}/${portable_rel}"
  [[ "${portable_rel}" == "." ]] && portable="${mountpoint}"
  [[ -d "${portable}" ]] || return 1
  [[ -f "${portable}/install/${INSTALLER_NAME}" ]] || return 1

  PORTABLE_ROOT="$(cd -P -- "${portable}" && pwd)"
  if [[ -n "${job_rel}" && -d "${mountpoint}/${job_rel}" ]]; then
    :
  fi
  if [[ -n "${package_rel}" && -d "${mountpoint}/${package_rel}" ]]; then
    PACKAGE_ROOT="$(cd -P -- "${mountpoint}/${package_rel}" && pwd)"
  fi
  return 0
}

verify_portable_checkpoint() {
  local portable="$1"
  require_dir "${portable}/install" "LumenPortable/install"
  local installer="${portable}/install/${INSTALLER_NAME}"
  require_file "${installer}" "install_machine.sh"
  local found
  found="$(extract_expected_commit_from_script "${installer}")" \
    || fail "${EXIT_VERIFY}" "Could not read EXPECTED_COMMIT from ${installer}"
  if [[ "${found}" != "${EXPECTED_COMMIT}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Live LumenPortable scientific checkpoint mismatch: expected ${EXPECTED_COMMIT}, found ${found}"
  fi
  local portable_launcher="${portable}/install/${LAUNCHER_NAME}"
  if [[ -f "${portable_launcher}" ]]; then
    local launcher_commit
    launcher_commit="$(extract_expected_commit_from_script "${portable_launcher}" || true)"
    if [[ -n "${launcher_commit}" && "${launcher_commit}" != "${EXPECTED_COMMIT}" ]]; then
      fail "${EXIT_VERIFY}" \
        "Live portable launcher checkpoint mismatch: expected ${EXPECTED_COMMIT}, found ${launcher_commit}"
    fi
  fi
}

verify_selected_package() {
  local package="$1"
  require_dir "${package}" "machine package"
  require_file "${package}/package.properties" "package.properties"
  require_file "${package}/package_checksums.sha256" "package_checksums.sha256"

  local pkg_machine pkg_commit
  pkg_machine="$(prop_value "${package}/package.properties" "machine_id")" \
    || fail "${EXIT_VERIFY}" "package.properties missing machine_id"
  pkg_commit="$(prop_value "${package}/package.properties" "scientific_checkpoint")" \
    || fail "${EXIT_VERIFY}" "package.properties missing scientific_checkpoint"

  if [[ "${pkg_machine}" != "${MACHINE_ID}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Package machine_id ${pkg_machine} does not match launcher ${MACHINE_ID}"
  fi
  if [[ "${pkg_commit}" != "${EXPECTED_COMMIT}" ]]; then
    fail "${EXIT_VERIFY}" \
      "Package scientific_checkpoint ${pkg_commit} does not match ${EXPECTED_COMMIT}"
  fi

  (
    cd -- "${package}"
    sha256sum -c -- package_checksums.sha256 >/dev/null
  ) || fail "${EXIT_VERIFY}" "package_checksums.sha256 verification failed for ${package}"
}

run_cmd_logged() {
  local label="$1"
  shift
  log "Running ${label}: $(quoted_cmd "$@")"
  "$@"
}

ensure_install() {
  local installer=''
  if [[ -n "${PORTABLE_ROOT}" && -f "${PORTABLE_ROOT}/install/${INSTALLER_NAME}" ]]; then
    installer="${PORTABLE_ROOT}/install/${INSTALLER_NAME}"
  fi

  if ! installation_present; then
    if [[ -e "${INSTALL_ROOT}" ]]; then
      fail "${EXIT_INSTALL}" \
        "Install path exists without installation.properties; refusing to replace it: ${INSTALL_ROOT}"
    fi
    [[ -n "${installer}" ]] \
      || fail "${EXIT_INSTALL}" "LumenRuntime is missing and LumenPortable installer is not available"
    log "Installing runtime for ${MACHINE_ID}"
    if [[ -n "${LUMEN_AUTOPILOT_INSTALL_CMD:-}" ]]; then
      # shellcheck disable=SC2086
      run_cmd_logged "install" ${LUMEN_AUTOPILOT_INSTALL_CMD} "${MACHINE_ID}"
    else
      run_cmd_logged "install" bash -- "${installer}" "${MACHINE_ID}"
    fi
    installation_present \
      || fail "${EXIT_INSTALL}" "Installer did not create ${INSTALL_ROOT}/installation.properties"
    ensure_state_dir
    write_state "INSTALL"
    local pid_tmp="${PID_PATH}.tmp.$$"
    printf '%s\n' "$$" >"${pid_tmp}"
    mv -f -- "${pid_tmp}" "${PID_PATH}"
    return 0
  fi

  [[ -d "${INSTALL_ROOT}" ]] \
    || fail "${EXIT_INSTALL}" "Install path exists but is not a directory: ${INSTALL_ROOT}"

  local existing_commit existing_machine
  existing_commit="$(prop_value "${INSTALL_ROOT}/installation.properties" "production_commit")" \
    || fail "${EXIT_INSTALL}" "Existing installation missing production_commit"
  existing_machine="$(prop_value "${INSTALL_ROOT}/installation.properties" "machine_id")" \
    || fail "${EXIT_INSTALL}" "Existing installation missing machine_id"

  if [[ "${existing_commit}" != "${EXPECTED_COMMIT}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation checkpoint ${existing_commit} conflicts with ${EXPECTED_COMMIT}; refusing to replace it"
  fi
  if [[ "${existing_machine}" != "${MACHINE_ID}" ]]; then
    fail "${EXIT_INSTALL}" \
      "Existing installation is ${existing_machine}, launcher is ${MACHINE_ID}; refusing to replace it"
  fi

  local verify_launcher="${INSTALL_ROOT}/${LAUNCHER_NAME}"
  require_file "${verify_launcher}" "installed run_lumen_machine.sh"
  log "Verifying existing installation"
  if [[ -n "${LUMEN_AUTOPILOT_VERIFY_CMD:-}" ]]; then
    # shellcheck disable=SC2086
    run_cmd_logged "verify-only" ${LUMEN_AUTOPILOT_VERIFY_CMD}
  else
    run_cmd_logged "verify-only" bash -- "${verify_launcher}" --verify-only
  fi
  ensure_state_dir
  write_state "INSTALL"
  local pid_tmp="${PID_PATH}.tmp.$$"
  printf '%s\n' "$$" >"${pid_tmp}"
  mv -f -- "${pid_tmp}" "${PID_PATH}"
}

install_local_autopilot_copy() {
  local dest="${INSTALL_ROOT}/${AUTOPILOT_NAME}"
  local src="${SCRIPT_PATH}"
  if [[ -n "${PORTABLE_ROOT}" && -f "${PORTABLE_ROOT}/install/${AUTOPILOT_NAME}" ]]; then
    src="${PORTABLE_ROOT}/install/${AUTOPILOT_NAME}"
  fi
  require_file "${src}" "${AUTOPILOT_NAME}"
  mkdir -p -- "${INSTALL_ROOT}"
  if [[ "$(resolve_existing "${dest}")" != "$(resolve_existing "${src}")" ]]; then
    cp -f -- "${src}" "${dest}"
  fi
  chmod 755 -- "${dest}"
  bash -n -- "${dest}" \
    || fail "${EXIT_VERIFY}" "Local autopilot failed bash -n: ${dest}"
  write_local_reclick_launcher
}

resolve_existing() {
  local path="$1"
  if [[ -e "${path}" ]]; then
    cd -P "$(dirname -- "${path}")" && printf '%s\n' "$(pwd)/$(basename -- "${path}")"
  else
    printf '%s\n' "${path}"
  fi
}

write_local_reclick_launcher() {
  local dest="${INSTALL_ROOT}/START_${MACHINE_ID}_${CORE_ID}.sh"
  atomic_write "${dest}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
ROOT="\$(cd -P "\$(dirname -- "\${BASH_SOURCE[0]}")" && pwd)"
exec bash -- "\${ROOT}/${AUTOPILOT_NAME}" --machine-id '${MACHINE_ID}' --core-id '${CORE_ID}'
EOF
  chmod 755 -- "${dest}"
}

reexec_from_local_if_needed() {
  local dest="${INSTALL_ROOT}/${AUTOPILOT_NAME}"
  [[ -f "${dest}" ]] || return 0
  local self local
  self="$(resolve_existing "${SCRIPT_PATH}")"
  local="$(resolve_existing "${dest}")"
  if [[ "${self}" == "${local}" ]]; then
    RUNNING_FROM_LOCAL=1
    return 0
  fi
  log "Re-executing local autopilot: ${dest}"
  local -a args=(
    --machine-id "${MACHINE_ID}"
    --core-id "${CORE_ID}"
  )
  [[ -n "${PACKAGE_ROOT}" ]] && args+=(--package-root "${PACKAGE_ROOT}")
  [[ -n "${PORTABLE_ROOT}" ]] && args+=(--portable-root "${PORTABLE_ROOT}")
  [[ -n "${LAUNCHER_PATH}" ]] && args+=(--launcher-path "${LAUNCHER_PATH}")
  [[ "${LOCAL_WAIT_ONLY}" == "1" ]] && args+=(--local-wait)
  exec bash -- "${dest}" "${args[@]}"
}

job_root() {
  printf '%s\n' "${INSTALL_ROOT}/work/${MACHINE_ID}/job"
}

ensure_import() {
  write_state "IMPORT"
  local job
  job="$(job_root)"
  if [[ -f "${job}/import.properties" ]]; then
    local existing_machine existing_commit
    existing_machine="$(prop_value "${job}/import.properties" "machine_id" || true)"
    existing_commit="$(prop_value "${job}/import.properties" "scientific_checkpoint" || true)"
    if [[ "${existing_machine}" == "${MACHINE_ID}" && "${existing_commit}" == "${EXPECTED_COMMIT}" ]]; then
      if [[ -n "${PACKAGE_ROOT}" && -d "${PACKAGE_ROOT}" ]]; then
        log "Package already imported; running import to confirm identity"
      else
        log "Package already imported; ADATA package not present (resume)"
        return 0
      fi
    else
      fail "${EXIT_VERIFY}" \
        "Existing local job conflicts with selected package (machine=${existing_machine:-missing} checkpoint=${existing_commit:-missing})"
    fi
  fi

  if [[ -z "${PACKAGE_ROOT}" || ! -d "${PACKAGE_ROOT}" ]]; then
    fail "${EXIT_VALIDATION}" "Package is not imported locally and ADATA package is not available"
  fi

  local importer=''
  if [[ -n "${PORTABLE_ROOT}" && -f "${PORTABLE_ROOT}/install/${IMPORT_NAME}" ]]; then
    importer="${PORTABLE_ROOT}/install/${IMPORT_NAME}"
  elif [[ -f "${INSTALL_ROOT}/${IMPORT_NAME}" ]]; then
    importer="${INSTALL_ROOT}/${IMPORT_NAME}"
  fi

  if [[ -n "${LUMEN_AUTOPILOT_IMPORT_CMD:-}" ]]; then
    # shellcheck disable=SC2086
    run_cmd_logged "import" ${LUMEN_AUTOPILOT_IMPORT_CMD} "${PACKAGE_ROOT}"
  else
    [[ -n "${importer}" ]] || fail "${EXIT_VALIDATION}" "import_machine_package.sh not found"
    run_cmd_logged "import" bash -- "${importer}" "${PACKAGE_ROOT}"
  fi

  require_file "${job}/import.properties" "import.properties after import"
  require_file "${job}/package_checksums.sha256" "local package_checksums.sha256"
  (
    cd -- "${job}"
    sha256sum -c -- package_checksums.sha256 >/dev/null
  ) || fail "${EXIT_VERIFY}" "Local imported package checksum verification failed"
}

all_prechecks_success() {
  local job manifest
  job="$(job_root)"
  manifest="${job}/batch/machines/${MACHINE_ID}_manifest.csv"
  [[ -f "${manifest}" ]] || return 1
  local header line image_id status result
  IFS= read -r header <"${manifest}" || return 1
  local idx=0 field image_idx=-1
  IFS=',' read -r -a fields <<<"${header}"
  for field in "${fields[@]}"; do
    if [[ "${field}" == "image_id" ]]; then
      image_idx=${idx}
      break
    fi
    idx=$((idx + 1))
  done
  [[ "${image_idx}" -ge 0 ]] || return 1
  local any=0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == "${header}" ]] && continue
    any=1
    IFS=',' read -r -a cols <<<"${line}"
    image_id="${cols[${image_idx}]}"
    [[ -n "${image_id}" ]] || return 1
    result="${job}/batch/precheck/${MACHINE_ID}/jobs/${image_id}/result.properties"
    [[ -f "${result}" ]] || return 1
    status="$(prop_value "${result}" "status" || true)"
    [[ "${status}" == "PRECHECK_SUCCESS" ]] || return 1
  done <"${manifest}"
  [[ "${any}" == "1" ]]
}

ensure_precheck() {
  write_state "PRECHECK"
  if all_prechecks_success; then
    log "All assigned images already PRECHECK_SUCCESS"
    return 0
  fi
  local launcher="${INSTALL_ROOT}/${LAUNCHER_NAME}"
  local rc=0
  set +e
  if [[ -n "${LUMEN_AUTOPILOT_PRECHECK_CMD:-}" ]]; then
    # shellcheck disable=SC2086
    run_cmd_logged "precheck" ${LUMEN_AUTOPILOT_PRECHECK_CMD}
    rc=$?
  else
    require_file "${launcher}" "run_lumen_machine.sh"
    run_cmd_logged "precheck" bash -- "${launcher}" --precheck-only
    rc=$?
  fi
  set -e
  if [[ "${rc}" -ne 0 ]] || ! all_prechecks_success; then
    write_state "AUTOPILOT_FAILED_PRECHECK" "precheck_exit=${rc}"
    echo "LUMEN AUTOPILOT FAILED PRECHECK"
    echo "machine: ${MACHINE_ID}"
    echo "precheck exit: ${rc}"
    echo "log: ${LOG_PATH}"
    echo "scientific processing launched: NO"
    exit "${EXIT_PRECHECK}"
  fi
}

print_safe_to_unplug() {
  sync || true
  write_state "SAFE_TO_UNPLUG"
  local banner
  banner="$(cat <<'EOF'
============================================================
SAFE TO UNPLUG ADATA
PROCESSING CONTINUES ENTIRELY FROM LOCAL STORAGE
============================================================
EOF
)"
  log "${banner}"
  echo "${banner}"
}

progress_file() {
  printf '%s\n' "$(job_root)/batch/machine_supervisor/${MACHINE_ID}/machine_progress.properties"
}

progress_status() {
  local f
  f="$(progress_file)"
  prop_value "${f}" "status" 2>/dev/null || true
}

runner_pid_file() {
  printf '%s\n' "${INSTALL_ROOT}/work/${MACHINE_ID}/control/full_processing/runner.pid"
}

runner_is_alive() {
  local f pid
  f="$(runner_pid_file)"
  [[ -f "${f}" ]] || return 1
  pid="$(tr -d '[:space:]' <"${f}" || true)"
  pid_is_alive "${pid}"
}

machine_success() {
  local f status assigned completed percent failed blocked remaining
  f="$(progress_file)"
  [[ -f "${f}" ]] || return 1
  status="$(prop_value "${f}" "status" || true)"
  [[ "${status}" == "MACHINE_SUCCESS" ]] || return 1
  assigned="$(prop_value "${f}" "assigned_count" || true)"
  completed="$(prop_value "${f}" "completed_count" || true)"
  percent="$(prop_value "${f}" "progress_percent" || true)"
  failed="$(prop_value "${f}" "failed_count" || true)"
  blocked="$(prop_value "${f}" "blocked_count" || true)"
  remaining="$(prop_value "${f}" "remaining_count" || true)"
  [[ "${assigned}" =~ ^[1-9][0-9]*$ ]] || return 1
  [[ "${completed}" == "${assigned}" ]] || return 1
  [[ "${percent}" == "100.0" ]] || return 1
  [[ "${failed}" == "0" && "${blocked}" == "0" && "${remaining}" == "0" ]] || return 1
  return 0
}

processing_failed_closed() {
  local f status assigned completed
  f="$(progress_file)"
  [[ -f "${f}" ]] || return 1
  status="$(prop_value "${f}" "status" || true)"
  case "${status}" in
    MACHINE_FAILED_*)
      return 0
      ;;
  esac
  if runner_is_alive; then
    return 1
  fi
  assigned="$(prop_value "${f}" "assigned_count" || true)"
  completed="$(prop_value "${f}" "completed_count" || true)"
  if [[ "${assigned}" =~ ^[1-9][0-9]*$ && "${completed}" == "${assigned}" && "${status}" != "MACHINE_SUCCESS" ]]; then
    return 0
  fi
  return 1
}

ensure_processing_started() {
  if machine_success; then
    log "MACHINE_SUCCESS already present; skipping processing start"
    return 0
  fi
  if runner_is_alive; then
    log "Detached processing already running"
    return 0
  fi
  write_state "PROCESSING"
  local starter="${INSTALL_ROOT}/${STARTER_NAME}"
  if [[ -n "${LUMEN_AUTOPILOT_START_CMD:-}" ]]; then
    # shellcheck disable=SC2086
    run_cmd_logged "start-processing" ${LUMEN_AUTOPILOT_START_CMD}
  else
    require_file "${starter}" "start_lumen_processing.sh"
    run_cmd_logged "start-processing" bash -- "${starter}"
  fi
}

wait_for_machine_success() {
  write_state "PROCESSING"
  local attempts_env="${LUMEN_AUTOPILOT_PROCESS_WAIT_ATTEMPTS:-0}"
  local n=0
  while true; do
    if machine_success; then
      log "Machine workflow reached MACHINE_SUCCESS"
      return 0
    fi
    if processing_failed_closed; then
      local status
      status="$(progress_status)"
      write_state "AUTOPILOT_FAILED_PROCESSING" "machine_status=${status}"
      echo "LUMEN AUTOPILOT FAILED PROCESSING"
      echo "machine: ${MACHINE_ID}"
      echo "status: ${status}"
      echo "export: NO"
      echo "log: ${LOG_PATH}"
      exit "${EXIT_PROCESSING}"
    fi
    n=$((n + 1))
    if [[ "${attempts_env}" =~ ^[1-9][0-9]*$ && "${n}" -ge "${attempts_env}" ]]; then
      fail "${EXIT_PROCESSING}" "Timed out waiting for MACHINE_SUCCESS"
    fi
    sleep "$(poll_seconds)"
  done
}

job_root_on_adata() {
  [[ -f "${DRIVE_PROPS_PATH}" ]] || return 1
  local uuid job_rel mountpoint
  uuid="$(prop_value "${DRIVE_PROPS_PATH}" "filesystem_uuid")"
  job_rel="$(prop_value "${DRIVE_PROPS_PATH}" "job_relpath")"
  [[ -n "${uuid}" && -n "${job_rel}" ]] || return 1
  mountpoint="$(mountpoint_for_uuid "${uuid}")" || return 1
  local live
  live="$(lookup_uuid_for_path "${mountpoint}" || true)"
  if [[ -n "${live}" && "${live}" != "${uuid}" ]]; then
    return 1
  fi
  local job="${mountpoint}/${job_rel}"
  [[ -d "${job}" ]] || return 1
  printf '%s\n' "$(cd -P -- "${job}" && pwd)"
}

export_results_now() {
  local adata_job dest
  adata_job="$(job_root_on_adata)" \
    || fail "${EXIT_VERIFY}" "Cannot locate production job on rediscovered ADATA"
  if [[ -n "${PORTABLE_ROOT}" ]]; then
    verify_portable_checkpoint "${PORTABLE_ROOT}"
  else
    discover_adata_from_drive_props || true
    [[ -n "${PORTABLE_ROOT}" ]] && verify_portable_checkpoint "${PORTABLE_ROOT}"
  fi
  dest="${adata_job}/completed_results"
  mkdir -p -- "${dest}"
  write_state "EXPORTING" "export_destination=${dest}"
  local launcher="${INSTALL_ROOT}/${LAUNCHER_NAME}"
  if [[ -n "${LUMEN_AUTOPILOT_EXPORT_CMD:-}" ]]; then
    # shellcheck disable=SC2086
    run_cmd_logged "export-results" ${LUMEN_AUTOPILOT_EXPORT_CMD} "${dest}"
  else
    require_file "${launcher}" "run_lumen_machine.sh"
    run_cmd_logged "export-results" bash -- "${launcher}" --export-results "${dest}"
  fi
  write_complete "${dest}"
}

write_complete() {
  local dest="$1"
  local progress ts status
  progress="$(progress_file)"
  status="$(prop_value "${progress}" "status" || true)"
  ts="$(prop_value "${progress}" "timestamp_epoch_ms" || true)"
  local checksum_file="${dest}/${MACHINE_ID}/export_checksums.sha256"
  local export_props="${dest}/${MACHINE_ID}/export.properties"
  local checksum_evidence=''
  if [[ -f "${checksum_file}" ]]; then
    checksum_evidence="export_checksums=${checksum_file}"
  fi
  if [[ -f "${export_props}" ]]; then
    checksum_evidence="${checksum_evidence}"$'\n'"export_properties=${export_props}"
  fi
  local now
  now="$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")"
  atomic_write "${COMPLETE_PATH}" <<EOF
schema_version=1
status=AUTOPILOT_COMPLETE
machine_id=${MACHINE_ID}
core_id=${CORE_ID}
scientific_commit=${EXPECTED_COMMIT}
production_job=$(prop_value "${DRIVE_PROPS_PATH}" "job_relpath" 2>/dev/null || true)
machine_success_status=${status}
machine_success_timestamp_epoch_ms=${ts}
export_destination=${dest}
${checksum_evidence}
completed_epoch_ms=${now}
EOF
  write_state "AUTOPILOT_COMPLETE" "export_destination=${dest}"$'\n'"complete_marker=${COMPLETE_PATH}"
  cat <<'EOF'
============================================================
LUMEN MACHINE COMPLETE
RESULTS EXPORTED AND VERIFIED
============================================================
EOF
  echo "machine: ${MACHINE_ID}"
  echo "scientific_commit: ${EXPECTED_COMMIT}"
  echo "export destination: ${dest}"
  echo "complete marker: ${COMPLETE_PATH}"
  echo "log: ${LOG_PATH}"
}

wait_for_adata_and_export() {
  if discover_adata_from_drive_props && job_root_on_adata >/dev/null; then
    log "ADATA is connected; exporting"
    export_results_now
    return 0
  fi

  write_state "WAITING_FOR_ADATA"
  echo "LUMEN AUTOPILOT WAITING_FOR_ADATA"
  echo "machine: ${MACHINE_ID}"
  echo "log: ${LOG_PATH}"
  echo "Reconnect the same ADATA drive; export will start automatically."

  local attempts_env="${LUMEN_AUTOPILOT_ADATA_WAIT_ATTEMPTS:-0}"
  local n=0
  while true; do
    if discover_adata_from_drive_props && job_root_on_adata >/dev/null; then
      log "Rediscovered ADATA; exporting"
      export_results_now
      return 0
    fi
    n=$((n + 1))
    if [[ "${attempts_env}" =~ ^[1-9][0-9]*$ && "${n}" -ge "${attempts_env}" ]]; then
      log "Still WAITING_FOR_ADATA after ${n} attempts (test/limited wait)"
      return 0
    fi
    sleep "$(adata_poll_seconds)"
  done
}

detach_local_wait() {
  local dest="${INSTALL_ROOT}/${AUTOPILOT_NAME}"
  require_file "${dest}" "local autopilot"
  local runner="${STATE_DIR}/wait_runner.sh"
  atomic_write "${runner}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
export HOME=$(printf '%q' "${HOME}")
cd /tmp 2>/dev/null || cd /
exec bash -- $(printf '%q' "${dest}") \\
  --machine-id $(printf '%q' "${MACHINE_ID}") \\
  --core-id $(printf '%q' "${CORE_ID}") \\
  --local-wait
EOF
  chmod 755 -- "${runner}"
  release_pid_file
  nohup bash -- "${runner}" >>"${LOG_PATH}" 2>&1 &
  local child=$!
  log "Detached local wait/export autopilot PID ${child}"
  echo "Detached local autopilot PID: ${child}"
  echo "log: ${LOG_PATH}"
  echo "Monitor with: ${INSTALL_ROOT}/monitor_lumen_machine.sh"
}

run_local_wait_phases() {
  ensure_processing_started
  wait_for_machine_success
  wait_for_adata_and_export
}

run_bootstrap_phases() {
  if [[ -n "${PORTABLE_ROOT}" ]]; then
    verify_portable_checkpoint "${PORTABLE_ROOT}"
  fi
  if [[ -n "${PACKAGE_ROOT}" ]]; then
    verify_selected_package "${PACKAGE_ROOT}"
  elif [[ ! -f "$(job_root)/import.properties" ]]; then
    fail "${EXIT_VALIDATION}" "No package-root supplied and no local import is present"
  fi

  ensure_install
  ensure_state_dir
  if [[ -n "${PORTABLE_ROOT}" ]]; then
    record_drive_identity "${PORTABLE_ROOT}" "${PACKAGE_ROOT}"
  elif [[ ! -f "${DRIVE_PROPS_PATH}" ]]; then
    fail "${EXIT_VERIFY}" "Drive identity is not recorded and LumenPortable is not available"
  fi

  install_local_autopilot_copy
  reexec_from_local_if_needed

  ensure_import
  ensure_precheck
  print_safe_to_unplug

  if is_foreground || [[ "${LOCAL_WAIT_ONLY}" == "1" ]]; then
    run_local_wait_phases
    return 0
  fi

  detach_local_wait
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run: ./lumen_machine_autopilot.sh ..." >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  require_cmd sha256sum
  require_cmd awk
  require_cmd flock
  require_cmd findmnt
  require_cmd lsblk
  require_cmd bash
  require_cmd cp
  require_cmd mkdir
  require_cmd mv
  require_cmd grep
  require_cmd date
  require_cmd chmod
  require_cmd nohup
  require_cmd sync

  resolve_script_path
  parse_args "$@"
  validate_machine_id
  [[ -n "${CORE_ID}" ]] || fail "${EXIT_USAGE}" "Missing --core-id"
  if [[ -n "${PACKAGE_ROOT}" ]]; then
    PACKAGE_ROOT="$(cd -P -- "${PACKAGE_ROOT}" && pwd)"
  fi
  if [[ -n "${PORTABLE_ROOT}" ]]; then
    PORTABLE_ROOT="$(cd -P -- "${PORTABLE_ROOT}" && pwd)"
  fi

  init_paths
  echo "Autopilot log: ${LOG_PATH}"
  log "Autopilot start machine=${MACHINE_ID} core=${CORE_ID} pid=$$ local_wait=${LOCAL_WAIT_ONLY}"

  if maybe_report_complete; then
    exit 0
  fi

  acquire_instance_lock
  trap release_pid_file EXIT

  if [[ "${LOCAL_WAIT_ONLY}" == "1" ]]; then
    if [[ ! -f "$(job_root)/import.properties" ]]; then
      fail "${EXIT_VALIDATION}" "Local wait requested but no imported job exists"
    fi
    run_local_wait_phases
    exit 0
  fi

  # Re-click with ADATA absent: restore from recorded drive identity.
  if [[ -z "${PORTABLE_ROOT}" && -f "${DRIVE_PROPS_PATH}" ]]; then
    discover_adata_from_drive_props || true
  fi

  local status
  status="$(current_status)"
  if [[ "${status}" == "WAITING_FOR_ADATA" ]] || [[ "${status}" == "SAFE_TO_UNPLUG" ]] \
     || [[ "${status}" == "PROCESSING" ]] || [[ "${status}" == "EXPORTING" ]]; then
    if [[ -f "$(job_root)/import.properties" ]]; then
      log "Resuming from status=${status}"
      if all_prechecks_success; then
        run_local_wait_phases
        exit 0
      fi
    fi
  fi

  run_bootstrap_phases
}

main "$@"
