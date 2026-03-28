package ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import model.EmbeddingRepository;
import model.WordNode;
import service.LatentSpaceFacade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Composes view, façade, and sub-controllers for the main application screen.
 */
public class  MainController {

    private final LatentSpaceFacade facade;
    private final Scene3DManager scene3DManager;
    private final IVisualizationView view2D;
    private final IVisualizationView view3D;
    private final PcaStateSubject pcaStateSubject;
    private final InteractionModel interactionModel;
    private final ControlPanelView controlPanelView;
    private final Graph2DView graph2DView;
    private final ExplorationController explorationController;
    private final ViewController viewController;
    @SuppressWarnings("unused")
    private final MathAnalyticsController mathAnalyticsController;

    private IVisualizationView currentView;
    private BorderPane mainLayout;

    public MainController() {
        this.facade = new LatentSpaceFacade();
        this.pcaStateSubject = new PcaStateSubject();
        this.interactionModel = new InteractionModel();
        this.controlPanelView = new ControlPanelView();
        this.graph2DView = new Graph2DView();
        this.scene3DManager = new Scene3DManager();

        this.view2D = graph2DView;
        this.view3D = scene3DManager;
        this.currentView = view2D;

        this.explorationController = new ExplorationController(
                this.facade,
                this.controlPanelView,
                this.interactionModel,
                () -> this.currentView
        );

        this.viewController = new ViewController(
                this.controlPanelView,
                this.pcaStateSubject,
                this::switchVisualizationMode
        );
        this.viewController.configureViewAccess(
                this.graph2DView,
                this.scene3DManager,
                this::isThreeDModeActive,
                this::redrawChart
        );

        this.mathAnalyticsController = new MathAnalyticsController(
                this.facade,
                this.controlPanelView,
                () -> this.currentView
        );

        // Keep click handling delegated to the exploration workflow controller.
        this.view2D.setOnWordClicked(explorationController::handleWordClick);
        this.view3D.setOnWordClicked(explorationController::handleWordClick);
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

        currentView.setOnWordClicked(explorationController::handleWordClick);
        explorationController.resetVisualFocusState();

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

        graph2DView.setSelectedWord(interactionModel.getSourceNode());
        graph2DView.setHighlightedWords(buildHighlightedNodes(xIndex, yIndex));
        scene3DManager.setSelectedWord(interactionModel.getSourceNode());

        int[] selectedAxes = new int[]{xIndex, yIndex, zIndex};
        view2D.updateData(allWords, selectedAxes);
        view3D.updateData(allWords, selectedAxes);
    }

    private boolean isThreeDModeActive() {
        return currentView == view3D;
    }

    private boolean hasUsablePcaCoordinates(WordNode wordNode, int xIndex, int yIndex) {
        if (wordNode == null || wordNode.getPcaVector() == null) {
            return false;
        }

        int neededLength = Math.max(xIndex, yIndex) + 1;
        if (wordNode.getPcaVector().length < neededLength) {
            return false;
        }

        double x = wordNode.getPcaVector()[xIndex];
        double y = wordNode.getPcaVector()[yIndex];
        return Double.isFinite(x) && Double.isFinite(y);
    }

    private List<WordNode> buildHighlightedNodes(int xIndex, int yIndex) {
        List<WordNode> highlightedNodes = new ArrayList<>();

        WordNode targetNode = interactionModel.getActiveTargetNode();
        if (hasUsablePcaCoordinates(targetNode, xIndex, yIndex)) {
            highlightedNodes.add(targetNode);
        }

        for (WordNode neighbor : interactionModel.getActiveNeighborNodes()) {
            if (hasUsablePcaCoordinates(neighbor, xIndex, yIndex)) {
                highlightedNodes.add(neighbor);
            }
        }

        return highlightedNodes;
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
}

