# Vessel Reconstruction - Robust Ellipse Prototype

Independent Fiji/ImageJ 1 plugin project for the **Robust Ellipse Reconstruction** algorithm.

This repository is independent from the legacy contour project. The old contour bridgepoint algorithm is not present in this repository.

**Project directory:** `/home/thomas/IdeaProjects/minimal-ij1-plugin-ellipse`

**Algorithm name:** Robust Ellipse Reconstruction

## Fixed Weka classifier mapping

Trainable Weka Segmentation class values cannot be renamed. This project uses:

### Trainable Weka class numbers versus result pixel values

Human-facing class order:

| Class | Label |
|-------|-------|
| 1 | Wood |
| 2 | Ray |
| 3 | Lumen |
| 4 | Background |

Direct "Create result" 8-bit encoding:

| Pixel value | Class | Label |
|-------------|-------|-------|
| 0 | 1 | Wood |
| 1 | 2 | Ray |
| 2 | 3 | Lumen |
| 3 | 4 | Background |

The class mapping has not changed. Only the raw result-image encoding is zero-based.

The analyzer operates on result pixel value 2 when extracting Class 3 Lumen.

## Plugin modes

### 1. Detailed single-lumen development mode

Menu: **Robust Ellipse Reconstruction Prototype**

- processes the currently selected/largest Lumen
- opens detailed diagnostic windows
- used for algorithm development and debugging
- source image remains unchanged

### 2. All-lumen diagnostic mode

Menu: **Robust Ellipse Reconstruction - All Lumina**

- processes every Class 3 Lumen component independently
- one result row per component
- no per-lumen diagnostic windows
- at most: one ResultsTable, one decision overview, one combined reconstructed copy, one combined change mask
- `SUMMARY_ONLY` shows those combined outputs
- `SILENT` creates no windows, tables, or dialogs; only a concise log summary
- all components are analyzed against the unchanged original classified image
- accepted non-conflicting Wood→Lumen candidates are combined into one diagnostic duplicate
- source image remains unchanged
- no automatic saving
- directory/stack processing is not implemented

Supported all-lumen scope:

- one connected approximately elliptical Lumen
- at most one connected inward Wood artifact per Lumen
- strong residual evidence
- exactly one associated fill component
- no other-Lumen overlap
- Wood-only candidate pixels

Unsupported all-lumen scope:

- multiple artifacts in one vessel
- split or disconnected lumen reconnection
- subtractive false-Lumen removal
- non-elliptical vessel reconstruction
- automatic directory processing
- stack processing

Planned later architecture:

Directory Dataset Runner
→ loads classified TIFF
→ calls `AllLumenBatchProcessor` in `SILENT` mode
→ writes one reconstructed TIFF
→ writes one change-mask TIFF
→ appends one CSV results file
→ closes all images
→ continues with the next file

The future dataset runner will use the same headless processor and will not create hundreds of visible ImageJ windows.

## Current state

### Implemented

- active 8-bit classified-image validation
- Class 3 connected-component extraction using 8-connectivity
- deterministic largest-component selection
- exact exposed pixel-edge midpoint support extraction
- initial free ellipse fit to all support points
- initial proposed-fill preview
- diagnostic visualization
- mathematical free-ellipse model and deterministic fitter
- deterministic exposed directed pixel-edge construction
- conservative closed-loop extraction
- deterministic outer-loop selection
- ordered outer-boundary residual profile
- residual visualization and profile plot
- robust residual scale using normalized MAD
- strict inward-outlier core threshold
- expansion threshold
- circular contiguous interval extraction
- deterministic dominant-interval selection
- diagnostic interval visualization
- outer-boundary-only baseline ellipse fit
- exclusion of exactly one diagnostically selected inward interval from fit support
- one free-ellipse refit
- baseline-versus-refit parameter diagnostics
- baseline-versus-refit residual diagnostics
- diagnostic comparison visualization
- frozen single-interval refitted ellipse fill preview
- exact pixel-center inclusion test
- original-class accounting of proposed fill
- baseline-versus-refit fill comparison
- additive preview lumen
- exposed-edge preview perimeter
- secondary circularity diagnostics
- source-image-safe visualization
- 8-connected proposed-fill component extraction
- exact selected-Lumen cardinal contact diagnostics
- interval-exterior seed derivation
- exact interval-to-fill component association
- deterministic primary associated-component identification
- whole-ellipse versus interval-associated fill comparison
- interval-associated preview perimeter and circularity diagnostics
- unobscured diagnostic visualization
- first-failure ambiguous lattice-vertex diagnostics for ordered-boundary extraction
- ImageJ core boundary reference audit (ThresholdToSelection, Wand FOUR/EIGHT)
- diagnostic artifact-evidence assessment after interval, refit, and fill-component stages

The plugin analyzes the active 8-bit classified image, selects the largest Class 3 lumen component, fits one initial free ellipse, detects a contiguous inward-outlier interval, performs one outer-boundary baseline fit and one interval-excluded refit, builds a whole-ellipse fill preview, splits proposed fill into 8-connected components associated to the selected interval via exact exterior seeds, and then applies a conservative diagnostic artifact-evidence gate.

8-connected lumen components can contain diagonal pixel contacts. A diagonal contact may create a lattice vertex with multiple unused outgoing exposed directed edges.

ImageJ reference evidence for exact checkerboard contacts:

- ThresholdToSelection fully accounted for the exposed-edge union, but its main left-turn outline contained repeated non-closing vertices
- Wand EIGHT_CONNECTED chose the left turn and returned one seed outline
- Wand FOUR_CONNECTED chose the right turn and returned a simple seed outline

The project therefore adopts `RIGHT_TURN_SAME_FACE` only for exact diagonal checkerboard occupancy (`DIAGONAL_NW_SE` or `DIAGONAL_NE_SW` with exactly one RIGHT_TURN and one LEFT_TURN candidate). The extractor continues tracing all remaining edges, unlike a single Wand invocation, producing separate simple loops with complete exposed-edge accounting.

- component membership remains 8-connected
- boundary loops at point contacts are separate
- all other ambiguous lattice configurations remain conservative failures
- ImageJ boundary APIs remain diagnostic references
- no external contour dependency was added
- ellipse fitting and biological residual logic remain unchanged

`RIGHT_TURN_SAME_FACE` is a narrow digital-topology rule. It does not detect artifacts. It does not approve reconstruction. It does not modify any classified pixels.

`AMBIGUOUS_BOUNDARY_VERTEX` remains a conservative diagnostic stop for unresolved non-diagonal ambiguities.

### Artifact evidence gate

Interval uniqueness is not sufficient evidence. A sole interval (including
`dominanceRatio=Infinity`) no longer implies biological importance.

After interval selection, interval-excluded refit, and proposed-fill component
association, the prototype evaluates scale-independent residual evidence:

- minimumDepthSigma = (medianResidual - minimumResidual) / robustScale
- meanDepthSigma = (medianResidual - meanResidual) / robustScale
- coreFraction = corePointCount / intervalEdgeCount
- normalizedIntegratedInwardExcess = integratedInwardExcess / (intervalEdgeCount * robustScale)
- retainedRmsImprovementFraction = (baselineRetainedRms - refittedRetainedRms) / baselineRetainedRms

Conservative prototype thresholds:

- minimumDepthSigma >= 4.0
- meanDepthSigma >= 3.0
- coreFraction >= 0.15
- normalizedIntegratedInwardExcess >= 1.50
- retainedRmsImprovementFraction >= 0.20

Structural association also requires exactly one interval-associated fill
component, all exterior seeds matched, and no other Lumen overlap.

Circularity and associated fill fraction remain secondary diagnostics and do
not control the evidence decision.

### Provisional minimum-depth calibration

The original minimum-depth threshold was 5.0 robust scales. Broad human-confirmed
positive example 8 reached only 4.714939 robust scales while still passing mean
depth, core fraction, integrated excess, retained-wall RMS improvement, and all
structural checks. The minimum-depth threshold was therefore provisionally
calibrated to 4.0.

Verified regression outcomes under the calibrated thresholds:

- positive thin-artifact example 2 → `STRONG_ARTIFACT_EVIDENCE` (still well above 4.0)
- clean negative example 7 → `INSUFFICIENT_RESIDUAL_EVIDENCE` (3.131289, and all other residual criteria also fail)
- broad positive example 8 → `STRONG_ARTIFACT_EVIDENCE` (4.714939 >= 4.0)

All five residual criteria remain mandatory. All structural requirements remain
mandatory. No image-specific code was added. Circularity and associated fill
fraction remain secondary diagnostics.

This calibration is provisional. It permits diagnostic reconstructed copies but
does not constitute final biological validation or production approval.
Additional labelled examples are still required before final threshold selection
or production reconstruction.

### Diagnostic reconstructed copy

When conservative artifact evidence is strong and the structural association
checks pass, the plugin may create a diagnostic reconstructed image copy:

- the active classified `ByteProcessor` is copied; the source is never modified
- only the primary interval-associated proposed-fill component pixels are changed
- only raw Weka result value `0` (Wood) is converted to raw value `2` (Lumen)
- Ray, Background, unknown, and existing Lumen pixels are never converted
- non-associated ellipse-fill components remain unchanged
- observed Lumen pixels are not removed
- a binary change mask and a two-panel review image are shown
- output is never saved automatically
- clean or insufficient-evidence vessels create no reconstructed copy

Verified regression outcomes for the copy gate:

- positive example 2 → `READY_FOR_DIAGNOSTIC_COPY` (primary associated Wood → Lumen)
- broad positive example 8 → eligible for diagnostic copy once evidence is strong
- clean example 7 → `INSUFFICIENT_ARTIFACT_EVIDENCE` (no copy, no change mask)

This remains diagnostic reconstruction only. Evidence thresholds are unchanged
and still provisional. Broader positive and negative validation is still
required. Production reconstruction remains disabled.

### External / core sources evaluated

- ImageJ Wand FOUR_CONNECTED
- ImageJ Wand EIGHT_CONNECTED
- ImageJ ThresholdToSelection and ShapeRoi
- IJBlob
- Ellipse Split
- MorphoLibJ
- ImageJ EllipseFitter
- TrackMate contour ellipse fitting
- ImageJ Ops DefaultContour

### Current reuse decision

- ImageJ core boundary APIs are used as independent diagnostic references
- no external contour implementation has replaced OrderedBoundaryExtractor
- IJBlob is not added as a dependency
- MorphoLibJ is not added as a dependency
- ImageJ Ops is not added as a dependency
- the custom free-ellipse fitter remains in use
- the biological residual, interval and fill-association logic remains custom

The diagonal checkerboard topology policy for exact contacts is `RIGHT_TURN_SAME_FACE`.
All other ambiguous lattice configurations remain rejected.

Detection, refit, fill preview, component association, evidence assessment, and
the optional diagnostic reconstructed copy are diagnostic only. The selected
interval is excluded only from mathematical fit support. Fill previews are
additive and in-memory. The classified source image is never modified. When a
diagnostic copy is created, only primary interval-associated Wood pixels become
Lumen in that independent duplicate. Circularity remains a secondary diagnostic.

### Not implemented

- component acceptance thresholds
- ellipse acceptance
- automatic choice between whole and associated fill
- source-image modification
- automatic saving of a reconstructed classified image
- directory dataset iteration
- stack processing
- iterative interval exclusion
- broader non-elliptical vessel handling
- repeated interval detection after refitting
- stopping based on parameter stability
- production reconstruction

The classified source image is never modified. Circularity is not optimized and the ellipse is not forced to become a circle.

## Planned workflow

1. Identify one Class 3 lumen component
2. Extract its observed boundary
3. Fit a free ellipse with center, two independent axes, and rotation
4. Measure signed boundary deviations
5. Exclude a statistically distinct contiguous inward outlier arc
6. Refit until the ellipse stabilizes
7. Freeze the accepted ellipse
8. Preview non-lumen pixels inside the frozen ellipse
9. Keep production reconstruction disabled until explicitly approved

## Design constraints

- The ellipse is never forced to become a circle.
- Circularity is not the ellipse-fitting objective.
- Reconstruction remains disabled during early diagnostic development.
- The old contour bridgepoint algorithm is not present in this repository.

## Build

```bash
cd ~/IdeaProjects/minimal-ij1-plugin-ellipse
mvn clean package
```

Generated JAR:

`target/VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar`

## Install in Fiji

```bash
cd ~/IdeaProjects/minimal-ij1-plugin-ellipse
mvn clean package
cp target/VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar \
  ~/Downloads/Fiji.app/plugins/
~/Downloads/Fiji.app/ImageJ-linux64
```

## Fiji menu location

**Plugins → Vessel Reconstruction → Robust Ellipse Reconstruction Prototype**
