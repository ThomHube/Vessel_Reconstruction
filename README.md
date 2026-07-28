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

The plugin analyzes the active 8-bit classified image, selects the largest Class 3 lumen component, fits one initial free ellipse to all exposed-edge midpoint support points, orders the selected outer boundary, detects a candidate contiguous inward-outlier interval, performs exactly one diagnostic outer-boundary baseline fit plus one interval-excluded refit, and builds an in-memory fill preview for the frozen refitted ellipse.

The initial image ellipse may be influenced by the artifact because all observed boundary points are included. The comparison baseline uses all and only selected outer-boundary midpoints.

A selected lumen with an ambiguous high-degree lattice boundary vertex is rejected in this stage rather than resolved speculatively.

Detection, single-interval refit, and fill preview are diagnostic only. The selected interval is excluded only from mathematical fit support. The fill preview is additive and in-memory. No classified pixels are removed or changed. Circularity is calculated only to describe the preview; it does not control the ellipse fit or automatically approve reconstruction.

### Not implemented

- ellipse acceptance
- automatic reconstruction approval
- source-image modification
- saving a reconstructed classified image
- processing all vessels
- iterative interval exclusion
- broader non-elliptical vessel handling
- repeated interval detection after refitting
- stopping based on parameter stability
- pixel modification
- reconstruction

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
