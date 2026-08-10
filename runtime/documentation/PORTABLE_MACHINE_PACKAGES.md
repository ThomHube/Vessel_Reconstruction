# Portable Machine Packages (Phase 4C2B)

Create machine-specific distribution packages from an **already validated**
central job produced by Phase 4C2A (`prepare_master_batch.sh`).

## Usage

```bash
./create_machine_packages.sh <job-root>
```

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

## What each package contains

- only the TIFFs assigned to that machine
- the authoritative machine manifest (byte-for-byte)
- the authoritative `production_batch.properties` (exact file copy)
- `package.properties` provenance (checkpoint, counts, hashes) **without**
  mount-specific absolute paths
- `package_checksums.sha256` for post-transfer verification via `sha256sum -c`

## Integrity

Before and after each TIFF copy, SHA-256 must match `source_sha256` from the
machine manifest. Source TIFFs under `input_root` are never modified.

`package_checksums.sha256` uses paths relative to the machine package root and
is verified with `sha256sum -c` before publication.

## What this phase does not do

- no Fiji / Maven / Java
- no precheck
- no classification or reconstruction
- no import into a laboratory `LumenRuntime` install (next phase)

## Next step

Import one package into an installed laboratory machine and run portable
verification / processing orchestration.
