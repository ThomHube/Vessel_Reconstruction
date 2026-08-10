# Portable Machine Install

User-local isolated install of the Lumen Reconstruction checkpoint from a
portable hard-drive package. No root privileges are required.

## What this does

`install_machine.sh` copies and verifies the complete Lumen runtime into the
**current user's home directory**:

```text
$HOME/LumenRuntime/0c1c326
```

It also validates and installs the portable launcher as:

```text
$HOME/LumenRuntime/0c1c326/run_lumen_machine.sh
```

It does **not** modify:

- system Java
- system Maven
- system Fiji
- global `PATH`
- `/usr` or `/opt`
- `~/.bashrc` or `~/.profile`

Unrelated installed software is left untouched.

**Git does not need to be installed** on a processing machine. The local project
is restored from the immutable source archive
`canonical/source/minimal-ij1-plugin-ellipse_0c1c326.tar.gz`. The complete Git
repository/history remains separately on the external drive as
`canonical/source/minimal-ij1-plugin-ellipse_0c1c326.bundle` and is only needed
if someone later wants to restore the development repository from that bundle.

## Deployed layout on the drive

`LumenPortable/install/` must contain both:

```text
LumenPortable/
  install/
    install_machine.sh
    run_lumen_machine.sh
    PORTABLE_MACHINE_INSTALL.md
    PORTABLE_MACHINE_RUN.md
  canonical/
    ...
```

The installer discovers `LumenPortable` from its own script path. It does not
hard-code host-specific mount points.

Before changing the local machine, the installer requires
`run_lumen_machine.sh` next to `install_machine.sh`, verifies its fixed
SHA-256, copies it into the staged install root, marks it executable
(`chmod 755`), and checks `bash -n` on the staged copy.

## Usage

Copy/run from the drive as a **script** (do not `source`):

```bash
cd /path/to/LumenPortable/install
./install_machine.sh machine_01
```

Allowed machine IDs: `machine_01` … `machine_99`.

After a successful install, verify with:

```bash
$HOME/LumenRuntime/0c1c326/run_lumen_machine.sh --verify-only
```

Full processing orchestration is a later deployment phase. Phase 4C1 currently
supports `--verify-only` only.

## What the installer verifies

Before publishing the final directory it checks, among other things:

- required canonical archives and classifier exist
- portable launcher exists beside the installer and matches the fixed SHA-256
- extracted source tree contains required project files
  (`pom.xml`, batch scripts, key Java sources)
- production checkpoint recorded as
  `0c1c326fc07f7e049f1d359226e810c7b8db24ce`
- Classifier9.model SHA-256
- Fiji launcher `ImageJ-linux64` is executable
- Fiji plugin JAR SHA-256
- portable `java` / `javac` run
- portable Maven reports Java 21 using only the portable JDK
- Maven repository is non-empty
- staged launcher SHA-256 + `bash -n`

Installation is staged under `$HOME/LumenRuntime/.install_staging_0c1c326_<pid>`
and moved atomically to `$HOME/LumenRuntime/0c1c326` only after all checks pass.

If that exact install directory already exists and `installation.properties`
matches this checkpoint **including** validated `launcher_path` /
`launcher_sha256`, the script exits successfully without changing it.

An older installation that predates launcher integration is **not** accepted or
auto-upgraded; recreate it intentionally.

## Intentionally not done yet

This installer does **not**:

- launch Fiji
- run `mvn package`
- process TIFFs
- run precheck
- create a batch manifest
- require or invoke system Git
- enable full one-command processing (later deployment phase)

Batch processing is a later step after a successful install and verify.
