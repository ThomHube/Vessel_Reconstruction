# Portable Machine Monitor

`monitor_lumen_machine.sh` is a **read-only**, **one-shot** status snapshot for an
installed `LumenRuntime/0c1c326` machine. It observes overnight processing
progress without controlling, restarting, or signaling the run.

## Installed location

Fresh `install_machine.sh` installs:

```text
$HOME/LumenRuntime/0c1c326/monitor_lumen_machine.sh
```

Source on the portable drive (beside the installer):

```text
LumenPortable/install/monitor_lumen_machine.sh
```

## Invocation

```bash
./monitor_lumen_machine.sh
```

No arguments. One invocation prints one snapshot and exits.

Optional repeated display (external tool; not built into the monitor):

```bash
watch -n 10 ./monitor_lumen_machine.sh
```

It is safe to close and reopen a monitoring terminal at any time. Monitoring
does **not** launch, stop, or restart processing.

## Example output (running)

```text
LUMEN MACHINE STATUS
machine: machine_01
state: RUNNING
overall: 3 / 10 complete (30.0%)
successful this run: 2
already complete/skipped: 1
blocked: 0
failed: 0
remaining: 7
run elapsed: 1 h 04 m
current image: 010104
sequence: 4 / 10
planned action: RUN_FULL_PROCESSING
current stage: CLASSIFICATION RUNNING
current image elapsed: 8 m 03 s (approximate)
mean completed image: 42 s
ETA sample images: 2
estimated remaining: ~4 m 10 s
processing process detected: YES
```

## States

| State | Meaning |
|-------|---------|
| `NO_IMPORTED_JOB` | No `work/<machine-id>/job` yet |
| `NOT_STARTED` | Job imported, but no `machine_progress.properties` |
| `RUNNING` | Progress shows a current image/action |
| `COMPLETE` | All assigned images complete (`completed == assigned`, `remaining == 0`, current image/action `NA`) |
| `IDLE_OR_INTERRUPTED` | Progress exists but no clear current image and not complete |

Completion is **not** inferred from `status=MACHINE_SUCCESS` alone, because the
supervisor may write interim status values while an image is in flight.

## Overall progress

Overall percentage is **completed-image** percentage from
`machine_progress.properties` (`completed_count` / `assigned_count`).

It is not a tile-level classification percentage. The current Java pipeline does
not expose reliable tile-by-tile progress into the machine progress file.

## Stage detection

For `current_action=RUN_FULL_PROCESSING`, the monitor inspects the highest
numeric `attempt_<N>` under:

```text
job/batch/full/<machine-id>/<image-id>/
```

and reports a stage from committed files:

1. `result.properties` → FULL PROCESSING COMPLETE
2. `reconstruction/result.properties` → RECONSTRUCTION COMPLETE
3. `reconstruction/job.properties` → RECONSTRUCTION RUNNING
4. `classification/result.properties` → CLASSIFICATION COMPLETE / TRANSITION
5. `classification/job.properties` → CLASSIFICATION RUNNING
6. otherwise → FULL PROCESSING STARTING

Attempt directories are selected **numerically** (`attempt_10` after `attempt_9`).

## Elapsed time

`machine_progress.properties` is not rewritten continuously while an image is
inside `BatchFullProcessingCli`, so persisted `elapsed_ms` is not shown as a
live ticker while RUNNING.

Instead the monitor reconstructs approximate supervisor start time as:

```text
timestamp_epoch_ms - elapsed_ms
```

and computes live wall-clock elapsed from that. For COMPLETE runs it prefers
the persisted `elapsed_ms`.

Current-image elapsed uses the modification time of
`classification/job.properties` (or the attempt directory) as an observational
approximation.

## ETA methodology

ETA is approximate and **image-level only**.

Samples come from `machine_run_summary.csv` rows where:

- `processing_attempted == TRUE`
- `final_status == FULL_SUCCESS`
- `elapsed_ms` is a valid non-negative integer

Skipped, blocked, failed, header, and malformed rows are ignored.

When RUNNING with `remaining_count > 0` and at least one sample:

```text
estimated remaining ≈
  max(0, mean - current_image_elapsed)
  + mean * max(0, remaining_count - 1)
```

The leading `~` marks approximation.

### Limitations

- First-image ETA is unavailable until at least one successful completed timing
  sample exists.
- Heterogeneous image sizes make mean-based ETA less accurate.
- No tile-level classification percentage is attempted.
- Process detection (`ps`) is optional and never authoritative for state.

## Safety

The monitor is strictly observational. It does **not**:

- launch Java / Fiji / Maven
- launch precheck or full processing
- kill or signal processes
- modify progress, summaries, attempts, datasets, or TIFFs
- create files inside the imported job
- sleep/poll forever or run background processes
