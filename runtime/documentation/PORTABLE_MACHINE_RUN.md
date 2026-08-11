# Portable Machine Run

`run_lumen_machine.sh` is the user-local launcher for an installed
`LumenRuntime/0c1c326` tree. It discovers the installation root from its own
path and uses only the portable JDK, Maven, Fiji, classifier, and config that
`install_machine.sh` placed under that root.

## Supported modes

```bash
./run_lumen_machine.sh --verify-only
./run_lumen_machine.sh --precheck-only
./run_lumen_machine.sh --full-only <tile-profile-index>
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
$HOME/LumenRuntime/0c1c326/work/<machine-id>/job
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

## Isolated tools

Verification / offline compile / precheck / full supervisor use:

- portable `jdk21`
- portable Maven with `-o` and `-Dmaven.repo.local=<installed maven_repository>`
- installed Fiji (via Java supervisors only)

System Java/Maven and `~/.m2` are not used.

## Installer hash note

Changing `run_lumen_machine.sh` changes its SHA-256. The installer still pins
the previous validated launcher hash until this new launcher is tested and a
separate deployment bite updates `install_machine.sh`. Do not place this
launcher into the canonical portable deployment set (`LumenPortable/install/`)
until that pin is updated.

## Not in this phase

- no live progress polling / ETA UI
- no automatic precheck before `--full-only`
- no direct `BatchFullProcessingCli` invocation from Bash
- no `runtime/batch/run_machine.sh` execution from this launcher
