# Portable Machine Run

`run_lumen_machine.sh` is the user-local launcher for an installed
`LumenRuntime/0c1c326` tree. It discovers the installation root from its own
path and uses only the portable JDK, Maven, Fiji, classifier, and config that
`install_machine.sh` placed under that root.

## Supported modes

```bash
./run_lumen_machine.sh --verify-only
./run_lumen_machine.sh --precheck-only
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

**Classification and reconstruction are not run.** Full overnight processing is
still a later phase.

## Isolated tools

Verification / offline compile / precheck use:

- portable `jdk21`
- portable Maven with `-o` and `-Dmaven.repo.local=<installed maven_repository>`
- installed Fiji (only via the Java precheck supervisor in `--precheck-only`)

System Java/Maven and `~/.m2` are not used.

## Installer hash note

Changing `run_lumen_machine.sh` changes its SHA-256. The installer still pins
the previous launcher hash during this development bite and **must be updated**
in a separate deployment bite before this launcher is placed into the canonical
portable deployment set (`LumenPortable/install/`).

## Not enabled yet

- no `run_machine.sh` execution
- no `BatchMachineSupervisorCli` full-processing orchestration
- no `BatchFullProcessingCli` classification/reconstruction launch from this
  launcher mode
