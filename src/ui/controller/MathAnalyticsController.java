package ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import model.WordNode;
import service.LatentSpaceFacade;
import command.CalculateEquationCommand;
import command.ProjectSemanticAxisCommand;
import command.CommandManager;
import ui.state.InteractionModel;
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
    private final InteractionModel interactionModel;
    private final Supplier<IVisualizationView> activeViewSupplier;
    private final Supplier<int[]> activeAxesSupplier;
    private final int semanticAxisTargetIndex;
    private final CommandManager commandManager;
    private final Runnable onCommandExecuted;

    private boolean isSemanticModeActive = false;
    private List<Map.Entry<String, Double>> activeSemanticScores = null;
    private WordNode activePoleA = null;
    private WordNode activePoleB = null;
    private String lastSearchedWordA = "";
    private String lastSearchedWordB = "";

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            InteractionModel interactionModel,
            Supplier<IVisualizationView> activeViewSupplier
    ) {
        this(facade, controlPanelView, interactionModel, activeViewSupplier, () -> new int[]{0, 1, 2}, 0, null, () -> {});
    }

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            InteractionModel interactionModel,
            Supplier<IVisualizationView> activeViewSupplier,
            Supplier<int[]> activeAxesSupplier,
            int semanticAxisTargetIndex
    ) {
        this(facade, controlPanelView, interactionModel, activeViewSupplier, activeAxesSupplier, semanticAxisTargetIndex, null, () -> {});
    }

    public MathAnalyticsController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            InteractionModel interactionModel,
            Supplier<IVisualizationView> activeViewSupplier,
            Supplier<int[]> activeAxesSupplier,
            int semanticAxisTargetIndex,
            CommandManager commandManager,
            Runnable onCommandExecuted
    ) {
        this.facade = facade;
        this.controlPanelView = controlPanelView;
        this.interactionModel = interactionModel;
        this.activeViewSupplier = activeViewSupplier;
        this.activeAxesSupplier = activeAxesSupplier;
        this.semanticAxisTargetIndex = semanticAxisTargetIndex;
        this.commandManager = commandManager;
        this.onCommandExecuted = onCommandExecuted;
        setupListeners();
    }

    private void setupListeners() {
        controlPanelView.setOnCalculateEquationAction(this::handleCalculateEquationAction);
        controlPanelView.setOnProjectAxisAction(this::handleProjectOnAxisAction);
    }

    /**
     * Updates the internal tracker for semantic state to ensure correct undo/redo operations.
     */
    public void setInternalSemanticState(
            boolean isActive,
            List<Map.Entry<String, Double>> scores,
            WordNode poleA,
            WordNode poleB,
            String wordA,
            String wordB
    ) {
        this.isSemanticModeActive = isActive;
        this.activeSemanticScores = scores;
        this.activePoleA = poleA;
        this.activePoleB = poleB;
        this.lastSearchedWordA = wordA != null ? wordA : "";
        this.lastSearchedWordB = wordB != null ? wordB : "";
    }

    private void handleCalculateEquationAction(ActionEvent event) {
        String equationText = controlPanelView.getEquationText() == null
                ? ""
                : controlPanelView.getEquationText().trim();

        if (equationText.isBlank()) {
            controlPanelView.setStatusMessage("Please enter an equation to solve.");
            return;
        }

        if (interactionModel != null) {
            interactionModel.clearMeasurementState();
            interactionModel.setActiveTargetNode(null);
            interactionModel.setActiveNeighborNodes(List.of());
        }
        controlPanelView.setSearchText("");

        ObservableList<String> previousResults = FXCollections.observableArrayList(controlPanelView.getResultsListView().getItems());
        String previousResultsTitle = controlPanelView.getResultsTitleLabel().getText();
        String previousStatusMessage = controlPanelView.getStatusLabel().getText();
        String previousEquationText = controlPanelView.getEquationText();
        WordNode previousResultWord = null;
        List<WordNode> previousEquationWords = new ArrayList<>();

        List<WordNode> equationWords = new ArrayList<>();
        WordNode resultWord = facade.solveEquation(equationText, equationWords);
        if (resultWord == null) {
            controlPanelView.setStatusMessage("Equation result could not be calculated. Verify all words exist in the vocabulary.");
            return;
        }

        ObservableList<String> newResults = FXCollections.observableArrayList();
        newResults.add(resultWord.getWord());
        String newResultsTitle = "Showing: Math Results";
        String newStatusMessage = "Equation computed: " + equationText + ".";

        IVisualizationView activeView = activeViewSupplier.get();

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

        if (commandManager != null) {
            commandManager.executeCommand(command);
            if (onCommandExecuted != null) {
                onCommandExecuted.run();
            }
        }
    }

    private void handleProjectOnAxisAction(ActionEvent event) {
        String wordA = controlPanelView.getSemanticAxisWordAText();
        String wordB = controlPanelView.getSemanticAxisWordBText();

        if (wordA == null || wordA.isBlank() || wordB == null || wordB.isBlank()) {
            controlPanelView.setStatusMessage("Please enter both a positive and negative pole word.");
            return;
        }

        // Capture previous state before applying semantic projection
        ObservableList<String> previousResults = FXCollections.observableArrayList(controlPanelView.getResultsListView().getItems());
        String previousResultsTitle = controlPanelView.getResultsTitleLabel().getText();
        String previousStatusMessage = controlPanelView.getStatusLabel().getText();
        String previousSearchText = controlPanelView.getSearchText();
        String previousEquationText = controlPanelView.getEquationText();
        String previousWordA = this.lastSearchedWordA;
        String previousWordB = this.lastSearchedWordB;
        boolean previousSemanticMode = this.isSemanticModeActive;
        List<Map.Entry<String, Double>> previousScores = this.activeSemanticScores;
        WordNode previousPoleA = this.activePoleA;
        WordNode previousPoleB = this.activePoleB;

        if (interactionModel != null) {
            interactionModel.clearMeasurementState();
            interactionModel.setActiveTargetNode(null);
            interactionModel.setActiveNeighborNodes(List.of());
        }

        List<Map.Entry<String, Double>> projections = facade.getSemanticProjection(wordA.trim(), wordB.trim());

        if (projections == null || projections.isEmpty()) {
            controlPanelView.setStatusMessage("Semantic axis could not be calculated. Check that both words exist in the vocabulary.");
            controlPanelView.clearResults();
            return;
        }

        ObservableList<String> formattedResults = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : projections) {
            String formatted = String.format(java.util.Locale.US, "%s: %.3f", entry.getKey(), entry.getValue());
            formattedResults.add(formatted);
        }

        WordNode poleANode = facade.getWordNode(wordA.trim());
        WordNode poleBNode = facade.getWordNode(wordB.trim());
        String newTitle = "Showing: Semantic Projection";
        String newStatus = "Semantic projection calculated using '" + wordA + "' (positive) and '" + wordB + "' (negative).";

        ProjectSemanticAxisCommand command = new ProjectSemanticAxisCommand(
                activeViewSupplier.get(), controlPanelView, facade.getAllWords(), activeAxesSupplier, semanticAxisTargetIndex, this,
                previousResults, previousResultsTitle, previousStatusMessage, previousSearchText, previousEquationText, previousWordA, previousWordB,
                previousSemanticMode, previousScores, previousPoleA, previousPoleB,
                formattedResults, newTitle, newStatus, wordA, wordB, projections, poleANode, poleBNode
        );

        if (commandManager != null) {
            commandManager.executeCommand(command);
            if (onCommandExecuted != null) {
                onCommandExecuted.run();
            }
        }
    }
}