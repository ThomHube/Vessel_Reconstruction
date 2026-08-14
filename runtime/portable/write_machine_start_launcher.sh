#!/usr/bin/env bash
# Generate a thin machine-specific one-click START launcher.
#
# Usage:
#   ./write_machine_start_launcher.sh \
#     <launchers-dir> <machine_id> <core_id> <package-relpath>
#
# Writes:
#   <launchers-dir>/START_<machine_id>_<core_id>.sh
#   <launchers-dir>/lumen_machine_start.sh   (shared bootstrap, copied)
#
set -euo pipefail

readonly EXIT_USAGE=2
readonly EXIT_VALIDATION=3

fail() {
  local code="$1"
  shift
  echo "ERROR: $*" >&2
  exit "${code}"
}

usage() {
  cat >&2 <<'EOF'
Usage:
  ./write_machine_start_launcher.sh <launchers-dir> <machine_id> <core_id> <package-relpath>
EOF
}

resolve_self_dir() {
  local source="${BASH_SOURCE[0]}"
  local dir
  while [[ -L "${source}" ]]; do
    dir="$(cd -P "$(dirname -- "${source}")" && pwd)"
    source="$(readlink -- "${source}")"
    if [[ "${source}" != /* ]]; then
      source="${dir}/${source}"
    fi
  done
  cd -P "$(dirname -- "${source}")" && pwd
}

main() {
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    echo "ERROR: Do not source this script." >&2
    return "${EXIT_USAGE}" 2>/dev/null || exit "${EXIT_USAGE}"
  fi

  if [[ "$#" -ne 4 ]]; then
    usage
    fail "${EXIT_USAGE}" "Expected 4 arguments"
  fi

  local dest_dir="$1"
  local machine_id="$2"
  local core_id="$3"
  local package_relpath="$4"

  [[ "${machine_id}" =~ ^machine_(0[1-9]|[1-9][0-9])$ ]] \
    || fail "${EXIT_VALIDATION}" "Invalid machine ID '${machine_id}'"
  [[ "${core_id}" =~ ^[0-9]{4}[AB]$ ]] \
    || fail "${EXIT_VALIDATION}" "Invalid core ID '${core_id}' (expected e.g. 0105A)"
  [[ -n "${package_relpath}" ]] \
    || fail "${EXIT_VALIDATION}" "Missing package relative path"
  [[ "${package_relpath}" != /* ]] \
    || fail "${EXIT_VALIDATION}" "package-relpath must be relative, got ${package_relpath}"

  mkdir -p -- "${dest_dir}"
  dest_dir="$(cd -P -- "${dest_dir}" && pwd)"

  local src_dir
  src_dir="$(resolve_self_dir)"
  local common_src="${src_dir}/lumen_machine_start.sh"
  [[ -f "${common_src}" ]] || fail "${EXIT_VALIDATION}" "Missing ${common_src}"

  local common_dest="${dest_dir}/lumen_machine_start.sh"
  cp -f -- "${common_src}" "${common_dest}"
  chmod 755 -- "${common_dest}"
  if ! bash -n -- "${common_dest}"; then
    fail "${EXIT_VALIDATION}" "Copied lumen_machine_start.sh failed bash -n"
  fi

  local launcher_name="START_${machine_id}_${core_id}.sh"
  local launcher_path="${dest_dir}/${launcher_name}"
  local tmp="${launcher_path}.tmp.$$"

  cat >"${tmp}" <<EOF
#!/usr/bin/env bash
# Generated one-click Lumen launcher. Do not edit by hand.
set -euo pipefail

readonly LUMEN_LAUNCHER_MACHINE_ID='${machine_id}'
readonly LUMEN_LAUNCHER_CORE_ID='${core_id}'
readonly LUMEN_LAUNCHER_PACKAGE_RELPATH='${package_relpath}'

SELF="\${BASH_SOURCE[0]}"
while [[ -L "\${SELF}" ]]; do
  DIR="\$(cd -P "\$(dirname -- "\${SELF}")" && pwd)"
  SELF="\$(readlink -- "\${SELF}")"
  if [[ "\${SELF}" != /* ]]; then
    SELF="\${DIR}/\${SELF}"
  fi
done
SELF_DIR="\$(cd -P "\$(dirname -- "\${SELF}")" && pwd)"
SELF="\${SELF_DIR}/\$(basename -- "\${SELF}")"

COMMON="\${SELF_DIR}/lumen_machine_start.sh"
if [[ ! -f "\${COMMON}" ]]; then
  echo "ERROR: Missing shared bootstrap: \${COMMON}" >&2
  exit 3
fi

exec bash -- "\${COMMON}" \\
  --machine-id "\${LUMEN_LAUNCHER_MACHINE_ID}" \\
  --core-id "\${LUMEN_LAUNCHER_CORE_ID}" \\
  --package-relpath "\${LUMEN_LAUNCHER_PACKAGE_RELPATH}" \\
  --from-launcher "\${SELF}"
EOF

  chmod 755 -- "${tmp}"
  if ! bash -n -- "${tmp}"; then
    rm -f -- "${tmp}"
    fail "${EXIT_VALIDATION}" "Generated ${launcher_name} failed bash -n"
  fi
  mv -f -- "${tmp}" "${launcher_path}"
  echo "Wrote ${launcher_path}"
}

main "$@"
