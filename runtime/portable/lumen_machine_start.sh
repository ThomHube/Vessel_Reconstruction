#!/usr/bin/env bash
# Common one-click bootstrap for generated START_machine_XX_<core>.sh launchers.
#
# Discovers LumenPortable / package paths from the generated launcher location,
# then execs the autopilot. Long-running work is not kept on this script.
#
# Usage (invoked by a generated START_*.sh):
#   ./lumen_machine_start.sh \
#     --machine-id machine_03 \
#     --core-id 0105A \
#     --package-relpath ../machine_packages/machine_03 \
#     --from-launcher /path/to/START_machine_03_0105A.sh
#
set -euo pipefail

readonly EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
readonly EXPECTED_COMMIT_SHORT='6dd42df'
readonly AUTOPILOT_NAME='lumen_machine_autopilot.sh'

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3

MACHINE_ID=''
CORE_ID=''
PACKAGE_RELPATH=''
FROM_LAUNCHER=''

fail() {
  local code="$1"
  shift
  echo "ERROR: $*" >&2
  exit "${code}"
}

usage() {
  cat >&2 <<'EOF'
Usage:
  ./lumen_machine_start.sh --machine-id <machine_XX> --core-id <coreId> \
      --package-relpath <rel> --from-launcher <START_*.sh>
EOF
}

resolve_path() {
  local source="$1"
  local dir
  while [[ -L "${source}" ]]; do
    dir="$(cd -P "$(dirname -- "${source}")" && pwd)"
    source="$(readlink -- "${source}")"
    if [[ "${source}" != /* ]]; then
      source="${dir}/${source}"
    fi
  done
  printf '%s\n' "$(cd -P "$(dirname -- "${source}")" && pwd)/$(basename -- "${source}")"
}

walk_to_portable_root() {
  local start_dir="$1"
  local dir="${start_dir}"
  while true; do
    if [[ -f "${dir}/LumenPortable/install/${AUTOPILOT_NAME}" ]] \
       || [[ -f "${dir}/LumenPortable/install/install_machine.sh" ]]; then
      printf '%s\n' "${dir}/LumenPortable"
      return 0
    fi
    local parent
    parent="$(dirname -- "${dir}")"
    if [[ "${parent}" == "${dir}" ]]; then
      return 1
    fi
    dir="${parent}"
  done
}

parse_args() {
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --machine-id)
        [[ "$#" -ge 2 ]] || fail "${EXIT_USAGE}" "Missing value for --machine-id"
        MACHINE_ID="$2"
        shift 2
        ;;
      --core-id)
        [[ "$#" -ge 2 ]] || fail "${EXIT_USAGE}" "Missing value for --core-id"
        CORE_ID="$2"
        shift 2
        ;;
      --package-relpath)
        [[ "$#" -ge 2 ]] || fail "${EXIT_USAGE}" "Missing value for --package-relpath"
        PACKAGE_RELPATH="$2"
        shift 2
        ;;
      --from-launcher)
        [[ "$#" -ge 2 ]] || fail "${EXIT_USAGE}" "Missing value for --from-launcher"
        FROM_LAUNCHER="$2"
        shift 2
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

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script. Run it as a script." >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  parse_args "$@"

  [[ "${MACHINE_ID}" =~ ^machine_(0[1-9]|[1-9][0-9])$ ]] \
    || fail "${EXIT_USAGE}" "Invalid machine ID '${MACHINE_ID}'"
  [[ -n "${CORE_ID}" ]] || fail "${EXIT_USAGE}" "Missing --core-id"
  [[ -n "${PACKAGE_RELPATH}" ]] || fail "${EXIT_USAGE}" "Missing --package-relpath"
  [[ -n "${FROM_LAUNCHER}" ]] || fail "${EXIT_USAGE}" "Missing --from-launcher"

  local launcher_abs launcher_dir
  launcher_abs="$(resolve_path "${FROM_LAUNCHER}")"
  launcher_dir="$(cd -P "$(dirname -- "${launcher_abs}")" && pwd)"

  local portable_root=''
  portable_root="$(walk_to_portable_root "${launcher_dir}")" || true

  local package_root=''
  local package_candidate="${launcher_dir}/${PACKAGE_RELPATH}"
  if [[ -d "${package_candidate}" ]]; then
    package_root="$(cd -P -- "${package_candidate}" && pwd)"
  fi

  local local_runtime="${HOME}/LumenRuntime/${EXPECTED_COMMIT_SHORT}"
  local local_autopilot="${local_runtime}/${AUTOPILOT_NAME}"
  local portable_autopilot=''
  if [[ -n "${portable_root}" && -f "${portable_root}/install/${AUTOPILOT_NAME}" ]]; then
    portable_autopilot="${portable_root}/install/${AUTOPILOT_NAME}"
  fi

  local -a args=(
    --machine-id "${MACHINE_ID}"
    --core-id "${CORE_ID}"
    --launcher-path "${launcher_abs}"
  )
  if [[ -n "${package_root}" ]]; then
    args+=(--package-root "${package_root}")
  fi
  if [[ -n "${portable_root}" ]]; then
    args+=(--portable-root "${portable_root}")
  fi

  # Prefer the ADATA copy first so a live portable tree can refresh the local
  # autopilot, then the local copy so WAITING_FOR_ADATA re-clicks work unplugged.
  if [[ -n "${portable_autopilot}" ]]; then
    echo "One-click bootstrap: ${MACHINE_ID} (${CORE_ID})"
    echo "Scientific checkpoint: ${EXPECTED_COMMIT_SHORT}"
    exec bash -- "${portable_autopilot}" "${args[@]}"
  fi

  if [[ -x "${local_autopilot}" || -f "${local_autopilot}" ]]; then
    echo "One-click bootstrap (local runtime): ${MACHINE_ID} (${CORE_ID})"
    echo "Scientific checkpoint: ${EXPECTED_COMMIT_SHORT}"
    exec bash -- "${local_autopilot}" "${args[@]}"
  fi

  fail "${EXIT_VALIDATION}" \
    "Could not find ${AUTOPILOT_NAME}. Plug in the Lumen ADATA drive or install ${local_runtime}."
}

main "$@"
