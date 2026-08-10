package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchTileProfileTest {

    @Test
    void constructionAndTokenRoundTrip() {
        final BatchTileProfile profile = new BatchTileProfile(2048, 1536);
        assertEquals(2048, profile.widthPx());
        assertEquals(1536, profile.heightPx());
        assertEquals("2048x1536", profile.toToken());
    }

    @Test
    void parseTokenAcceptsUpperAndLowerX() {
        assertEquals(new BatchTileProfile(1024, 1024), BatchTileProfile.parseToken("1024x1024"));
        assertEquals(new BatchTileProfile(1024, 512), BatchTileProfile.parseToken("1024X512"));
        assertEquals(new BatchTileProfile(300, 400), BatchTileProfile.parseToken("  300x400  "));
    }

    @Test
    void rejectsBelowMinimumDimension() {
        assertThrows(IllegalArgumentException.class, () -> new BatchTileProfile(255, 1000));
        assertThrows(IllegalArgumentException.class, () -> new BatchTileProfile(1000, 255));
    }

    @Test
    void parseTokenRejectsInvalidFormats() {
        assertThrows(IllegalArgumentException.class, () -> BatchTileProfile.parseToken(null));
        assertThrows(IllegalArgumentException.class, () -> BatchTileProfile.parseToken("1024"));
        assertThrows(IllegalArgumentException.class, () -> BatchTileProfile.parseToken("x1024"));
        assertThrows(IllegalArgumentException.class, () -> BatchTileProfile.parseToken("1024x"));
        assertThrows(IllegalArgumentException.class, () -> BatchTileProfile.parseToken("axb"));
    }

    @Test
    void equalsAndHashCode() {
        final BatchTileProfile a = new BatchTileProfile(512, 512);
        final BatchTileProfile b = new BatchTileProfile(512, 512);
        final BatchTileProfile c = new BatchTileProfile(512, 1024);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(false, a.equals(c));
        assertEquals(false, a.equals(null));
        assertEquals(false, a.equals("512x512"));
    }
}
