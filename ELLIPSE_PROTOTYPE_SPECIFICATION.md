# Robust Ellipse Reconstruction — Prototype Specification

## Project identity

- **Algorithm:** Robust Ellipse Reconstruction
- **Project directory:** `/home/thomas/IdeaProjects/minimal-ij1-plugin-ellipse`
- **Artifact:** `VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar`
- **Status:** Detailed single-lumen diagnostic prototype plus headless all-lumen batch processor implemented; directory dataset runner, automatic saving, and production reconstruction remain unimplemented
- **Independence:** This repository does not contain the legacy contour bridgepoint algorithm

## Mathematical free-ellipse stage

Implemented:

- immutable free-ellipse model (`EllipseModel`)
- deterministic free-ellipse fitter (`FreeEllipseFitter`)
- immutable fit diagnostics (`EllipseFitResult`)

The fitter recovers center, two independent axes, and rotation. It minimizes
normalized radial residuals. It does not optimize circularity and does not
force the ellipse toward a circle.

## Initial image-analysis stage

Implemented:

- active 8-bit classified-image validation
- Class 3 connected-component extraction using 8-connectivity
- deterministic largest-component selection
- exact exposed pixel-edge midpoint support extraction
- initial free ellipse fit to all support points
- initial proposed-fill preview
- diagnostic visualization
- deterministic exposed directed pixel-edge construction
- conservative closed-loop extraction
- deterministic outer-loop selection
- ordered outer-boundary residual profile
- residual visualization and profile plot
- robust residual scale using normalized MAD (`1.4826 * MAD`)
- strict inward-outlier core threshold (`median - 3.0 * robustScale`)
- expansion threshold (`median - 1.5 * robustScale`)
- circular contiguous expansion-run extraction (no smoothing, no gap bridging)
- deterministic dominant inward-outlier interval selection
- diagnostic interval visualization on the boundary and residual-profile plot
- outer-boundary-only baseline ellipse fit
- exclusion of exactly one diagnostically selected inward interval from fit support
- one free-ellipse refit (`SingleIntervalEllipseRefitter`)
- baseline-versus-refit parameter diagnostics
- baseline-versus-refit residual diagnostics
- diagnostic comparison visualization
- frozen single-interval refitted ellipse fill preview
- exact pixel-center inclusion test (`x+0.5`, `y+0.5`, tolerance `1.0e-12`)
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
- optional diagnostic reconstructed copy when strong evidence and structural association pass
- source-safe Wood→Lumen conversion limited to the primary interval-associated component
- diagnostic change mask and two-panel reconstruction review visualization
- headless all-lumen batch processor (`AllLumenBatchProcessor`) with `SUMMARY_ONLY` and `SILENT` display policies
- separate ImageJ entry point: Robust Ellipse Reconstruction - All Lumina

The initial ellipse may be influenced by the artifact because all observed
boundary points are included.

8-connected lumen components can contain diagonal pixel contacts. A diagonal
contact may create a lattice vertex with multiple unused outgoing exposed
directed edges.

ImageJ reference evidence for exact checkerboard contacts:

- ThresholdToSelection fully accounted for the exposed-edge union, but its main
  left-turn outline contained repeated non-closing vertices
- Wand EIGHT_CONNECTED chose the left turn and returned one seed outline
- Wand FOUR_CONNECTED chose the right turn and returned a simple seed outline

The project therefore adopts `RIGHT_TURN_SAME_FACE` only for exact diagonal
checkerboard occupancy (`DIAGONAL_NW_SE` or `DIAGONAL_NE_SW` with exactly one
`RIGHT_TURN` and one `LEFT_TURN` candidate). The extractor continues tracing
all remaining unused exposed edges, unlike a single Wand invocation, and
therefore produces separate simple loops with complete exposed-edge accounting.

- component membership remains 8-connected
- boundary loops at point contacts are separate
- all other ambiguous lattice configurations remain conservative failures
- ImageJ boundary APIs remain diagnostic references
- no external contour dependency was added
- ellipse fitting and biological residual logic remain unchanged

`RIGHT_TURN_SAME_FACE` is a narrow digital-topology rule. It does not detect
artifacts. It does not approve reconstruction. It does not modify any
classified pixels.

On unresolved `AMBIGUOUS_BOUNDARY_VERTEX`, the plugin still runs the ImageJ
core boundary reference audit and stops without reconstruction.

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

The diagonal checkerboard topology policy for exact contacts is
`RIGHT_TURN_SAME_FACE`. All other ambiguous lattice configurations remain
rejected.

Detection, single-interval refit, fill preview, component association, evidence
assessment, and the optional diagnostic reconstructed copy are diagnostic only.
The selected interval is excluded only from mathematical fit support. Fill
previews are additive and in-memory. The classified source image is never
modified. When a diagnostic copy is created, only primary interval-associated
Wood pixels become Lumen in that independent duplicate; non-associated fill
components are unchanged, output is not saved automatically, and production
reconstruction remains disabled. Circularity remains a secondary diagnostic and
does not control the ellipse fit or readiness. Prototype evidence thresholds are
not final biological thresholds.

### Artifact evidence gate

Interval uniqueness is not sufficient evidence. A sole interval (including
`dominanceRatio=Infinity`) no longer implies biological importance.

After interval selection, interval-excluded refit, and proposed-fill component
association, the prototype evaluates scale-independent residual evidence:

- `minimumDepthSigma = (medianResidual - minimumResidual) / robustScale`
- `meanDepthSigma = (medianResidual - meanResidual) / robustScale`
- `coreFraction = corePointCount / intervalEdgeCount`
- `normalizedIntegratedInwardExcess = integratedInwardExcess / (intervalEdgeCount * robustScale)`
- `retainedRmsImprovementFraction = (baselineRetainedRms - refittedRetainedRms) / baselineRetainedRms`

Conservative prototype thresholds:

- `minimumDepthSigma >= 4.0`
- `meanDepthSigma >= 3.0`
- `coreFraction >= 0.15`
- `normalizedIntegratedInwardExcess >= 1.50`
- `retainedRmsImprovementFraction >= 0.20`

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
- clean example 6 still stops earlier with `NO_CORE_POINTS`

All five residual criteria remain mandatory. All structural requirements remain
mandatory. No image-specific code was added. Circularity and associated fill
fraction remain secondary diagnostics.

This calibration is provisional. It permits diagnostic reconstructed copies but
does not constitute final biological validation or production approval.
Additional labelled examples are still required before final threshold selection
or production reconstruction.

The diagnostic evidence result is not production reconstruction approval.

### Diagnostic reconstructed copy

When `STRONG_ARTIFACT_EVIDENCE` and the structural association checks pass, the
plugin may create a diagnostic reconstructed image copy:

- copy the active classified `ByteProcessor`; never modify the source
- change exactly the primary interval-associated component pixels
- convert only raw Weka result value `0` (Wood) to raw value `2` (Lumen)
- do not convert Ray, Background, unknown, or existing Lumen values
- leave non-associated ellipse-fill components unchanged
- do not remove observed Lumen pixels
- show a binary change mask and a two-panel review image
- never save output automatically
- create no reconstructed copy for clean or insufficient-evidence vessels

Verified regression outcomes for the copy gate:

- positive example 2 → `READY_FOR_DIAGNOSTIC_COPY`
- broad positive example 8 → eligible for diagnostic copy once evidence is strong
- clean example 7 → `INSUFFICIENT_ARTIFACT_EVIDENCE` (no copy, no change mask)

This is still not production reconstruction. Evidence thresholds remain
unchanged and provisional. Broader positive and negative validation is still
required.

### All-lumen diagnostic batch mode

A separate headless processor analyzes every Class 3 Lumen component against the
unchanged original classified image:

- extract all raw-value-2 components once (8-connected)
- filter by provisional minimum area and optional border skip
- run the verified single-artifact pipeline per eligible component
- resolve overlapping candidate pixels as `CANDIDATE_CONFLICT`
- combine only non-conflicting accepted Wood→Lumen candidates into one diagnostic copy

Display policies:

- `SUMMARY_ONLY`: at most one ResultsTable, one decision overview, one combined copy, one combined mask
- `SILENT`: no windows, tables, or dialogs; concise log only

Detailed single-lumen mode remains available for development and continues to
open per-lumen diagnostic windows.

Not yet implemented:

- component acceptance thresholds
- ellipse acceptance
- automatic choice between whole and associated fill
- automatic reconstruction approval
- source-image modification
- automatic saving of a reconstructed classified image
- directory dataset iteration
- stack processing
- iterative interval exclusion
- broader non-elliptical vessel handling
- repeated interval detection after refitting
- stopping based on parameter stability
- production reconstruction

Planned later architecture:

Directory Dataset Runner
→ loads classified TIFF
→ calls `AllLumenBatchProcessor` in `SILENT` mode
→ writes one reconstructed TIFF
→ writes one change-mask TIFF
→ appends one CSV results file
→ closes all images
→ continues with the next file

The future dataset runner will use the same headless processor and will not
create hundreds of visible ImageJ windows.

## Fixed classifier mapping

Trainable Weka Segmentation classes cannot be renamed.

### Trainable Weka class numbers versus result pixel values

Human-facing class order:

| Value | Label |
|------:|-------|
| 1 | Wood |
| 2 | Ray |
| 3 | Lumen |
| 4 | Background |

Direct "Create result" 8-bit encoding:

| Pixel value | Class | Label |
|------------:|------:|-------|
| 0 | 1 | Wood |
| 1 | 2 | Ray |
| 2 | 3 | Lumen |
| 3 | 4 | Background |

The class mapping has not changed.
Only the raw result-image encoding is zero-based.

The analyzer operates on result pixel value 2 when extracting Class 3 Lumen.

## Biological assumption

Most unaffected vessel lumina are approximately circular or elliptical.
A local wood intrusion may create a strong inward deviation while most of the
original vessel wall still supports a plausible free ellipse.

## Version 1 scope

- one isolated lumen
- a mostly circular or elliptical vessel
- one dominant local inward deformation
- diagnostic fitting only
- conservative rejection of ambiguous cases

## Excluded scope

- strongly non-elliptical vessels
- multiple large inward deformations
- artifacts spanning the full lumen
- falsely connected vessels
- vessel splitting
- production pixel modification

## Algorithm stages

1. observed lumen extraction
2. observed boundary extraction
3. initial free ellipse fit
4. signed boundary residual calculation
5. contiguous inward-outlier identification
6. ellipse refitting using retained support points
7. convergence and stability checking
8. frozen ellipse creation
9. proposed fill preview
10. diagnostic acceptance or conservative rejection

## Required diagnostics

Future diagnostic output must include:

- ellipse center X
- ellipse center Y
- semi-major axis
- semi-minor axis
- rotation angle
- retained support fraction
- excluded outlier fraction
- median residual
- median absolute deviation
- maximum inward residual
- maximum outward residual
- iteration count
- parameter changes per iteration
- convergence reason
- original lumen area
- fitted ellipse area
- proposed added-pixel count
- original circularity
- proposed circularity

Circularity may be logged as a secondary diagnostic, but it must not control
ellipse fitting or force the ellipse toward a circle.

## Acceptance philosophy

- ambiguous cases remain unchanged
- no vessel must be forced into an ellipse
- reconstruction is allowed only after strong diagnostic support
- initial development remains preview-only

## Terminology

Use only:

- observed lumen
- observed lumen boundary
- ellipse support point
- retained boundary point
- excluded inward outlier
- inward outlier
- inward-outlier core
- inward-outlier interval
- robust residual scale
- core threshold
- expansion threshold
- selected interval
- interval dominance
- robust ellipse fit
- fitted ellipse
- frozen ellipse
- proposed ellipse fill
- ellipse reconstruction candidate

## Legacy terminology prohibition

Do not use in production code:

- bridgepoint
- bridge
- peak P
- artifact arc
- closing line
- ArtifactArcCandidate
- ArtifactArcCandidateFinder
- ArtifactBridgeTriplet
- BridgeCandidate
- PixelEdgeBoundaryTracer
- inward outlier arc
