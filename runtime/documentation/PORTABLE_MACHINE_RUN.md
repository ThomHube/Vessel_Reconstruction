# Portable Machine Run

`run_lumen_machine.sh` is the user-local launcher for an installed
`LumenRuntime/6dd42df` tree. It discovers the installation root from its own
path and uses only the portable JDK, Maven, Fiji, classifier, and config that
`install_machine.sh` placed under that root.

## Supported modes

```bash
./run_lumen_machine.sh --verify-only
./run_lumen_machine.sh --precheck-only
./run_lumen_machine.sh --full-only <tile-profile-index>
./run_lumen_machine.sh --export-results <completed-results-root>
```

### `--verify-only`

Validates/prepares the runtime and, if needed, compiles controller classes
offline. It does **not** require an imported job and launches **no Fiji work**.

It also requires:

- `BatchMachineSupervisorCli.class`
- `BatchFullProcessingCli.class`
- `BatchPrecheckSupervisorCli.class`

### `--precheck-only`

Requires an imported machine job at:

```text
$HOME/LumenRuntime/6dd42df/work/<machine-id>/job
```

Before launching precheck it verifies:

- `package_checksums.sha256` paths are safe, then `sha256sum -c`
- import/package machine IDs match the installed machine
- production config and machine-manifest raw SHA-256 values
- no symlinks under `job/input` or `job/batch/machines`

Then it runs portable Java:

```text
<jdk_root>/bin/java -cp <project>/target/classes \
  vesselreconstruction.ellipse.BatchPrecheckSupervisorCli \
  --input-root <job>/input \
  --batch-root <job>/batch \
  --machine-id <installed-machine-id> \
  --config <job>/production_batch.properties \
  --fiji-launcher <fiji_root>/ImageJ-linux64
```

The launcher preserves the exact `BatchPrecheckSupervisorCli` exit code:

| Code | Meaning |
|------|---------|
| 0 | all assigned images `PRECHECK_SUCCESS` |
| 10 | one or more images require review |
| 11 | one or more images failed |
| 12 | invalid arguments |
| 13 | invalid configuration |
| 14 | invalid manifest |
| 15 | Fiji launch failure |
| 16 | unexpected supervisor failure |

**Classification and reconstruction are not run.**

### `--full-only <tile-profile-index>`

Runs the existing `BatchMachineSupervisorCli` from the current canonical job
state after the same runtime and imported-job verification used by
`--precheck-only`.

Example:

```bash
./run_lumen_machine.sh --full-only 2
```

The tile-profile index is **required** (decimal integer `>= 0`). There is no
silent default to `0`. Whether the index exists in the production configuration
is validated by the Java supervisor/config, not by Bash.

This mode does **not** automatically run `--precheck-only` /
`BatchPrecheckSupervisorCli`. The Java machine supervisor is authoritative:

- images without `PRECHECK_SUCCESS` are blocked
- other ready images may still continue
- already `FULL_SUCCESS` images are skipped
- resumable reconstruction may resume
- after all assigned images are `FULL_SUCCESS`, local physical-core coordinate
  assembly runs before `MACHINE_SUCCESS`
- a machine with some blocked images may still process eligible images and
  exit `10`

Invocation (portable JDK only; no system `java`, no `runtime/batch/run_machine.sh`):

```text
<jdk_root>/bin/java \
  -Dvesselreconstruction.classes.dir=<project>/target/classes \
  -cp <project>/target/classes \
  vesselreconstruction.ellipse.BatchMachineSupervisorCli \
  --input-root <job>/input \
  --batch-root <job>/batch \
  --machine-id <installed-machine-id> \
  --config <job>/production_batch.properties \
  --classifier <installed classifier> \
  --fiji-launcher <fiji_root>/ImageJ-linux64 \
  --tile-profile-index <tile-profile-index>
```

`--full-only` explicitly pins the controller classes directory via
`vesselreconstruction.classes.dir` so machine-supervisor child processes do not
depend on the invoking shell's working directory or on a development-project
`target/classes` directory. Both the supervisor and child full-processing
launches are intended to use resources inside the installed `LumenRuntime`.

The launcher preserves the exact supervisor exit code:

| Code | Meaning |
|------|---------|
| 0 | `MACHINE_SUCCESS` |
| 10 | `MACHINE_COMPLETED_WITH_BLOCKED_IMAGES` |
| 11 | `MACHINE_COMPLETED_WITH_FAILURES` |
| 2 | `MACHINE_FAILED_CONFIGURATION` |
| 6 | `MACHINE_FAILED_UNEXPECTED` |

After the run, the launcher prints paths to:

```text
<job>/batch/machine_supervisor/<machine-id>/machine_progress.properties
<job>/batch/machine_supervisor/<machine-id>/machine_run_summary.csv
```

Live ETA / progress display is **not** part of this phase. The next phase will
build a reader around those files. This mode does not delete prior precheck,
classification, reconstruction, or supervisor outputs.

## Local processing vs portable collection

After a successful package import, the assigned TIFFs, precheck outputs,
classification, reconstruction, and the production dataset all live under the
imported local job:

```text
$HOME/LumenRuntime/6dd42df/work/<machine-id>/job
```

The portable collection drive is **not** the active processing filesystem.
After import checksums succeed, that drive may be disconnected for the duration
of processing.

## `--export-results <completed-results-root>`

After the machine supervisor reports `MACHINE_SUCCESS`, the local dataset
already contains physical-core longitudinal coordinates
(`global_segment_start_um` / `global_longitudinal_um`). Replug the collection
drive and export the completed local result:

```bash
./run_lumen_machine.sh --export-results "/media/operator/COLLECTION/completed_results"
```

`<completed-results-root>` must already exist as an absolute, writable
directory. The launcher will not create a missing destination root (a typo
must not silently create a local folder that looks like a successful ADATA
export). Staging and the final `<machine-id>/` child are created inside that
existing root.

This mode is filesystem-only. It does **not** launch Java, Fiji, Weka, Maven,
precheck, classification, reconstruction, or the machine supervisor. It does
**not** calculate scientific coordinates. It does **not** run automatically
after `--full-only`.

Export is allowed only when:

- no detached full-processing runner is alive (`work/<machine>/control/full_processing/runner.pid` + `kill -0`)
- `machine_progress.properties` has `status=MACHINE_SUCCESS`,
  `completed_count == assigned_count`, `progress_percent=100.0`, and
  `failed_count` / `blocked_count` / `remaining_count` all `0`
- the local dataset `job/batch/output/lumen_dataset_<machine-id>/` has the
  canonical directory layout

Copy work uses `cp -a` into a staging sibling, then verifies the copied file
set and SHA-256 of every regular file, writes `export.properties` and
`export_checksums.sha256`, checks the checksum manifest, and publishes with
one `mv` to:

```text
<completed-results-root>/<machine-id>/
  lumen_dataset_<machine-id>/
  provenance/
    installation.properties
    job/...
  export.properties
  export_checksums.sha256
```

`job/input/` source-TIFF working copies are **not** exported. Fiji, JDK,
Maven, the classifier, and the project tree are **not** exported. No local
job or dataset file is modified or deleted.

Optional completed-run control provenance, if present, is restricted to
these stable files under `provenance/control/full_processing/`:

- `runner.log`
- `runner.exitcode`
- `runner.sh`
- `start.properties`

Transient `runner.pid` is still inspected before export (`kill -0`) so a
live runner cannot be exported over, but it is **not** copied. PID identity
is local and can appear, go stale, or change without any scientific result
change; including it would make an otherwise identical completed export
differ on retry. Other unrecognized control files (`*.tmp.*`, extra
directories) are also excluded. Absence of all four stable files is allowed
and does not create an empty control directory.

Identical retry is safe: if that destination already exists, its checksums
are verified first. A matching current export prints
`LUMEN RESULTS ALREADY EXPORTED` and makes no changes. A corrupt existing
export, or a valid existing export that differs from the current local
result, is refused and left untouched.

After publication, `sha256sum -c export_checksums.sha256` in the final
directory must succeed. The package is self-contained; the processing
machine is not required for later verification.

## Isolated tools

Verification / offline compile / precheck / full supervisor use:

- portable `jdk21`
- portable Maven with `-o` and `-Dmaven.repo.local=<installed maven_repository>`
- installed Fiji (via Java supervisors only)

System Java/Maven and `~/.m2` are not used.

## Installer hash note

Changing `run_lumen_machine.sh` changes its SHA-256. `install_machine.sh`
pins the launcher, monitor, and starter hashes for scientific checkpoint
`6dd42df`. After any further edit to those scripts, recompute and update
those pins before publishing `LumenPortable/install/`.

## Not in this phase

- no live progress polling / ETA UI
- no automatic precheck before `--full-only`
- no automatic export after `--full-only` (export is an explicit operator step)
- no local job/dataset cleanup after export
- no core-level cumulative coordinate assembly during export
  (`MACHINE_SUCCESS` already means the local dataset was assembled)
- no direct `BatchFullProcessingCli` invocation from Bash
- no `runtime/batch/run_machine.sh` execution from this launcher
