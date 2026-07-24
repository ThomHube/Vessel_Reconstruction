package vesselreconstruction.ellipse;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic free-ellipse fitter for 2D ellipse support points.
 * Minimizes the mean squared normalized radial residual using damped
 * Gauss-Newton / Levenberg-Marquardt iteration. Does not optimize circularity
 * and does not force the two axes to be equal.
 */
public final class FreeEllipseFitter {

    public static final int MAX_ITERATIONS = 200;

    private static final int MAX_DAMPING_ATTEMPTS = 20;
    private static final double INITIAL_DAMPING = 1.0e-3;
    private static final double MIN_DAMPING = 1.0e-12;
    private static final double MAX_DAMPING = 1.0e12;
    private static final double OBJECTIVE_TOLERANCE = 1.0e-14;
    private static final double PARAMETER_TOLERANCE = 1.0e-10;
    private static final double MATRIX_EPSILON = 1.0e-12;
    private static final double GEOMETRY_EPSILON = 1.0e-12;

    private static final int PARAM_COUNT = 5;
    private static final int IDX_CX = 0;
    private static final int IDX_CY = 1;
    private static final int IDX_LOG_A = 2;
    private static final int IDX_LOG_B = 3;
    private static final int IDX_THETA = 4;

    public static final String PARAMETER_CHANGE_BELOW_TOLERANCE =
            "PARAMETER_CHANGE_BELOW_TOLERANCE";
    public static final String OBJECTIVE_CHANGE_BELOW_TOLERANCE =
            "OBJECTIVE_CHANGE_BELOW_TOLERANCE";
    public static final String MAXIMUM_ITERATIONS_REACHED =
            "MAXIMUM_ITERATIONS_REACHED";
    public static final String NO_IMPROVING_DAMPED_STEP =
            "NO_IMPROVING_DAMPED_STEP";
    public static final String SINGULAR_NORMAL_EQUATIONS =
            "SINGULAR_NORMAL_EQUATIONS";

    /**
     * Fits a free ellipse to the supplied ellipse support points.
     * Does not mutate the list or any point objects.
     */
    public EllipseFitResult fit(final List<Point2D.Double> supportPoints) {
        final Point2D.Double[] points = validateAndCopy(supportPoints);
        final EllipseModel initial = initialEstimate(points);

        final double[] params = toParameters(initial);
        double currentMse = meanSquaredResidual(points, params);
        final double initialMse = currentMse;
        if (!Double.isFinite(initialMse)) {
            throw new IllegalArgumentException(
                    "Initial free-ellipse estimate produced a non-finite objective");
        }

        double damping = INITIAL_DAMPING;
        String reason = MAXIMUM_ITERATIONS_REACHED;
        boolean converged = false;
        int iterationCount = 0;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            iterationCount = iteration + 1;

            final Workspace workspace = buildWorkspace(points, params);
            if (!workspace.finite) {
                reason = SINGULAR_NORMAL_EQUATIONS;
                break;
            }

            boolean stepAccepted = false;
            boolean singular = false;
            double[] acceptedParams = null;
            double acceptedMse = currentMse;

            for (int attempt = 0; attempt < MAX_DAMPING_ATTEMPTS; attempt++) {
                final double[] delta = solveNormalEquations(workspace, damping);
                if (delta == null) {
                    singular = true;
                    damping = increaseDamping(damping);
                    if (damping > MAX_DAMPING) {
                        break;
                    }
                    continue;
                }

                final double[] trial = addDelta(params, delta);
                canonicalizeParameters(trial);
                final double trialMse = meanSquaredResidual(points, trial);
                if (!Double.isFinite(trialMse) || !(trialMse < currentMse)) {
                    damping = increaseDamping(damping);
                    if (damping > MAX_DAMPING) {
                        break;
                    }
                    continue;
                }

                acceptedParams = trial;
                acceptedMse = trialMse;
                stepAccepted = true;
                damping = decreaseDamping(damping);
                break;
            }

            if (!stepAccepted) {
                if (singular && damping > MAX_DAMPING) {
                    reason = SINGULAR_NORMAL_EQUATIONS;
                } else {
                    reason = NO_IMPROVING_DAMPED_STEP;
                }
                break;
            }

            final double objectiveChange = Math.abs(currentMse - acceptedMse);
            final boolean parameterConverged =
                    parameterChangeBelowTolerance(params, acceptedParams);
            System.arraycopy(acceptedParams, 0, params, 0, PARAM_COUNT);
            currentMse = acceptedMse;

            if (parameterConverged) {
                reason = PARAMETER_CHANGE_BELOW_TOLERANCE;
                converged = true;
                break;
            }
            if (objectiveChange < OBJECTIVE_TOLERANCE) {
                reason = OBJECTIVE_CHANGE_BELOW_TOLERANCE;
                converged = true;
                break;
            }
        }

        final EllipseModel fitted = fromParameters(params);
        return buildResult(fitted, points, converged, reason, iterationCount, initialMse);
    }

    private static Point2D.Double[] validateAndCopy(final List<Point2D.Double> supportPoints) {
        if (supportPoints == null) {
            throw new IllegalArgumentException("supportPoints must not be null");
        }
        if (supportPoints.size() < 6) {
            throw new IllegalArgumentException(
                    "At least 6 ellipse support points are required; got "
                            + supportPoints.size());
        }

        final Point2D.Double[] points = new Point2D.Double[supportPoints.size()];
        final Set<Long> distinct = new HashSet<Long>();
        for (int i = 0; i < supportPoints.size(); i++) {
            final Point2D.Double point = supportPoints.get(i);
            if (point == null) {
                throw new IllegalArgumentException("supportPoints must not contain null entries");
            }
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y)) {
                throw new IllegalArgumentException(
                        "Point coordinates must be finite at index " + i
                                + ": (" + point.x + ", " + point.y + ")");
            }
            points[i] = new Point2D.Double(point.x, point.y);
            distinct.add(coordinateKey(point.x, point.y));
        }
        if (distinct.size() < 6) {
            throw new IllegalArgumentException(
                    "At least 6 distinct coordinate pairs are required; got "
                            + distinct.size());
        }
        return points;
    }

    private static long coordinateKey(final double x, final double y) {
        return (Double.doubleToLongBits(x) << 1)
                ^ Double.doubleToLongBits(y);
    }

    private static EllipseModel initialEstimate(final Point2D.Double[] points) {
        final int n = points.length;
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            meanX += points[i].x;
            meanY += points[i].y;
        }
        meanX /= n;
        meanY /= n;

        double cxx = 0.0;
        double cyy = 0.0;
        double cxy = 0.0;
        for (int i = 0; i < n; i++) {
            final double dx = points[i].x - meanX;
            final double dy = points[i].y - meanY;
            cxx += dx * dx;
            cyy += dy * dy;
            cxy += dx * dy;
        }
        cxx /= n;
        cyy /= n;
        cxy /= n;

        final double trace = cxx + cyy;
        final double detTerm = (cxx - cyy) * (cxx - cyy) + 4.0 * cxy * cxy;
        final double spread = Math.sqrt(Math.max(0.0, detTerm));
        final double lambda1 = 0.5 * (trace + spread);
        final double lambda2 = 0.5 * (trace - spread);
        final double larger = Math.max(lambda1, lambda2);
        final double smaller = Math.min(lambda1, lambda2);

        // Reject singular / effectively one-dimensional covariance.
        if (!(larger > GEOMETRY_EPSILON) || !(smaller > GEOMETRY_EPSILON)) {
            throw new IllegalArgumentException(
                    "Point set covariance is singular or effectively one-dimensional;"
                            + " eigenvalues=(" + larger + ", " + smaller + ")");
        }

        final double rotation = 0.5 * Math.atan2(2.0 * cxy, cxx - cyy);
        final double semiMajor = Math.sqrt(2.0 * larger);
        final double semiMinor = Math.sqrt(2.0 * smaller);
        return new EllipseModel(meanX, meanY, semiMajor, semiMinor, rotation);
    }

    private static double[] toParameters(final EllipseModel model) {
        return new double[] {
                model.getCenterX(),
                model.getCenterY(),
                Math.log(model.getSemiMajorAxis()),
                Math.log(model.getSemiMinorAxis()),
                model.getRotationRadians()
        };
    }

    private static EllipseModel fromParameters(final double[] params) {
        return new EllipseModel(
                params[IDX_CX],
                params[IDX_CY],
                Math.exp(params[IDX_LOG_A]),
                Math.exp(params[IDX_LOG_B]),
                params[IDX_THETA]);
    }

    private static void canonicalizeParameters(final double[] params) {
        final EllipseModel model = fromParameters(params);
        params[IDX_CX] = model.getCenterX();
        params[IDX_CY] = model.getCenterY();
        params[IDX_LOG_A] = Math.log(model.getSemiMajorAxis());
        params[IDX_LOG_B] = Math.log(model.getSemiMinorAxis());
        params[IDX_THETA] = model.getRotationRadians();
    }

    private static double[] addDelta(final double[] params, final double[] delta) {
        final double[] trial = new double[PARAM_COUNT];
        for (int i = 0; i < PARAM_COUNT; i++) {
            trial[i] = params[i] + delta[i];
        }
        return trial;
    }

    private static boolean parameterChangeBelowTolerance(
            final double[] before,
            final double[] after) {
        final double centerShift = Math.hypot(
                after[IDX_CX] - before[IDX_CX],
                after[IDX_CY] - before[IDX_CY]);
        final double logAxisChange = Math.max(
                Math.abs(after[IDX_LOG_A] - before[IDX_LOG_A]),
                Math.abs(after[IDX_LOG_B] - before[IDX_LOG_B]));
        final double rotationChange = smallestRotationDifference(
                after[IDX_THETA] - before[IDX_THETA]);
        return centerShift < PARAMETER_TOLERANCE
                && logAxisChange < PARAMETER_TOLERANCE
                && rotationChange < PARAMETER_TOLERANCE;
    }

    private static double smallestRotationDifference(final double delta) {
        double x = delta % Math.PI;
        if (x > Math.PI / 2.0) {
            x -= Math.PI;
        }
        if (x < -Math.PI / 2.0) {
            x += Math.PI;
        }
        return Math.abs(x);
    }

    private static double meanSquaredResidual(
            final Point2D.Double[] points,
            final double[] params) {
        final double a = Math.exp(params[IDX_LOG_A]);
        final double b = Math.exp(params[IDX_LOG_B]);
        final double cos = Math.cos(params[IDX_THETA]);
        final double sin = Math.sin(params[IDX_THETA]);
        double sum = 0.0;
        for (int i = 0; i < points.length; i++) {
            final double residual = residualAt(
                    points[i].x, points[i].y,
                    params[IDX_CX], params[IDX_CY],
                    a, b, cos, sin);
            sum += residual * residual;
        }
        return sum / points.length;
    }

    private static double residualAt(
            final double x,
            final double y,
            final double cx,
            final double cy,
            final double a,
            final double b,
            final double cos,
            final double sin) {
        final double dx = x - cx;
        final double dy = y - cy;
        final double localX = cos * dx + sin * dy;
        final double localY = -sin * dx + cos * dy;
        final double q = (localX * localX) / (a * a) + (localY * localY) / (b * b);
        return Math.sqrt(q) - 1.0;
    }

    private static Workspace buildWorkspace(
            final Point2D.Double[] points,
            final double[] params) {
        final Workspace workspace = new Workspace(points.length);
        final double a = Math.exp(params[IDX_LOG_A]);
        final double b = Math.exp(params[IDX_LOG_B]);
        final double cos = Math.cos(params[IDX_THETA]);
        final double sin = Math.sin(params[IDX_THETA]);
        final double invA2 = 1.0 / (a * a);
        final double invB2 = 1.0 / (b * b);

        for (int i = 0; i < points.length; i++) {
            final double dx = points[i].x - params[IDX_CX];
            final double dy = points[i].y - params[IDX_CY];
            final double localX = cos * dx + sin * dy;
            final double localY = -sin * dx + cos * dy;
            final double q = localX * localX * invA2 + localY * localY * invB2;
            final double radius = Math.sqrt(q);
            final double residual = radius - 1.0;
            workspace.residuals[i] = residual;

            final double denom = 2.0 * Math.max(radius, GEOMETRY_EPSILON);
            final double scale = 1.0 / denom;

            final double dqDcx = 2.0 * localX * invA2 * (-cos)
                    + 2.0 * localY * invB2 * (sin);
            final double dqDcy = 2.0 * localX * invA2 * (-sin)
                    + 2.0 * localY * invB2 * (-cos);
            final double dqDlogA = -2.0 * localX * localX * invA2;
            final double dqDlogB = -2.0 * localY * localY * invB2;
            final double dqDtheta = 2.0 * localX * localY * (invA2 - invB2);

            workspace.jacobian[i][IDX_CX] = scale * dqDcx;
            workspace.jacobian[i][IDX_CY] = scale * dqDcy;
            workspace.jacobian[i][IDX_LOG_A] = scale * dqDlogA;
            workspace.jacobian[i][IDX_LOG_B] = scale * dqDlogB;
            workspace.jacobian[i][IDX_THETA] = scale * dqDtheta;

            if (!Double.isFinite(residual)
                    || !Double.isFinite(workspace.jacobian[i][IDX_CX])
                    || !Double.isFinite(workspace.jacobian[i][IDX_CY])
                    || !Double.isFinite(workspace.jacobian[i][IDX_LOG_A])
                    || !Double.isFinite(workspace.jacobian[i][IDX_LOG_B])
                    || !Double.isFinite(workspace.jacobian[i][IDX_THETA])) {
                workspace.finite = false;
                return workspace;
            }
        }

        for (int r = 0; r < PARAM_COUNT; r++) {
            double rhs = 0.0;
            for (int i = 0; i < points.length; i++) {
                rhs -= workspace.jacobian[i][r] * workspace.residuals[i];
            }
            workspace.rhs[r] = rhs;
            for (int c = 0; c < PARAM_COUNT; c++) {
                double sum = 0.0;
                for (int i = 0; i < points.length; i++) {
                    sum += workspace.jacobian[i][r] * workspace.jacobian[i][c];
                }
                workspace.jTj[r][c] = sum;
            }
        }
        return workspace;
    }

    private static double[] solveNormalEquations(
            final Workspace workspace,
            final double damping) {
        final double[][] matrix = new double[PARAM_COUNT][PARAM_COUNT];
        final double[] rhs = new double[PARAM_COUNT];
        for (int r = 0; r < PARAM_COUNT; r++) {
            System.arraycopy(workspace.jTj[r], 0, matrix[r], 0, PARAM_COUNT);
            matrix[r][r] += damping;
            rhs[r] = workspace.rhs[r];
        }
        return solveLinearSystem(matrix, rhs);
    }

    /**
     * Deterministic Gaussian elimination with partial pivoting.
     * Returns null when the system is singular or yields a non-finite solution.
     */
    private static double[] solveLinearSystem(final double[][] a, final double[] b) {
        final int n = PARAM_COUNT;
        final double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }

        for (int col = 0; col < n; col++) {
            int pivot = col;
            double pivotAbs = Math.abs(m[col][col]);
            for (int row = col + 1; row < n; row++) {
                final double candidate = Math.abs(m[row][col]);
                if (candidate > pivotAbs) {
                    pivotAbs = candidate;
                    pivot = row;
                }
            }
            if (!(pivotAbs > MATRIX_EPSILON)) {
                return null;
            }
            if (pivot != col) {
                final double[] tmp = m[col];
                m[col] = m[pivot];
                m[pivot] = tmp;
            }

            final double diag = m[col][col];
            for (int j = col; j <= n; j++) {
                m[col][j] /= diag;
            }
            for (int row = 0; row < n; row++) {
                if (row == col) {
                    continue;
                }
                final double factor = m[row][col];
                for (int j = col; j <= n; j++) {
                    m[row][j] -= factor * m[col][j];
                }
            }
        }

        final double[] solution = new double[n];
        for (int i = 0; i < n; i++) {
            solution[i] = m[i][n];
            if (!Double.isFinite(solution[i])) {
                return null;
            }
        }
        return solution;
    }

    private static double increaseDamping(final double damping) {
        final double next = damping * 10.0;
        return Math.min(Math.max(next, MIN_DAMPING), MAX_DAMPING * 10.0);
    }

    private static double decreaseDamping(final double damping) {
        final double next = damping / 10.0;
        return Math.max(next, MIN_DAMPING);
    }

    private static EllipseFitResult buildResult(
            final EllipseModel fitted,
            final Point2D.Double[] points,
            final boolean converged,
            final String reason,
            final int iterationCount,
            final double initialMse) {
        final List<Double> residuals = new ArrayList<Double>(points.length);
        double sumSq = 0.0;
        double maxInward = 0.0;
        double maxOutward = 0.0;
        boolean hasInward = false;
        boolean hasOutward = false;

        for (int i = 0; i < points.length; i++) {
            final double residual = fitted.normalizedRadialResidual(points[i].x, points[i].y);
            if (!Double.isFinite(residual)) {
                throw new IllegalStateException(
                        "Non-finite residual at index " + i + ": " + residual);
            }
            residuals.add(Double.valueOf(residual));
            sumSq += residual * residual;
            if (residual < 0.0) {
                if (!hasInward || residual < maxInward) {
                    maxInward = residual;
                }
                hasInward = true;
            } else if (residual > 0.0) {
                if (!hasOutward || residual > maxOutward) {
                    maxOutward = residual;
                }
                hasOutward = true;
            }
        }

        final double finalMse = sumSq / points.length;
        final double rms = Math.sqrt(finalMse);
        final double median = medianOf(residuals);
        final List<Double> absDev = new ArrayList<Double>(residuals.size());
        for (int i = 0; i < residuals.size(); i++) {
            absDev.add(Double.valueOf(Math.abs(residuals.get(i).doubleValue() - median)));
        }
        final double mad = medianOf(absDev);

        return new EllipseFitResult(
                fitted,
                converged,
                reason,
                iterationCount,
                initialMse,
                finalMse,
                rms,
                median,
                mad,
                hasInward ? maxInward : 0.0,
                hasOutward ? maxOutward : 0.0,
                residuals);
    }

    private static double medianOf(final List<Double> values) {
        final int n = values.size();
        final double[] sorted = new double[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = values.get(i).doubleValue();
        }
        java.util.Arrays.sort(sorted);
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
    }

    private static final class Workspace {
        private final double[] residuals;
        private final double[][] jacobian;
        private final double[][] jTj;
        private final double[] rhs;
        private boolean finite = true;

        private Workspace(final int pointCount) {
            residuals = new double[pointCount];
            jacobian = new double[pointCount][PARAM_COUNT];
            jTj = new double[PARAM_COUNT][PARAM_COUNT];
            rhs = new double[PARAM_COUNT];
        }
    }
}
