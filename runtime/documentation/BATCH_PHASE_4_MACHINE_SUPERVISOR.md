# Batch Phase 4 — Machine Supervisor

Phase 4 runs every TIFF assigned to one laboratory machine unattended:
deterministic six-digit order, safe resume, live progress, and a final machine
summary.

## Mini-iterations

| Iteration | Scope |
|-----------|--------|
| **4A** | Read-only planning/status (`BatchMachineRunPlanner`) |
| **4B** | Sequential full processing of already-prechecked images + persistent progress |
| Later | Automatic precheck, ETA, result collection, portable drive bootstrap |

**4B does not** run precheck automatically, does **not** compute ETA, and does
**not** collect results from laboratory machines.

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
- Processes images in strict ascending six-digit (`PPSSRR`) order.
- **Exactly one image at a time** — never concurrent images or concurrent Fiji stages from this supervisor.
- `FULL_SUCCESS` → skip (no subprocess).
- `CLASSIFICATION_COMPLETE` → invoke existing `BatchFullProcessingCli` (resume reconstruction).
- `READY_FOR_FULL_PROCESSING` → invoke existing `BatchFullProcessingCli` (classify + reconstruct as required).
- `PRECHECK_MISSING` / `PRECHECK_FAILED` / `PRECHECK_REVIEW_REQUIRED` → **blocked** in 4B (no auto-precheck yet); continue.
- `CANONICAL_CONFLICT` / `UNKNOWN_INVALID_STATE` → blocked/hard failure for that image; continue.
- Blocked or controlled failed images **do not** stop later assigned images.
- After each attempted image, the planner is re-run; completion counts use persisted `FULL_SUCCESS` only (not process exit code alone).
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

## Six-digit ordering

Image IDs are `PPSSRR` (plot / sample / replicate). Ascending lexical order is
the required scientific order (`SixDigitSampleId.compareTo`).

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
