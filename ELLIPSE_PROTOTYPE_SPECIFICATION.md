# Robust Ellipse Reconstruction — Prototype Specification

## Project identity

- **Algorithm:** Robust Ellipse Reconstruction
- **Project directory:** `/home/thomas/IdeaProjects/minimal-ij1-plugin-ellipse`
- **Artifact:** `VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar`
- **Status:** Initial free-ellipse fit, diagnostic inward-outlier interval detection, and single-interval diagnostic refit implemented; iterative exclusion, acceptance, and reconstruction remain unimplemented
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

The initial ellipse may be influenced by the artifact because all observed
boundary points are included.

A selected lumen with an ambiguous high-degree lattice boundary vertex is
rejected in this stage rather than resolved speculatively.

Inward-outlier interval detection and the single-interval refit are diagnostic
only. The selected interval is excluded only from mathematical fit support. No
classified pixels are removed or changed. The comparison baseline uses all and
only selected outer-boundary midpoints (not the original all-exposed-boundary
image fit). Prototype constants
(`NORMALIZED_MAD_SCALE`, `CORE_SIGMA_MULTIPLIER`, `EXPANSION_SIGMA_MULTIPLIER`,
`MIN_INTERVAL_EDGE_COUNT`, `MAX_INTERVAL_BOUNDARY_FRACTION`,
`MIN_DOMINANCE_RATIO`) are not final biological thresholds.

Not yet implemented:

- repeated interval detection after refitting
- iterative support-point exclusion
- stopping based on parameter stability
- final ellipse acceptance
- proposed fill acceptance
- source-image modification
- reconstruction
- pixel modification

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
