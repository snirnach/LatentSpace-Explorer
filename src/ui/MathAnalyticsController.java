package ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import model.WordNode;
import service.LatentSpaceFacade;

import java.util.ArrayList;
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

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            Supplier<IVisualizationView> activeViewSupplier
    ) {
        this.facade = facade;
        this.controlPanelView = controlPanelView;
        this.activeViewSupplier = activeViewSupplier;
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

        if (wordA == null || wordA.isBlank() || wordB == null || wordB.isBlank()) {
            controlPanelView.setStatusMessage("Please enter both a positive and negative pole word.");
            return;
        }

        List<Map.Entry<String, Double>> projections = facade.getSemanticProjection(wordA.trim(), wordB.trim());

        if (projections == null || projections.isEmpty()) {
            controlPanelView.setStatusMessage("Semantic axis could not be calculated. Check that both words exist in the vocabulary.");
            controlPanelView.clearResults();
            return;
        }

        // Format projection results as "Word: 0.000" (3 decimal places).
        ObservableList<String> formattedResults = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : projections) {
            String formatted = String.format("%s: %.3f", entry.getKey(), entry.getValue());
            formattedResults.add(formatted);
        }

        controlPanelView.displayResults(formattedResults);
        controlPanelView.setResultsTitle("Showing: Semantic Projection");
        controlPanelView.setStatusMessage("Semantic projection calculated using '" + wordA + "' (positive) and '" + wordB + "' (negative).");
    }
}

