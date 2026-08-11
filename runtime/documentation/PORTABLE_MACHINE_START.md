# Portable Machine Start

`start_lumen_processing.sh` is the operator-facing production start command for
an installed `LumenRuntime/0c1c326` machine.

It is an operational wrapper only. It does **not** reimplement classification or
reconstruction. After pre-flight checks it starts the already validated launcher:

```bash
<installation-root>/run_lumen_machine.sh --full-only 2
```

## Installed location

```text
$HOME/LumenRuntime/0c1c326/start_lumen_processing.sh
```

Source on the portable drive (beside the installer):

```text
LumenPortable/install/start_lumen_processing.sh
```

## Operator command

```bash
$HOME/LumenRuntime/0c1c326/start_lumen_processing.sh
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
watch -n 10 $HOME/LumenRuntime/0c1c326/monitor_lumen_machine.sh
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

If `machine_progress.properties` shows all assigned images complete
(`completed_count == assigned_count`, `remaining_count == 0`, current image/action
`NA`), the wrapper performs a successful no-op and does not launch again.

## Already running

If the wrapper's runner PID is still active, or a conservative process scan finds
this installation's full-processing work, the wrapper reports
`LUMEN PROCESSING ALREADY RUNNING` and does not start a second runner.

## Restart / resume

If a previous run has ended and nothing is active, running
`start_lumen_processing.sh` again is permitted.

Resume/skip decisions remain authoritative in `BatchMachineSupervisorCli`
(already `FULL_SUCCESS` images are skipped; unfinished/eligible images continue).

## Safety

The wrapper never modifies original TIFFs, imported package files, manifests,
precheck results, classifier, configuration, or Java classes. Pre-flight is
read-only; the only starter writes are under
`work/<machine-id>/control/full_processing/`.
