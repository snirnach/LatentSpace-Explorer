package ui.controller;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import model.EmbeddingRepository;
import model.WordNode;
import service.LatentSpaceFacade;
import command.CommandManager;
import ui.state.InteractionModel;
import ui.state.PcaStateSubject;
import ui.view.ControlPanelView;
import ui.view.Graph2DView;
import ui.view.IVisualizationView;
import ui.view.Scene3DManager;

import java.util.Comparator;
import java.util.List;

/**
 * Composes view, façade, and sub-controllers for the main application screen.
 */
public class MainController {

    private final LatentSpaceFacade facade;
    private final Scene3DManager scene3DManager;
    private final IVisualizationView view2D;
    private final IVisualizationView view3D;
    private final PcaStateSubject pcaStateSubject;
    private final InteractionModel interactionModel;
    private final ControlPanelView controlPanelView;
    private final Graph2DView graph2DView;
    private final ViewController viewController;
    private final CommandManager commandManager;

    private IVisualizationView currentView;
    private BorderPane mainLayout;

    public MainController() {
        this.facade = new LatentSpaceFacade();
        this.pcaStateSubject = new PcaStateSubject();
        this.commandManager = new CommandManager();
        this.interactionModel = new InteractionModel();
        this.controlPanelView = new ControlPanelView();
        this.graph2DView = new Graph2DView();
        this.scene3DManager = new Scene3DManager();

        this.view2D = graph2DView;
        this.view3D = scene3DManager;
        this.currentView = view2D;

        ExplorationController explorationController = new ExplorationController(
                this.facade,
                this.controlPanelView,
                this.interactionModel,
                () -> this.currentView,
                this.commandManager,
                this::updateHistoryButtonsState
        );

        this.viewController = new ViewController(
                this.controlPanelView,
                this.pcaStateSubject,
                this.commandManager,
                this::switchVisualizationMode,
                this::updateHistoryButtonsState
        );
        this.viewController.configureViewAccess(
                this.graph2DView,
                this.scene3DManager,
                this::isThreeDModeActive,
                this::redrawChart
        );

        new MathAnalyticsController(
                this.facade,
                this.controlPanelView,
                this.interactionModel,
                () -> this.currentView,
                () -> new int[]{pcaStateSubject.getPcaX(), pcaStateSubject.getPcaY(), pcaStateSubject.getPcaZ()},
                0,
                this.commandManager,
                this::updateHistoryButtonsState
        );

        this.view2D.setOnWordClicked(explorationController::handleWordClick);
        this.view3D.setOnWordClicked(explorationController::handleWordClick);

        controlPanelView.setOnUndoAction(e -> {
            commandManager.undo();
            updateHistoryButtonsState();
        });

        controlPanelView.setOnRedoAction(e -> {
            commandManager.redo();
            updateHistoryButtonsState();
        });

        updateHistoryButtonsState();
    }

    private void updateHistoryButtonsState() {
        controlPanelView.setUndoButtonDisabled(!commandManager.canUndo());
        controlPanelView.setRedoButtonDisabled(!commandManager.canRedo());
    }

    public Parent getView() {
        mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(12));
        mainLayout.setCenter(currentView.getUIComponent());
        mainLayout.setRight(controlPanelView.getView());

        viewController.attachCenterInteractionHandlers(currentView.getUIComponent());
        redrawChart();
        updateStatusWithRepositoryInfo();
        return mainLayout;
    }

    private void switchVisualizationMode(boolean show3D) {
        if (show3D) {
            controlPanelView.setToggleButtonText("Switch to 2D View");
            currentView = view3D;
        } else {
            controlPanelView.setToggleButtonText("Switch to 3D View");
            currentView = view2D;
        }

        if (currentView != null) {
            currentView.clearVisualSelection();
        }

        if (mainLayout != null) {
            mainLayout.setCenter(currentView.getUIComponent());
            viewController.attachCenterInteractionHandlers(currentView.getUIComponent());
        }
        redrawChart();
    }

    private void redrawChart() {
        int xIndex = pcaStateSubject.getPcaX();
        int yIndex = pcaStateSubject.getPcaY();
        int zIndex = pcaStateSubject.getPcaZ();

        List<WordNode> allWords = EmbeddingRepository.INSTANCE.getAllWords().stream()
                .filter(node -> node != null && node.getWord() != null)
                .sorted(Comparator.comparing(WordNode::getWord, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int[] selectedAxes = new int[]{xIndex, yIndex, zIndex};
        view2D.updateData(allWords, selectedAxes);
        view3D.updateData(allWords, selectedAxes);

        // Restore exploration state visually to the active view to ensure consistency
        if (interactionModel.getActiveTargetNode() != null) {
            currentView.showNearestNeighbors(interactionModel.getActiveTargetNode(), interactionModel.getActiveNeighborNodes());
        } else if (interactionModel.getSourceNode() != null) {
            currentView.focusOnWord(interactionModel.getSourceNode());
        }
    }

    private boolean isThreeDModeActive() {
        return currentView == view3D;
    }

    private void updateStatusWithRepositoryInfo() {
        int totalWords = EmbeddingRepository.INSTANCE.getAllWords().size();
        if (totalWords == 0) {
            controlPanelView.setStatusMessage(
                    "No embedding data is loaded yet. Launch the application with a JSON file path to display data."
            );
            return;
        }

        controlPanelView.setStatusMessage(
                "Loaded " + totalWords + " words. The visualization renders the full dataset."
        );
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }
}