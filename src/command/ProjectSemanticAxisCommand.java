package command;

import javafx.collections.ObservableList;
import model.WordNode;
import ui.controller.MathAnalyticsController;
import ui.view.ControlPanelView;
import ui.view.Graph2DRenderer;
import ui.view.IVisualizationView;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Command for calculating and applying a semantic projection axis.
 * Encapsulates the complete state required to undo and redo the semantic projection view.
 */
public class ProjectSemanticAxisCommand implements ICommand {

    private final IVisualizationView activeView;
    private final ControlPanelView controlPanelView;
    private final Collection<WordNode> allWords;
    private final Supplier<int[]> activeAxesSupplier;
    private final int semanticAxisTargetIndex;
    private final MathAnalyticsController controller;

    private final ObservableList<String> previousResults;
    private final String previousResultsTitle;
    private final String previousStatusMessage;
    private final String previousSearchText;
    private final String previousEquationText;
    private final String previousWordA;
    private final String previousWordB;
    private final boolean previousSemanticMode;
    private final List<Map.Entry<String, Double>> previousScores;
    private final WordNode previousPoleA;
    private final WordNode previousPoleB;

    private final ObservableList<String> newResults;
    private final String newResultsTitle;
    private final String newStatusMessage;
    private final String newWordA;
    private final String newWordB;
    private final List<Map.Entry<String, Double>> newScores;
    private final WordNode newPoleA;
    private final WordNode newPoleB;

    public ProjectSemanticAxisCommand(
            IVisualizationView activeView,
            ControlPanelView controlPanelView,
            Collection<WordNode> allWords,
            Supplier<int[]> activeAxesSupplier,
            int semanticAxisTargetIndex,
            MathAnalyticsController controller,
            ObservableList<String> previousResults,
            String previousResultsTitle,
            String previousStatusMessage,
            String previousSearchText,
            String previousEquationText,
            String previousWordA,
            String previousWordB,
            boolean previousSemanticMode,
            List<Map.Entry<String, Double>> previousScores,
            WordNode previousPoleA,
            WordNode previousPoleB,
            ObservableList<String> newResults,
            String newResultsTitle,
            String newStatusMessage,
            String newWordA,
            String newWordB,
            List<Map.Entry<String, Double>> newScores,
            WordNode newPoleA,
            WordNode newPoleB
    ) {
        this.activeView = activeView;
        this.controlPanelView = controlPanelView;
        this.allWords = allWords;
        this.activeAxesSupplier = activeAxesSupplier;
        this.semanticAxisTargetIndex = semanticAxisTargetIndex;
        this.controller = controller;
        this.previousResults = previousResults;
        this.previousResultsTitle = previousResultsTitle;
        this.previousStatusMessage = previousStatusMessage;
        this.previousSearchText = previousSearchText;
        this.previousEquationText = previousEquationText;
        this.previousWordA = previousWordA;
        this.previousWordB = previousWordB;
        this.previousSemanticMode = previousSemanticMode;
        this.previousScores = previousScores;
        this.previousPoleA = previousPoleA;
        this.previousPoleB = previousPoleB;
        this.newResults = newResults;
        this.newResultsTitle = newResultsTitle;
        this.newStatusMessage = newStatusMessage;
        this.newWordA = newWordA;
        this.newWordB = newWordB;
        this.newScores = newScores;
        this.newPoleA = newPoleA;
        this.newPoleB = newPoleB;
    }

    @Override
    public void execute() {
        controlPanelView.setSearchText("");
        controlPanelView.setEquationText("");
        restoreState(
                newWordA, newWordB, newResults, newResultsTitle, newStatusMessage,
                true, newScores, newPoleA, newPoleB
        );
    }

    @Override
    public void undo() {
        controlPanelView.setSearchText(previousSearchText);
        controlPanelView.setEquationText(previousEquationText);
        restoreState(
                previousWordA, previousWordB, previousResults, previousResultsTitle, previousStatusMessage,
                previousSemanticMode, previousScores, previousPoleA, previousPoleB
        );
    }

    private void restoreState(
            String wordA, String wordB, ObservableList<String> results, String title, String status,
            boolean isSemantic, List<Map.Entry<String, Double>> scores, WordNode poleA, WordNode poleB
    ) {
        controlPanelView.setSemanticAxisWordAText(wordA);
        controlPanelView.setSemanticAxisWordBText(wordB);
        controlPanelView.displayResults(results);
        controlPanelView.setResultsTitle(title);
        controlPanelView.setStatusMessage(status);

        if (controller != null) {
            controller.setInternalSemanticState(isSemantic, scores, poleA, poleB, wordA, wordB);
        }

        if (activeView != null) {
            if (!isSemantic) {
                activeView.setSemanticScores(null);
                activeView.setSemanticPoles(null, null);
                activeView.updateData(allWords, activeAxesSupplier.get());
            } else {
                activeView.setSemanticScores(scores);
                activeView.setSemanticPoles(poleA, poleB);
                int[] baseAxes = activeAxesSupplier.get();
                int[] projectedAxes = Arrays.copyOf(baseAxes, baseAxes.length);
                int targetIdx = Math.max(0, Math.min(2, semanticAxisTargetIndex));
                projectedAxes[targetIdx] = Graph2DRenderer.SEMANTIC_AXIS_INDEX;
                activeView.updateData(allWords, projectedAxes);
            }
        }
    }
}