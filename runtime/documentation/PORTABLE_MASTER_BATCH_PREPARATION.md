# Portable Master Batch Preparation (Phase 4C4B1)

Create **one** authoritative central batch plan on the preparation/master
computer **before** the external drive is taken to laboratory PCs.

Individual lab machines must **not** independently repartition the dataset.

## Usage

Requires an installed portable runtime at `$HOME/LumenRuntime/6dd42df`.

```bash
./prepare_master_batch.sh \
  <all-tiffs-input-root> \
  <portable-job-root> \
  <machine-count>
```

- `all-tiffs-input-root` — read-only source directory of production TIFFs
  (`tiffs/<segment_id>/<segment_id>.tif`; do not flatten)
- `portable-job-root` — new job directory (must not already exist)
- `machine-count` — integer `1` … `99`

## Authoritative TIFF layout

Production sources are nested one directory below the TIFF root:

```text
tiffs/
  0105A2/
    0105A2.tif
```

There are no TIFFs directly in `tiffs/`. Do **not** move segments out of their
folders. `prepare_master_batch.sh` therefore always writes
`input.recursive=true` into `production_batch.properties`, regardless of
`default_batch.properties`. Nested `source_relative_path` values
(`0105A2/0105A2.tif`) are preserved through machine-package creation and
machine import.

## What this phase does

1. Calls `run_lumen_machine.sh --verify-only` so portable controller classes exist.
2. Writes `production_batch.properties` from `default_batch.properties`, setting
   `machines.count=<N>`, forcing
   `input.require_complete_expected_set=false` (PPTTCS has no fixed expected set),
   and forcing `input.recursive=true` for the nested production TIFF layout.
3. Runs the existing `BatchManifestPlannerCli` with portable Java against the
   source TIFF root.
4. Validates schema-2 manifests, PPTTCS identity, canonical order, configuration
   SHA-256 consistency, and **core-preserving** machine assignment.
5. Publishes the job root atomically from a staging directory.

## Identity and ordering

Every TIFF base name is a production image ID `PPTTCS`:

| Field | Meaning | Valid values |
|-------|---------|--------------|
| `PP` | plot | two digits, not `00` |
| `TT` | tree | two digits, not `00` |
| `C` | physical core | `A` or `B` |
| `S` | segment order | `1`–`5` |

Examples: `0101A1`, `0101A2`, `0101B1`, `0207B5`.

Canonical order: plot ascending, tree ascending, core A before B, segment
ascending. Segment `1` is closest to bark; higher numbers move toward pith/center.
A physical core has 1–5 segments. All segments of one `core_id` stay on the same
machine.

Total TIFF count is **variable** (derived from the master manifest).
`first_image_id` / `last_image_id` are the actual first/last canonical rows, not
fixed constants.

## Manifest schema

Java `BatchManifestSchema.SCHEMA_VERSION` is **2**. Portable preparation records:

- `portable_plan_schema_version=2`
- `manifest_schema_version=2`

Old schema-1 six-digit manifests are rejected.

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
- continuous physical coordinates across multi-segment cores remain a later phase
