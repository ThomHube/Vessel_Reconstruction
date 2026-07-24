# Vessel Reconstruction - Robust Ellipse Prototype

Independent Fiji/ImageJ 1 plugin project for the **Robust Ellipse Reconstruction** algorithm.

This repository is independent from the legacy contour project. The old contour bridgepoint algorithm is not present in this repository.

**Project directory:** `/home/thomas/IdeaProjects/minimal-ij1-plugin-ellipse`

**Algorithm name:** Robust Ellipse Reconstruction

## Fixed Weka classifier mapping

Trainable Weka Segmentation class values cannot be renamed. This project uses:

| Class | Label |
|-------|-------|
| 1 | Wood |
| 2 | Ray |
| 3 | Lumen |
| 4 | Background |

## Current state

The plugin entry point remains diagnostic-only: it logs the fixed classifier mapping and shows a confirmation dialog. It does not require an active image and does not inspect or modify any image.

Mathematical free-ellipse fitting is implemented independently of image processing:

- `EllipseModel` represents a free ellipse (center, two independent axes, rotation)
- `FreeEllipseFitter` deterministically fits ellipse support points by minimizing mean squared normalized radial residuals
- Circularity is not optimized and the ellipse is not forced to become a circle

Still unimplemented:

- image access, lumen detection, and boundary tracing
- inward-outlier exclusion and robust iteration
- pixel filling / reconstruction
- Fiji visualization

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
