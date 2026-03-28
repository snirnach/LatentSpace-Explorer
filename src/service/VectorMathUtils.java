package service;

/**
 * Utility class for pure vector mathematics operations.
 */
public final class VectorMathUtils {

    private VectorMathUtils() {
        // Utility class should not be instantiated.
    }

    /**
     * Adds two vectors element-wise.
     *
     * @param v1 first vector
     * @param v2 second vector
     * @return new vector containing v1 + v2
     */
    public static double[] addVectors(double[] v1, double[] v2) {
        validateEqualLength(v1, v2, "addition");
        double[] result = new double[v1.length];
        for (int i = 0; i < v1.length; i++) {
            result[i] = v1[i] + v2[i];
        }
        return result;
    }

    /**
     * Subtracts the second vector from the first element-wise.
     *
     * @param v1 first vector
     * @param v2 second vector
     * @return new vector containing v1 - v2
     */
    public static double[] subtractVectors(double[] v1, double[] v2) {
        validateEqualLength(v1, v2, "subtraction");
        double[] result = new double[v1.length];
        for (int i = 0; i < v1.length; i++) {
            result[i] = v1[i] - v2[i];
        }
        return result;
    }

    /**
     * Calculates the dot product of two vectors.
     *
     * @param v1 first vector
     * @param v2 second vector
     * @return scalar dot product
     */
    public static double calculateDotProduct(double[] v1, double[] v2) {
        validateEqualLength(v1, v2, "dot product calculation");
        double result = 0.0;
        for (int i = 0; i < v1.length; i++) {
            result += v1[i] * v2[i];
        }
        return result;
    }

    /**
     * Calculates the Euclidean norm of a vector.
     *
     * @param v vector
     * @return Euclidean norm
     */
    public static double calculateVectorNorm(double[] v) {
        double sumOfSquares = 0.0;
        for (double component : v) {
            sumOfSquares += component * component;
        }
        return Math.sqrt(sumOfSquares);
    }

    private static void validateEqualLength(double[] v1, double[] v2, String operationName) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length for " + operationName);
        }
    }
}

