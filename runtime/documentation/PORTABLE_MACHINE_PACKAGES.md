# Portable Machine Packages (Phase 4C4B1)

Create **one** analysis-host package from an **already validated**
central job produced by `prepare_master_batch.sh` (PPTTCS / manifest schema 2).
The package contains every production TIFF (`machines.count=1`).

## Usage

```bash
./create_machine_packages.sh <job-root>
```

Requires `preparation.properties` with:

- `portable_plan_schema_version=2`
- `manifest_schema_version=2`
- positive actual `valid_tiff_count`
- PPTTCS `first_image_id` / `last_image_id` derived from the master plan

Creates:

```text
<job-root>/machine_packages/
  machine_01/
    input/          # all production TIFFs (relative paths preserved)
    batch/machines/machine_01_manifest.csv
    production_batch.properties
    package.properties
    package_checksums.sha256
```

If `machine_packages/` already exists, the script fails and leaves it unchanged.

Also writes:

```text
<job-root>/launchers/
  lumen_machine_start.sh
  START_machine_01_<coreId>.sh
  START_single_machine.sh
```

`START_single_machine.sh` is the canonical one-host launcher (same payload as
`START_machine_01_<coreId>.sh`). `machine_id=machine_01` remains the identity of
the analysis host.

Each generated `START_*.sh` encodes only machine ID, core ID, and
`../machine_packages/<machine-id>`. Shared one-click logic lives in
`lumen_machine_start.sh` / `lumen_machine_autopilot.sh` (see
`PORTABLE_MACHINE_AUTOPILOT.md`).


The single analysis-host package contains every production TIFF (non-empty
manifest). `machine_count` other than 1 is rejected.

## What the package contains

- every production TIFF assigned to the analysis host (`machine_01`)
- the authoritative schema-2 machine manifest (byte-for-byte)
- the authoritative `production_batch.properties` (exact file copy)
- `package.properties` provenance with `package.schema_version=2` and
  `manifest.schema_version=2` (checkpoint, counts, hashes) **without**
  mount-specific absolute paths
- `package_checksums.sha256` for post-transfer verification via `sha256sum -c`

## Integrity

Manifest rows are parsed by header name. Each row must have
`schema_version=2`, a valid PPTTCS `image_id`, and identity fields that match
decomposition of that ID. Before and after each TIFF copy, SHA-256 must match
`source_sha256` from the machine manifest. Source TIFFs under `input_root` are
never modified.

## What this phase does not do

- no Fiji / Maven / Java
- no precheck
- no classification or reconstruction
- no import into a laboratory `LumenRuntime` install (next step)

## Next step

Import the `machine_01` package onto the analysis host and run portable
verification / processing orchestration (`START_single_machine.sh`).
