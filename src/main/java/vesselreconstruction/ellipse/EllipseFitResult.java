package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostics for a free-ellipse fit.
 */
public final class EllipseFitResult {

    private final EllipseModel fittedEllipse;
    private final boolean converged;
    private final String convergenceReason;
    private final int iterationCount;
    private final double initialMeanSquaredResidual;
    private final double finalMeanSquaredResidual;
    private final double rootMeanSquaredResidual;
    private final double medianResidual;
    private final double medianAbsoluteDeviation;
    private final double maximumInwardResidual;
    private final double maximumOutwardResidual;
    private final List<Double> residuals;

    public EllipseFitResult(
            final EllipseModel fittedEllipse,
            final boolean converged,
            final String convergenceReason,
            final int iterationCount,
            final double initialMeanSquaredResidual,
            final double finalMeanSquaredResidual,
            final double rootMeanSquaredResidual,
            final double medianResidual,
            final double medianAbsoluteDeviation,
            final double maximumInwardResidual,
            final double maximumOutwardResidual,
            final List<Double> residuals) {
        if (fittedEllipse == null) {
            throw new IllegalArgumentException("fittedEllipse must not be null");
        }
        if (convergenceReason == null || convergenceReason.isEmpty()) {
            throw new IllegalArgumentException("convergenceReason must not be null or empty");
        }
        if (iterationCount < 0) {
            throw new IllegalArgumentException(
                    "iterationCount must be non-negative: " + iterationCount);
        }
        requireFinite("initialMeanSquaredResidual", initialMeanSquaredResidual);
        requireFinite("finalMeanSquaredResidual", finalMeanSquaredResidual);
        requireFinite("rootMeanSquaredResidual", rootMeanSquaredResidual);
        requireFinite("medianResidual", medianResidual);
        requireFinite("medianAbsoluteDeviation", medianAbsoluteDeviation);
        requireFinite("maximumInwardResidual", maximumInwardResidual);
        requireFinite("maximumOutwardResidual", maximumOutwardResidual);
        if (residuals == null) {
            throw new IllegalArgumentException("residuals must not be null");
        }

        final List<Double> copy = new ArrayList<Double>(residuals.size());
        for (int i = 0; i < residuals.size(); i++) {
            final Double value = residuals.get(i);
            if (value == null || !Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException(
                        "residuals must contain finite values; index " + i + " = " + value);
            }
            copy.add(Double.valueOf(value.doubleValue()));
        }

        this.fittedEllipse = fittedEllipse;
        this.converged = converged;
        this.convergenceReason = convergenceReason;
        this.iterationCount = iterationCount;
        this.initialMeanSquaredResidual = initialMeanSquaredResidual;
        this.finalMeanSquaredResidual = finalMeanSquaredResidual;
        this.rootMeanSquaredResidual = rootMeanSquaredResidual;
        this.medianResidual = medianResidual;
        this.medianAbsoluteDeviation = medianAbsoluteDeviation;
        this.maximumInwardResidual = maximumInwardResidual;
        this.maximumOutwardResidual = maximumOutwardResidual;
        this.residuals = Collections.unmodifiableList(copy);
    }

    public EllipseModel getFittedEllipse() {
        return fittedEllipse;
    }

    public boolean isConverged() {
        return converged;
    }

    public String getConvergenceReason() {
        return convergenceReason;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public double getInitialMeanSquaredResidual() {
        return initialMeanSquaredResidual;
    }

    public double getFinalMeanSquaredResidual() {
        return finalMeanSquaredResidual;
    }

    public double getRootMeanSquaredResidual() {
        return rootMeanSquaredResidual;
    }

    public double getMedianResidual() {
        return medianResidual;
    }

    public double getMedianAbsoluteDeviation() {
        return medianAbsoluteDeviation;
    }

    public double getMaximumInwardResidual() {
        return maximumInwardResidual;
    }

    public double getMaximumOutwardResidual() {
        return maximumOutwardResidual;
    }

    /**
     * Immutable residual list in original support-point order.
     */
    public List<Double> getResiduals() {
        return residuals;
    }

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EllipseFitResult)) {
            return false;
        }
        final EllipseFitResult other = (EllipseFitResult) obj;
        return converged == other.converged
                && iterationCount == other.iterationCount
                && Double.doubleToLongBits(initialMeanSquaredResidual)
                == Double.doubleToLongBits(other.initialMeanSquaredResidual)
                && Double.doubleToLongBits(finalMeanSquaredResidual)
                == Double.doubleToLongBits(other.finalMeanSquaredResidual)
                && Double.doubleToLongBits(rootMeanSquaredResidual)
                == Double.doubleToLongBits(other.rootMeanSquaredResidual)
                && Double.doubleToLongBits(medianResidual)
                == Double.doubleToLongBits(other.medianResidual)
                && Double.doubleToLongBits(medianAbsoluteDeviation)
                == Double.doubleToLongBits(other.medianAbsoluteDeviation)
                && Double.doubleToLongBits(maximumInwardResidual)
                == Double.doubleToLongBits(other.maximumInwardResidual)
                && Double.doubleToLongBits(maximumOutwardResidual)
                == Double.doubleToLongBits(other.maximumOutwardResidual)
                && Objects.equals(fittedEllipse, other.fittedEllipse)
                && Objects.equals(convergenceReason, other.convergenceReason)
                && Objects.equals(residuals, other.residuals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                fittedEllipse,
                Boolean.valueOf(converged),
                convergenceReason,
                Integer.valueOf(iterationCount),
                Double.valueOf(initialMeanSquaredResidual),
                Double.valueOf(finalMeanSquaredResidual),
                Double.valueOf(rootMeanSquaredResidual),
                Double.valueOf(medianResidual),
                Double.valueOf(medianAbsoluteDeviation),
                Double.valueOf(maximumInwardResidual),
                Double.valueOf(maximumOutwardResidual),
                residuals);
    }

    @Override
    public String toString() {
        return "EllipseFitResult{fittedEllipse=" + fittedEllipse
                + ", converged=" + converged
                + ", convergenceReason='" + convergenceReason + '\''
                + ", iterationCount=" + iterationCount
                + ", initialMeanSquaredResidual=" + initialMeanSquaredResidual
                + ", finalMeanSquaredResidual=" + finalMeanSquaredResidual
                + ", rootMeanSquaredResidual=" + rootMeanSquaredResidual
                + ", medianResidual=" + medianResidual
                + ", medianAbsoluteDeviation=" + medianAbsoluteDeviation
                + ", maximumInwardResidual=" + maximumInwardResidual
                + ", maximumOutwardResidual=" + maximumOutwardResidual
                + ", residualCount=" + residuals.size()
                + '}';
    }
}
