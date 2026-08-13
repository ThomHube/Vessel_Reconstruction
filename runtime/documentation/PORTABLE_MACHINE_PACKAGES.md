# Portable Machine Packages (Phase 4C4B1)

Create machine-specific distribution packages from an **already validated**
central job produced by `prepare_master_batch.sh` (PPTTCS / manifest schema 2).

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
    input/          # assigned TIFFs (relative paths preserved)
    batch/machines/machine_01_manifest.csv
    production_batch.properties
    package.properties
    package_checksums.sha256
  machine_02/
    ...
```

If `machine_packages/` already exists, the script fails and leaves it unchanged.

Every configured machine must receive at least one physical core (non-empty
manifest). Empty machine slices fail clearly so operators can reduce
`machines.count`.

## What each package contains

- only the TIFFs assigned to that machine (core groups are never split)
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

Import one package into an installed laboratory machine and run portable
verification / processing orchestration.
