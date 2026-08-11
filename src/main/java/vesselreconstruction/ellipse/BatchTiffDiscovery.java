package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Discovers and validates production {@code PPTTCS} TIFF files under an input root.
 *
 * <p>There is no fixed expected image set: segment presence is data-dependent.
 * The historical {@code input.require_complete_expected_set} flag is accepted for
 * configuration compatibility but no longer enforces a closed ID list.</p>
 */
public final class BatchTiffDiscovery {

    public static final class Result {
        private final List<BatchTiffFile> validTiffs;
        private final List<String> malformedTiffLikeFiles;
        private final List<String> nonTiffFiles;
        private final List<String> duplicateIds;
        private final List<CoreSegmentId> missingExpectedIds;
        private final List<String> unexpectedIds;

        public Result(
                final List<BatchTiffFile> validTiffs,
                final List<String> malformedTiffLikeFiles,
                final List<String> nonTiffFiles,
                final List<String> duplicateIds,
                final List<CoreSegmentId> missingExpectedIds,
                final List<String> unexpectedIds) {
            this.validTiffs = Collections.unmodifiableList(
                    new ArrayList<BatchTiffFile>(validTiffs));
            this.malformedTiffLikeFiles = Collections.unmodifiableList(
                    new ArrayList<String>(malformedTiffLikeFiles));
            this.nonTiffFiles = Collections.unmodifiableList(
                    new ArrayList<String>(nonTiffFiles));
            this.duplicateIds = Collections.unmodifiableList(
                    new ArrayList<String>(duplicateIds));
            this.missingExpectedIds = Collections.unmodifiableList(
                    new ArrayList<CoreSegmentId>(missingExpectedIds));
            this.unexpectedIds = Collections.unmodifiableList(
                    new ArrayList<String>(unexpectedIds));
        }

        public List<BatchTiffFile> validTiffs() {
            return validTiffs;
        }

        public List<String> malformedTiffLikeFiles() {
            return malformedTiffLikeFiles;
        }

        public List<String> nonTiffFiles() {
            return nonTiffFiles;
        }

        public List<String> duplicateIds() {
            return duplicateIds;
        }

        /**
         * Always empty under the production core/segment model (no fixed expected set).
         */
        public List<CoreSegmentId> missingExpectedIds() {
            return missingExpectedIds;
        }

        public List<String> unexpectedIds() {
            return unexpectedIds;
        }

        public boolean hasBlockingErrors() {
            return !malformedTiffLikeFiles.isEmpty() || !duplicateIds.isEmpty();
        }
    }

    private BatchTiffDiscovery() {
    }

    public static Result discover(
            final Path inputRoot,
            final boolean recursive,
            final boolean requireCompleteExpectedSet) throws IOException {
        if (inputRoot == null) {
            throw new IllegalArgumentException("inputRoot must not be null");
        }
        final Path root = inputRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(
                    "inputRoot must exist and be a directory: " + root);
        }
        // requireCompleteExpectedSet is intentionally unused: a fixed complete image
        // set is incompatible with variable segment counts per core. Callers that set
        // the flag true are rejected by BatchManifestPlanner.
        if (requireCompleteExpectedSet) {
            // Keep parameter for API compatibility; planner owns the hard failure.
        }

        final List<Path> candidates = new ArrayList<Path>();
        collectFiles(root, root, recursive, candidates);

        final List<String> malformed = new ArrayList<String>();
        final List<String> nonTiff = new ArrayList<String>();
        final Map<String, List<BatchTiffFile>> byId =
                new HashMap<String, List<BatchTiffFile>>();

        for (int i = 0; i < candidates.size(); i++) {
            final Path absolute = candidates.get(i).toAbsolutePath().normalize();
            if (!absolute.startsWith(root)) {
                throw new IOException("Discovered path escapes input root: " + absolute);
            }
            final Path relative = root.relativize(absolute);
            final String filename = absolute.getFileName().toString();
            final String lower = filename.toLowerCase(Locale.ROOT);
            final boolean tiffLike = lower.endsWith(".tif") || lower.endsWith(".tiff");
            if (!tiffLike) {
                nonTiff.add(relative.toString().replace('\\', '/'));
                continue;
            }
            try {
                final BatchTiffFile.ParsedFilename parsed = BatchTiffFile.parseFilename(filename);
                final long size = Files.size(absolute);
                final String sha = Sha256Files.sha256OfFile(absolute);
                final BatchTiffFile file = new BatchTiffFile(
                        parsed.id,
                        absolute,
                        relative,
                        filename,
                        parsed.extension,
                        size,
                        sha);
                final String key = parsed.id.imageId();
                List<BatchTiffFile> list = byId.get(key);
                if (list == null) {
                    list = new ArrayList<BatchTiffFile>();
                    byId.put(key, list);
                }
                list.add(file);
            } catch (final IllegalArgumentException ex) {
                malformed.add(relative.toString().replace('\\', '/')
                        + " (" + ex.getMessage() + ")");
            }
        }

        final List<String> duplicates = new ArrayList<String>();
        final List<BatchTiffFile> valid = new ArrayList<BatchTiffFile>();
        for (final Map.Entry<String, List<BatchTiffFile>> entry : byId.entrySet()) {
            final List<BatchTiffFile> list = entry.getValue();
            if (list.size() != 1) {
                final StringBuilder sb = new StringBuilder(entry.getKey());
                sb.append(" -> ");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(list.get(i).relativePath().toString().replace('\\', '/'));
                }
                duplicates.add(sb.toString());
            } else {
                valid.add(list.get(0));
            }
        }
        Collections.sort(valid, new Comparator<BatchTiffFile>() {
            @Override
            public int compare(final BatchTiffFile a, final BatchTiffFile b) {
                return a.id().compareTo(b.id());
            }
        });
        Collections.sort(malformed);
        Collections.sort(nonTiff);
        Collections.sort(duplicates);

        final Set<String> present = new HashSet<String>();
        for (int i = 0; i < valid.size(); i++) {
            present.add(valid.get(i).id().imageId());
        }
        final List<String> unexpected = new ArrayList<String>();
        for (final String id : present) {
            try {
                CoreSegmentId.parse(id);
            } catch (final IllegalArgumentException ex) {
                unexpected.add(id);
            }
        }
        Collections.sort(unexpected);

        final List<CoreSegmentId> missing = Collections.emptyList();
        return new Result(valid, malformed, nonTiff, duplicates, missing, unexpected);
    }

    private static void collectFiles(
            final Path root,
            final Path current,
            final boolean recursive,
            final List<Path> out) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (final Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if (recursive) {
                        collectFiles(root, entry, true, out);
                    }
                } else if (Files.isRegularFile(entry)) {
                    out.add(entry);
                }
            }
        }
    }
}
