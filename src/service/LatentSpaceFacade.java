package service;

import model.EmbeddingRepository;
import model.WordNode;
import model.distance.DistanceStrategy;
import model.distance.DistanceStrategyFactory;
import util.DataLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Facade that exposes a simplified API for UI layers.
 *
 * <p>This class hides repository access, data loading, strategy selection,
 * and KNN internals behind focused methods.</p>
 */
public class LatentSpaceFacade {

    private final KNNService knnService;
    private final VectorMathService vectorMathService;
    private final DataLoader dataLoader;

    /**
     * Creates a facade with internally managed backend services.
     */
    public LatentSpaceFacade() {
        this.knnService = new KNNService();
        this.vectorMathService = new VectorMathService();
        this.dataLoader = new DataLoader();
    }

    /**
     * Loads embedding data from full and PCA JSON files.
     */
    public void loadData(String fullVectorsPath, String pcaVectorsPath) {
        dataLoader.loadDataToRepository(fullVectorsPath, pcaVectorsPath);
    }

    /**
     * Returns all words currently loaded in the repository.
     */
    public Collection<WordNode> getAllWords() {
        return EmbeddingRepository.INSTANCE.getAllWords();
    }

    /**
     * Finds similar words using the requested distance strategy.
     *
     * @param targetWord the word to search neighbors for
     * @param k the number of neighbors to return
     * @param strategyName strategy label, such as "Euclidean" or "Cosine"
     * @return nearest neighbors, or an empty list when the target word is not found
     */
    public List<WordNode> findSimilarWords(String targetWord, int k, String strategyName) {
        if (targetWord == null || targetWord.isBlank() || k <= 0) {
            return Collections.emptyList();
        }

        WordNode targetNode = findWordNode(targetWord);
        if (targetNode == null || targetNode.getOriginalVector() == null) {
            return Collections.emptyList();
        }

        DistanceStrategy strategy = DistanceStrategyFactory.createStrategy(strategyName);
        knnService.setDistanceStrategy(strategy);
        return knnService.findNearestNeighbors(targetNode.getOriginalVector(), k);
    }

    /**
     * Calculates distance between two nodes using the selected strategy.
     *
     * @return calculated distance, or {@code Double.NaN} for invalid inputs
     */
    public double calculateDistance(WordNode n1, WordNode n2, String strategyName) {
        if (n1 == null || n2 == null) {
            return Double.NaN;
        }

        double[] firstVector = n1.getOriginalVector();
        double[] secondVector = n2.getOriginalVector();
        if (firstVector == null || secondVector == null || firstVector.length != secondVector.length) {
            return Double.NaN;
        }

        DistanceStrategy strategy = DistanceStrategyFactory.createStrategy(strategyName);
        knnService.setDistanceStrategy(strategy);
        return knnService.calculateDistance(firstVector, secondVector);
    }

    private WordNode findWordNode(String requestedWord) {
        WordNode directMatch = EmbeddingRepository.INSTANCE.getWord(requestedWord);
        if (directMatch != null) {
            return directMatch;
        }

        WordNode lowerCaseMatch = EmbeddingRepository.INSTANCE.getWord(requestedWord.toLowerCase(Locale.ROOT));
        if (lowerCaseMatch != null) {
            return lowerCaseMatch;
        }

        return EmbeddingRepository.INSTANCE.getAllWords().stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getWord() != null)
                .filter(node -> node.getWord().equalsIgnoreCase(requestedWord))
                .findFirst()
                .orElse(null);
    }

    /**
     * Calculates the scalar projection of all words onto a semantic axis defined by two reference words.
     * The axis is calculated as vectorA - vectorB. Returns results sorted by projection score in descending order.
     *
     * @param wordA the first reference word defining the positive direction of the semantic axis
     * @param wordB the second reference word defining the negative direction of the semantic axis
     * @return a list of map entries (word name, projection score) sorted by score descending,
     *         or an empty list if either word is not found or vectors have mismatched lengths
     */
    public List<Map.Entry<String, Double>> getSemanticProjection(String wordA, String wordB) {
        return vectorMathService.calculateSemanticProjection(wordA, wordB);
    }

    /**
     * Calculates centroid of a selected group of words using original vectors.
     *
     * @param group selected word group
     * @return centroid vector, or an empty vector when the group has no usable vectors
     */
    public double[] calculateCentroid(Collection<WordNode> group) {
        return vectorMathService.calculateCentroid(group);
    }

    /**
     * Finds nearest neighbors for a raw target vector using the requested distance metric.
     *
     * @param targetVector raw vector used as the KNN query
     * @param k number of neighbors to return
     * @param distanceMetric distance metric label (for example, "Euclidean" or "Cosine")
     * @return nearest neighbors sorted by increasing distance
     */
    public List<WordNode> findSimilarToVector(double[] targetVector, int k, String distanceMetric) {
        return knnService.findSimilarToVector(targetVector, k, distanceMetric);
    }

    /**
     * Calculates analogy result for the equation w1 - w2 + w3.
     *
     * @param w1 first word in the equation
     * @param w2 subtracted word in the equation
     * @param w3 added word in the equation
     * @return closest matching word node, or null when inputs are invalid
     */
    public WordNode calculateAnalogy(String w1, String w2, String w3) {
        return vectorMathService.calculateAnalogy(w1, w2, w3);
    }

    /**
     * Solves a dynamic vector equation such as "king - man + woman".
     *
     * @param equation equation string containing words and +/- operators
     * @param parsedWordsOut output list with words parsed and found in the repository
     * @return closest matching word node for the computed equation vector
     */
    public WordNode solveEquation(String equation, List<WordNode> parsedWordsOut) {
        return vectorMathService.solveEquation(equation, parsedWordsOut);
    }
}

