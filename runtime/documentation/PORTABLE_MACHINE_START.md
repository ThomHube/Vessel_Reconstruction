# Portable Machine Start

`start_lumen_processing.sh` is the operator-facing production start command for
an installed `LumenRuntime/6dd42df` machine.

It is an operational wrapper only. It does **not** reimplement classification or
reconstruction. After pre-flight checks it starts the already validated launcher:

```bash
<installation-root>/run_lumen_machine.sh --full-only 2
```

## Installed location

```text
$HOME/LumenRuntime/6dd42df/start_lumen_processing.sh
```

Source on the portable drive (beside the installer):

```text
LumenPortable/install/start_lumen_processing.sh
```

## Operator command

```bash
$HOME/LumenRuntime/6dd42df/start_lumen_processing.sh
```

No arguments. The production tile profile is fixed:

- `--full-only` tile-profile-index **2**
- tile size **1024x1024**

## Pre-flight requirements

Before any detached start:

1. installation identity and launcher/monitor SHA-256 from
   `installation.properties` must match the installed files;
2. the machine package must already be imported under `work/<machine-id>/job`;
3. `classification.tile_profiles` index `2` must be exactly `1024x1024`;
4. every assigned image in
   `job/batch/machines/<machine-id>_manifest.csv` must already have:

```text
job/batch/precheck/<machine-id>/jobs/<image-id>/result.properties
```

with:

- `status=PRECHECK_SUCCESS`
- `source_unchanged=TRUE`
- matching `image_id`, `machine_id`, `source_sha256`, `configuration_sha256`

Image IDs are PPTTCS (`0101A1`, …). Package/import metadata must be schema 2
(`package.schema_version=2`, `manifest.schema_version=2`,
`import.schema_version=2`).

The wrapper does **not** run precheck automatically.

## What happens after a successful start

The detached runner executes the validated launcher, which runs
`BatchMachineSupervisorCli`. Images are processed **sequentially**:

1. classification (tiled Weka) first;
2. reconstruction / artifact reduction afterward;

for each eligible image, under supervisor control.

## Detached run / terminal safety

Start is detached with `nohup`. After:

```text
LUMEN PROCESSING STARTED
```

it is safe to close the start terminal. Closing the terminal does **not** stop
overnight processing.

## Monitor

```bash
watch -n 10 $HOME/LumenRuntime/6dd42df/monitor_lumen_machine.sh
```

## Operational control files

Outside the scientific job tree:

```text
work/<machine-id>/control/full_processing/
  runner.sh
  runner.pid
  runner.log
  runner.exitcode
  start.properties
```

`runner.exitcode` is written only after the full launcher invocation ends. It
contains the exact launcher exit code.

## Already complete

`LUMEN PROCESSING ALREADY COMPLETE` means the previous machine result is
genuine scientific success:

- `status=MACHINE_SUCCESS`
- `assigned_count > 0`
- `completed_count == assigned_count`
- `progress_percent=100.0`
- `failed_count=0`
- `blocked_count=0`
- `remaining_count=0`
- current image/action `NA`

`completed_count == assigned_count` only means every assigned **image**
reached `FULL_SUCCESS`. `status=MACHINE_SUCCESS` additionally means local
physical-core coordinate assembly succeeded.

A machine with `status=MACHINE_FAILED_UNEXPECTED` is **not** already
complete, even when image counts are 100% (`progress_percent=100.0`,
`remaining_count=0`, `failed_count=0`). A normal `start_lumen_processing.sh`
rerun therefore launches the supervisor again. Already-`FULL_SUCCESS` images
are skipped; machine finalization / core assembly is retried. There is no
special assembly-retry command.

`MACHINE_COMPLETED_WITH_FAILURES`, `MACHINE_COMPLETED_WITH_BLOCKED_IMAGES`,
`MACHINE_FAILED_CONFIGURATION`, missing, empty, or unknown status values
are also not already complete.

## Already running

If the wrapper's runner PID is still active, or a conservative process scan finds
this installation's full-processing work, the wrapper reports
`LUMEN PROCESSING ALREADY RUNNING` and does not start a second runner.

The one-click machine autopilot (`PORTABLE_MACHINE_AUTOPILOT.md`) is the
operator-facing wrapper that runs precheck, then this starter, then export.
This file remains the production start command for full processing itself.

## Restart / resume

If a previous run has ended and nothing is active, running
`start_lumen_processing.sh` again is permitted.

Resume/skip decisions remain authoritative in `BatchMachineSupervisorCli`
(already `FULL_SUCCESS` images are skipped; unfinished/eligible images continue;
assembly-only failure retries coordinate assembly without reprocessing those
images).

## Safety

The wrapper never modifies original TIFFs, imported package files, manifests,
precheck results, classifier, configuration, or Java classes. Pre-flight is
read-only; the only starter writes are under
`work/<machine-id>/control/full_processing/`.
