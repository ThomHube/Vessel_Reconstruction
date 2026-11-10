# Data Format

## Classified input

The analyzer expects an active 8-bit Trainable Weka result image using:

    Weka class Meaning        Raw result pixel
  ------------ ------------ ------------------
             1 Wood                          0
             2 Ray                           1
             3 Lumen                         2
             4 Background                    3

Raw numeric values define class identity; display colours do not. Lumen
extraction uses value `2`.

## Tiled Weka validation inputs

The tiled validation mode loads a saved Trainable Weka `.model` and an
original RGB sample image. It creates a downsampled automatic sample
mask for user review/editing before classification.

-   Core tile size is configured in pixels; documented default: 2048 ×
    2048.
-   Physical calibration is established while the original RGB image is
    available.
-   Scale options include automatic red scale-bar detection with
    user-entered physical value (no OCR), manual scale-bar line, entered
    µm/px, or valid micrometre metadata.
-   300-PPI/inch TIFF metadata is rejected as a physical micrometre
    calibration.
-   Default cropped mode writes a crop-sized 8-bit label image.
    Crop-local coordinates begin at `(0,0)`; original coordinates
    require crop offsets.
-   Full-size output is available when crop output is disabled.
-   Tiles with no included mask pixels are skipped.
-   Outside the approved mask, labels are forced to Background (class 4
    / raw value `3`).
-   Original RGB pixels are preserved unchanged.

## Three spatial levels

1.  **Physical sample segment:** real sample piece identified by
    sample/segment IDs.
2.  **Valid analysis crop:** rectangular crop derived from the approved
    mask, optionally padded.
3.  **Weka classification tile:** temporary subdivision for memory
    management.

Classification tiles are not physical segments. Global segment
coordinates require orientation and, where available, a global start
position.

## Dataset tree

The documented dataset root defaults to `lumen_dataset`:

``` text
lumen_dataset/
├── images/
│   ├── original_rgb/
│   ├── approved_masks/
│   ├── masked_rgb/
│   ├── masked_classified/
│   └── reconstructed_classified/
├── data/
│   ├── lumen_measurements/
│   │   ├── per_segment/
│   │   └── lumen_measurements_all.csv
│   ├── manifests/
│   │   ├── image_manifest.csv
│   │   ├── segment_manifest.csv
│   │   └── processing_runs.csv
│   ├── diagnostics/
│   │   ├── per_segment/
│   │   └── artifact_diagnostics_all.csv
│   └── data_dictionary.csv
└── logs/
```

## Stage ownership

  -----------------------------------------------------------------------
  Stage                               Writes
  ----------------------------------- -----------------------------------
  Tiled Weka Validation               Original RGB, approved mask, masked
                                      RGB, masked classified TIFF,
                                      classification manifest fields,
                                      segment row, successful
                                      processing-run record,
                                      classification log

  All Lumina                          Reconstructed classified copy,
                                      per-segment measurements and
                                      diagnostics, reconstruction
                                      manifest fields, aggregate refresh,
                                      successful processing-run record,
                                      reconstruction log
  -----------------------------------------------------------------------

The masked classified TIFF is the immutable input to All Lumina. It is
not overwritten by reconstruction. The original RGB is also unchanged.

## Canonical filenames

Names use sanitized sample/segment IDs:

-   `<safe_sample>__<safe_segment>__original_rgb.tif`
-   `...__approved_mask_ds<factor>.tif`
-   `...__masked_rgb.tif`
-   `...__masked_classified.tif`
-   `...__reconstructed_classified.tif`
-   `...__lumen_measurements.csv`
-   `...__artifact_diagnostics.csv`
-   `<run_id>.log`

## Manifests and collision policy

Manifest files:

-   `image_manifest.csv`
-   `segment_manifest.csv`
-   `processing_runs.csv`
-   `data_dictionary.csv`

Classification tile counts and outside-mask forced-Background counts are
recorded in classification logs, TIFF metadata, and the image manifest,
not reconstruction-specific columns in `processing_runs.csv`.

Collision handling: write if missing; reuse if SHA-256 is identical;
abort on conflict. Do not silently create suffixed duplicates.

## CSV conventions

-   UTF-8
-   comma delimiter
-   decimal point
-   `TRUE` / `FALSE`
-   missing values as `NA` (not Java `NaN`)
-   RFC-style escaping
-   aggregate files rebuilt from per-segment files, not blindly appended

## Measurement semantics

The documented main measurement geometry is the final lumen geometry
after accepted reconstruction (original Lumen plus accepted
Wood-to-Lumen pixels). This is a schema/measurement definition, not
proof that any particular processing run performed reconstruction.

Other conventions:

-   `ellipse_axis_ratio_minor_major = minor / major`
-   wall length uses an 8-connected Moore chain
    (`MOORE_CHAIN_8_CONNECTED`)
-   nearest-lumen scope is `SEGMENT` among valid main-table candidates
-   `measurement_valid == TRUE` is the recommended R filter

## Supported scope and output caveat

Supported diagnostic target: one connected approximately elliptical
Lumen, at most one connected inward Wood artifact, strong residual
evidence, exactly one associated fill component, no other-Lumen overlap,
and Wood-only candidate pixels.

Unsupported: multiple artifacts in one vessel, split/disconnected lumen
reconnection, subtractive false-Lumen removal, non-elliptical
reconstruction, automatic directory processing, and stack processing.

A diagnostic reconstructed copy is an in-memory review image and is not
automatically saved. The dataset layout includes a
`reconstructed_classified` destination, but the README also states that
production reconstruction/automatic saving is not implemented or
approved in the current diagnostic state. The existence of a folder or
planned filename is not evidence of a successful production
reconstruction.
