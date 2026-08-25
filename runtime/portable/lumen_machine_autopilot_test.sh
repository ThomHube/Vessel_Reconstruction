#!/usr/bin/env bash
# Disposable integration tests for Issue 7H one-click autopilot.
# Run from the repository; uses mktemp fixtures only. Does not touch ADATA.
#
set -euo pipefail

REPO="$(cd -P "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
PORTABLE="${REPO}/runtime/portable"
PASS=0
FAIL=0
TEST_NAME=''
TEST_RC=0

EXPECTED_COMMIT='6dd42dfc0447255fc7b65d476b5a5048229eba47'
EXPECTED_SHORT='6dd42df'
MACHINE='machine_03'
CORE='0105A'
UUID_A='11111111-aaaa-bbbb-cccc-222222222222'
UUID_B='99999999-ffff-eeee-dddd-000000000000'

assert_eq() {
  local got="$1"
  local want="$2"
  local label="$3"
  if [[ "${got}" == "${want}" ]]; then
    return 0
  fi
  echo "ASSERT FAIL [${TEST_NAME}] ${label}: got='${got}' want='${want}'" >&2
  TEST_RC=1
  return 1
}

assert_file() {
  if [[ -f "$1" ]]; then
    return 0
  fi
  echo "ASSERT FAIL [${TEST_NAME}] missing file: $1" >&2
  TEST_RC=1
  return 1
}

assert_contains() {
  local hay="$1"
  local needle="$2"
  local label="$3"
  if grep -Fq -- "${needle}" <<<"${hay}"; then
    return 0
  fi
  echo "ASSERT FAIL [${TEST_NAME}] ${label}: expected to contain '${needle}'" >&2
  echo "----- output -----" >&2
  echo "${hay}" >&2
  TEST_RC=1
  return 1
}

assert_not_contains() {
  local hay="$1"
  local needle="$2"
  local label="$3"
  if grep -Fq -- "${needle}" <<<"${hay}"; then
    echo "ASSERT FAIL [${TEST_NAME}] ${label}: unexpectedly contained '${needle}'" >&2
    echo "----- output -----" >&2
    echo "${hay}" >&2
    TEST_RC=1
    return 1
  fi
  return 0
}

pass() {
  PASS=$((PASS + 1))
  echo "PASS ${TEST_NAME}"
}

fail_test() {
  FAIL=$((FAIL + 1))
  echo "FAIL ${TEST_NAME}: $*" >&2
}

run_test() {
  TEST_NAME="$1"
  TEST_RC=0
  shift
  set +e
  "$@"
  local rc=$?
  set -e
  if [[ "${rc}" -ne 0 ]]; then
    TEST_RC=1
  fi
  if [[ "${TEST_RC}" -eq 0 ]]; then
    pass
  else
    fail_test "assertions or command failed (rc=${rc})"
  fi
}

write_executable() {
  local path="$1"
  cat >"${path}"
  chmod 755 -- "${path}"
}

setup_fixture() {
  WORK="$(mktemp -d /tmp/lumen-7h-XXXXXX)"
  export HOME="${WORK}/home"
  unset STUB_PRECHECK_FAIL STUB_MACHINE_STATUS STUB_START_SLEEP
  export STUB_PRECHECK_FAIL=0
  mkdir -p -- "${HOME}" "${WORK}/counts" "${WORK}/bin"
  : >"${WORK}/counts/install"
  : >"${WORK}/counts/verify"
  : >"${WORK}/counts/import"
  : >"${WORK}/counts/precheck"
  : >"${WORK}/counts/start"
  : >"${WORK}/counts/export"

  ADATA="${WORK}/adata"
  JOB="${ADATA}/LumenProductionJobs/production_${EXPECTED_SHORT}_test"
  PKG="${JOB}/machine_packages/${MACHINE}"
  mkdir -p -- \
    "${ADATA}/LumenPortable/install" \
    "${PKG}/input/${CORE}" \
    "${JOB}/launchers"

  printf 'dummy-tiff\n' >"${PKG}/input/${CORE}/${CORE}1.tif"
  cat >"${PKG}/package.properties" <<EOF
package.schema_version=2
scientific_checkpoint=${EXPECTED_COMMIT}
scientific_checkpoint_short=${EXPECTED_SHORT}
machine_id=${MACHINE}
assigned_tiff_count=1
EOF
  (
    cd -- "${PKG}"
    sha256sum -- "package.properties" "input/${CORE}/${CORE}1.tif" >package_checksums.sha256
  )

  cp -f -- "${PORTABLE}/lumen_machine_autopilot.sh" "${ADATA}/LumenPortable/install/"
  cp -f -- "${PORTABLE}/lumen_machine_start.sh" "${ADATA}/LumenPortable/install/"
  chmod 755 -- "${ADATA}/LumenPortable/install/"*.sh

  write_executable "${ADATA}/LumenPortable/install/install_machine.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
readonly EXPECTED_COMMIT='${EXPECTED_COMMIT}'
readonly EXPECTED_COMMIT_SHORT='${EXPECTED_SHORT}'
echo "stub installer \${1}"
EOF

  bash -- "${PORTABLE}/write_machine_start_launcher.sh" \
    "${JOB}/launchers" "${MACHINE}" "${CORE}" "../machine_packages/${MACHINE}"

  write_executable "${WORK}/bin/stub_install" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/install"
mid="\${1}"
root="\${HOME}/LumenRuntime/${EXPECTED_SHORT}"
mkdir -p -- "\${root}/work/\${mid}"/{input,batch,output} "\${root}/work/\${mid}/control"
cat >"\${root}/installation.properties" <<PROP
schema_version=1
production_commit=${EXPECTED_COMMIT}
production_commit_short=${EXPECTED_SHORT}
machine_id=\${mid}
installation_root=\${root}
work_root=\${root}/work/\${mid}
launcher_path=\${root}/run_lumen_machine.sh
starter_path=\${root}/start_lumen_processing.sh
PROP
touch -- "\${root}/run_lumen_machine.sh" "\${root}/start_lumen_processing.sh"
chmod 755 -- "\${root}/run_lumen_machine.sh" "\${root}/start_lumen_processing.sh"
echo "STUB INSTALL \${mid}"
EOF

  write_executable "${WORK}/bin/stub_verify" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/verify"
echo "STUB VERIFY"
EOF

  write_executable "${WORK}/bin/stub_import" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/import"
pkg="\${1}"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/input/${CORE}" "\${job}/batch/machines" "\${job}/batch/precheck/${MACHINE}/jobs"
cp -a -- "\${pkg}/package.properties" "\${job}/"
cp -a -- "\${pkg}/package_checksums.sha256" "\${job}/"
cp -a -- "\${pkg}/input/." "\${job}/input/"
cat >"\${job}/import.properties" <<PROP
import.schema_version=2
scientific_checkpoint=${EXPECTED_COMMIT}
machine_id=${MACHINE}
assigned_tiff_count=1
PROP
printf '%s\\n' 'image_id' '${CORE}1' >"\${job}/batch/machines/${MACHINE}_manifest.csv"
(
  cd -- "\${job}"
  sha256sum -- package.properties \$(find input -type f | LC_ALL=C sort) >package_checksums.sha256
)
echo "STUB IMPORT"
EOF

  write_executable "${WORK}/bin/stub_precheck" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/precheck"
if [[ "\${STUB_PRECHECK_FAIL:-0}" == "1" ]]; then
  echo "STUB PRECHECK FAIL"
  exit 1
fi
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/batch/precheck/${MACHINE}/jobs/${CORE}1"
cat >"\${job}/batch/precheck/${MACHINE}/jobs/${CORE}1/result.properties" <<PROP
status=PRECHECK_SUCCESS
image_id=${CORE}1
machine_id=${MACHINE}
PROP
echo "STUB PRECHECK OK"
EOF

  write_executable "${WORK}/bin/stub_start" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/start"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
progress_dir="\${job}/batch/machine_supervisor/${MACHINE}"
mkdir -p -- "\${progress_dir}"
status="\${STUB_MACHINE_STATUS:-MACHINE_SUCCESS}"
cat >"\${progress_dir}/machine_progress.properties" <<PROP
status=\${status}
machine_id=${MACHINE}
assigned_count=1
completed_count=1
progress_percent=100.0
failed_count=0
blocked_count=0
remaining_count=0
timestamp_epoch_ms=1
PROP
if [[ -n "\${STUB_START_SLEEP:-}" ]]; then
  sleep "\${STUB_START_SLEEP}"
fi
echo "STUB START \${status}"
EOF

  write_executable "${WORK}/bin/stub_export" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/export"
dest="\${1}"
mkdir -p -- "\${dest}/${MACHINE}"
echo "exported" >"\${dest}/${MACHINE}/export.properties"
echo "dummy  export.properties" >"\${dest}/${MACHINE}/export_checksums.sha256"
printf '%s\\n' "\${dest}" >"${WORK}/counts/export_dest"
echo "STUB EXPORT \${dest}"
EOF

  export PATH="${WORK}/bin:${PATH}"
  export LUMEN_AUTOPILOT_FOREGROUND=1
  export LUMEN_AUTOPILOT_POLL_SECONDS=0.05
  export LUMEN_AUTOPILOT_ADATA_POLL_SECONDS=0.05
  export LUMEN_AUTOPILOT_ADATA_WAIT_ATTEMPTS=1
  export LUMEN_AUTOPILOT_PROCESS_WAIT_ATTEMPTS=40
  export LUMEN_AUTOPILOT_INSTALL_CMD="${WORK}/bin/stub_install"
  export LUMEN_AUTOPILOT_VERIFY_CMD="${WORK}/bin/stub_verify"
  export LUMEN_AUTOPILOT_IMPORT_CMD="${WORK}/bin/stub_import"
  export LUMEN_AUTOPILOT_PRECHECK_CMD="${WORK}/bin/stub_precheck"
  export LUMEN_AUTOPILOT_START_CMD="${WORK}/bin/stub_start"
  export LUMEN_AUTOPILOT_EXPORT_CMD="${WORK}/bin/stub_export"
  export LUMEN_AUTOPILOT_TEST_UUID="${UUID_A}"
  export LUMEN_AUTOPILOT_TEST_MOUNTPOINT="${ADATA}"

  AUTOPILOT="${PORTABLE}/lumen_machine_autopilot.sh"
  START="${JOB}/launchers/START_${MACHINE}_${CORE}.sh"
  STATE="${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/autopilot"
}

count_of() {
  wc -l <"${WORK}/counts/$1" | tr -d ' '
}

run_autopilot() {
  bash -- "${AUTOPILOT}" \
    --machine-id "${MACHINE}" \
    --core-id "${CORE}" \
    --package-root "${PKG}" \
    --portable-root "${ADATA}/LumenPortable" \
    --launcher-path "${START}"
}

run_start_launcher() {
  bash -- "${START}"
}

cleanup_fixture() {
  if [[ -n "${WORK:-}" && -d "${WORK}" ]]; then
    chmod -R u+w -- "${WORK}" 2>/dev/null || true
    rm -rf -- "${WORK}"
  fi
}

# --- tests ---

test_01_fresh_install() {
  setup_fixture
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  assert_eq "${rc}" "0" "exit"
  assert_eq "$(count_of install)" "1" "install once"
  assert_file "${HOME}/LumenRuntime/${EXPECTED_SHORT}/installation.properties"
  assert_file "${STATE}/AUTOPILOT_COMPLETE"
  assert_contains "${out}" "SAFE TO UNPLUG ADATA" "unplug banner"
  assert_contains "${out}" "LUMEN MACHINE COMPLETE" "complete banner"
  cleanup_fixture
}

test_02_existing_correct_install() {
  setup_fixture
  bash -- "${WORK}/bin/stub_install" "${MACHINE}"
  : >"${WORK}/counts/install"
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  assert_eq "${rc}" "0" "exit"
  assert_eq "$(count_of install)" "0" "no reinstall"
  local verify_n
  verify_n="$(count_of verify)"
  [[ "${verify_n}" -ge 1 ]] || { echo "verify not called"; return 1; }
  assert_file "${STATE}/AUTOPILOT_COMPLETE"
  cleanup_fixture
}

test_03_conflicting_install_fails() {
  setup_fixture
  bash -- "${WORK}/bin/stub_install" machine_01
  : >"${WORK}/counts/install"
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected failure"; return 1; }
  assert_contains "${out}" "Existing installation is machine_01" "conflict message"
  assert_eq "$(count_of import)" "0" "no import"
  assert_eq "$(count_of start)" "0" "no processing"
  cleanup_fixture
}

test_04_package_checkpoint_mismatch() {
  setup_fixture
  sed -i 's/^scientific_checkpoint=.*/scientific_checkpoint=deadbeefdeadbeefdeadbeefdeadbeefdeadbeef/' \
    "${PKG}/package.properties"
  (
    cd -- "${PKG}"
    sha256sum -- package.properties "input/${CORE}/${CORE}1.tif" >package_checksums.sha256
  )
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected failure"; return 1; }
  assert_contains "${out}" "scientific_checkpoint" "checkpoint mismatch"
  assert_eq "$(count_of install)" "0" "no install"
  cleanup_fixture
}

test_05_package_machine_mismatch() {
  setup_fixture
  sed -i 's/^machine_id=.*/machine_id=machine_01/' "${PKG}/package.properties"
  (
    cd -- "${PKG}"
    sha256sum -- package.properties "input/${CORE}/${CORE}1.tif" >package_checksums.sha256
  )
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected failure"; return 1; }
  assert_contains "${out}" "does not match launcher" "machine mismatch"
  assert_eq "$(count_of import)" "0" "no import"
  cleanup_fixture
}

test_06_successful_local_import() {
  setup_fixture
  local rc=0
  run_autopilot >/dev/null || rc=$?
  assert_eq "${rc}" "0" "exit"
  assert_eq "$(count_of import)" "1" "import once"
  assert_file "${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job/import.properties"
  assert_file "${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job/input/${CORE}/${CORE}1.tif"
  cleanup_fixture
}

test_07_already_imported_resume() {
  setup_fixture
  bash -- "${WORK}/bin/stub_install" "${MACHINE}"
  bash -- "${WORK}/bin/stub_import" "${PKG}"
  : >"${WORK}/counts/install"
  : >"${WORK}/counts/import"
  local rc=0
  run_autopilot >/dev/null || rc=$?
  assert_eq "${rc}" "0" "exit"
  assert_eq "$(count_of install)" "0" "no reinstall"
  # Import may be invoked once more; the real importer is idempotent. Local job
  # must remain the matching package and processing must still complete.
  assert_file "${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job/import.properties"
  assert_file "${STATE}/AUTOPILOT_COMPLETE"
  cleanup_fixture
}

test_08_precheck_failure_blocks() {
  setup_fixture
  export STUB_PRECHECK_FAIL=1
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected precheck failure"; return 1; }
  assert_contains "${out}" "AUTOPILOT FAILED PRECHECK" "precheck banner"
  assert_eq "$(count_of start)" "0" "processing not started"
  assert_eq "$(count_of export)" "0" "no export"
  local status
  status="$(grep '^status=' "${STATE}/autopilot.properties" | head -n1 | cut -d= -f2-)"
  assert_eq "${status}" "AUTOPILOT_FAILED_PRECHECK" "state"
  cleanup_fixture
}

test_09_precheck_success_permits_processing() {
  setup_fixture
  local rc=0
  run_autopilot >/dev/null || rc=$?
  assert_eq "${rc}" "0" "exit"
  local pre start
  pre="$(count_of precheck)"
  start="$(count_of start)"
  [[ "${pre}" -ge 1 ]] || { echo "precheck not run"; return 1; }
  [[ "${start}" -ge 1 ]] || { echo "processing not started"; return 1; }
  cleanup_fixture
}

test_10_duplicate_click_no_duplicate_processing() {
  setup_fixture
  export STUB_START_SLEEP=2
  export STUB_MACHINE_STATUS=RUNNING_FAKE
  # Keep wait loop alive: incomplete progress so it does not fail-closed immediately.
  write_executable "${WORK}/bin/stub_start" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/start"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/batch/machine_supervisor/${MACHINE}"
cat >"\${job}/batch/machine_supervisor/${MACHINE}/machine_progress.properties" <<PROP
status=RUNNING
machine_id=${MACHINE}
assigned_count=1
completed_count=0
progress_percent=0.0
failed_count=0
blocked_count=0
remaining_count=1
timestamp_epoch_ms=1
PROP
sleep 3
echo "STUB START HOLD"
EOF
  export LUMEN_AUTOPILOT_PROCESS_WAIT_ATTEMPTS=80
  run_autopilot >/tmp/lumen-7h-dup-first.log 2>&1 &
  local first=$!
  local i
  for i in $(seq 1 80); do
    if [[ "$(count_of start)" -ge 1 ]]; then
      break
    fi
    sleep 0.05
  done
  [[ "$(count_of start)" -ge 1 ]] || { kill "${first}" 2>/dev/null || true; echo "first start never ran"; return 1; }
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  assert_eq "${rc}" "0" "duplicate exit 0"
  assert_contains "${out}" "LUMEN AUTOPILOT RUNNING" "running banner"
  assert_eq "$(count_of start)" "1" "single start"
  kill "${first}" 2>/dev/null || true
  wait "${first}" 2>/dev/null || true
  cleanup_fixture
}

test_11_machine_failed_no_export() {
  setup_fixture
  export STUB_MACHINE_STATUS=MACHINE_FAILED_UNEXPECTED
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected processing failure"; return 1; }
  assert_contains "${out}" "AUTOPILOT FAILED PROCESSING" "failed banner"
  assert_contains "${out}" "export: NO" "no export note"
  assert_eq "$(count_of export)" "0" "export not called"
  cleanup_fixture
}

test_12_counts_complete_without_machine_success_no_export() {
  setup_fixture
  export STUB_MACHINE_STATUS=MACHINE_COMPLETED_WITH_BLOCKED_IMAGES
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected closed failure"; return 1; }
  assert_eq "$(count_of export)" "0" "export not called"
  assert_contains "${out}" "export: NO" "no export"
  cleanup_fixture
}

test_13_machine_success_adata_connected_exports() {
  setup_fixture
  local rc=0
  run_autopilot >/dev/null || rc=$?
  assert_eq "${rc}" "0" "exit"
  assert_eq "$(count_of export)" "1" "export once"
  local dest
  dest="$(cat "${WORK}/counts/export_dest")"
  assert_eq "${dest}" "${JOB}/completed_results" "export dest"
  cleanup_fixture
}

test_14_waiting_for_adata() {
  setup_fixture
  export LUMEN_AUTOPILOT_TEST_MOUNTPOINT="${WORK}/missing-adata"
  # Still need ADATA during bootstrap. Keep real ADATA for install/import, then
  # hide it after MACHINE_SUCCESS by pointing mount lookup at a missing dir.
  # Implement by wrapping export discovery: record against ADATA, then change
  # TEST_MOUNTPOINT via a start stub that rewrites the env file... Use two-step:
  # first, run with ADATA until we can swap. Simpler: start stub unsets visibility
  # by rewriting drive lookup file.
  write_executable "${WORK}/bin/stub_start" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/start"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/batch/machine_supervisor/${MACHINE}"
cat >"\${job}/batch/machine_supervisor/${MACHINE}/machine_progress.properties" <<PROP
status=MACHINE_SUCCESS
machine_id=${MACHINE}
assigned_count=1
completed_count=1
progress_percent=100.0
failed_count=0
blocked_count=0
remaining_count=0
timestamp_epoch_ms=1
PROP
# Hide the drive for subsequent UUID lookup by pointing TEST_MOUNTPOINT at a hole.
printf '%s' "${WORK}/missing-adata" >"${WORK}/counts/hide_mount"
echo "STUB START SUCCESS THEN HIDE"
EOF
  # The autopilot reads TEST_MOUNTPOINT from env, not a file. Recreate the
  # fixture using a mountpoint that exists for bootstrap then is renamed.
  cleanup_fixture
  setup_fixture
  write_executable "${WORK}/bin/stub_start" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/start"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/batch/machine_supervisor/${MACHINE}"
cat >"\${job}/batch/machine_supervisor/${MACHINE}/machine_progress.properties" <<PROP
status=MACHINE_SUCCESS
machine_id=${MACHINE}
assigned_count=1
completed_count=1
progress_percent=100.0
failed_count=0
blocked_count=0
remaining_count=0
timestamp_epoch_ms=1
PROP
mv -- "${ADATA}" "${WORK}/adata.unplugged"
echo "STUB START UNPLUG"
EOF
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  assert_eq "${rc}" "0" "waiting is success"
  assert_contains "${out}" "WAITING_FOR_ADATA" "waiting banner"
  assert_eq "$(count_of export)" "0" "no export"
  local status
  status="$(grep '^status=' "${STATE}/autopilot.properties" | head -n1 | cut -d= -f2-)"
  assert_eq "${status}" "WAITING_FOR_ADATA" "state"
  cleanup_fixture
}

test_15_rediscover_same_uuid() {
  setup_fixture
  write_executable "${WORK}/bin/stub_start" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/start"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/batch/machine_supervisor/${MACHINE}"
cat >"\${job}/batch/machine_supervisor/${MACHINE}/machine_progress.properties" <<PROP
status=MACHINE_SUCCESS
machine_id=${MACHINE}
assigned_count=1
completed_count=1
progress_percent=100.0
failed_count=0
blocked_count=0
remaining_count=0
timestamp_epoch_ms=1
PROP
mv -- "${ADATA}" "${WORK}/adata.unplugged"
echo "STUB START UNPLUG"
EOF
  local rc=0
  run_autopilot >/dev/null || rc=$?
  assert_eq "${rc}" "0" "first wait"
  assert_eq "$(count_of export)" "0" "no export yet"

  local replug="${WORK}/replug"
  mkdir -p -- "${replug}"
  mv -- "${WORK}/adata.unplugged" "${replug}/vol"
  # Same relative layout under a new mountpoint, same UUID.
  export LUMEN_AUTOPILOT_TEST_MOUNTPOINT="${replug}/vol"
  export LUMEN_AUTOPILOT_TEST_UUID="${UUID_A}"
  : >"${WORK}/counts/start"
  local out rc=0
  out="$(
    bash -- "${HOME}/LumenRuntime/${EXPECTED_SHORT}/lumen_machine_autopilot.sh" \
      --machine-id "${MACHINE}" --core-id "${CORE}" 2>&1
  )" || rc=$?
  assert_eq "${rc}" "0" "rediscover exit"
  assert_contains "${out}" "LUMEN MACHINE COMPLETE" "complete after replug"
  assert_eq "$(count_of export)" "1" "export after rediscovery"
  local dest
  dest="$(cat "${WORK}/counts/export_dest")"
  assert_eq "${dest}" "${replug}/vol/LumenProductionJobs/production_${EXPECTED_SHORT}_test/completed_results" \
    "export on new mount"
  cleanup_fixture
}

test_16_different_uuid_rejected() {
  setup_fixture
  write_executable "${WORK}/bin/stub_start" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/start"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/batch/machine_supervisor/${MACHINE}"
cat >"\${job}/batch/machine_supervisor/${MACHINE}/machine_progress.properties" <<PROP
status=MACHINE_SUCCESS
machine_id=${MACHINE}
assigned_count=1
completed_count=1
progress_percent=100.0
failed_count=0
blocked_count=0
remaining_count=0
timestamp_epoch_ms=1
PROP
mv -- "${ADATA}" "${WORK}/adata.unplugged"
echo "STUB START UNPLUG"
EOF
  run_autopilot >/dev/null
  local other="${WORK}/otherdrive"
  mkdir -p -- "${other}/LumenPortable/install" \
    "${other}/LumenProductionJobs/production_${EXPECTED_SHORT}_test/machine_packages/${MACHINE}"
  echo 'readonly EXPECTED_COMMIT='"'"${EXPECTED_COMMIT}"'" >"${other}/LumenPortable/install/install_machine.sh"
  mv -- "${WORK}/adata.unplugged/LumenProductionJobs" "${other}/LumenProductionJobs" 2>/dev/null || true
  mkdir -p -- "${other}/LumenProductionJobs/production_${EXPECTED_SHORT}_test"
  export LUMEN_AUTOPILOT_TEST_MOUNTPOINT="${other}"
  export LUMEN_AUTOPILOT_TEST_UUID="${UUID_B}"
  local out rc=0
  out="$(
    bash -- "${HOME}/LumenRuntime/${EXPECTED_SHORT}/lumen_machine_autopilot.sh" \
      --machine-id "${MACHINE}" --core-id "${CORE}" 2>&1
  )" || rc=$?
  assert_eq "${rc}" "0" "wait remains success"
  assert_contains "${out}" "WAITING_FOR_ADATA" "still waiting"
  assert_eq "$(count_of export)" "0" "foreign UUID not exported"
  cleanup_fixture
}

test_17_export_writes_complete() {
  setup_fixture
  run_autopilot >/dev/null
  assert_file "${STATE}/AUTOPILOT_COMPLETE"
  grep -q '^status=AUTOPILOT_COMPLETE' "${STATE}/AUTOPILOT_COMPLETE"
  grep -q "^machine_id=${MACHINE}" "${STATE}/AUTOPILOT_COMPLETE"
  grep -q "^scientific_commit=${EXPECTED_COMMIT}" "${STATE}/AUTOPILOT_COMPLETE"
  grep -q '^export_destination=' "${STATE}/AUTOPILOT_COMPLETE"
  grep -q 'export_checksums=' "${STATE}/AUTOPILOT_COMPLETE"
  cleanup_fixture
}

test_18_reclick_already_complete() {
  setup_fixture
  run_autopilot >/dev/null
  local out rc=0
  out="$(run_autopilot 2>&1)" || rc=$?
  assert_eq "${rc}" "0" "exit"
  assert_contains "${out}" "ALREADY COMPLETE" "already complete"
  assert_eq "$(count_of export)" "1" "export not repeated"
  assert_eq "$(count_of start)" "1" "start not repeated"
  cleanup_fixture
}

test_19_no_adata_tiff_io_after_import() {
  setup_fixture
  write_executable "${WORK}/bin/stub_import" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/import"
pkg="\${1}"
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
mkdir -p -- "\${job}/input/${CORE}" "\${job}/batch/machines" "\${job}/batch/precheck/${MACHINE}/jobs"
cp -a -- "\${pkg}/package.properties" "\${job}/"
cp -a -- "\${pkg}/input/." "\${job}/input/"
cat >"\${job}/import.properties" <<PROP
import.schema_version=2
scientific_checkpoint=${EXPECTED_COMMIT}
machine_id=${MACHINE}
assigned_tiff_count=1
PROP
printf '%s\\n' 'image_id' '${CORE}1' >"\${job}/batch/machines/${MACHINE}_manifest.csv"
(
  cd -- "\${job}"
  sha256sum -- package.properties \$(find input -type f | LC_ALL=C sort) >package_checksums.sha256
)
# Remove scientific TIFFs from ADATA after the local copy exists.
rm -f -- "\${pkg}/input/${CORE}/${CORE}1.tif"
echo "STUB IMPORT THEN STRIP ADATA TIFFS"
EOF
  write_executable "${WORK}/bin/stub_start" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo 1 >>"${WORK}/counts/start"
for arg in "\$@"; do
  if [[ "\${arg}" == *"/machine_packages/"*"/input/"* ]]; then
    echo "ERROR: start received ADATA TIFF path: \${arg}" >&2
    exit 1
  fi
done
if [[ -r "${PKG}/input/${CORE}/${CORE}1.tif" ]]; then
  echo "ERROR: ADATA TIFF still readable during start" >&2
  exit 1
fi
job="\${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job"
[[ -f "\${job}/input/${CORE}/${CORE}1.tif" ]] || { echo "local TIFF missing"; exit 1; }
mkdir -p -- "\${job}/batch/machine_supervisor/${MACHINE}"
cat >"\${job}/batch/machine_supervisor/${MACHINE}/machine_progress.properties" <<PROP
status=MACHINE_SUCCESS
machine_id=${MACHINE}
assigned_count=1
completed_count=1
progress_percent=100.0
failed_count=0
blocked_count=0
remaining_count=0
timestamp_epoch_ms=1
PROP
echo "STUB START LOCAL ONLY"
EOF
  local rc=0
  run_autopilot >/dev/null || rc=$?
  assert_eq "${rc}" "0" "exit"
  [[ ! -e "${PKG}/input/${CORE}/${CORE}1.tif" ]] || { echo "ADATA TIFF should be gone"; return 1; }
  assert_file "${HOME}/LumenRuntime/${EXPECTED_SHORT}/work/${MACHINE}/job/input/${CORE}/${CORE}1.tif"
  assert_file "${STATE}/AUTOPILOT_COMPLETE"
  cleanup_fixture
}

test_20_bash_n_scripts() {
  local s
  for s in \
    "${PORTABLE}/lumen_machine_autopilot.sh" \
    "${PORTABLE}/lumen_machine_start.sh" \
    "${PORTABLE}/write_machine_start_launcher.sh" \
    "${PORTABLE}/create_machine_packages.sh" \
    "${PORTABLE}/install_machine.sh" \
    "${PORTABLE}/import_machine_package.sh" \
    "${PORTABLE}/run_lumen_machine.sh" \
    "${PORTABLE}/start_lumen_processing.sh" \
    "${PORTABLE}/monitor_lumen_machine.sh" \
    "${PORTABLE}/prepare_master_batch.sh" \
    "${PORTABLE}/single_machine_production_test.sh"
  do
    bash -n "${s}"
  done
  setup_fixture
  bash -n "${START}"
  bash -n "${JOB}/launchers/lumen_machine_start.sh"
  grep -q "LUMEN_LAUNCHER_MACHINE_ID='${MACHINE}'" "${START}"
  grep -q "LUMEN_LAUNCHER_CORE_ID='${CORE}'" "${START}"
  grep -q "LUMEN_LAUNCHER_PACKAGE_RELPATH='../machine_packages/${MACHINE}'" "${START}"
  cleanup_fixture
}

main() {
  echo "Issue 7H disposable autopilot tests"
  echo "repo: ${REPO}"
  run_test "01_fresh_install" test_01_fresh_install
  run_test "02_existing_correct_install" test_02_existing_correct_install
  run_test "03_conflicting_install_fails" test_03_conflicting_install_fails
  run_test "04_package_checkpoint_mismatch" test_04_package_checkpoint_mismatch
  run_test "05_package_machine_mismatch" test_05_package_machine_mismatch
  run_test "06_successful_local_import" test_06_successful_local_import
  run_test "07_already_imported_resume" test_07_already_imported_resume
  run_test "08_precheck_failure_blocks" test_08_precheck_failure_blocks
  run_test "09_precheck_success_permits_processing" test_09_precheck_success_permits_processing
  run_test "10_duplicate_click_no_duplicate_processing" test_10_duplicate_click_no_duplicate_processing
  run_test "11_machine_failed_no_export" test_11_machine_failed_no_export
  run_test "12_counts_complete_without_machine_success_no_export" test_12_counts_complete_without_machine_success_no_export
  run_test "13_machine_success_adata_connected_exports" test_13_machine_success_adata_connected_exports
  run_test "14_waiting_for_adata" test_14_waiting_for_adata
  run_test "15_rediscover_same_uuid" test_15_rediscover_same_uuid
  run_test "16_different_uuid_rejected" test_16_different_uuid_rejected
  run_test "17_export_writes_complete" test_17_export_writes_complete
  run_test "18_reclick_already_complete" test_18_reclick_already_complete
  run_test "19_no_adata_tiff_io_after_import" test_19_no_adata_tiff_io_after_import
  run_test "20_bash_n_scripts" test_20_bash_n_scripts
  echo
  echo "passed=${PASS} failed=${FAIL} total=$((PASS + FAIL))"
  [[ "${FAIL}" -eq 0 ]]
}

main "$@"
