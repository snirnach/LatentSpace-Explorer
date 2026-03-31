package ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import model.EmbeddingRepository;
import model.WordNode;
import model.distance.DistanceStrategy;
import model.distance.DistanceStrategyFactory;
import service.LatentSpaceFacade;
import command.CommandManager;
import command.FindNeighborsCommand;
import ui.view.ControlPanelView;
import ui.view.IVisualizationView;
import ui.state.InteractionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Handles user exploration workflows: search, probing, and distance measurement.
 */
public class ExplorationController {

    private final LatentSpaceFacade facade;
    private final ControlPanelView controlPanelView;
    private final InteractionModel interactionModel;
    private final Supplier<IVisualizationView> activeViewSupplier;
    private final CommandManager commandManager;
    private final Runnable onCommandExecuted;

    public ExplorationController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            InteractionModel interactionModel,
            Supplier<IVisualizationView> activeViewSupplier,
            CommandManager commandManager,
            Runnable onCommandExecuted
    ) {
        this.facade = facade;
        this.controlPanelView = controlPanelView;
        this.interactionModel = interactionModel;
        this.activeViewSupplier = activeViewSupplier;
        this.commandManager = commandManager;
        this.onCommandExecuted = onCommandExecuted;
        setupListeners();
    }

    private void setupListeners() {
        controlPanelView.setOnSearchAction(this::handleFindNearestNeighborsAction);
        controlPanelView.setOnMeasureAction(this::handleMeasureButtonAction);
        controlPanelView.setOnAnalyzeSubspaceGroupAction(this::handleAnalyzeSubspaceGroupAction);
    }

    private void handleFindNearestNeighborsAction(ActionEvent event) {
        String requestedWord = controlPanelView.getSearchText() == null
                ? ""
                : controlPanelView.getSearchText().trim();

        if (requestedWord.isEmpty()) {
            controlPanelView.setStatusMessage("Please enter a word to focus.");
            return;
        }

        WordNode foundWord = EmbeddingRepository.INSTANCE.getWord(requestedWord);
        if (foundWord == null) {
            controlPanelView.setResultsTitle("Search Result");
            ObservableList<String> notFoundMsg = FXCollections.observableArrayList();
            notFoundMsg.add("Word '" + requestedWord + "' not found.");
            controlPanelView.displayResults(notFoundMsg);
            controlPanelView.setStatusMessage("The word '" + requestedWord + "' is not in the vocabulary.");
            return;
        }

        controlPanelView.setEquationText("");
        controlPanelView.setSemanticAxisWordAText("");
        controlPanelView.setSemanticAxisWordBText("");

        WordNode previousTargetNode = interactionModel.getActiveTargetNode();
        List<WordNode> previousNeighbors = new ArrayList<>(interactionModel.getActiveNeighborNodes());
        ObservableList<String> previousResults = FXCollections.observableArrayList(controlPanelView.getResultsListView().getItems());
        String previousResultsTitle = controlPanelView.getResultsTitleLabel().getText();
        String previousStatusMessage = controlPanelView.getStatusLabel().getText();
        String previousSearchText = controlPanelView.getSearchText();

        IVisualizationView activeView = activeViewSupplier.get();
        if (activeView != null) {
            activeView.clearVisualSelection();
        }

        interactionModel.clearMeasurementState();

        int kValue = controlPanelView.getKValue();
        List<WordNode> neighbors = facade.findSimilarWords(
                foundWord.getWord(),
                kValue,
                controlPanelView.getDistanceMetric()
        );

        ObservableList<String> newResults = toWordList(neighbors);
        FindNeighborsCommand command = new FindNeighborsCommand(
                interactionModel,
                activeView,
                controlPanelView,
                previousTargetNode,
                previousNeighbors,
                previousResults,
                previousResultsTitle,
                previousStatusMessage,
                previousSearchText,
                foundWord,
                neighbors,
                newResults,
                "Showing: Nearest Neighbors",
                "Focused on word: '" + foundWord.getWord() + "'.",
                requestedWord
        );

        commandManager.executeCommand(command);

        if (onCommandExecuted != null) {
            onCommandExecuted.run();
        }
    }

    private void handleMeasureButtonAction(ActionEvent event) {
        InteractionModel.InteractionResult result = interactionModel.beginMeasurement();
        if (result.type() == InteractionModel.ResultType.ERROR) {
            controlPanelView.setDistanceResultText(result.message());
            controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());
            return;
        }

        controlPanelView.setDistanceResultText("Select second point...");
        controlPanelView.setMeasureButtonDisabled(true);
    }

    private void handleAnalyzeSubspaceGroupAction(ActionEvent event) {
        IVisualizationView activeView = activeViewSupplier.get();
        if (activeView == null) {
            return;
        }

        Set<WordNode> selectedGroup = activeView.getSelectedGroup();
        if (selectedGroup == null || selectedGroup.isEmpty()) {
            controlPanelView.setStatusMessage("Please select a group of words first (Shift + Click).");
            return;
        }

        double[] centroid = facade.calculateCentroid(selectedGroup);
        if (centroid.length == 0) {
            return;
        }

        int kValue = controlPanelView.getKValue();
        String distanceMetric = controlPanelView.getDistanceMetric();
        List<WordNode> neighbors = facade.findSimilarToVector(centroid, kValue, distanceMetric);

        interactionModel.setActiveTargetNode(null);
        interactionModel.setActiveNeighborNodes(neighbors);

        activeView.showNearestNeighbors(null, neighbors);

        controlPanelView.displayResults(toWordList(neighbors));
        controlPanelView.setResultsTitle("Subspace Centroid Neighbors");
        controlPanelView.setStatusMessage(
                "Showing " + neighbors.size() + " neighbors for a centroid of " + selectedGroup.size() + " words."
        );
    }

    public void handleWordClick(WordNode clickedNode) {
        IVisualizationView activeView = activeViewSupplier.get();
        if (clickedNode == null || clickedNode.getWord() == null || clickedNode.getWord().isBlank()) {
            clearSelectionState(activeView);
            return;
        }

        InteractionModel.InteractionResult result = interactionModel.handlePointClick(
                clickedNode,
                createSelectedDistanceStrategy()
        );

        switch (result.type()) {
            case ERROR -> {
                controlPanelView.setDistanceResultText(result.message());
                controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());
            }
            case SOURCE_SELECTED -> {
                controlPanelView.setEquationText("");
                controlPanelView.setSemanticAxisWordAText("");
                controlPanelView.setSemanticAxisWordBText("");

                controlPanelView.setSelectedWordText("Selected: " + getDisplayWord(result.sourceNode()));
                controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());

                if (!interactionModel.isMeasuringMode()) {
                    controlPanelView.setDistanceResultText("");
                    String selectedMetric = controlPanelView.getDistanceMetric();
                    int kValue = controlPanelView.getKValue();
                    List<WordNode> neighbors = facade.findSimilarWords(clickedNode.getWord(), kValue, selectedMetric);

                    interactionModel.setActiveTargetNode(clickedNode);
                    interactionModel.setActiveNeighborNodes(neighbors);

                    if (activeView != null) {
                        activeView.showNearestNeighbors(clickedNode, neighbors);
                    }

                    controlPanelView.displayResults(toWordList(neighbors));
                    controlPanelView.setResultsTitle("Showing: Nearest Neighbors");
                } else {
                    if (activeView != null) {
                        activeView.focusOnWord(clickedNode);
                    }
                }
            }
            case DISTANCE_MEASURED -> {
                controlPanelView.setDistanceResultText(String.format(
                        Locale.US,
                        "Distance between '%s' and '%s': %.6f",
                        getDisplayWord(result.sourceNode()),
                        getDisplayWord(result.targetNode()),
                        result.distance()
                ));
                controlPanelView.setSelectedWordText("Selected: None");
                controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());

                interactionModel.setActiveTargetNode(null);
                interactionModel.setActiveNeighborNodes(List.of());
            }
            case AWAITING_SECOND_POINT -> {
                controlPanelView.setDistanceResultText(result.message());
                controlPanelView.setMeasureButtonDisabled(true);
            }
        }
    }

    public void resetVisualFocusState() {
        IVisualizationView activeView = activeViewSupplier.get();
        if (activeView != null) {
            activeView.clearVisualSelection();
        }
    }

    private void clearSelectionState(IVisualizationView activeView) {
        interactionModel.clearMeasurementState();
        interactionModel.setActiveTargetNode(null);
        interactionModel.setActiveNeighborNodes(List.of());

        if (activeView != null) {
            activeView.clearVisualSelection();
        }

        controlPanelView.clearResults();
        controlPanelView.setResultsTitle("Showing: Nearest Neighbors");
        controlPanelView.setSelectedWordText("Selected: None");
        controlPanelView.setDistanceResultText("");
        controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());
        controlPanelView.setEquationText("");
        controlPanelView.setSearchText("");
        controlPanelView.setStatusMessage("Selection cleared.");
    }

    private DistanceStrategy createSelectedDistanceStrategy() {
        return DistanceStrategyFactory.createStrategy(controlPanelView.getDistanceMetric());
    }

    private ObservableList<String> toWordList(List<WordNode> nodes) {
        ObservableList<String> names = FXCollections.observableArrayList();
        if (nodes == null) {
            return names;
        }

        for (WordNode node : nodes) {
            if (node != null && node.getWord() != null) {
                names.add(node.getWord());
            }
        }
        return names;
    }

    private String getDisplayWord(WordNode wordNode) {
        if (wordNode == null || wordNode.getWord() == null || wordNode.getWord().isBlank()) {
            return "<unnamed>";
        }
        return wordNode.getWord();
    }
}