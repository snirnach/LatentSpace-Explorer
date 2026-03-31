package ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import model.WordNode;
import service.LatentSpaceFacade;
import command.CalculateEquationCommand;
import command.CommandManager;
import ui.view.ControlPanelView;
import ui.view.Graph2DRenderer;
import ui.view.IVisualizationView;

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
    private final CommandManager commandManager;

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            Supplier<IVisualizationView> activeViewSupplier
    ) {
        this(facade, controlPanelView, activeViewSupplier, () -> new int[]{0, 1, 2}, 0, null);
    }

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            Supplier<IVisualizationView> activeViewSupplier,
            Supplier<int[]> activeAxesSupplier,
            int semanticAxisTargetIndex
    ) {
        this(facade, controlPanelView, activeViewSupplier, activeAxesSupplier, semanticAxisTargetIndex, null);
    }

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            Supplier<IVisualizationView> activeViewSupplier,
            Supplier<int[]> activeAxesSupplier,
            int semanticAxisTargetIndex,
            CommandManager commandManager
    ) {
        this.facade = facade;
        this.controlPanelView = controlPanelView;
        this.activeViewSupplier = activeViewSupplier;
        this.activeAxesSupplier = activeAxesSupplier;
        this.semanticAxisTargetIndex = semanticAxisTargetIndex;
        this.commandManager = commandManager;
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

        // Capture previous state before calculation
        ObservableList<String> previousResults = FXCollections.observableArrayList(controlPanelView.getResultsListView().getItems());
        String previousResultsTitle = controlPanelView.getResultsTitleLabel().getText();
        String previousStatusMessage = controlPanelView.getStatusLabel().getText();
        String previousEquationText = controlPanelView.getEquationText();
        WordNode previousResultWord = null; // We don't track this in current UI, so it's null
        List<WordNode> previousEquationWords = new ArrayList<>(); // We don't track this in current UI

        // Calculate the new equation result
        List<WordNode> equationWords = new ArrayList<>();
        WordNode resultWord = facade.solveEquation(equationText, equationWords);
        if (resultWord == null) {
            controlPanelView.setStatusMessage("Equation result could not be calculated. Verify all words exist in the vocabulary.");
            return;
        }

        // Prepare new state
        ObservableList<String> newResults = FXCollections.observableArrayList();
        newResults.add(resultWord.getWord());
        String newResultsTitle = "Showing: Math Results";
        String newStatusMessage = "Equation computed: " + equationText + ".";

        IVisualizationView activeView = activeViewSupplier.get();

        // Create command with both old and new state
        CalculateEquationCommand command = new CalculateEquationCommand(
                activeView,
                controlPanelView,
                previousResultWord,
                previousEquationWords,
                previousResults,
                previousResultsTitle,
                previousStatusMessage,
                previousEquationText,
                resultWord,
                equationWords,
                newResults,
                newResultsTitle,
                newStatusMessage,
                equationText
        );


        // Execute command through CommandManager if available
        if (commandManager != null) {
            commandManager.executeCommand(command);
            updateHistoryButtonsState();
        }
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
            int targetIdx = Math.clamp(semanticAxisTargetIndex, 0, 2);
            projectedAxes[targetIdx] = Graph2DRenderer.SEMANTIC_AXIS_INDEX; // Uses the 999 sentinel value

            // Trigger a full redraw with the new semantic coordinate mapping
            Collection<WordNode> allWords = facade.getAllWords();
            activeView.updateData(allWords, projectedAxes);
        }

        controlPanelView.setStatusMessage("Semantic projection calculated using '" + wordA + "' (positive) and '" + wordB + "' (negative).");
    }

    /**
     * Updates the state of undo and redo buttons based on command history availability.
     */
    private void updateHistoryButtonsState() {
        if (commandManager != null) {
            controlPanelView.setUndoButtonDisabled(!commandManager.canUndo());
            controlPanelView.setRedoButtonDisabled(!commandManager.canRedo());
        }
    }
}

