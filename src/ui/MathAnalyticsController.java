package ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import model.WordNode;
import service.LatentSpaceFacade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Handles equation and projection analytics actions to keep MainController focused on core view orchestration.
 */
public class MathAnalyticsController {

    private final LatentSpaceFacade facade;
    private final ControlPanelView controlPanelView;
    private final Supplier<IVisualizationView> activeViewSupplier;
    private final Supplier<int[]> activeAxesSupplier;
    private final int semanticAxisTargetIndex;

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            Supplier<IVisualizationView> activeViewSupplier
    ) {
        this(facade, controlPanelView, activeViewSupplier, () -> new int[]{0, 1, 2}, 0);
    }

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            Supplier<IVisualizationView> activeViewSupplier,
            Supplier<int[]> activeAxesSupplier,
            int semanticAxisTargetIndex
    ) {
        this.facade = facade;
        this.controlPanelView = controlPanelView;
        this.activeViewSupplier = activeViewSupplier;
        this.activeAxesSupplier = activeAxesSupplier;
        this.semanticAxisTargetIndex = semanticAxisTargetIndex;
        setupListeners();
    }

    private void setupListeners() {
        controlPanelView.setOnCalculateEquationAction(this::handleCalculateEquationAction);
        controlPanelView.setOnProjectAxisAction(this::handleProjectOnAxisAction);
    }

    private void handleCalculateEquationAction(ActionEvent event) {
        String equationText = controlPanelView.getEquationText() == null
                ? ""
                : controlPanelView.getEquationText().trim();

        if (equationText.isBlank()) {
            controlPanelView.setStatusMessage("Please enter an equation to solve.");
            return;
        }

        List<WordNode> equationWords = new ArrayList<>();
        WordNode resultWord = facade.solveEquation(equationText, equationWords);
        if (resultWord == null) {
            controlPanelView.setStatusMessage("Equation result could not be calculated. Verify all words exist in the vocabulary.");
            return;
        }

        ObservableList<String> mathItems = FXCollections.observableArrayList();
        mathItems.add(resultWord.getWord());
        controlPanelView.displayResults(mathItems);
        controlPanelView.setResultsTitle("Showing: Math Results");

        IVisualizationView activeView = activeViewSupplier.get();
        if (activeView != null) {
            activeView.showMathPath(equationWords, resultWord);
        }

        controlPanelView.setStatusMessage("Equation computed: " + equationText + ".");
    }

    private void handleProjectOnAxisAction(ActionEvent event) {
        String wordA = controlPanelView.getSemanticAxisWordAText();
        String wordB = controlPanelView.getSemanticAxisWordBText();

        // 1. Validate user input
        if (wordA == null || wordA.isBlank() || wordB == null || wordB.isBlank()) {
            controlPanelView.setStatusMessage("Please enter both a positive and negative pole word.");
            return;
        }

        // 2. Calculate semantic projection scores via the facade
        List<Map.Entry<String, Double>> projections = facade.getSemanticProjection(wordA.trim(), wordB.trim());

        if (projections == null || projections.isEmpty()) {
            controlPanelView.setStatusMessage("Semantic axis could not be calculated. Check that both words exist in the vocabulary.");
            controlPanelView.clearResults();
            return;
        }

        // 3. Update the UI results list (textual representation)
        ObservableList<String> formattedResults = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : projections) {
            String formatted = String.format("%s: %.3f", entry.getKey(), entry.getValue());
            formattedResults.add(formatted);
        }
        controlPanelView.displayResults(formattedResults);
        controlPanelView.setResultsTitle("Showing: Semantic Projection");

        // 4. Update the Visualization View (The visual "Bridge")
        IVisualizationView activeView = activeViewSupplier.get();
        if (activeView != null) {
            WordNode poleANode = facade.getWordNode(wordA.trim());
            WordNode poleBNode = facade.getWordNode(wordB.trim());

            // Pass the calculated semantic scores to the view/renderer
            activeView.setSemanticScores(projections);
            activeView.setSemanticPoles(poleANode, poleBNode);

            // Create a DEEP COPY of the current PCA axes to avoid modifying original state
            int[] baseAxes = activeAxesSupplier.get();
            int[] projectedAxes = java.util.Arrays.copyOf(baseAxes, baseAxes.length);

            // Map the custom semantic axis (index 999) to the target dimension (X, Y, or Z)
            int targetIdx = Math.max(0, Math.min(2, semanticAxisTargetIndex));
            projectedAxes[targetIdx] = Graph2DRenderer.SEMANTIC_AXIS_INDEX; // Uses the 999 sentinel value

            // Trigger a full redraw with the new semantic coordinate mapping
            Collection<WordNode> allWords = facade.getAllWords();
            activeView.updateData(allWords, projectedAxes);
        }

        controlPanelView.setStatusMessage("Semantic projection calculated using '" + wordA + "' (positive) and '" + wordB + "' (negative).");
    }

    private int[] normalizeAxes(int[] axes) {
        if (axes == null || axes.length < 3) {
            return new int[]{0, 1, 2};
        }

        return new int[]{
                axes[0],
                axes[1],
                axes[2]
        };
    }
}

