package ui;

import javafx.scene.Node;
import model.WordNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Common contract for visualization views so the controller can switch views polymorphically.
 */
public interface IVisualizationView {

    /**
     * Returns the root UI component that should be placed in the center area.
     */
    Node getUIComponent();

    /**
     * Updates the rendered data using the provided words and selected PCA axes.
     */
    void updateData(Collection<WordNode> words, int[] selectedAxes);

    /**
     * Registers a click listener for selected words in the visualization.
     */
    void setOnWordClicked(Consumer<WordNode> listener);

    /**
     * Focuses and centers the view on a specific word.
     */
    void focusOnWord(WordNode word);

    /**
     * Clears all active visual selection state (focus, probe, math path, and group highlights).
     */
    void clearVisualSelection();

    /**
     * Displays the nearest neighbors probe visualization for a source word
     * with connections/highlights to its neighbors.
     */
    void showNearestNeighbors(WordNode source, List<WordNode> neighbors);

    /**
     * Displays a vector arithmetic path across equation words and points to the closest result word.
     */
    void showMathPath(List<WordNode> equationWords, WordNode closestResult);

    /**
     * Returns the currently selected group of words for subspace analysis.
     */
    java.util.Set<WordNode> getSelectedGroup();

    /**
     * Sets semantic projection scores used by renderers when a semantic axis index is selected.
     */
    void setSemanticScores(List<Map.Entry<String, Double>> projections);

    /**
     * Sets the two semantic pole words used for explicit visual highlighting.
     */
    void setSemanticPoles(WordNode poleA, WordNode poleB);
}

