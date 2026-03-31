package command;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.WordNode;
import ui.view.ControlPanelView;
import ui.view.IVisualizationView;

import java.util.ArrayList;
import java.util.List;

/**
 * Command that encapsulates the "Calculate Equation" action (Vector Arithmetic Lab).
 * Captures and restores the complete UI and model state for undo/redo.
 *
 * <p>State captured:</p>
 * <ul>
 *   <li>Previous math equation result word and equation words path</li>
 *   <li>Previous results display in control panel</li>
 *   <li>Previous visual math path on the graph</li>
 *   <li>New math equation result word and equation words path</li>
 *   <li>New results display state</li>
 * </ul>
 */
public class CalculateEquationCommand implements ICommand {

    private final IVisualizationView visualizationView;
    private final ControlPanelView controlPanelView;

    // Previous state (for undo)
    private final WordNode previousResultWord;
    private final List<WordNode> previousEquationWords;
    private final ObservableList<String> previousResults;
    private final String previousResultsTitle;
    private final String previousStatusMessage;
    private final String previousEquationText;

    // New state (for redo)
    private final WordNode newResultWord;
    private final List<WordNode> newEquationWords;
    private final ObservableList<String> newResults;
    private final String newResultsTitle;
    private final String newStatusMessage;
    private final String newEquationText;

    /**
     * Constructs a CalculateEquationCommand.
     *
     * @param visualizationView the view to display the math path
     * @param controlPanelView the control panel to update results
     * @param previousResultWord the previous equation result word (null if none)
     * @param previousEquationWords the previous list of equation words in the computation path
     * @param previousResults the previous results display items
     * @param previousResultsTitle the previous results title
     * @param previousStatusMessage the previous status message
     * @param previousEquationText the previous equation field text
     * @param newResultWord the new equation result word
     * @param newEquationWords the new list of equation words in the computation path
     * @param newResults the new results display items
     * @param newResultsTitle the new results title
     * @param newStatusMessage the new status message
     * @param newEquationText the new equation field text
     */
    public CalculateEquationCommand(
            IVisualizationView visualizationView,
            ControlPanelView controlPanelView,
            WordNode previousResultWord,
            List<WordNode> previousEquationWords,
            ObservableList<String> previousResults,
            String previousResultsTitle,
            String previousStatusMessage,
            String previousEquationText,
            WordNode newResultWord,
            List<WordNode> newEquationWords,
            ObservableList<String> newResults,
            String newResultsTitle,
            String newStatusMessage,
            String newEquationText
    ) {
        this.visualizationView = visualizationView;
        this.controlPanelView = controlPanelView;

        // Store previous state (immutable copy)
        this.previousResultWord = previousResultWord;
        this.previousEquationWords = previousEquationWords == null ? List.of() : List.copyOf(previousEquationWords);
        this.previousResults = previousResults == null
                ? FXCollections.observableArrayList()
                : FXCollections.observableArrayList(new ArrayList<>(previousResults));
        this.previousResultsTitle = previousResultsTitle;
        this.previousStatusMessage = previousStatusMessage;
        this.previousEquationText = previousEquationText != null ? previousEquationText : "";

        // Store new state (immutable copy)
        this.newResultWord = newResultWord;
        this.newEquationWords = newEquationWords == null ? List.of() : List.copyOf(newEquationWords);
        this.newResults = newResults == null
                ? FXCollections.observableArrayList()
                : FXCollections.observableArrayList(new ArrayList<>(newResults));
        this.newResultsTitle = newResultsTitle;
        this.newStatusMessage = newStatusMessage;
        this.newEquationText = newEquationText != null ? newEquationText : "";
    }

    /**
     * Executes the command by applying the new equation result state.
     */
    @Override
    public void execute() {
        applyState(newResultWord, newEquationWords, newResults, newResultsTitle, newStatusMessage, newEquationText);
    }

    /**
     * Undoes the command by restoring the previous equation result state.
     */
    @Override
    public void undo() {
        applyState(previousResultWord, previousEquationWords, previousResults, previousResultsTitle, previousStatusMessage, previousEquationText);
    }

    /**
     * Applies the math equation state to the views.
     */
    private void applyState(
            WordNode resultWord,
            List<WordNode> equationWords,
            ObservableList<String> results,
            String resultsTitle,
            String statusMessage,
            String equationText
    ) {
        // Update visualization with the math path
        if (visualizationView != null) {
            if (resultWord != null && !equationWords.isEmpty()) {
                visualizationView.showMathPath(equationWords, resultWord);
            } else {
                visualizationView.clearVisualSelection();
            }
        }

        // Update control panel display
        controlPanelView.setEquationText(equationText);
        controlPanelView.displayResults(results);
        controlPanelView.setResultsTitle(resultsTitle);
        controlPanelView.setStatusMessage(statusMessage);
    }
}

