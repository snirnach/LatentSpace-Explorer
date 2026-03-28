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
        double[] axis = VectorMathUtils.subtractVectors(vectorA, vectorB);

        // Calculate the magnitude (norm) of the axis vector.
        double axisNorm = VectorMathUtils.calculateVectorNorm(axis);

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
            double dotProduct = VectorMathUtils.calculateDotProduct(wordVector, axis);
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

            sumVector = VectorMathUtils.addVectors(sumVector, vector);
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

            targetVector = operator > 0
                    ? VectorMathUtils.addVectors(targetVector, wordVector)
                    : VectorMathUtils.subtractVectors(targetVector, wordVector);
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

            double distance = VectorMathUtils.calculateVectorNorm(
                    VectorMathUtils.subtractVectors(targetVector, candidateVector)
            );

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

}
