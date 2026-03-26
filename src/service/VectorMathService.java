package service;

import model.WordNode;
import model.EmbeddingRepository;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Calculates the scalar projection of all words onto a semantic axis defined by two words.
     * The axis is calculated as vectorA - vectorB, and each word's projection is computed as
     * (dotProduct(wordVector, axis)) / norm(axis).
     *
     * @param wordA the first reference word defining the positive direction of the axis
     * @param wordB the second reference word defining the negative direction of the axis
     * @return a list of map entries (word name, projection score) sorted in descending order by score,
     *         or an empty list if either wordA or wordB is not found in the repository
     */
    public List<Map.Entry<String, Double>> calculateSemanticProjection(String wordA, String wordB) {
        // Retrieve vectors for both reference words.
        WordNode nodeA = EmbeddingRepository.INSTANCE.getWord(wordA);
        WordNode nodeB = EmbeddingRepository.INSTANCE.getWord(wordB);

        // Return empty list if either word is not found.
        if (nodeA == null || nodeB == null || nodeA.getOriginalVector() == null || nodeB.getOriginalVector() == null) {
            return new ArrayList<>();
        }

        double[] vectorA = nodeA.getOriginalVector();
        double[] vectorB = nodeB.getOriginalVector();

        // Verify vectors have the same length.
        if (vectorA.length != vectorB.length) {
            return new ArrayList<>();
        }

        // Calculate the axis vector: axis = vectorA - vectorB.
        double[] axis = subtractVectors(vectorA, vectorB);

        // Calculate the magnitude (norm) of the axis vector.
        double axisNorm = calculateVectorNorm(axis);

        // Avoid division by zero.
        if (axisNorm == 0.0) {
            return new ArrayList<>();
        }

        // Calculate projections for all words in the repository.
        List<Map.Entry<String, Double>> projections = new ArrayList<>();

        for (WordNode word : EmbeddingRepository.INSTANCE.getAllWords()) {
            if (word == null || word.getWord() == null || word.getOriginalVector() == null) {
                continue;
            }

            double[] wordVector = word.getOriginalVector();

            // Skip vectors with mismatched length.
            if (wordVector.length != axis.length) {
                continue;
            }

            // Calculate scalar projection: (dotProduct(wordVector, axis)) / norm(axis).
            double dotProduct = calculateDotProduct(wordVector, axis);
            double projection = dotProduct / axisNorm;

            // Store the result as a map entry.
            projections.add(new AbstractMap.SimpleEntry<>(word.getWord(), projection));
        }

        // Sort in descending order by projection score (highest positive score first).
        projections.sort(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed());

        return projections;
    }

    /**
     * Calculates the centroid (average vector) for a selected group of words.
     *
     * @param group collection of word nodes used to compute the centroid
     * @return centroid vector, or an empty vector when the group has no usable vectors
     */
    public double[] calculateCentroid(Collection<WordNode> group) {
        if (group == null || group.isEmpty()) {
            return new double[0];
        }

        double[] sumVector = null;
        int validCount = 0;

        for (WordNode wordNode : group) {
            if (wordNode == null || wordNode.getOriginalVector() == null) {
                continue;
            }

            double[] vector = wordNode.getOriginalVector();
            if (sumVector == null) {
                sumVector = new double[vector.length];
            }

            if (vector.length != sumVector.length) {
                continue;
            }

            for (int i = 0; i < vector.length; i++) {
                sumVector[i] += vector[i];
            }
            validCount++;
        }

        if (sumVector == null || validCount == 0) {
            return new double[0];
        }

        for (int i = 0; i < sumVector.length; i++) {
            sumVector[i] /= validCount;
        }

        return sumVector;
    }

    /**
     * Calculates an analogy result for the equation w1 - w2 + w3.
     *
     * @param w1 first word in the equation
     * @param w2 subtracted word in the equation
     * @param w3 added word in the equation
     * @return closest matching word node to the target vector, excluding w1, w2, and w3
     */
    public WordNode calculateAnalogy(String w1, String w2, String w3) {
        List<WordNode> parsedWords = new ArrayList<>();
        return solveEquation(w1 + " - " + w2 + " + " + w3, parsedWords);
    }

    /**
     * Solves a dynamic vector arithmetic equation such as "king - man + woman".
     *
     * @param equation user equation containing words and +/- operators
     * @param parsedWordsOut output list of parsed words that were found in the repository
     * @return closest matching word node to the equation vector, excluding parsed input words
     */
    public WordNode solveEquation(String equation, List<WordNode> parsedWordsOut) {
        if (parsedWordsOut != null) {
            parsedWordsOut.clear();
        }

        if (equation == null || equation.isBlank()) {
            return null;
        }

        String normalizedEquation = equation.replaceAll("([+-])", " $1 ").trim();
        if (normalizedEquation.isEmpty()) {
            return null;
        }

        String[] tokens = normalizedEquation.split("\\s+");

        int operator = 1;
        boolean initialized = false;
        double[] targetVector = new double[100];
        Set<String> excludedWords = new HashSet<>();

        for (String token : tokens) {
            if ("+".equals(token)) {
                operator = 1;
                continue;
            }

            if ("-".equals(token)) {
                operator = -1;
                continue;
            }

            WordNode wordNode = resolveWordNode(token);
            if (wordNode == null || wordNode.getOriginalVector() == null) {
                continue;
            }

            double[] wordVector = wordNode.getOriginalVector();
            if (!initialized) {
                targetVector = new double[wordVector.length];
                initialized = true;
            }

            if (wordVector.length != targetVector.length) {
                continue;
            }

            if (parsedWordsOut != null) {
                parsedWordsOut.add(wordNode);
            }
            excludedWords.add(wordNode.getWord().toLowerCase());

            for (int i = 0; i < targetVector.length; i++) {
                targetVector[i] += operator * wordVector[i];
            }
        }

        if (!initialized) {
            return null;
        }

        WordNode closest = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (WordNode candidate : EmbeddingRepository.INSTANCE.getAllWords()) {
            if (candidate == null || candidate.getWord() == null || candidate.getOriginalVector() == null) {
                continue;
            }

            if (excludedWords.contains(candidate.getWord().toLowerCase())) {
                continue;
            }

            double[] candidateVector = candidate.getOriginalVector();
            if (candidateVector.length != targetVector.length) {
                continue;
            }

            double distance = 0.0;
            for (int i = 0; i < targetVector.length; i++) {
                double diff = targetVector[i] - candidateVector[i];
                distance += diff * diff;
            }
            distance = Math.sqrt(distance);

            if (distance < bestDistance) {
                bestDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private WordNode resolveWordNode(String token) {
        WordNode directMatch = EmbeddingRepository.INSTANCE.getWord(token);
        if (directMatch != null) {
            return directMatch;
        }

        return EmbeddingRepository.INSTANCE.getAllWords().stream()
                .filter(wordNode -> wordNode != null && wordNode.getWord() != null)
                .filter(wordNode -> wordNode.getWord().equalsIgnoreCase(token))
                .findFirst()
                .orElse(null);
    }

    /**
     * Calculates the dot product (scalar product) of two vectors.
     *
     * @param v1 the first vector
     * @param v2 the second vector
     * @return the dot product of v1 and v2
     * @throws IllegalArgumentException if vectors have different lengths
     */
    private double calculateDotProduct(double[] v1, double[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length for dot product calculation");
        }

        double result = 0.0;
        for (int i = 0; i < v1.length; i++) {
            result += v1[i] * v2[i];
        }
        return result;
    }

    /**
     * Calculates the Euclidean norm (magnitude) of a vector.
     *
     * @param v the vector
     * @return the Euclidean norm of the vector
     */
    private double calculateVectorNorm(double[] v) {
        double sumOfSquares = 0.0;
        for (double component : v) {
            sumOfSquares += component * component;
        }
        return Math.sqrt(sumOfSquares);
    }
}
