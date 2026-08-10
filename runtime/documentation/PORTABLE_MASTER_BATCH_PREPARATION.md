# Portable Master Batch Preparation (Phase 4C2A)

Create **one** authoritative central batch plan on the preparation/master
computer **before** the external drive is taken to laboratory PCs.

Individual lab machines must **not** independently repartition the dataset.

## Usage

Requires an installed portable runtime at `$HOME/LumenRuntime/0c1c326`.

```bash
./prepare_master_batch.sh \
  <all-tiffs-input-root> \
  <portable-job-root> \
  <machine-count>
```

- `all-tiffs-input-root` — read-only source directory of the complete TIFF set
- `portable-job-root` — new job directory (must not already exist)
- `machine-count` — integer `1` … `99`

## What this phase does

1. Calls `run_lumen_machine.sh --verify-only` so portable controller classes exist.
2. Copies `default_batch.properties` to `production_batch.properties` and sets
   only `machines.count=<N>` (keeps `input.require_complete_expected_set=true`).
3. Runs the existing `BatchManifestPlannerCli` with portable Java against the
   source TIFF root.
4. Validates structure, order, config SHA-256 consistency, and machine slices.
5. Publishes the job root atomically from a staging directory.

## Ordering and assignment

All 80 TIFFs are ordered by six-digit `PPSSRR` (plot / tree / replicate).
Assignments are contiguous ranges; remainder goes to the earliest machines.

For **three** machines the validated planner yields:

| Machine | Count |
|---------|-------|
| machine_01 | 27 |
| machine_02 | 27 |
| machine_03 | 26 |

First image: `010101`. Last image: `022002`.

## Configuration hashes

`BatchConfiguration` has a **canonical application-level SHA-256**
(`BatchConfiguration.sha256()`). That value is written into
`master_batch_manifest.csv` / machine manifests and is what precheck and full
processing use for configuration consistency.

The `production_batch.properties` file also has a **raw byte-level SHA-256**
(`sha256sum` of the file). That hash verifies the exact properties file was
copied unchanged.

Both are recorded in `preparation.properties`:

- `production_config_sha256` / `production_config_canonical_sha256` — Java canonical hash
- `production_config_file_sha256` — raw file hash

They are intentionally **not** expected to be identical.

## Job root layout

```text
<job-root>/
  production_batch.properties
  preparation.properties
  preparation.log
  plan/
    master_batch_manifest.csv
    machine_assignment_summary.txt
    batch_plan_report.txt
    machines/
      machine_01_manifest.csv
      ...
```

The exact `production_batch.properties` and manifests are authoritative. Each lab
machine will later receive only its assigned TIFFs plus this plan/config
(unchanged config SHA-256 must match manifest rows).

## Intentionally not done yet

- no TIFF copies into machine installations
- no Fiji / precheck / classification / reconstruction
- no processing outputs
- no changes under `LumenPortable/canonical`
- source TIFF directory is never modified
