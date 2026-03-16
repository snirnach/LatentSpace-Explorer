package service;

import model.WordNode;
import model.EmbeddingRepository;
import model.distance.DistanceStrategy;
import model.distance.EuclideanDistance;
import java.util.*;

/**
 * Service for finding K-Nearest Neighbors (KNN) using a pluggable distance strategy.
 * This class uses the Strategy Design Pattern to allow dynamic switching of distance metrics,
 * promoting extensibility and adherence to OOP principles.
 * The KNN algorithm finds the k words whose original vectors are closest to a target vector.
 */
public class KNNService {

    private DistanceStrategy currentStrategy;

    /**
     * Constructor initializes with Euclidean distance as the default strategy.
     */
    public KNNService() {
        this.currentStrategy = new EuclideanDistance();
    }

    /**
     * Sets the distance strategy to be used for calculations.
     * This allows runtime switching of strategies (e.g., from Euclidean to Cosine).
     * @param strategy the distance strategy to use
     */
    public void setDistanceStrategy(DistanceStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Distance strategy cannot be null");
        }

        this.currentStrategy = strategy;
    }

    /**
     * Calculates the distance between two vectors using the currently selected strategy.
     * This method allows higher layers, such as the GUI controller, to obtain formatted
     * distance values without knowing which concrete strategy is active.
     *
     * @param vectorA the first vector
     * @param vectorB the second vector
     * @return the distance produced by the current strategy
     */
    public double calculateDistance(double[] vectorA, double[] vectorB) {
        if (vectorA == null || vectorB == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }

        return currentStrategy.calculateDistance(vectorA, vectorB);
    }

    /**
     * Finds the k nearest neighbors to the target vector based on the current distance strategy.
     * Distances are calculated using the original vectors of words.
     * Uses a PriorityQueue for efficient O(n log k) time complexity.
     * @param targetVector the vector to find neighbors for
     * @param k the number of nearest neighbors to return
     * @return a list of the k nearest WordNodes, sorted by increasing distance
     */
    public List<WordNode> findNearestNeighbors(double[] targetVector, int k) {
        if (targetVector == null) {
            throw new IllegalArgumentException("Target vector cannot be null");
        }

        if (k <= 0) {
            return Collections.emptyList();
        }

        Collection<WordNode> allWords = EmbeddingRepository.INSTANCE.getAllWords();
        // PriorityQueue as max-heap: keeps the k smallest distances
        PriorityQueue<DistanceEntry> pq = new PriorityQueue<>((a, b) -> Double.compare(b.distance, a.distance));

        for (WordNode word : allWords) {
            double[] candidateVector = word.getOriginalVector();
            if (candidateVector == null || candidateVector.length != targetVector.length) {
                continue;
            }

            double distance = currentStrategy.calculateDistance(targetVector, candidateVector);
            if (pq.size() < k) {
                pq.add(new DistanceEntry(word, distance));
            } else if (!pq.isEmpty() && distance < pq.peek().distance) {
                pq.poll();
                pq.add(new DistanceEntry(word, distance));
            }
        }

        // Extract results: since max-heap, poll gives largest first, so collect and reverse
        List<WordNode> result = new ArrayList<>();
        while (!pq.isEmpty()) {
            result.add(pq.poll().node);
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Helper class to pair a WordNode with its calculated distance.
     */
    private static class DistanceEntry {
        WordNode node;
        double distance;

        DistanceEntry(WordNode node, double distance) {
            this.node = node;
            this.distance = distance;
        }
    }
}
