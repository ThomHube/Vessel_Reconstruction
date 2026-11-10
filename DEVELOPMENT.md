# Development

## Project

Fiji / ImageJ 1 Java plugin implementing the **Robust Ellipse
Reconstruction** method. Maven project; Java package
`vesselreconstruction.ellipse`. This repository is independent of the
legacy contour project; the legacy contour/bridgepoint algorithm is not
included.

## Fixed Trainable Weka mapping

    Weka class Meaning        8-bit Create result value
  ------------ ------------ ---------------------------
             1 Wood                                   0
             2 Ray                                    1
             3 Lumen                                  2
             4 Background                             3

Class identity is determined by numeric class/result values, never by
display colours. The analyzer extracts Class 3 Lumen using raw result
value `2`.

## Biological and geometric premise

Most unaffected vessel lumina are assumed to be approximately circular
or elliptical. A local inward Wood intrusion may cause a strong inward
boundary deviation while the remaining wall still supports a plausible
free ellipse.

The model has a centre, two independent semi-axes, and a rotation. It is
not constrained to a circle; circularity is not the fitting objective.

## Implemented diagnostic workflow

1.  Validate the active 8-bit classified image.
2.  Extract 8-connected Class 3 components and assign deterministic,
    image-local IDs.
3.  Select the largest component in single-component mode.
4.  Construct exposed directed pixel edges, boundary loops, and support
    points at exposed-edge midpoints.
5.  Fit an initial free ellipse.
6.  Calculate signed boundary residuals and robust scale using
    normalized MAD.
7.  Detect inward outliers using core and expansion thresholds; extract
    contiguous circular intervals and select the deterministic dominant
    interval.
8.  Fit an outer-boundary baseline ellipse, then refit after excluding
    the selected inward interval from mathematical fit support.
9.  Compare baseline/refit parameters and residuals.
10. Freeze the interval-excluded refitted ellipse for diagnostic fill
    evaluation.
11. Test pixel-centre inclusion and account for proposed fill by
    original class.
12. Split proposed fill into 8-connected components and associate them
    with the selected interval using exterior seeds.
13. Apply residual-evidence and structural-association gates.
14. If the gate passes, create an in-memory diagnostic copy and change
    only the primary associated Wood pixels to Lumen.

The interval is excluded from fit support only. It is not erased from
the classified image.

## Boundary topology

Lumen membership uses 8-connectivity. At exact diagonal checkerboard
contacts, the implementation uses `RIGHT_TURN_SAME_FACE` only when
exactly one right-turn and one left-turn candidate exist. This produces
separate simple loops at point contacts while accounting for exposed
edges. Other ambiguous lattice configurations are conservatively
rejected as `AMBIGUOUS_BOUNDARY_VERTEX`.

This topology rule is not an artifact detector and does not approve
reconstruction.

## Plugin modes

-   **Prototype:** detailed diagnostics for one automatically selected
    component.
-   **All Lumina:** processes all Class 3 components, combined summary,
    optional silent mode; default minimum component area is 1000 px.
-   **Inspect Component ID:** detailed inspection of a selected
    image-local component ID. IDs may change after crop, resize,
    rotation, editing, reclassification, or changed discovery order.
-   **Tiled Weka Validation:** mask-based tiled classification and
    dataset preparation; this mode does not reconstruct vessels.

## Diagnostic-copy safeguards

-   The classified source is never modified.
-   A separate in-memory copy is created only when evidence and
    structural checks pass.
-   Only raw value `0` (Wood) may become raw value `2` (Lumen).
-   Ray, Background, unknown values, and existing Lumen pixels are not
    converted.
-   Only the primary interval-associated fill component is eligible.
-   No copy is created for clean/insufficient-evidence vessels.
-   Diagnostic output is not automatically saved.

## Implementation limits

Implemented: component extraction, deterministic boundary construction,
free-ellipse fitting, residual profiling, inward-interval detection,
interval-excluded refitting, fill preview, fill-component association,
evidence assessment, and optional diagnostic-copy generation.

Not implemented: component/ellipse acceptance thresholds, production
choice between whole-ellipse and associated fill, source modification,
automatic saving of reconstructed images, directory iteration, stack
processing, iterative interval exclusion, repeated interval detection
after refitting, parameter-stability stopping, and general
non-elliptical reconstruction.

The design notes describe refitting until stabilization as a planned
workflow. The documented current implementation performs one baseline
fit and one interval-excluded refit.

## Build and install

``` bash
cd ~/IdeaProjects/minimal-ij1-plugin-ellipse
mvn clean package
cp target/VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar \\
  ~/Downloads/Fiji.app/plugins/
~/Downloads/Fiji.app/ImageJ-linux64
```

Menu: **Plugins → Vessel Reconstruction → Robust Ellipse Reconstruction
Prototype**.
