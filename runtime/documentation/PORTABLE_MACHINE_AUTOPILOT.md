# Portable One-Click Machine Autopilot (Issue 7H)

Operator workflow on the analysis host:

1. Prepare the production job (`prepare_master_batch.sh` with `machines.count=1`).
2. Create the single-host package (`create_machine_packages.sh`).
3. Run **one** launcher:

```bash
/path/to/LumenProductionJobs/<job>/launchers/START_single_machine.sh
```

`START_machine_01_<coreId>.sh` is equivalent. `machine_id=machine_01` is the
identity of this analysis host, not a subset of the dataset.

3. No further terminal commands are required.

The launcher encodes the machine ID, physical core, and relative package path.
It discovers the ADATA / `LumenPortable` root from **its own filesystem
location**. It does not hard-code a username or a mount such as
`/media/thomas/ADATA CH94`.

## Layout

```text
<ADATA volume>/
  LumenPortable/install/
    install_machine.sh
    import_machine_package.sh
    run_lumen_machine.sh
    start_lumen_processing.sh
    monitor_lumen_machine.sh
    lumen_machine_autopilot.sh
    lumen_machine_start.sh
  LumenProductionJobs/<job>/
    machine_packages/machine_01/
    launchers/
      lumen_machine_start.sh          # shared bootstrap
      START_machine_01_<coreId>.sh
      START_single_machine.sh
    completed_results/                # created at export time
```

Each `START_*.sh` file contains only machine ID, core ID, and the relative
package path, then execs the shared bootstrap. Shared orchestration lives in
`lumen_machine_autopilot.sh`.

`create_machine_packages.sh` writes the `launchers/` directory automatically.

## Local long-running control

After runtime install, the autopilot copies itself to:

```text
$HOME/LumenRuntime/6dd42df/lumen_machine_autopilot.sh
```

and re-execs that local copy. Later processing, `MACHINE_SUCCESS` wait, ADATA
watch, and export do **not** keep an executable open on ADATA.

A local re-click companion is also written:

```text
$HOME/LumenRuntime/6dd42df/START_<machine-id>_<core-id>.sh
```

Use that after reboot if the drive is unplugged (WAITING_FOR_ADATA).

Durable state:

```text
$HOME/LumenRuntime/6dd42df/work/<machine-id>/autopilot/
  autopilot.properties
  autopilot.log
  autopilot.pid
  autopilot.lock
  drive.properties
  AUTOPILOT_COMPLETE
```

The initial launcher prints the exact log path. `monitor_lumen_machine.sh`
remains the live progress reader.

## State machine

ADATA verification (scientific checkpoint `6dd42dfc0447255fc7b65d476b5a5048229eba47`,
package `machine_id`, `package_checksums.sha256`)
→ install (`install_machine.sh`, or `--verify-only` when already present)
→ local import (`import_machine_package.sh`)
→ precheck (`run_lumen_machine.sh --precheck-only`)
→ **SAFE TO UNPLUG ADATA** (sync; the drive is not unmounted)
→ detached full processing (`start_lumen_processing.sh` → `--full-only 2`)
→ wait until `status=MACHINE_SUCCESS` (Issue 7D assembly + 7E gate)
→ export (`run_lumen_machine.sh --export-results`) when the **same** ADATA
  filesystem UUID is present
→ `AUTOPILOT_COMPLETE`

Heavy TIFF I/O stays on local SSD. ADATA is distribution and result collection
only.

Conflicting existing installations are refused. Matching imports and
`PRECHECK_SUCCESS` trees resume. `MACHINE_FAILED_*`, or completed counts
without `MACHINE_SUCCESS`, never export.

## Drive identity

At first bootstrap the autopilot records filesystem UUID (via `findmnt` /
`lsblk`), the initial mountpoint, and paths relative to that mount:

- `lumen_portable_relpath`
- `job_relpath`
- `package_relpath`

After reconnect it looks up the current mountpoint for that UUID and rebuilds
those paths. A different UUID is not accepted. Missing UUID/PARTUUID fails
closed.

## Duplicate click / reboot

| Situation | Result |
|-----------|--------|
| Clicked twice during import/precheck/processing | `RUNNING`; no duplicate scientific work |
| Clicked while waiting for ADATA | `WAITING_FOR_ADATA`; watcher kept/restored |
| Clicked after successful export | `ALREADY COMPLETE` |
| Reboot during processing | Re-click resumes via `start_lumen_processing.sh` |
| Reboot while waiting for ADATA | Re-click restores the export watcher |

Only one autopilot instance may control a machine (`flock` + PID file; stale
PIDs are replaced).

## What this does not do

- no Java / Maven / classifier changes
- no reimplementation of `BatchMachineSupervisor` or physical-core assembly
- no automatic unmount/eject
- no scientific coordinate calculation at export time
