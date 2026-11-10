# Validation

## Scope and status

The documented tests concern diagnostic ellipse fitting, inward-interval
detection, fill association, and diagnostic-copy eligibility. They do
not establish biological validation or production approval. All
thresholds remain provisional.

## Residual evidence

A unique inward interval alone is insufficient. After interval
selection, interval-excluded refitting, and fill-component association,
five scale-independent criteria are evaluated:

-   `minimumDepthSigma = (medianResidual - minimumResidual) / robustScale`
-   `meanDepthSigma = (medianResidual - meanResidual) / robustScale`
-   `coreFraction = corePointCount / intervalEdgeCount`
-   `normalizedIntegratedInwardExcess = integratedInwardExcess / (intervalEdgeCount * robustScale)`
-   `retainedRmsImprovementFraction = (baselineRetainedRms - refittedRetainedRms) / baselineRetainedRms`

Robust scale is based on normalized median absolute deviation (MAD).

## Prototype thresholds

All five criteria are mandatory:

  Criterion                              Threshold
  ------------------------------------ -----------
  `minimumDepthSigma`                        ≥ 4.0
  `meanDepthSigma`                           ≥ 3.0
  `coreFraction`                            ≥ 0.15
  `normalizedIntegratedInwardExcess`        ≥ 1.50
  `retainedRmsImprovementFraction`          ≥ 0.20

Structural requirements are also mandatory: exactly one
interval-associated fill component, all exterior seeds matched, and no
overlap with another Lumen. Circularity and associated-fill fraction are
secondary diagnostics only.

## Recorded threshold calibration

The minimum-depth threshold was provisionally reduced from 5.0 to 4.0
robust scales. Broad human-confirmed positive example 8 reached
`4.714939` while passing the other residual and structural criteria.

  -----------------------------------------------------------------------------------
  Example                 Label/interpretation    Recorded result
  ----------------------- ----------------------- -----------------------------------
  Positive thin-artifact  Positive                `STRONG_ARTIFACT_EVIDENCE`
  example 2                                       

  Clean negative example  Negative                `INSUFFICIENT_RESIDUAL_EVIDENCE`;
  7                                               minimum depth `3.131289`, with
                                                  other residual criteria also
                                                  failing

  Broad positive example  Positive                `STRONG_ARTIFACT_EVIDENCE`; minimum
  8                                               depth `4.714939`
  -----------------------------------------------------------------------------------

These are limited regression examples, not a broad validation dataset.

## Diagnostic-copy regression

-   Example 2: `READY_FOR_DIAGNOSTIC_COPY`.
-   Example 8: eligible after strong evidence and structural checks.
-   Example 7: `INSUFFICIENT_ARTIFACT_EVIDENCE`; no copy and no change
    mask.

When eligible, only primary interval-associated Wood pixels are changed
in the duplicate (raw `0` → raw `2`). The source is unchanged and the
diagnostic output is not automatically saved.

## Boundary checks

The custom exposed-edge boundary handling was compared with ImageJ core
tools, including `ThresholdToSelection`, `ShapeRoi`, and Wand
FOUR-/EIGHT-connected modes. The exact diagonal checkerboard policy uses
a right-turn same-face rule to preserve separate simple loops and
complete exposed-edge accounting. Other ambiguous lattice configurations
remain conservative failures.

These checks concern digital boundary construction, not biological
artifact classification.

## Limitations

-   Threshold calibration is provisional.
-   Additional labelled positive and negative examples are required.
-   No final biological threshold selection has been established.
-   Diagnostic reconstructed copies are not production reconstruction.
-   Production reconstruction remains disabled/not approved.
-   The documented tests do not establish performance for multiple
    artifacts per vessel, disconnected lumen fragments, non-elliptical
    vessels, or broad image populations.
