# Batch Phase 3 — Full Single-Image Processing (Two-Process Architecture)

Phase 3 re-verifies one already-prechecked image and, only if that
re-verification reproduces the automatically-approved Phase-2 geometry
exactly, runs tiled Weka classification and all-lumen reconstruction into
the production dataset.

Multi-image machine orchestration (ordering, progress, dry-run planning)
is documented in
[`BATCH_PHASE_4_MACHINE_SUPERVISOR.md`](BATCH_PHASE_4_MACHINE_SUPERVISOR.md).

It does **not**:

- process an image whose precheck is missing, unsuccessful, or stale
- accept a mask or scale bar that differs from the precheck result
- show dialogs
- register a visible Fiji menu command

## Why two processes

All Lumina reconstruction (specifically `FinalLumenGeometry.buildAll`) can
exhaust heap after an otherwise-successful classification. Previously,
classification and reconstruction ran back-to-back inside **one** Fiji JVM
via `BatchFullProcessingService.process()`; an out-of-memory error during
reconstruction could crash that JVM before `result.properties` was ever
written, silently destroying the record of a classification that had
already succeeded and been committed to disk.

Phase 3 now splits the work into two independently-launched stages, each
running in its **own, fresh Fiji JVM**:

- **`CLASSIFICATION`** — binds the precheck, reproduces the mask and scale
  bar, runs tiled Weka classification, and commits only the
  classification-stage production outputs (original RGB, approved mask,
  masked RGB, masked classified, manifests, and a `TILED_CLASSIFICATION`
  `SUCCESS` processing-run row). It never touches All Lumina.
- **`RECONSTRUCTION`** — opens *only* the already-saved masked-classified
  TIFF from the dataset, verifies its SHA-256 against `image_manifest`, and
  runs `AllLumenBatchProcessor` + `AllLuminaDatasetWriter`. It never loads a
  Weka classifier model, so it cannot repeat (or be slowed down by)
  classification, and an out-of-memory error here can never corrupt or lose
  the already-committed classification outputs.

### Final lumen geometry memory (mini-iteration 3D)

Previously, each `FinalLumenGeometry.ComponentGeometry` retained one
complete-image `boolean[]` mask of size `imageWidth × imageHeight`. With
thousands of lumen components on a large crop (for example 3688×2076), that
produced memory complexity approximately:

`O(componentCount × imageWidth × imageHeight)`

and was the confirmed allocation site of reconstruction `OutOfMemoryError`.

Geometry masks are now **component-local**: each component keeps only the
final bounding rectangle plus a one-pixel false background border, with
explicit global origins (`maskOriginX`, `maskOriginY`). Conversion is
`global = local + origin`. A single shared image-sized `BitSet` validates
that final component pixels do not overlap. Scientific coordinates and
measurements (centroids, wall length, ellipse fit, nearest-lumen distances)
remain global crop-local values and are unchanged; boundary points are
translated from the local mask before any position-dependent calculation.

Target retained-mask complexity is therefore approximately:

`O(imagePixels for one shared overlap structure
  + sum of component-local bounding-box areas)`

rather than `componentCount × imageArea`.

### Child stage results (mini-iteration 3B)

Inside each Fiji child, `BatchFullProcessingImagePlugin` loads a
stage-aware job (`processing_stage` required), executes exactly one of
`processClassificationStage` or `processReconstructionStage`, and writes one
atomic `BatchFullProcessingStageResult` to the job's `result_path`. No
scientific algorithms changed in that child-mode work. Parent CLI combined
`result.properties` ownership and resume orchestration are covered separately;
this documentation's parent-flow section describes the intended end state.

If a valid classification for an image already exists in the production
dataset (from a prior attempt, or a prior run of a shared dataset), the CLI
**reuses it** and skips the classification Fiji launch entirely, going
straight to a fresh reconstruction launch.

## Architecture

1. Pure-Java `BatchFullProcessingCli` (the *parent* process) re-derives the
   job from the Phase-1 machine manifest and the Phase-2 precheck outputs,
   hashes the source and classifier, and determines the next attempt number
   for `full/<machine_id>/<image_id>/attempt_<N>/`.
2. It asks `BatchProductionOutputValidator.inspectFinalizedDataset` whether
   the canonical dataset is already fully complete. If so, it writes only
   `attempt_<N>/result.properties` (`FULL_SUCCESS`) and exits without
   launching Fiji.
3. Otherwise it asks `BatchClassificationResumeInspector` whether a
   matching classification can be reused.
4. If classification is not resumable, it writes
   `attempt_<N>/classification/job.properties` with
   `processing_stage=CLASSIFICATION` and launches a **fresh Fiji process**,
   capturing logs under `attempt_<N>/classification/`.
5. When the final dataset is not already complete, once classification has
   resolved (by reuse or by a completed/failed/timed-out/OOM'd fresh run),
   it writes `attempt_<N>/reconstruction/job.properties` with
   `processing_stage=RECONSTRUCTION` and launches a **fresh Fiji process**,
   capturing logs under `attempt_<N>/reconstruction/`.
6. **Always**, it writes one combined `attempt_<N>/result.properties`
   summarizing both stages — even if classification or reconstruction
   failed, timed out, OOM'd, never wrote its own `result.properties`, or
   both stages were reused from the dataset.
7. The CLI maps the combined status to a stable process exit code.

Each Fiji process is invoked exactly like before, just once per stage:

```bash
/path/to/Fiji.app/ImageJ-linux64 \
  --headless \
  --no-splash \
  --allow-multiple \
  --full-classpath \
  --main-class vesselreconstruction.ellipse.BatchFullProcessingImagePlugin \
  /absolute/path/to/<stage>/job.properties
```

### Structured child results are authoritative

Each child writes a `BatchFullProcessingStageResult` to the `result_path`
recorded in its stage job. After Fiji exits, the parent reloads that job and
reads **that** path. When the structured child result exists and matches the
expected stage, image ID, machine ID, and attempt number, it is authoritative
for combined status, reason, message, OOM flags, run IDs, and counts — even if
the Fiji launcher returns exit code 0. The launcher exit code is recorded
separately (`classification_exit_code` / `reconstruction_exit_code`).

If the expected result file is truly missing or cannot be parsed/validated,
the parent keeps the missing-result fallback (including stderr/stdout
`OutOfMemoryError` scanning).

`--full-classpath` loads `jars/` and `plugins/` so the deployed plugin JAR is
visible. The entry point does not construct `new ImageJ(...)` under
`--headless` (that throws `HeadlessException`); `main` processes the job file
directly and dispatches to `BatchFullProcessingService.processClassification`
or `processReconstruction` based on `job.processing_stage`.

## Attempt directory layout

```
full/<machine_id>/<image_id>/attempt_<N>/
  result.properties            <- combined result, ALWAYS written by the parent CLI
  classification/               <- absent if classification was reused, not run
    job.properties
    result.properties           <- written by the classification Fiji process (if it got that far)
    stdout.log
    stderr.log
  reconstruction/                <- always present; reconstruction always runs
    job.properties
    result.properties           <- written by the reconstruction Fiji process (if it got that far)
    stdout.log
    stderr.log
```

## Classification-stage pipeline (`BatchFullProcessingService.processClassification`)

1. Bind precheck: load `precheck_result_path`, require `PRECHECK_SUCCESS`,
   require source/configuration SHA-256 to match the job, load
   `precheck_csv_path` and require the CSV row to agree with the result file
   (image/machine id, SHAs, status, crop geometry, mask checksum, scale-bar
   method, pixel size).
2. Re-hash the source file against `source_sha256`, open it, verify RGB,
   compute a content checksum (`checksum_before`).
3. Regenerate the automatic sample mask and crop geometry; require the mask
   checksum and crop rectangle to exactly match the precheck result.
4. Re-run automatic scale-bar detection; require method, measured
   center-to-center length, and derived pixel size to exactly match the
   precheck result (pixel size compared with `<= 1e-6` relative deviation).
5. Derive physical calibration from the reproduced scale bar.
6. Configure `TiledWekaClassifier` from the job's tile profile
   (`tile_width_px` / `tile_height_px`) and run classification. Loaded
   classifier model class labels that differ from the expected
   `["Wood","Ray","Lumen","Background"]` are logged as a warning (the
   `0=Wood,1=Ray,2=Lumen,3=Background` index mapping is preserved either
   way).
7. Save pre-classification artifacts and the finalized classification stage
   into the production dataset (`ProductionDatasetLayout` /
   `ClassificationDatasetWriter`).
8. Validate the committed classification-only production outputs
   (`BatchProductionOutputValidator.validateClassificationOnly`).
9. Recompute the source content checksum (`checksum_after`); any change
   from `checksum_before` fails the run even if every prior step succeeded.
10. Success requires: source unchanged, and classification output
    validation passed. All Lumina is never invoked in this stage.

Heap usage is logged (`HEAP[CLASSIFICATION_START]` / `HEAP[CLASSIFICATION_END]`)
via `BatchFullProcessingService.logHeap`.

## Reconstruction-stage pipeline (`BatchFullProcessingService.processReconstruction`)

1. Assumes a prior successful classification stage (either just completed
   or reused); does **not** re-run precheck binding, mask/scale-bar
   reproduction, or Weka classification, and never loads a classifier model.
2. Locates the masked-classified TIFF at
   `images/masked_classified/<imageId>__<imageId>__masked_classified.tif`
   under the job's `dataset_root` and requires it to exist.
3. Reads the `image_manifest` row for the image and verifies the
   masked-classified file's actual SHA-256 matches
   `masked_classified_sha256`.
4. Opens *only* that TIFF (logging heap before open).
5. Runs `AllLumenBatchProcessor` (logging heap before, and issuing one
   `System.gc()` immediately before, to give reconstruction the largest
   possible contiguous heap) then `AllLuminaDatasetWriter.write` (logging
   heap before the writer call, which is where `FinalLumenGeometry.buildAll`
   executes) to reconstruct and commit lumen measurements, diagnostics, and
   manifests.
6. Validates the full committed production outputs
   (`BatchProductionOutputValidator.validate`, which also re-confirms the
   classification-stage outputs are still present and consistent).
7. Success requires output validation to pass.

## Out-of-memory detection

- **Inside a Fiji process:** `BatchFullProcessingService.containsOutOfMemory`
  walks the exception cause chain (depth-limited) looking for an
  `OutOfMemoryError`; both `processClassification` and
  `processReconstruction` also catch `OutOfMemoryError` directly. Either
  path sets `out_of_memory_detected=TRUE` and
  `status=FAILED_OUT_OF_MEMORY` in that stage's own `result.properties`.
  `BatchFullProcessingImagePlugin` additionally catches `OutOfMemoryError`
  (and other `Error` subtypes, except `ThreadDeath`) around the whole
  dispatch and writes a minimal best-effort OOM result even if the JVM is
  too constrained to do anything else.
- **From the parent CLI, when a stage never wrote a `result.properties`:**
  the CLI case-insensitively scans that stage's captured `stdout.log` /
  `stderr.log` for `OutOfMemoryError` and, if found, reports that stage as
  `FAILED_OUT_OF_MEMORY` in the combined result.

## Pure-Java parent CLI

`BatchFullProcessingCli` is intentionally launched as portable pure Java:

```bash
java -cp target/classes vesselreconstruction.ellipse.BatchFullProcessingCli ...
```

Finalized-dataset reuse inspection
(`BatchProductionOutputValidator.inspectFinalizedDataset`) does **not** require
Fiji/ImageJ runtime classes. It validates already-written files, manifests,
CSV schemas, hashes, run IDs, and counts using JDK and project utilities
only. ImageJ-level pixel/metadata validation remains in the Fiji child
stages that create those outputs. This permits fast completed-image checks
without launching Fiji.

## Reusing a fully completed image (`BatchProductionOutputValidator`)

Before launching any Fiji child, the CLI inspects the **canonical production
dataset** (files and manifests), not prior attempt-level
`result.properties`. `BatchProductionOutputValidator.inspectFinalizedDataset`
returns one of:

- `FINAL_DATASET_COMPLETE` — classification and reconstruction are both
  present and valid;
- `CLASSIFICATION_COMPLETE_RECONSTRUCTION_INCOMPLETE` — classification can
  be reused; reconstruction still needs a Fiji launch;
- `CLASSIFICATION_INCOMPLETE` — classification (and then reconstruction)
  must run;
- `CANONICAL_CONFLICT` — a conflicting production artifact was detected
  (for example original RGB SHA-256 mismatch); neither stage is launched.

When the dataset is `FINAL_DATASET_COMPLETE`, the CLI prints:

```text
FULL_DATASET_ALREADY_COMPLETE: reusing classification and reconstruction
```

and:

- launches **no** classification child;
- launches **no** reconstruction child;
- creates neither `attempt_N/classification/` nor `attempt_N/reconstruction/`;
- appends no processing-run rows and does not rewrite scientific outputs;
- still creates a new `attempt_N/result.properties` with `FULL_SUCCESS`;
- returns exit code 0.

That combined result populates validation flags
(`precheck_verified`, `classification_*`, `reconstruction_*`,
`required_outputs_validated`, `source_unchanged`,
`out_of_memory_detected=FALSE`), canonical artifact/manifest paths,
`classification_run_id` / `reconstruction_run_id`, and reconstruction
counts from the successful `ALL_LUMINA_RECONSTRUCTION` processing-run row.
Child stage result paths and exit codes remain `NA` because no child ran.

Incomplete reconstruction (classification already valid) still resumes from
the saved classification and launches only the reconstruction Fiji process.

## Resuming a prior classification (`BatchClassificationResumeInspector`)

When the final dataset is not already complete, the CLI checks whether the
production dataset already holds a valid, matching classification for the
image. All of the following must hold for classification to be treated as
resumable (and its Fiji launch skipped):

- the masked-classified TIFF exists on disk under `dataset_root`;
- `image_manifest` has a row for the image whose `source_rgb_sha256`
  matches the job's source SHA-256;
- that row's `masked_classified_sha256` is present and matches the actual
  file's SHA-256;
- that row's `classifier_sha256` matches the job's classifier SHA-256
  (when the manifest records one);
- that row's `classification_run_id` is present;
- `processing_runs` has a `TILED_CLASSIFICATION` / `SUCCESS` row for the
  image.

`image_manifest` and `processing_runs` do not currently record a
configuration SHA-256, so configuration compatibility cannot be strictly
re-verified on resume; this is a documented limitation, not an oversight.

When resumable, the combined result records
`classification_stage_reused=TRUE`, `classification_process_launched=FALSE`,
and `classification_run_id` taken from the dataset — and only the
reconstruction stage launches a Fiji process.

## Status enum (`BatchFullProcessingStatus`)

`FULL_SUCCESS`, `PRECHECK_NOT_FOUND`, `PRECHECK_NOT_SUCCESSFUL`,
`PRECHECK_STALE_SOURCE`, `PRECHECK_STALE_CONFIGURATION`, `INVALID_JOB`,
`INVALID_SOURCE`, `INVALID_CLASSIFIER`, `FAILED_MASK_REPRODUCTION`,
`FAILED_SCALE_BAR_REPRODUCTION`, `FAILED_CLASSIFICATION`,
`FAILED_CLASSIFICATION_SAVE`, `FAILED_RECONSTRUCTION`,
`FAILED_RECONSTRUCTION_SAVE`, `FAILED_OUTPUT_VALIDATION`,
`CANONICAL_CONFLICT`, `FAILED_OUT_OF_MEMORY`, `FAILED_TIMEOUT`,
`FAILED_UNEXPECTED`.

Only `FULL_SUCCESS` is a success; every other status is a failure. This
enum is reused as-is for a single stage's own `result.properties`
(`FULL_SUCCESS` there means "this stage committed successfully", not
necessarily that the whole two-stage attempt succeeded) and for the
parent's combined `result.properties` (where `FULL_SUCCESS` does mean the
whole attempt succeeded). `isPrecheckProblem()` covers the four
`PRECHECK_*` statuses.

## Processing stage enum (`BatchFullProcessingStage`)

`CLASSIFICATION`, `RECONSTRUCTION`. Every `job.properties` file (for either
stage) carries `processing_stage`, parsed via `BatchFullProcessingStage.parse`.
`BatchFullProcessingService.process(job, config)` dispatches to
`processClassification` or `processReconstruction` based on this field.

## CLI exit codes (`BatchFullProcessingCli.mapExitCode`)

The combined result's status (see "Combined result" below for how it is
derived from the two stage outcomes) is mapped exactly like a single
stage's status would be:

| Code | Meaning                    | Statuses |
|-----:|-----------------------------|----------|
| 0    | success                     | `FULL_SUCCESS` |
| 20   | precheck missing/stale/not successful | `PRECHECK_NOT_FOUND`, `PRECHECK_NOT_SUCCESSFUL`, `PRECHECK_STALE_SOURCE`, `PRECHECK_STALE_CONFIGURATION` |
| 21   | invalid input/configuration/job | `INVALID_JOB`, `INVALID_SOURCE`, `INVALID_CLASSIFIER`, `FAILED_MASK_REPRODUCTION`, `FAILED_SCALE_BAR_REPRODUCTION`, plus CLI-side argument/config/manifest/classifier/tile-profile validation failures |
| 22   | classification failure     | `FAILED_CLASSIFICATION`, `FAILED_CLASSIFICATION_SAVE` |
| 23   | reconstruction failure     | `FAILED_RECONSTRUCTION`, `FAILED_RECONSTRUCTION_SAVE` |
| 24   | output validation failure  | `FAILED_OUTPUT_VALIDATION` |
| 25   | canonical conflict         | `CANONICAL_CONFLICT` |
| 26   | out of memory               | `FAILED_OUT_OF_MEMORY` |
| 27   | timeout                     | `FAILED_TIMEOUT` (also returned directly when either stage's Fiji process exceeds `full.timeout_minutes`) |
| 28   | unexpected failure          | `FAILED_UNEXPECTED`; also returned directly on a Fiji launch failure, interruption, or a missing/unreadable stage `result.properties` that isn't attributable to a timeout or OOM |

`FAILED_MASK_REPRODUCTION` and `FAILED_SCALE_BAR_REPRODUCTION` are mapped to
21 (invalid input) rather than a classification/reconstruction code: they
mean the source image no longer reproduces the automatically-approved
precheck geometry, which is closer to stale/invalid input than to a
classification or reconstruction defect.

## Job properties (`job.properties`)

Written by the CLI (once per stage, into `<stage>/job.properties`), consumed
by the plugin. Keys (exact):

```
job.schema_version, image_id, machine_id,
source_path, source_relative_path, source_sha256,
configuration_path, configuration_sha256,
classifier_path, classifier_sha256,
batch_root, dataset_root,
precheck_csv_path, precheck_result_path,
tile_profile_index, tile_width_px, tile_height_px,
attempt_number,
result_path, stdout_path, stderr_path,
processing_stage
```

`precheck_csv_path`, `precheck_result_path`, `result_path`, `stdout_path`,
and `stderr_path` must resolve under `batch_root`. `processing_stage` must
be exactly `CLASSIFICATION` or `RECONSTRUCTION`; missing or unknown values
are rejected. The classifier path/SHA are present on reconstruction jobs
too (for identity/metadata only) — `processReconstruction` never opens or
loads that file.

## Stage result properties (`<stage>/result.properties`)

Written by the plugin for whichever stage it ran, read by the CLI. Uses the
**same schema** as the combined result (see below); a stage-only result
simply leaves every combined-only field at `NA`. Keys (exact):

```
result.schema_version, status, reason_code, message,
image_id, machine_id,
source_filename, source_sha256, configuration_sha256,
classifier_filename, classifier_sha256,
attempt_number, tile_profile_index, tile_width_px, tile_height_px,
precheck_verified, mask_reproduced, scale_bar_reproduced,
classification_completed, reconstruction_completed,
required_outputs_validated, source_unchanged, out_of_memory_detected,
classification_run_id, reconstruction_run_id,
dataset_root,
original_rgb_path, approved_mask_path, masked_rgb_path,
masked_classified_path, reconstructed_classified_path,
lumen_measurements_path, artifact_diagnostics_path,
image_manifest_path, segment_manifest_path, processing_runs_path,
total_components, eligible_components, reconstructed_components,
changed_pixels,
source_checksum_before, source_checksum_after,
processing_time_ms,
failed_stage, classification_stage_reused,
classification_process_launched, classification_exit_code,
classification_outputs_validated, classification_stage_result_path,
reconstruction_process_launched, reconstruction_exit_code,
reconstruction_outputs_validated, reconstruction_stage_result_path
```

All dataset artifact paths (`original_rgb_path` through
`processing_runs_path`) are written **relative to `dataset_root`** as
portable forward-slash paths, so a `result.properties` file remains valid if
the dataset is later moved or copied elsewhere. Fields that do not apply to
a given outcome or stage are written as the CSV/property `NA` sentinel used
throughout this project (`RCsvSupport.NA`).

`source_checksum_before` / `source_checksum_after` are the same fast
in-memory RGB content checksum used elsewhere in this project (not a
SHA-256), computed only by `processClassification` (reconstruction never
reopens the source image, so both are `NA` there); a mismatch between them
indicates the source file was modified concurrently with classification and
forces that stage to fail even if every other step succeeded.

## Combined result properties (`attempt_<N>/result.properties`)

Written **only by the parent CLI process**, after both stages have resolved
(by success, failure, timeout, OOM, reuse, or a missing/unreadable stage
result). Uses the same schema as above, but is the only place where every
field is populated:

- `status` / `reason_code` / `message` reflect the **first** stage that did
  not complete successfully (classification checked before
  reconstruction); if both stages completed successfully, `status` is
  `FULL_SUCCESS`.
- `failed_stage` is `CLASSIFICATION`, `RECONSTRUCTION`, or `NA` (full
  success).
- `classification_stage_reused` is `TRUE` when
  `BatchClassificationResumeInspector` found a valid prior classification
  and no classification Fiji process was launched this attempt.
- `classification_process_launched` / `reconstruction_process_launched`
  record whether the parent actually invoked a Fiji process for that stage
  (reused classification: `FALSE`; reconstruction: always `TRUE`, since
  reconstruction is *always* attempted regardless of the classification
  outcome).
- `classification_exit_code` / `reconstruction_exit_code` are the Fiji
  process exit codes (`NA` when a stage was reused, never launched, or
  never finished, e.g. after a timeout).
- `classification_outputs_validated` / `reconstruction_outputs_validated`
  mirror `required_outputs_validated` from that stage's own result (`TRUE`
  for a reused classification, since a resumable dataset was already
  validated when it was written).
- `classification_run_id` / `reconstruction_run_id` are the dataset run IDs
  for each stage (from the reused dataset row, or from that stage's own
  result).
- `classification_stage_result_path` / `reconstruction_stage_result_path`
  point at each stage's own `result.properties` (`NA` when reused or
  missing).
- `out_of_memory_detected` is `TRUE` if either stage detected an OOM (via
  its own result, or via a case-insensitive `OutOfMemoryError` scan of that
  stage's captured stdout/stderr when its `result.properties` is missing).
- Dataset artifact paths, manifest paths, and component counts are merged
  in from whichever stage(s) actually ran and reported them (reconstruction
  values, when present, take precedence over classification's for
  overlapping fields such as `dataset_root`).

**When the final dataset is not already complete, reconstruction is still
attempted even after a classification failure or OOM.** This keeps the two
Fiji launches independent when work remains, and the combined result still
correctly reports `failed_stage=CLASSIFICATION` in that case. A fully
completed, valid dataset skips both launches (see "Reusing a fully
completed image" above).

## Rerun / attempt behavior

- `attempt_number` is part of both job schemas and the attempt's working
  directory (`full/<machine_id>/<image_id>/attempt_<N>/`), so repeated
  attempts for the same image do not overwrite each other.
- The CLI automatically determines the next attempt number by scanning
  `full/<machine_id>/<image_id>/attempt_*` for the highest existing `N` and
  using `N + 1` (or `1` if no prior attempts exist) — no manual bookkeeping
  needed between reruns.
- If a prior attempt already produced a valid, matching classification in
  the production dataset, a later attempt reuses it automatically (see
  "Resuming a prior classification" above) instead of re-running
  classification from scratch.

## Timeout behavior

If either stage's Fiji process exceeds `full.timeout_minutes` (see
`runtime/batch/default_batch.properties`), the CLI destroys/force-kills that
process, still captures its `stdout.log` / `stderr.log`, records that stage
as `FAILED_TIMEOUT` in the combined result, and (if it was the
classification stage) still attempts the reconstruction stage afterward.

## Source integrity

Source RGB content checksum is computed immediately after opening the image
in the classification stage (before mask/scale-bar reproduction and
classification) and again after classification/validation. Any difference
between the two values is treated as `INVALID_SOURCE` / `SOURCE_CHANGED` for
that stage, even if every processing step otherwise reported success.
Reconstruction never reopens the source image, so it has no equivalent
check.

## Exact run command

Unchanged from the single-process CLI — same arguments, same script:

```bash
runtime/batch/run_one_full_image.sh \
  "/path/to/input" \
  "/path/to/batch" \
  "machine_01" \
  "010203" \
  "/path/to/batch.properties" \
  "/path/to/classifier.model" \
  "$HOME/Downloads/Fiji.app/ImageJ-linux64" \
  "0"
```

Arguments, in order: input root, batch root, machine id, image id,
configuration path, classifier path, Fiji launcher path, tile profile index.
Internally this now launches up to two Fiji processes (classification, then
always reconstruction) instead of one.

## Exact inspection command

```bash
runtime/batch/inspect_one_full_image.sh "/path/to/batch" "machine_01" "010203" "1"
```

Arguments, in order: batch root, machine id, image id, attempt number.
Prints the combined `result.properties` (plus a status/reason/stage
summary), then, for each of the `classification/` and `reconstruction/`
stage directories: `job.properties`, `result.properties` (plus a short
summary), and the tail of `stdout.log` / `stderr.log`. A stage directory
that is absent (classification reused from the dataset) is reported as
such rather than as an error.
