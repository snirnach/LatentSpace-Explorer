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

    public ExplorationController(
            LatentSpaceFacade facade,
            ControlPanelView controlPanelView,
            InteractionModel interactionModel,
            Supplier<IVisualizationView> activeViewSupplier,
            CommandManager commandManager
    ) {
        this.facade = facade;
        this.controlPanelView = controlPanelView;
        this.interactionModel = interactionModel;
        this.activeViewSupplier = activeViewSupplier;
        this.commandManager = commandManager;
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

        // Capture previous state before making changes
        WordNode previousTargetNode = interactionModel.getActiveTargetNode();
        List<WordNode> previousNeighbors = new ArrayList<>(interactionModel.getActiveNeighborNodes());
        ObservableList<String> previousResults = FXCollections.observableArrayList(controlPanelView.getResultsListView().getItems());
        String previousResultsTitle = controlPanelView.getResultsTitleLabel().getText();
        String previousStatusMessage = controlPanelView.getStatusLabel().getText();
        String previousSearchText = controlPanelView.getSearchText();

        // Clear old probe/math/group state before applying new focus.
        IVisualizationView activeView = activeViewSupplier.get();
        if (activeView != null) {
            activeView.clearVisualSelection();
        }

        // Clear measurement state so old measurement UI does not remain.
        interactionModel.clearMeasurementState();

        int kValue = controlPanelView.getKValue();
        List<WordNode> neighbors = facade.findSimilarWords(
                foundWord.getWord(),
                kValue,
                controlPanelView.getDistanceMetric()
        );

        // Create command with both old and new state
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


        // Execute command through CommandManager
        commandManager.executeCommand(command);
        
        // Update undo/redo buttons
        updateHistoryButtonsState();
    }

    private void handleMeasureButtonAction(ActionEvent event) {
        InteractionModel.InteractionResult result = interactionModel.beginMeasurement();
        if (result.getType() == InteractionModel.ResultType.ERROR) {
            controlPanelView.setDistanceResultText(result.getMessage());
            controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());
            return;
        }

        controlPanelView.setDistanceResultText("Select second point...");
        controlPanelView.setMeasureButtonDisabled(true);
    }

    /**
     * Updates the state of undo and redo buttons based on command history availability.
     */
    private void updateHistoryButtonsState() {
        controlPanelView.setUndoButtonDisabled(!commandManager.canUndo());
        controlPanelView.setRedoButtonDisabled(!commandManager.canRedo());
    }

    private void handleAnalyzeSubspaceGroupAction(ActionEvent event) {
        IVisualizationView activeView = activeViewSupplier.get();
        if (activeView == null) {
            return;
        }

        Set<WordNode> selectedGroup = activeView.getSelectedGroup();
        if (selectedGroup == null || selectedGroup.isEmpty()) {
            return;
        }

        double[] centroid = facade.calculateCentroid(selectedGroup);
        if (centroid.length == 0) {
            return;
        }

        int kValue = controlPanelView.getKValue();
        String distanceMetric = controlPanelView.getDistanceMetric();
        List<WordNode> neighbors = facade.findSimilarToVector(centroid, kValue, distanceMetric);

        controlPanelView.displayResults(toWordList(neighbors));
        controlPanelView.setResultsTitle("Subspace Centroid Neighbors");
        controlPanelView.setStatusMessage(
                "Showing centroid neighbors for a selected group of " + selectedGroup.size() + " words."
        );
    }

    /**
     * Handles clicks from the active visualization and updates state machine and UI.
     */
    public void handleWordClick(WordNode clickedNode) {
        IVisualizationView activeView = activeViewSupplier.get();
        if (clickedNode == null || clickedNode.getWord() == null || clickedNode.getWord().isBlank()) {
            clearSelectionState(activeView);
            return;
        }

        // A regular point click clears search focus before source/probe state is applied.
//        if (!interactionModel.isMeasuringMode() && activeView != null) {
//            activeView.focusOnWord(null);
//        }

        InteractionModel.InteractionResult result = interactionModel.handlePointClick(
                clickedNode,
                createSelectedDistanceStrategy()
        );

        if (result.getType() == InteractionModel.ResultType.ERROR) {
            controlPanelView.setDistanceResultText(result.getMessage());
            controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());
            return;
        }

        if (result.getType() == InteractionModel.ResultType.SOURCE_SELECTED) {
            controlPanelView.setSelectedWordText("Selected: " + getDisplayWord(interactionModel.getSourceNode()));
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
            }
            return;
        }

        if (result.getType() == InteractionModel.ResultType.DISTANCE_MEASURED) {
            controlPanelView.setDistanceResultText(String.format(
                    Locale.US,
                    "Distance between '%s' and '%s': %.6f",
                    getDisplayWord(result.getSourceNode()),
                    getDisplayWord(result.getTargetNode()),
                    result.getDistance()
            ));
            controlPanelView.setSelectedWordText("Selected: None");
            controlPanelView.setMeasureButtonDisabled(!interactionModel.isMeasureButtonEnabled());

            interactionModel.setActiveTargetNode(null);
            interactionModel.setActiveNeighborNodes(List.of());
            return;
        }

        if (result.getType() == InteractionModel.ResultType.AWAITING_SECOND_POINT) {
            controlPanelView.setDistanceResultText(result.getMessage());
            controlPanelView.setMeasureButtonDisabled(true);
        }
    }

    /**
     * Clears focused search visuals when an external UI action changes the active mode.
     */
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

