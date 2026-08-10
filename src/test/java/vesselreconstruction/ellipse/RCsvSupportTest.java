package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RCsvSupportTest {

    @Test
    void escapesCommasQuotesAndNewlines() {
        assertEquals("\"a,b\"", RCsvSupport.escape("a,b"));
        assertEquals("\"a\"\"b\"", RCsvSupport.escape("a\"b"));
        assertEquals("\"a\nb\"", RCsvSupport.escape("a\nb"));
    }

    @Test
    void preservesUnicodeAndUsesRLogicalAndNaValues() {
        assertEquals("12 µm", RCsvSupport.escape("12 µm"));
        assertEquals("TRUE", RCsvSupport.logical(true));
        assertEquals("FALSE", RCsvSupport.logical(false));
        assertEquals("NA", RCsvSupport.numberOrNa(Double.NaN));
    }

    @Test
    void formatsNumbersWithDecimalPoint() {
        assertEquals("1.5000000000000000", RCsvSupport.formatNumber(1.5));
    }
}
