package vesselreconstruction.ellipse;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic lookup of a {@link LumenComponentDescriptor} by component ID.
 *
 * <p>Does not renumber components, process images, or depend on ImageJ UI.</p>
 */
public final class LumenComponentIdSelector {

    public LumenComponentDescriptor selectById(
            final List<LumenComponentDescriptor> components,
            final int requestedComponentId) {
        final ValidatedList validated = validateComponents(components);
        if (requestedComponentId <= 0) {
            throw new IllegalArgumentException(
                    "requestedComponentId must be positive: " + requestedComponentId);
        }
        for (int i = 0; i < validated.components.size(); i++) {
            final LumenComponentDescriptor component = validated.components.get(i);
            if (component.getComponentId() == requestedComponentId) {
                return component;
            }
        }
        throw new IllegalArgumentException(
                "No component with requested ID " + requestedComponentId
                        + "; available IDs " + validated.minimumId
                        + "-" + validated.maximumId
                        + " (" + validated.components.size() + " total)");
    }

    public String describeAvailableRange(final List<LumenComponentDescriptor> components) {
        final ValidatedList validated = validateComponents(components);
        return String.format(Locale.US,
                "Available component IDs: %d-%d (%d total)",
                Integer.valueOf(validated.minimumId),
                Integer.valueOf(validated.maximumId),
                Integer.valueOf(validated.components.size()));
    }

    private static ValidatedList validateComponents(
            final List<LumenComponentDescriptor> components) {
        if (components == null) {
            throw new IllegalArgumentException("components must not be null");
        }
        if (components.isEmpty()) {
            throw new IllegalArgumentException("components must not be empty");
        }
        final Set<Integer> seenIds = new HashSet<Integer>(components.size() * 2);
        int minimumId = Integer.MAX_VALUE;
        int maximumId = Integer.MIN_VALUE;
        for (int i = 0; i < components.size(); i++) {
            final LumenComponentDescriptor component = components.get(i);
            if (component == null) {
                throw new IllegalArgumentException("components must not contain null elements");
            }
            final int id = component.getComponentId();
            if (id <= 0) {
                throw new IllegalArgumentException("component ID must be positive: " + id);
            }
            if (!seenIds.add(Integer.valueOf(id))) {
                throw new IllegalArgumentException("duplicate component ID: " + id);
            }
            if (id < minimumId) {
                minimumId = id;
            }
            if (id > maximumId) {
                maximumId = id;
            }
        }
        final ValidatedList validated = new ValidatedList();
        validated.components = components;
        validated.minimumId = minimumId;
        validated.maximumId = maximumId;
        return validated;
    }

    private static final class ValidatedList {
        private List<LumenComponentDescriptor> components;
        private int minimumId;
        private int maximumId;
    }
}
