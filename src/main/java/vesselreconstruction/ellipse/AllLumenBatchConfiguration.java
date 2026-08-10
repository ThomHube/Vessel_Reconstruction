package vesselreconstruction.ellipse;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable configuration for all-lumen diagnostic batch processing.
 */
public final class AllLumenBatchConfiguration {

    /**
     * Authoritative production default for the registered All Lumina command.
     */
    public static final int DEFAULT_MINIMUM_LUMEN_AREA_PX = 1000;

    public enum DisplayPolicy {
        SUMMARY_ONLY,
        SILENT
    }

    private final int minimumLumenArea;
    private final boolean skipBorderTouchingComponents;
    private final DisplayPolicy displayPolicy;
    private final boolean createCombinedDiagnosticCopy;
    private final boolean createCombinedChangeMask;
    private final boolean createDecisionOverview;
    private final boolean createResultsTable;

    public AllLumenBatchConfiguration(
            final int minimumLumenArea,
            final boolean skipBorderTouchingComponents,
            final DisplayPolicy displayPolicy,
            final boolean createCombinedDiagnosticCopy,
            final boolean createCombinedChangeMask,
            final boolean createDecisionOverview,
            final boolean createResultsTable) {
        if (minimumLumenArea <= 0) {
            throw new IllegalArgumentException(
                    "minimumLumenArea must be positive: " + minimumLumenArea);
        }
        if (displayPolicy == null) {
            throw new IllegalArgumentException("displayPolicy must not be null");
        }
        if (displayPolicy == DisplayPolicy.SILENT
                && (createCombinedDiagnosticCopy
                || createCombinedChangeMask
                || createDecisionOverview
                || createResultsTable)) {
            throw new IllegalArgumentException(
                    "SILENT requires every create/display flag to be false");
        }
        this.minimumLumenArea = minimumLumenArea;
        this.skipBorderTouchingComponents = skipBorderTouchingComponents;
        this.displayPolicy = displayPolicy;
        this.createCombinedDiagnosticCopy = createCombinedDiagnosticCopy;
        this.createCombinedChangeMask = createCombinedChangeMask;
        this.createDecisionOverview = createDecisionOverview;
        this.createResultsTable = createResultsTable;
    }

    public static AllLumenBatchConfiguration summaryDefaults() {
        return new AllLumenBatchConfiguration(
                DEFAULT_MINIMUM_LUMEN_AREA_PX,
                true,
                DisplayPolicy.SUMMARY_ONLY,
                true,
                true,
                true,
                true);
    }

    public static AllLumenBatchConfiguration silentDefaults() {
        return new AllLumenBatchConfiguration(
                DEFAULT_MINIMUM_LUMEN_AREA_PX,
                true,
                DisplayPolicy.SILENT,
                false,
                false,
                false,
                false);
    }

    public int getMinimumLumenArea() {
        return minimumLumenArea;
    }

    public boolean isSkipBorderTouchingComponents() {
        return skipBorderTouchingComponents;
    }

    public DisplayPolicy getDisplayPolicy() {
        return displayPolicy;
    }

    public boolean isCreateCombinedDiagnosticCopy() {
        return createCombinedDiagnosticCopy;
    }

    public boolean isCreateCombinedChangeMask() {
        return createCombinedChangeMask;
    }

    public boolean isCreateDecisionOverview() {
        return createDecisionOverview;
    }

    public boolean isCreateResultsTable() {
        return createResultsTable;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AllLumenBatchConfiguration)) {
            return false;
        }
        final AllLumenBatchConfiguration other = (AllLumenBatchConfiguration) obj;
        return minimumLumenArea == other.minimumLumenArea
                && skipBorderTouchingComponents == other.skipBorderTouchingComponents
                && displayPolicy == other.displayPolicy
                && createCombinedDiagnosticCopy == other.createCombinedDiagnosticCopy
                && createCombinedChangeMask == other.createCombinedChangeMask
                && createDecisionOverview == other.createDecisionOverview
                && createResultsTable == other.createResultsTable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(minimumLumenArea),
                Boolean.valueOf(skipBorderTouchingComponents),
                displayPolicy,
                Boolean.valueOf(createCombinedDiagnosticCopy),
                Boolean.valueOf(createCombinedChangeMask),
                Boolean.valueOf(createDecisionOverview),
                Boolean.valueOf(createResultsTable));
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "AllLumenBatchConfiguration{minArea=%d,skipBorder=%s,policy=%s,"
                        + "copy=%s,mask=%s,overview=%s,table=%s}",
                Integer.valueOf(minimumLumenArea),
                Boolean.valueOf(skipBorderTouchingComponents),
                displayPolicy,
                Boolean.valueOf(createCombinedDiagnosticCopy),
                Boolean.valueOf(createCombinedChangeMask),
                Boolean.valueOf(createDecisionOverview),
                Boolean.valueOf(createResultsTable));
    }
}
