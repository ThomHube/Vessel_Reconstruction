# Portable Machine Run (Phase 4C1)

`run_lumen_machine.sh` is the user-local launcher for an installed
`LumenRuntime/0c1c326` tree. It discovers the installation root from its own
path and uses only the portable JDK, Maven, Fiji, classifier, and config that
`install_machine.sh` placed under that root.

## Phase 4C1 scope

Currently supported:

```bash
./run_lumen_machine.sh --verify-only
```

This verifies the installation and ensures controller classes exist. **No images
are processed** in Phase 4C1. Full one-command processing will be added in the
next deployment phase.

Any invocation without `--verify-only` exits nonzero with a clear message that
processing orchestration is not enabled yet.

## Isolated tools

Verification and any offline compile use:

- portable `jdk21` (`JAVA_HOME` / `PATH` limited for that command only)
- portable Maven with `-o` and `-Dmaven.repo.local=<installed maven_repository>`
- installed Fiji (checked, never launched in 4C1)

System Java/Maven and `~/.m2` are not used. Global `PATH`, `~/.bashrc`, and
`~/.profile` are not modified.

## Controller classes

The project restored from the canonical source archive initially has no
`target/classes`. The first `--verify-only` run may compile offline:

```text
mvn -o -Dmaven.repo.local=<maven_repository> -DskipTests compile
```

Maven output goes to `logs/controller_offline_build.log`.

Later verifies skip the compile when both of these already exist:

- `target/classes/.../BatchMachineSupervisorCli.class`
- `target/classes/.../BatchFullProcessingCli.class`

## Not done in Phase 4C1

- no Fiji launch
- no `run_machine.sh` execution
- no batch manifests
- no precheck
- no TIFF processing
