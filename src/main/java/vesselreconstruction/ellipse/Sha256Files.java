package vesselreconstruction.ellipse;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Streaming SHA-256 helpers and canonical file write/reuse/conflict policy.
 */
public final class Sha256Files {

    private static final int BUFFER_SIZE = 1024 * 1024;

    private Sha256Files() {
    }

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public static String toHex(final byte[] digest) {
        final StringBuilder sb = new StringBuilder(digest.length * 2);
        for (int i = 0; i < digest.length; i++) {
            sb.append(String.format(Locale.US, "%02x", Integer.valueOf(digest[i] & 0xff)));
        }
        return sb.toString();
    }

    public static String sha256OfFile(final Path file) throws IOException {
        final MessageDigest digest = newDigest();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return toHex(digest.digest());
    }

    public static String sha256OfBytes(final byte[] bytes) {
        final MessageDigest digest = newDigest();
        digest.update(bytes);
        return toHex(digest.digest());
    }

    /**
     * Copies {@code source} to {@code target} byte-for-byte while computing SHA-256.
     */
    public static String copyFileStreaming(final Path source, final Path target)
            throws IOException {
        Files.createDirectories(target.getParent());
        final MessageDigest digest = newDigest();
        try (InputStream in = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(source)), digest);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        }
        return toHex(digest.digest());
    }

    /**
     * Writes bytes to a temporary sibling then moves into place.
     *
     * @return SHA-256 of the written content
     */
    public static String writeBytesAtomically(final Path target, final byte[] content)
            throws IOException {
        Files.createDirectories(target.getParent());
        final Path temp = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            final MessageDigest digest = newDigest();
            try (OutputStream out = new DigestOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)), digest)) {
                out.write(content);
            }
            final String sha = toHex(digest.digest());
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return sha;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Canonical scientific-file policy: write if missing; reuse if same SHA-256;
     * abort with conflict if different content exists.
     */
    public static String commitCanonicalBytes(final Path target, final byte[] content)
            throws IOException {
        final String intendedSha = sha256OfBytes(content);
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target)) {
                throw new IOException("Canonical path exists but is not a file: " + target);
            }
            final String existingSha = sha256OfFile(target);
            if (existingSha.equals(intendedSha)) {
                return existingSha;
            }
            throw new IOException(
                    "Canonical file conflict (different SHA-256). Refusing to overwrite:\n"
                            + target + "\nexisting=" + existingSha
                            + "\nintended=" + intendedSha
                            + "\nResolve the dataset conflict manually.");
        }
        return writeBytesAtomically(target, content);
    }

    /**
     * Canonical copy policy for original RGB: copy if missing; reuse if same hash;
     * conflict if different.
     */
    public static String commitCanonicalCopy(final Path source, final Path target)
            throws IOException {
        if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            return sha256OfFile(target);
        }
        final String intendedSha = sha256OfFile(source);
        if (Files.exists(target)) {
            final String existingSha = sha256OfFile(target);
            if (existingSha.equals(intendedSha)) {
                return existingSha;
            }
            throw new IOException(
                    "Canonical original RGB conflict (different SHA-256). Refusing to overwrite:\n"
                            + target + "\nexisting=" + existingSha
                            + "\nintended=" + intendedSha);
        }
        Files.createDirectories(target.getParent());
        final Path temp = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            final String sha = copyFileStreaming(source, temp);
            if (!sha.equals(intendedSha)) {
                throw new IOException("SHA-256 mismatch while copying original RGB");
            }
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return sha;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
