# Batch Phase 1 — Manifest Planning Only

This phase **plans** unattended multi-computer work. It does **not**:

- open Fiji
- run Weka
- reconstruct lumina
- generate masks
- execute headless image processing
- merge dataset shards
- copy portable Fiji bundles

Phase 1 discovers source TIFFs, validates filenames against the six-digit grammar, partitions work across machines, and writes master/per-machine manifests plus human-readable reports.

## Filename grammar

Every source TIFF base name is exactly one six-digit image ID:

```text
PPSSRR
```

| Field | Meaning | Valid values |
|-------|---------|--------------|
| `PP` | plot ID | `01` or `02` |
| `SS` | tree/sample number | `01` through `20` |
| `RR` | physical replicate from the tree | `01` or `02` |

Accepted extensions (case-insensitive): `.tif`, `.tiff`.

The base name must equal the ID exactly. Names such as `010101-copy.tif`, `010101_1.tif`, `sample010101.tif`, or `010101.jpg` are rejected.

Replicates (`RR`) are separate physical samples. They are never treated as continuous image segments.

## Expected production set

A complete production collection contains **exactly 80** IDs:

```text
2 plots × 20 trees × 2 replicates = 80
```

Canonical order begins with `010101` and ends with `022002`.

## Small subset test mode

The current computer may test with only two or three smaller TIFFs. Copy the default configuration and disable the complete-set requirement:

```bash
cp runtime/batch/default_batch.properties /tmp/lumen_test_batch.properties
```

Then edit `/tmp/lumen_test_batch.properties`:

```properties
input.require_complete_expected_set=false
machines.count=1
```

Keep the production default at:

```properties
input.require_complete_expected_set=true
```

## Configuration keys

Exact Phase-1 keys in `runtime/batch/default_batch.properties`:

```properties
batch.schema_version=1
input.recursive=false
input.require_complete_expected_set=true
machines.count=1
```

Unknown keys, missing keys, blank values, and non-strict booleans are rejected.

The configuration fingerprint is SHA-256 of the **canonical text**:

- keys sorted lexicographically
- `key=value` lines
- `\n` endings
- trailing newline

Key order in the properties file does not affect the fingerprint.

## Machine partitioning

Assignment is **balanced contiguous** ranges over the canonically sorted TIFF list:

- machine IDs: `machine_01`, `machine_02`, …
- global TIFF order is preserved
- remainder goes to the first machines
- assignment sizes differ by at most one
- empty machine manifests are allowed (header only)

Example: 80 files / 3 machines → `27, 27, 26`.

## Manifest locations

Under `<batch-root>`:

| Path | Purpose |
|------|---------|
| `master_batch_manifest.csv` | Full ordered plan |
| `machines/machine_XX_manifest.csv` | Per-machine rows |
| `machine_assignment_summary.txt` | Counts and ranges |
| `batch_plan_report.txt` | Validation / planning status |

Manifests store **relative** source paths only. Initial row status is `PENDING` with `attempt_count=0`. Unavailable fields use `NA`. Dataset shard paths look like `output/lumen_dataset_machine_01`.

## Exact planner command

After `mvn package` (so `target/classes` exists):

```bash
runtime/batch/plan_batch.sh \
  "/path/to/small_test_tiffs" \
  "/path/to/test_batch" \
  "/tmp/lumen_test_batch.properties"
```

Equivalent Java invocation:

```bash
java -cp target/classes vesselreconstruction.ellipse.BatchManifestPlannerCli \
  --input-root "/path/to/small_test_tiffs" \
  --batch-root "/path/to/test_batch" \
  --config "/tmp/lumen_test_batch.properties"
```

## Exact inspection command

```bash
runtime/batch/inspect_batch_plan.sh "/path/to/test_batch"
```

## Failure meanings (CLI exit codes)

| Code | Meaning |
|------|---------|
| `0` | Success |
| `2` | Invalid arguments |
| `3` | Invalid configuration |
| `4` | Discovery or expected-set validation failure |
| `5` | Output-writing failure |
| `6` | Unexpected failure |

On failure, any earlier **valid** plan under the batch root is preserved; partial mixed plans are not committed.

## Test example (subset)

```bash
cp runtime/batch/default_batch.properties /tmp/lumen_test_batch.properties
# edit: input.require_complete_expected_set=false and machines.count=1

runtime/batch/plan_batch.sh \
  "/path/to/small_test_tiffs" \
  "/path/to/test_batch" \
  "/tmp/lumen_test_batch.properties"

runtime/batch/inspect_batch_plan.sh "/path/to/test_batch"
```

Do not start image processing in this phase.
