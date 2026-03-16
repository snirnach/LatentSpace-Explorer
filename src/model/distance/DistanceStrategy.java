package model.distance;

/**
 * Interface for distance calculation strategies.
 * This follows the Strategy Design Pattern, allowing different distance metrics
 * to be used interchangeably for calculating similarity between vectors.
 */
public interface DistanceStrategy {

    /**
     * Calculates the distance between two vectors.
     * @param vectorA the first vector
     * @param vectorB the second vector
     * @return the distance value (lower values indicate higher similarity)
     * @throws IllegalArgumentException if vectors have different lengths
     */
    double calculateDistance(double[] vectorA, double[] vectorB);
}
