package command;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.WordNode;
import ui.view.ControlPanelView;
import ui.state.InteractionModel;
import ui.view.IVisualizationView;

import java.util.ArrayList;
import java.util.List;

/**
 * Command that encapsulates the "Find Nearest Neighbors" action.
 * Captures and restores the complete UI and model state for undo/redo.
 *
 * <p>State captured:</p>
 * <ul>
 *   <li>Previous target word and neighbors list</li>
 *   <li>Previous results display in control panel</li>
 *   <li>Previous visual focus on the graph</li>
 *   <li>New target word and neighbors list</li>
 *   <li>New results display state</li>
 * </ul>
 */
public class FindNeighborsCommand implements ICommand {

    private final InteractionModel interactionModel;
    private final IVisualizationView visualizationView;
    private final ControlPanelView controlPanelView;

    // Previous state (for undo)
    private final WordNode previousTargetNode;
    private final List<WordNode> previousNeighborNodes;
    private final ObservableList<String> previousResults;
    private final String previousResultsTitle;
    private final String previousStatusMessage;
    private final String previousSearchText;

    // New state (for redo)
    private final WordNode newTargetNode;
    private final List<WordNode> newNeighborNodes;
    private final ObservableList<String> newResults;
    private final String newResultsTitle;
    private final String newStatusMessage;
    private final String newSearchText;

    /**
     * Constructs a FindNeighborsCommand.
     *
     * @param interactionModel the model to update with target and neighbors
     * @param visualizationView the view to focus on the target word
     * @param controlPanelView the control panel to update results
     * @param previousTargetNode the previous target word (null if none)
     * @param previousNeighbors the previous neighbor list (empty if none)
     * @param previousResults the previous results display items
     * @param previousResultsTitle the previous results title
     * @param previousStatusMessage the previous status message
     * @param previousSearchText the previous search field text
     * @param newTargetNode the new target word to focus on
     * @param newNeighbors the new list of neighbors
     * @param newResults the new results display items
     * @param newResultsTitle the new results title
     * @param newStatusMessage the new status message
     * @param newSearchText the new search field text
     */
    public FindNeighborsCommand(
            InteractionModel interactionModel,
            IVisualizationView visualizationView,
            ControlPanelView controlPanelView,
            WordNode previousTargetNode,
            List<WordNode> previousNeighbors,
            ObservableList<String> previousResults,
            String previousResultsTitle,
            String previousStatusMessage,
            String previousSearchText,
            WordNode newTargetNode,
            List<WordNode> newNeighbors,
            ObservableList<String> newResults,
            String newResultsTitle,
            String newStatusMessage,
            String newSearchText
    ) {
        this.interactionModel = interactionModel;
        this.visualizationView = visualizationView;
        this.controlPanelView = controlPanelView;

        // Store previous state (immutable copy)
        this.previousTargetNode = previousTargetNode;
        this.previousNeighborNodes = previousNeighbors == null ? List.of() : List.copyOf(previousNeighbors);
        this.previousResults = previousResults == null
                ? FXCollections.observableArrayList()
                : FXCollections.observableArrayList(new ArrayList<>(previousResults));
        this.previousResultsTitle = previousResultsTitle;
        this.previousStatusMessage = previousStatusMessage;
        this.previousSearchText = previousSearchText != null ? previousSearchText : "";

        // Store new state (immutable copy)
        this.newTargetNode = newTargetNode;
        this.newNeighborNodes = newNeighbors == null ? List.of() : List.copyOf(newNeighbors);
        this.newResults = newResults == null
                ? FXCollections.observableArrayList()
                : FXCollections.observableArrayList(new ArrayList<>(newResults));
        this.newResultsTitle = newResultsTitle;
        this.newStatusMessage = newStatusMessage;
        this.newSearchText = newSearchText != null ? newSearchText : "";
    }

    /**
     * Executes the command by applying the new neighbors search state.
     */
    @Override
    public void execute() {
        applyState(newTargetNode, newNeighborNodes, newResults, newResultsTitle, newStatusMessage, newSearchText);
    }

    /**
     * Undoes the command by restoring the previous neighbors search state.
     */
    @Override
    public void undo() {
        applyState(previousTargetNode, previousNeighborNodes, previousResults, previousResultsTitle, previousStatusMessage, previousSearchText);
    }

    /**
     * Applies the neighbors state to the model and views.
     */
    private void applyState(
            WordNode targetNode,
            List<WordNode> neighborNodes,
            ObservableList<String> results,
            String resultsTitle,
            String statusMessage,
            String searchText
    ) {
        // Update interaction model
        interactionModel.setActiveTargetNode(targetNode);
        interactionModel.setActiveNeighborNodes(neighborNodes);

        // Update visualization (focus or clear)
        if (visualizationView != null) {
            if (targetNode != null) {
                visualizationView.focusOnWord(targetNode);
            } else {
                visualizationView.clearVisualSelection();
            }
        }

        // Update control panel display
        controlPanelView.setSearchText(searchText);
        controlPanelView.displayResults(results);
        controlPanelView.setResultsTitle(resultsTitle);
        controlPanelView.setStatusMessage(statusMessage);
    }
}

