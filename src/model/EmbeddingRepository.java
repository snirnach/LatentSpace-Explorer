package model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central in-memory repository for all loaded word embeddings.
 *
 * <p>An enum-based singleton is inherently thread-safe in Java and guarantees
 * that only one repository instance exists for the entire application.
 * The internal map is also thread-safe so future background loading or UI work
 * can access the repository safely.</p>
 */
public enum EmbeddingRepository {

    INSTANCE;

    private final Map<String, WordNode> wordMap = new ConcurrentHashMap<>();

    /**
     * Adds or replaces a word entry in the repository.
     *
     * @param node the word node to store
     */
    public void addWord(WordNode node) {
        if (node == null || node.getWord() == null) {
            return;
        }

        wordMap.put(node.getWord(), node);
    }

    /**
     * Retrieves a word node by its exact key.
     *
     * @param word the word to search for
     * @return the matching word node, or {@code null} if not found
     */
    public WordNode getWord(String word) {
        return wordMap.get(word);
    }

    /**
     * Returns a snapshot of all stored words.
     *
     * @return a detached collection containing the current repository values
     */
    public Collection<WordNode> getAllWords() {
        return new ArrayList<>(wordMap.values());
    }

    /**
     * Removes all loaded embeddings from memory.
     */
    public void clearData() {
        wordMap.clear();
    }
}
