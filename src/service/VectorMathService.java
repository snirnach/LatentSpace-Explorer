package service;

import model.WordNode;
import model.EmbeddingRepository;
import java.util.List;

/**
 * Service for performing vector arithmetic operations on word embeddings.
 * This class enables mathematical operations like "king - man + woman" to compute synthetic vectors,
 * which can then be used for finding analogous words via KNN.
 * All operations are element-wise and ensure vector length consistency.
 */
public class VectorMathService {

    /**
     * Adds two vectors element-wise.
     * @param v1 the first vector
     * @param v2 the second vector
     * @return a new vector that is the element-wise sum of v1 and v2
     * @throws IllegalArgumentException if vectors have different lengths
     */
    private double[] addVectors(double[] v1, double[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length for addition");
        }
        double[] result = new double[v1.length];
        for (int i = 0; i < v1.length; i++) {
            result[i] = v1[i] + v2[i];
        }
        return result;
    }

    /**
     * Subtracts the second vector from the first element-wise.
     * @param v1 the vector to subtract from
     * @param v2 the vector to subtract
     * @return a new vector that is the element-wise difference (v1 - v2)
     * @throws IllegalArgumentException if vectors have different lengths
     */
    private double[] subtractVectors(double[] v1, double[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length for subtraction");
        }
        double[] result = new double[v1.length];
        for (int i = 0; i < v1.length; i++) {
            result[i] = v1[i] - v2[i];
        }
        return result;
    }

    /**
     * Calculates a synthetic vector based on an equation of positive and negative word contributions.
     * For example, positiveWords = ["king", "woman"], negativeWords = ["man"] computes "king - man + woman".
     * Starts with a zero vector and iteratively adds positive vectors and subtracts negative vectors.
     * Skips words that do not exist in the repository and logs a warning.
     * @param positiveWords list of words to add to the equation
     * @param negativeWords list of words to subtract from the equation
     * @return the resulting synthetic vector
     * @throws IllegalArgumentException if no valid words are found or vector lengths mismatch
     */
    public double[] calculateEquation(List<String> positiveWords, List<String> negativeWords) {
        // Determine vector size from the first valid word
        int vectorSize = -1;
        for (String word : positiveWords) {
            WordNode node = EmbeddingRepository.INSTANCE.getWord(word);
            if (node != null) {
                vectorSize = node.getOriginalVector().length;
                break;
            }
        }
        if (vectorSize == -1) {
            for (String word : negativeWords) {
                WordNode node = EmbeddingRepository.INSTANCE.getWord(word);
                if (node != null) {
                    vectorSize = node.getOriginalVector().length;
                    break;
                }
            }
        }
        if (vectorSize == -1) {
            throw new IllegalArgumentException("No valid words found in the repository to determine vector size");
        }

        // Initialize result as zero vector
        double[] result = new double[vectorSize];

        // Add positive words
        for (String word : positiveWords) {
            WordNode node = EmbeddingRepository.INSTANCE.getWord(word);
            if (node != null) {
                double[] vec = node.getOriginalVector();
                if (vec.length != vectorSize) {
                    System.out.println("Warning: Skipping word '" + word + "' due to vector length mismatch");
                    continue;
                }
                result = addVectors(result, vec);
            } else {
                System.out.println("Warning: Word '" + word + "' not found in repository, skipping");
            }
        }

        // Subtract negative words
        for (String word : negativeWords) {
            WordNode node = EmbeddingRepository.INSTANCE.getWord(word);
            if (node != null) {
                double[] vec = node.getOriginalVector();
                if (vec.length != vectorSize) {
                    System.out.println("Warning: Skipping word '" + word + "' due to vector length mismatch");
                    continue;
                }
                result = subtractVectors(result, vec);
            } else {
                System.out.println("Warning: Word '" + word + "' not found in repository, skipping");
            }
        }

        return result;
    }
}
