# Batch Phase 1 — Manifest Planning Only

This phase **plans** unattended multi-computer work. It does **not**:

- open Fiji
- run Weka
- reconstruct lumina
- generate masks
- execute headless image processing
- merge dataset shards
- copy portable Fiji bundles

Phase 1 discovers source TIFFs, validates filenames against the production
core/segment grammar, partitions work across machines **without splitting a
physical core**, and writes master/per-machine manifests plus human-readable
reports.

## Filename grammar

Every source TIFF base name is exactly one production image ID:

```text
PPTTCS
```

| Field | Meaning | Valid values |
|-------|---------|--------------|
| `PP` | plot ID | two digits, not `00` |
| `TT` | individual tree | two digits, not `00` |
| `C` | physical core label | `A` or `B` |
| `S` | segment order | `1` through `5` |

Examples: `0101A1`, `0101A2`, `0101B1`, `0207B5`.

Accepted extensions (case-insensitive): `.tif`, `.tiff`.

The base name must equal the ID exactly (no surrounding whitespace, no case
folding). Names such as `0101A1-copy.tif`, `010101.tif`, `0101a1.tif`,
`0101C1.tif`, `0101A0.tif`, `0101A6.tif`, `0001A1.tif`, `0100A1.tif`,
` 0101A1.tif`, or `0101A1.jpg` are rejected.

Authoritative fields for `0101A2`:

| Field | Value |
|-------|-------|
| `image_id` | `0101A2` |
| `plot_id` | `01` |
| `tree_id` | `01` |
| `core_label` | `A` |
| `core_id` / `sample_id` | `0101A` |
| `segment_id` | `0101A2` |
| `segment_order` | `2` |

Segment order is physical: `1` = closest to bark … `5` = closest to center when
present. A core may contain fewer than five segments. There is **no** fixed
expected set of 80 images and **no** replicate field.

## Machine assignment

All segments sharing the same `core_id` are assigned to the same machine.
Ordered core groups are assigned to the currently least-loaded machine by image
count (ties → lower machine index). Core integrity takes priority over equal
image counts.

## Complete-set flag

`input.require_complete_expected_set=true` is **no longer supported** under
PPTTCS (no fixed expected ID list). Use `false`.

## Portable deployment note

Portable scripts target scientific checkpoint `9a53b84` with PPTTCS identity
and batch-manifest `schema_version=2`. Canonical drive assets for `9a53b84`
must be published before laboratory installation.

## Outputs

Unchanged layout under the batch root (`master_batch_manifest.csv`,
`machines/machine_XX_manifest.csv`, assignment summary, plan report). Manifest
columns now include `core_label`, `core_id`, and `segment_order` instead of
`replicate_id`. Newly written manifests use `schema_version=2` (incompatible
with the prior six-digit `schema_version=1` layout).
