# Portable Machine Package Import (Phase 4C4B1)

Import **one** verified machine package into an installed laboratory
`LumenRuntime` after:

1. `install_machine.sh` has installed `$HOME/LumenRuntime/9a53b84`
2. Central prepare/package scripts have produced schema-2 PPTTCS packages

## Usage

```bash
./import_machine_package.sh <machine-package-root>
```

The package machine ID must **exactly** match the installed machine ID from
`installation.properties`. A `machine_01` package will not import onto
`machine_02`.

Requires:

- `package.schema_version=2`
- `manifest.schema_version=2`
- PPTTCS `first_image_id` / `last_image_id`
- schema-2 machine manifest parsed by header name

## What happens

1. Verify the local installation (`installation.properties`, work root).
2. Reject any symbolic links in the source package.
3. Parse `package.properties` (never sourced as shell).
4. Validate every path in `package_checksums.sha256`, then run `sha256sum -c`.
5. Re-check config/manifest/TIFF hashes and PPTTCS identity against the package.
6. Copy (never move) contents into a staging directory under
   `work/<machine-id>/.job_import_staging_<pid>`.
7. Re-verify the staging copy, then atomically publish:

```text
$HOME/LumenRuntime/9a53b84/work/machine_XX/job/
```

Generated `import.properties` uses `import.schema_version=2`.

## Idempotence

If `work/<machine-id>/job` already exists and matches the supplied package
identity (checkpoint, package.properties SHA, checksums SHA, counts, config and
manifest hashes) and `sha256sum -c package_checksums.sha256` still passes, the
script exits successfully with **ALREADY IMPORTED** and makes no changes.

A different package against an existing job fails clearly without mutation.

## Intentionally not done

- no Fiji / Maven / Java
- no precheck
- no classification or reconstruction
- installer-created `work/.../input|batch|output` are left untouched
