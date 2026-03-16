package model.distance;

/**
 * Implementation of Euclidean distance strategy.
 * Euclidean distance is the straight-line distance between two points in n-dimensional space.
 * Formula: sqrt(sum((a_i - b_i)^2 for i in 0 to n-1))
 * This is a common metric for measuring dissimilarity in vector spaces.
 */
public class EuclideanDistance implements DistanceStrategy {

    @Override
    public double calculateDistance(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double sum = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            double diff = vectorA[i] - vectorB[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
