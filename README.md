# Vessel Lumen Reconstruction

**An ImageJ/Fiji plugin for the analysis and diagnostic reconstruction of vessel lumina in wood-anatomical images.**

## Overview

Vessel Lumen Reconstruction is a Java plugin for ImageJ/Fiji designed to identify and analyse vessel lumina in classified wood-anatomical images.

The plugin uses a robust ellipse-based approach to identify potential inward artefacts in vessel boundaries and evaluate whether they represent regions suitable for reconstruction.

The project is intended to support quantitative wood-anatomical research, particularly the analysis of vessel geometry.

## Main features

- Detection and analysis of connected vessel lumen components.
- Ellipse fitting to describe vessel geometry.
- Identification of potential inward boundary artefacts.
- Diagnostic visualisation of detected artefacts and proposed corrections.
- Conservative evaluation of reconstruction candidates.
- Export of lumen measurements and diagnostic results.
- Integration with Trainable Weka Segmentation in ImageJ/Fiji.

## Input data

The plugin works with 8-bit classified images generated using Trainable Weka Segmentation.

The required class mapping is:

| Class | Meaning |
|---|---|
| 1 | Wood |
| 2 | Ray |
| 3 | Lumen |
| 4 | Background |

## Current development status

The ellipse-based detection and diagnostic workflow has been implemented and tested.

The plugin can identify potential artefacts, evaluate their geometry and generate diagnostic reconstructed image copies when the evidence criteria are met.

**Important:** The reconstruction thresholds are still provisional. Further validation using labelled positive and negative examples is required. Production reconstruction is not yet approved.

The current implementation focuses on approximately elliptical vessels with limited, identifiable inward boundary artefacts. More complex vessel geometries and multiple artefacts within a single vessel are not yet supported.

## Installation

1. Install [Fiji (ImageJ)](https://fiji.sc/).
2. Download the plugin JAR from the repository's Releases section, if a release is available.
3. Place the JAR file in Fiji's `plugins` directory.
4. Restart Fiji.

The plugin is available under:

**Plugins → Vessel Reconstruction**

## Building from source

The project uses Maven.

Requirements:
- Java
- Maven
- ImageJ/Fiji dependencies configured in `pom.xml`

Build the plugin with:

```bash
mvn clean package
```

The compiled JAR is generated in the `target` directory.

## Project status

This is an actively developed research software project. Its algorithms and validation procedures may evolve as further testing is conducted.

