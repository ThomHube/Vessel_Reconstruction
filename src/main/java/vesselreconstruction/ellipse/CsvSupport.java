package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shared CSV escaping and row serialization for Lumen manifests.
 */
public final class CsvSupport {

    private CsvSupport() {
    }

    public static String escape(final String value) {
        if (value == null) {
            return "";
        }
        final boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        final String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public static String joinRow(final List<String> columns) {
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null");
        }
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
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        final List<List<String>> rows = parseTable(line);
        if (rows.isEmpty()) {
            return Collections.singletonList("");
        }
        if (rows.size() != 1) {
            throw new IllegalArgumentException(
                    "parseRow expected a single CSV record, found " + rows.size());
        }
        return rows.get(0);
    }

    /**
     * Parses a full CSV document, preserving quoted embedded newlines.
     */
    public static List<List<String>> parseTable(final String content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        final List<List<String>> rows = new ArrayList<List<String>>();
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            final char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(current.toString());
                current = new StringBuilder();
            } else if (ch == '\n') {
                fields.add(current.toString());
                current = new StringBuilder();
                if (!(fields.size() == 1 && fields.get(0).isEmpty())) {
                    rows.add(fields);
                }
                fields = new ArrayList<String>();
            } else if (ch == '\r') {
                // ignore CR; handle CRLF via the following LF
            } else {
                current.append(ch);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted CSV field");
        }
        if (current.length() > 0 || !fields.isEmpty()) {
            fields.add(current.toString());
            if (!(fields.size() == 1 && fields.get(0).isEmpty())) {
                rows.add(fields);
            }
        }
        return rows;
    }

    public static String blankIfNull(final Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String formatDoubleOrBlank(final Double value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.US, "%.17g", value);
    }

    public static String formatDouble(final double value) {
        return String.format(Locale.US, "%.17g", Double.valueOf(value));
    }

    public static List<String> immutableCopy(final String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
