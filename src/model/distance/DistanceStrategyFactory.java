package model.distance;

/**
 * Factory for creating distance strategy implementations.
 *
 * <p>This class centralizes strategy selection so callers remain closed to
 * creation details and open to future strategy extensions.</p>
 */
public final class DistanceStrategyFactory {

    private static final String COSINE_NAME = "cosine";
    private static final String EUCLIDEAN_NAME = "euclidean";

    private DistanceStrategyFactory() {
        // Utility class.
    }

    /**
     * Creates a distance strategy from a user-facing strategy name.
     *
     * @param strategyName the requested strategy name
     * @return a matching DistanceStrategy, or EuclideanDistance as the default fallback
     */
    public static DistanceStrategy createStrategy(String strategyName) {
        if (strategyName == null || strategyName.trim().isEmpty()) {
            return new EuclideanDistance();
        }

        String normalizedName = strategyName.trim().toLowerCase();
        if (COSINE_NAME.equals(normalizedName)) {
            return new CosineDistance();
        }

        if (EUCLIDEAN_NAME.equals(normalizedName)) {
            return new EuclideanDistance();
        }

        // Fallback for unknown strategy names.
        return new EuclideanDistance();
    }
}

