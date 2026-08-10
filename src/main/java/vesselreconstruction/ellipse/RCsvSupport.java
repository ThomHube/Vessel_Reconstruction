package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R-compatible CSV helpers: UTF-8 comma CSV, TRUE/FALSE, NA, decimal point.
 */
public final class RCsvSupport {

    public static final String NA = "NA";
    public static final String TRUE = "TRUE";
    public static final String FALSE = "FALSE";

    private RCsvSupport() {
    }

    public static String escape(final String value) {
        if (value == null) {
            return NA;
        }
        final boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String joinRow(final List<String> columns) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(columns.get(i)));
        }
        return sb.toString();
    }

    public static List<String> parseRow(final String line) {
        return CsvSupport.parseRow(line);
    }

    public static String logical(final boolean value) {
        return value ? TRUE : FALSE;
    }

    public static String textOrNa(final String value) {
        if (value == null || value.isEmpty()) {
            return NA;
        }
        return value;
    }

    public static String numberOrNa(final Double value) {
        if (value == null || Double.isNaN(value.doubleValue())
                || Double.isInfinite(value.doubleValue())) {
            return NA;
        }
        return formatNumber(value.doubleValue());
    }

    public static String numberOrNa(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return NA;
        }
        return formatNumber(value);
    }

    public static String intOrNa(final Integer value) {
        if (value == null) {
            return NA;
        }
        return Integer.toString(value.intValue());
    }

    public static String formatNumber(final double value) {
        // Full precision, decimal point, no grouping.
        return String.format(Locale.US, "%.17g", Double.valueOf(value));
    }

    public static byte[] utf8Bytes(final String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (final Exception ex) {
            throw new IllegalStateException("UTF-8 required", ex);
        }
    }

    public static String tableToString(final List<String> header, final List<List<String>> rows) {
        final StringBuilder sb = new StringBuilder();
        sb.append(joinRow(header)).append('\n');
        for (int i = 0; i < rows.size(); i++) {
            final List<String> row = rows.get(i);
            if (row.size() != header.size()) {
                throw new IllegalArgumentException(
                        "Row column count " + row.size() + " != header " + header.size());
            }
            sb.append(joinRow(row)).append('\n');
        }
        return sb.toString();
    }

    public static List<String> mutableCopy(final String... values) {
        final List<String> list = new ArrayList<String>(values.length);
        for (int i = 0; i < values.length; i++) {
            list.add(values[i]);
        }
        return list;
    }
}
