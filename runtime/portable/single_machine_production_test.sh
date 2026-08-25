#!/usr/bin/env bash
# Disposable tests for the single-analysis-host production path.
# Does not touch ADATA or LumenRuntime.
#
set -euo pipefail

REPO="$(cd -P "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
PORTABLE="${REPO}/runtime/portable"
PASS=0
FAIL=0
TEST_NAME=''
TEST_RC=0

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

test_bash_n() {
  local s
  for s in \
    "${PORTABLE}/prepare_master_batch.sh" \
    "${PORTABLE}/create_machine_packages.sh" \
    "${PORTABLE}/start_lumen_processing.sh" \
    "${PORTABLE}/monitor_lumen_machine.sh" \
    "${PORTABLE}/write_machine_start_launcher.sh" \
    "${PORTABLE}/run_hardware_benchmark.sh"
  do
    bash -n "${s}"
  done
}

test_prepare_rejects_multi_machine_count() {
  local out rc
  rc=0
  out="$(bash -- "${PORTABLE}/prepare_master_batch.sh" /tmp /tmp/lumen-job-should-not-exist 3 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected non-zero exit"; return 1; }
  assert_contains "${out}" "machine-count=1" "reject message"
  assert_contains "${out}" "Received '3'" "received count"
}

test_prepare_rejects_two_hosts() {
  local out rc
  rc=0
  out="$(bash -- "${PORTABLE}/prepare_master_batch.sh" /tmp /tmp/lumen-job-should-not-exist 2 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected non-zero exit"; return 1; }
  assert_contains "${out}" "machine-count=1" "reject message"
}

test_prepare_accepts_two_args_before_runtime() {
  local out rc
  rc=0
  out="$(bash -- "${PORTABLE}/prepare_master_batch.sh" \
    /tmp/lumen-no-such-input-root \
    /tmp/lumen-job-should-not-exist 2>&1)" || rc=$?
  [[ "${rc}" -ne 0 ]] || { echo "expected non-zero exit for missing input-root"; return 1; }
  assert_contains "${out}" "input-root is not a directory" "fails on input, not machine-count"
  if grep -Fq "Received '" <<<"${out}"; then
    echo "two-arg invocation was treated as an invalid machine-count" >&2
    echo "${out}" >&2
    return 1
  fi
}

test_create_packages_rejects_multi_machine_job() {
  local work job out rc
  work="$(mktemp -d /tmp/lumen-single-host-XXXXXX)"
  job="${work}/job"
  mkdir -p -- "${job}/plan/machines"
  cat >"${job}/preparation.properties" <<'EOF'
portable_plan_schema_version=2
manifest_schema_version=2
scientific_checkpoint=6dd42dfc0447255fc7b65d476b5a5048229eba47
scientific_checkpoint_short=6dd42df
machine_count=3
input_root=/tmp
job_root=/tmp/unused
production_config_path=/tmp/unused
production_config_sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
production_config_canonical_sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
production_config_file_sha256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
master_manifest_path=/tmp/unused
valid_tiff_count=1
first_image_id=0101A1
last_image_id=0101A1
preparation_timestamp_epoch_ms=1
EOF
  : >"${job}/production_batch.properties"
  : >"${job}/plan/master_batch_manifest.csv"
  : >"${job}/plan/machine_assignment_summary.txt"
  : >"${job}/plan/batch_plan_report.txt"
  rc=0
  out="$(bash -- "${PORTABLE}/create_machine_packages.sh" "${job}" 2>&1)" || rc=$?
  rm -rf -- "${work}"
  [[ "${rc}" -ne 0 ]] || { echo "expected non-zero exit"; echo "${out}"; return 1; }
  assert_contains "${out}" "machine_count=1" "reject multi-host package creation"
  assert_contains "${out}" "Found 3" "found count"
}

main() {
  echo "Single-machine production portable tests"
  echo "repo: ${REPO}"
  run_test "bash_n" test_bash_n
  run_test "prepare_rejects_count_3" test_prepare_rejects_multi_machine_count
  run_test "prepare_rejects_count_2" test_prepare_rejects_two_hosts
  run_test "prepare_two_args_not_count_error" test_prepare_accepts_two_args_before_runtime
  run_test "packages_reject_count_3" test_create_packages_rejects_multi_machine_job
  echo
  echo "passed: ${PASS}"
  echo "failed: ${FAIL}"
  [[ "${FAIL}" -eq 0 ]]
}

main "$@"
