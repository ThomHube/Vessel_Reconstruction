# Batch Phase 4 — Machine Supervisor

Phase 4 runs every TIFF assigned to one laboratory machine unattended:
deterministic PPTTCS (`CoreSegmentId`) order, safe resume, live progress, and a final machine
summary.

## Mini-iterations

| Iteration | Scope |
|-----------|--------|
| **4A** | Read-only planning/status (`BatchMachineRunPlanner`) |
| **4B** | Sequential full processing of already-prechecked images + persistent progress |
| Later | Automatic precheck, ETA, portable drive bootstrap |
| **7A** | Explicit completed-machine result export (`run_lumen_machine.sh --export-results`) |
| **7D** | Automatic physical-core coordinate assembly before `MACHINE_SUCCESS` |

**4B does not** run precheck automatically, does **not** compute ETA, and does
**not** automatically export results. After every assigned image is
`FULL_SUCCESS`, the supervisor runs `PhysicalCoreCoordinateAssembler` on the
local machine dataset. Only after that assembly succeeds does
`MACHINE_SUCCESS` become authoritative. An operator may then run the portable
launcher `--export-results` mode to publish a staged, checksummed result
package to an existing collection root. That export copies the already-assembled
machine dataset. It does not calculate scientific coordinates.

## Production segment orientation

All final production TIFFs share one acquisition convention:

- **top of the TIFF** = nearest bark
- **bottom of the TIFF** = nearest center / pith
- production `segment_orientation` = `TOP_TO_BOTTOM`

`BatchFullProcessingService` applies this invariant when constructing the
classification configuration. It is not a batch-config property and is not
chosen by the operator.

After classification and reconstruction of one segment:

- `segment_orientation` is `TOP_TO_BOTTOM` in `image_manifest.csv`,
  `segment_manifest.csv`, lumen measurements, and classified-TIFF metadata
- `segment_axis_position_um` is the finite local bark→center position from
  existing `LumenCoordinateContext` (original-image Y × pixel height, including
  any bark-side `crop_y_px` offset)
- `global_segment_start_um` and `global_longitudinal_um` remain **NA** until
  machine-level physical-core assembly

`global_x_um` / `global_y_um` remain NA (no two-dimensional registration).

## Physical-core coordinate assembly (7D)

After the per-image loop, when every assigned image is `FULL_SUCCESS` and there
are no failed or blocked images, `BatchMachineSupervisor` calls:

```text
new PhysicalCoreCoordinateAssembler().assemble(datasetRoot)
```

once, on the local machine shard:

```text
<batch-root>/output/lumen_dataset_<machine-id>
```

Assembly uses only local dataset CSVs. It does not open TIFFs and does not
require an ADATA / collection drive. One call assembles every physical core
present in the shard, including:

- single-segment cores (`global_segment_start_um = 0`,
  `global_longitudinal_um = segment_axis_position_um`)
- multi-segment cores (contiguous `1..n` bark-to-center concatenation)
- multiple complete physical cores assigned to one machine

Scientific completion order:

```text
all assigned images FULL_SUCCESS
  → physical-core coordinate assembly
  → MACHINE_SUCCESS
```

`MACHINE_SUCCESS` is not written, and export is not eligible, until assembly
succeeds. In-loop `machine_progress.properties` writes that would already look
export-eligible (`completed_count == assigned_count`, `remaining_count == 0`,
`failed_count == 0`, `blocked_count == 0`) are suppressed until assembly
returns.

If assembly throws `IOException`:

- status = `MACHINE_FAILED_UNEXPECTED` (CLI exit 6)
- `completed_count` remains the live `FULL_SUCCESS` image count
- `failed_count` and `blocked_count` are unchanged (remain 0 when only assembly
  failed)
- `remaining_count` remains `assigned - completed`
- no `processing_runs` core-assembly stage is added

A normal supervisor rerun then skips every already-`FULL_SUCCESS` image and
retries assembly. The assembler is idempotent and retry-safe after a partial
multi-file interruption.

Empty assignment (`assigned == 0`) does not call the assembler.

## READ-ONLY PLAN (4A)

```bash
runtime/batch/inspect_machine_run.sh \
  <input-root> <batch-root> <machine-id> <config> <classifier>
```

Equivalent Java:

```bash
java -cp target/classes vesselreconstruction.ellipse.BatchMachineRunPlanCli \
  --input-root <path> \
  --batch-root <path> \
  --machine-id <machine_XX> \
  --config <path> \
  --classifier <path>
```

- Never launches Fiji or Weka.
- Never writes production or attempt artifacts.
- Never creates `machine_supervisor/` directories.

## EXECUTION (4B)

```bash
runtime/batch/run_machine.sh \
  <input-root> \
  <batch-root> \
  <machine-id> \
  <config> \
  <classifier> \
  <fiji-launcher> \
  <tile-profile-index>
```

Equivalent Java:

```bash
java -cp target/classes vesselreconstruction.ellipse.BatchMachineSupervisorCli \
  --input-root <path> \
  --batch-root <path> \
  --machine-id <machine_XX> \
  --config <path> \
  --classifier <path> \
  --fiji-launcher <path> \
  --tile-profile-index <integer>
```

### Behaviour

- Consumes `BatchMachineRunPlanner` for the authoritative image list and state.
- Processes images in strict ascending production (`PPTTCS` / `CoreSegmentId`) order.
- **Exactly one image at a time** — never concurrent images or concurrent Fiji stages from this supervisor.
- `FULL_SUCCESS` → skip (no subprocess).
- `CLASSIFICATION_COMPLETE` → invoke existing `BatchFullProcessingCli` (resume reconstruction).
- `READY_FOR_FULL_PROCESSING` → invoke existing `BatchFullProcessingCli` (classify + reconstruct as required).
- `PRECHECK_MISSING` / `PRECHECK_FAILED` / `PRECHECK_REVIEW_REQUIRED` → **blocked** in 4B (no auto-precheck yet); continue.
- `CANONICAL_CONFLICT` / `UNKNOWN_INVALID_STATE` → blocked/hard failure for that image; continue.
- Blocked or controlled failed images **do not** stop later assigned images.
- After each attempted image, the planner is re-run; completion counts use persisted `FULL_SUCCESS` only (not process exit code alone).
- After **all** assigned images are `FULL_SUCCESS` (and none failed or blocked), one local `PhysicalCoreCoordinateAssembler` call finalizes core coordinates **before** `MACHINE_SUCCESS`.
- Progress percentage is printed live and persisted; **ETA is intentionally not implemented yet**.
- Automatic precheck execution is **intentionally deferred** to the next iteration.

### Subprocess architecture

For each ready/resume image the supervisor launches a **new JVM**:

```text
$JAVA_HOME/bin/java -cp <project>/target/classes \
  vesselreconstruction.ellipse.BatchFullProcessingCli \
  --input-root ... --batch-root ... --machine-id ... --image-id ... \
  --config ... --classifier ... --fiji-launcher ... --tile-profile-index ...
```

Fiji is passed only as `--fiji-launcher`. The supervisor classpath is production
`target/classes` only.

### Persisted machine progress

Created when the execution supervisor runs (not during read-only planning):

```text
<batch-root>/machine_supervisor/<machine-id>/
  machine_progress.properties   # rewritten after every image decision
  machine_run_summary.csv       # one row per assigned image for this run
  logs/<image-id>__controller.log
```

### Exit codes

| Status | Exit |
|--------|------|
| `MACHINE_SUCCESS` | 0 |
| `MACHINE_COMPLETED_WITH_BLOCKED_IMAGES` | 10 |
| `MACHINE_COMPLETED_WITH_FAILURES` | 11 |
| `MACHINE_FAILED_CONFIGURATION` | 2 |
| `MACHINE_FAILED_UNEXPECTED` | 6 |

## Production identity ordering

Image IDs are `PPTTCS` (plot / tree / core A|B / segment 1–5). Canonical order is
`CoreSegmentId.compareTo` (plot → tree → core A before B → segment order).

Portable scripts target scientific checkpoint `6dd42df` (PPTTCS / manifest schema 2).
## Authoritative inputs

| Input | Source |
|-------|--------|
| Assignment | `<batch-root>/machines/<machine-id>_manifest.csv` (Phase 1) |
| Precheck | `<batch-root>/precheck/<machine-id>/jobs/<image-id>/result.properties` (Phase 2) |
| Production completeness | `BatchProductionOutputValidator.inspectFinalizedDataset` (Phase 3) |

## Image states and 4B actions

| State | 4B action |
|-------|-----------|
| `PRECHECK_MISSING` / `PRECHECK_FAILED` | Block; continue (precheck deferred) |
| `PRECHECK_REVIEW_REQUIRED` | Block; continue |
| `READY_FOR_FULL_PROCESSING` | Run `BatchFullProcessingCli` |
| `CLASSIFICATION_COMPLETE` | Run `BatchFullProcessingCli` (resume) |
| `FULL_SUCCESS` | Skip |
| `CANONICAL_CONFLICT` | Block; continue |
| `UNKNOWN_INVALID_STATE` | Block; continue |

## Progress percentage

- `completedCount` = images in `FULL_SUCCESS` only
- `progressPercent` = `100 * completedCount / assignedCount` (or `0.0` if assigned is 0)
- Never NaN

## Relation to Phase 3

Single-image execution remains `BatchFullProcessingCli` /
`runtime/batch/run_one_full_image.sh`. The machine supervisor does not duplicate
classification or reconstruction logic.

## Result export (launcher, not the supervisor)

The machine supervisor itself still does **not** export or collect results.
After a local job reaches `MACHINE_SUCCESS` (which now means assigned images
are `FULL_SUCCESS` **and** local physical-core coordinates are assembled), the
installed launcher can publish the completed machine dataset:

```bash
$HOME/LumenRuntime/6dd42df/run_lumen_machine.sh \
  --export-results <existing-completed-results-root>
```

That mode is filesystem-only (no Java/Fiji/Weka). It stages under the supplied
root, checksums every regular exported file, and atomically publishes
`<root>/<machine-id>/`. Local `job/input/` working copies are not exported.
No local cleanup occurs. Export does not calculate `global_segment_start_um`
or `global_longitudinal_um`; it copies the already-assembled local dataset.
