# Batch Phase 2 — Automatic Non-Interactive Precheck

Phase 2 validates each assigned TIFF with a **fresh Fiji process per image**.

It does **not**:

- run Weka
- run All Lumina reconstruction
- write production dataset images
- show dialogs
- register a visible Fiji menu command

## Architecture

1. Pure-Java `BatchPrecheckSupervisor` reads the Phase-1 machine manifest.
2. For each TIFF it writes one `job.properties`.
3. It launches Fiji headlessly with `--main-class vesselreconstruction.ellipse.BatchPrecheckImagePlugin`.
4. That process opens exactly one TIFF, reuses `AutomaticSampleMaskGenerator` and `AutomaticScaleBarDetector`, writes result properties + PNG previews, then exits.
5. The supervisor updates `precheck_results.csv` atomically and continues.

## Exact Fiji invocation

Discovered from `ImageJ-linux64 --help` / `--dry-run`:

```bash
/path/to/Fiji.app/ImageJ-linux64 \
  --headless \
  --no-splash \
  --allow-multiple \
  --full-classpath \
  --main-class vesselreconstruction.ellipse.BatchPrecheckImagePlugin \
  /absolute/path/to/job.properties
```

`--full-classpath` loads `jars/` and `plugins/` so the deployed plugin JAR is visible.

The entry point must **not** construct `new ImageJ(...)` under `--headless` (that throws `HeadlessException`). The plugin `main` processes the job file directly.

## Required TIFF content

- production core/segment filename (`PPTTCS.tif` / `.tiff`, e.g. `0101A1.tif`)
- RGB source
- automatic sample region suitable for the conservative mask policy
- red scale bar outside the sample (terminal-tick centerlines preferred)

Portable deployment still uses the previous temporary six-digit checkpoint until
a later re-pin phase.

## Configuration thresholds

See `runtime/batch/default_batch.properties` Phase-2 keys:

- mask downsample / expansion / crop padding / included-fraction bounds / major-span / secondary-component
- scale-bar physical length, minimum confidence, terminal-tick requirement, shaft continuity, expected µm/px, max relative deviation
- precheck timeout minutes and continue-after-failure

## Mask acceptance

Requires successful automatic mask generation, nonempty approved mask, valid crop inside the source, included fraction and major-span within bounds, and secondary competing component fraction within bound.

Rejection status: `REVIEW_REQUIRED_MASK` with stable reason codes such as `MASK_INCLUDED_FRACTION_TOO_SMALL`.

## Scale-bar acceptance

Requires successful detection, positive measured center-to-center length, confidence ≥ configured minimum, shaft continuity ≥ minimum, geometry outside the sample mask, optional `TERMINAL_TICK_CENTERLINES`, and:

```text
abs(measuredPixelSizeUm - expectedPixelSizeUm) / expectedPixelSizeUm
  <= maximum_relative_pixel_size_deviation
```

Default expected pixel size: `1.0954306217917742` µm/px with max relative deviation `0.01`.

Rejection status: `REVIEW_REQUIRED_SCALE_BAR`.

## Result statuses

`PRECHECK_SUCCESS`, `REVIEW_REQUIRED_MASK`, `REVIEW_REQUIRED_SCALE_BAR`, plus failure statuses (`INVALID_*`, `FAILED_*`).

## Preview files

Under `precheck/<machine_id>/previews/`:

- `<image_id>__mask_preview.png`
- `<image_id>__scale_bar_preview.png`

Diagnostic duplicates only. Source TIFFs are never modified.

## Exact run command

```bash
runtime/batch/run_precheck.sh \
  "/path/to/input" \
  "/path/to/batch" \
  "machine_01" \
  "/path/to/batch.properties" \
  "$HOME/Downloads/Fiji.app/ImageJ-linux64"
```

## Exact inspection command

```bash
runtime/batch/inspect_precheck.sh "/path/to/batch" "machine_01"
```

## Rerun behavior

- Matching `PRECHECK_SUCCESS` with same source and configuration SHA-256 is skipped.
- Stale source/config or incomplete results are rerun.
- Aggregate CSV never gains duplicate image rows.

## Timeout behavior

If Fiji exceeds `precheck.timeout_minutes`, the process is destroyed/force-killed and the image is recorded as `FAILED_TIMEOUT`.

## Source integrity

Source RGB checksum is computed before and after processing. Any change yields `INVALID_SOURCE`.
