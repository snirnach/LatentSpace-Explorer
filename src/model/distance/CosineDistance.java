package model.distance;

/**
 * Implementation of Cosine distance strategy.
 * Cosine similarity measures the cosine of the angle between two vectors,
 * ranging from -1 (opposite) to 1 (identical).
 * Formula: dot(a, b) / (||a|| * ||b||)
 * Cosine distance is defined as 1 - cosine_similarity, so 0 means identical, 2 means opposite.
 * This is useful for high-dimensional sparse data where magnitude is less important than direction.
 */
public class CosineDistance implements DistanceStrategy {

    @Override
    public double calculateDistance(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double dot = dotProduct(vectorA, vectorB);
        double normA = norm(vectorA);
        double normB = norm(vectorB);
        if (normA == 0.0 || normB == 0.0) {
            // If either vector is zero, distance is maximum (1.0)
            return 1.0;
        }
        double similarity = dot / (normA * normB);
        // Clamp similarity to [-1, 1] in case of floating point errors
        similarity = Math.max(-1.0, Math.min(1.0, similarity));
        return 1.0 - similarity;
    }

    private double dotProduct(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private double norm(double[] v) {
        double sum = 0.0;
        for (double val : v) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }
}
