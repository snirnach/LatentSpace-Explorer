package ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import model.EmbeddingRepository;
import model.WordNode;
import model.distance.CosineDistance;
import model.distance.DistanceStrategy;
import model.distance.EuclideanDistance;
import service.LatentSpaceFacade;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Main JavaFX controller for the LatentSpace Explorer interface.
 */
public class MainController implements IPcaObserver {
    private static final double THREE_D_ZOOM_STEP = 120.0;
    private static final String COSINE_METRIC_NAME = "Cosine";

    private final LatentSpaceFacade facade;
    private final Scene3DManager scene3DManager;
    private final IVisualizationView view2D;
    private final IVisualizationView view3D;
    private final PcaStateSubject pcaStateSubject;
    private final InteractionModel interactionModel;
    private final ControlPanelView controlPanelView;
    private final Graph2DView graph2DView;
    private IVisualizationView currentView;

    private BorderPane mainLayout;

    public MainController() {
        this.facade = new LatentSpaceFacade();
        this.pcaStateSubject = new PcaStateSubject();
        this.interactionModel = new InteractionModel();
        this.controlPanelView = new ControlPanelView(pcaStateSubject);
        this.graph2DView = new Graph2DView();
        this.scene3DManager = new Scene3DManager();

        this.view2D = graph2DView;
        this.view3D = scene3DManager;
        this.currentView = view2D;

        this.view2D.setOnWordClicked(this::handlePointClick);
        this.view3D.setOnWordClicked(this::handlePointClick);
        this.pcaStateSubject.attach(this);
    }

    public Parent getView() {
        mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(12));
        mainLayout.setCenter(currentView.getUIComponent());
        mainLayout.setRight(controlPanelView.getView());
        attachCenterInteractionHandlers();

        redrawChart();
        wireEvents();
        updateStatusWithRepositoryInfo();

        return mainLayout;
    }

    private void wireEvents() {
        controlPanelView.getSearchButton().setOnAction(this::handleFindNearestNeighborsAction);
        controlPanelView.getSearchField().setOnAction(this::handleFindNearestNeighborsAction);
        controlPanelView.getToggle3dButton().setOnAction(this::handleViewToggleAction);
        controlPanelView.getZoomInButton().setOnAction(this::handleZoomInAction);
        controlPanelView.getZoomOutButton().setOnAction(this::handleZoomOutAction);
        controlPanelView.getMeasureButton().setOnAction(this::handleMeasureButtonAction);
        controlPanelView.getCalculateEquationButton().setOnAction(this::handleCalculateEquationAction);
        controlPanelView.getProjectOnAxisButton().setOnAction(this::handleProjectOnAxisAction);
        controlPanelView.getAnalyzeSubspaceGroupButton().setOnAction(this::handleAnalyzeSubspaceGroupAction);
    }

    private void handleFindNearestNeighborsAction(ActionEvent event) {
        String requestedWord = controlPanelView.getSearchField().getText() == null
                ? ""
                : controlPanelView.getSearchField().getText().trim();

        if (requestedWord.isEmpty()) {
            controlPanelView.getStatusLabel().setText("Please enter a word to focus.");
            return;
        }

        WordNode foundWord = EmbeddingRepository.INSTANCE.getWord(requestedWord);
        if (foundWord == null) {
            controlPanelView.getResultsTitleLabel().setText("Search Result");
            ObservableList<String> notFoundMsg = FXCollections.observableArrayList();
            notFoundMsg.add("Word '" + requestedWord + "' not found.");
            controlPanelView.getResultsListView().setItems(notFoundMsg);
            controlPanelView.getStatusLabel().setText("The word '" + requestedWord + "' is not in the vocabulary.");
            return;
        }

        currentView.focusOnWord(foundWord);

        int kValue = controlPanelView.getKValue();
        List<WordNode> neighbors = facade.findSimilarWords(
                foundWord.getWord(),
                kValue,
                controlPanelView.getDistanceMetricCombo().getValue()
        );
        ObservableList<String> names = FXCollections.observableArrayList();
        for (WordNode neighbor : neighbors) {
            names.add(neighbor.getWord());
        }
        controlPanelView.getResultsListView().setItems(names);
        controlPanelView.getResultsTitleLabel().setText("Showing: Nearest Neighbors");

        controlPanelView.getStatusLabel().setText("Focused on word: '" + foundWord.getWord() + "'.");
    }

    private void handleViewToggleAction(ActionEvent event) {
        handleViewToggle();
    }

    private void handleZoomInAction(ActionEvent event) {
        handleZoomIn();
    }

    private void handleZoomOutAction(ActionEvent event) {
        handleZoomOut();
    }

    private void handleMeasureButtonAction(ActionEvent event) {
        handleMeasureButtonClick();
    }

    private void handleCalculateEquationAction(ActionEvent event) {
        String equationText = controlPanelView.getEquationField().getText() == null
                ? ""
                : controlPanelView.getEquationField().getText().trim();

        if (equationText.isBlank()) {
            controlPanelView.getStatusLabel().setText("Please enter an equation to solve.");
            return;
        }

        java.util.List<WordNode> equationWords = new java.util.ArrayList<>();
        WordNode resultWord = facade.solveEquation(equationText, equationWords);
        if (resultWord == null) {
            controlPanelView.getStatusLabel().setText("Equation result could not be calculated. Verify all words exist in the vocabulary.");
            return;
        }

        ObservableList<String> mathItems = FXCollections.observableArrayList();
        mathItems.add(resultWord.getWord());
        controlPanelView.getResultsListView().setItems(mathItems);
        controlPanelView.getResultsTitleLabel().setText("Showing: Math Results");

        currentView.showMathPath(equationWords, resultWord);

        controlPanelView.getStatusLabel().setText("Equation computed: " + equationText + ".");
    }

    private void handleProjectOnAxisAction(ActionEvent event) {
        String wordA = controlPanelView.getSemanticAxisWordAField().getText();
        String wordB = controlPanelView.getSemanticAxisWordBField().getText();

        if (wordA == null || wordA.isBlank() || wordB == null || wordB.isBlank()) {
            controlPanelView.getStatusLabel().setText("Please enter both a positive and negative pole word.");
            return;
        }

        List<Map.Entry<String, Double>> projections = facade.getSemanticProjection(wordA.trim(), wordB.trim());

        if (projections == null || projections.isEmpty()) {
            controlPanelView.getStatusLabel().setText("Semantic axis could not be calculated. Check that both words exist in the vocabulary.");
            controlPanelView.getResultsListView().getItems().clear();
            return;
        }

        // Format projection results as "Word: 0.000" (3 decimal places).
        ObservableList<String> formattedResults = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : projections) {
            String formatted = String.format("%s: %.3f", entry.getKey(), entry.getValue());
            formattedResults.add(formatted);
        }

        controlPanelView.getResultsListView().setItems(formattedResults);
        controlPanelView.getResultsTitleLabel().setText("Showing: Semantic Projection");
        controlPanelView.getStatusLabel().setText("Semantic projection calculated using '" + wordA + "' (positive) and '" + wordB + "' (negative).");
    }

    private void handleAnalyzeSubspaceGroupAction(ActionEvent event) {
        Set<WordNode> selectedGroup = currentView.getSelectedGroup();
        if (selectedGroup == null || selectedGroup.isEmpty()) {
            return;
        }

        double[] centroid = facade.calculateCentroid(selectedGroup);
        if (centroid.length == 0) {
            return;
        }

        int kValue = controlPanelView.getKValue();
        String distanceMetric = controlPanelView.getDistanceMetricCombo().getValue();
        List<WordNode> neighbors = facade.findSimilarToVector(centroid, kValue, distanceMetric);

        ObservableList<String> resultItems = FXCollections.observableArrayList();
        for (WordNode neighbor : neighbors) {
            resultItems.add(neighbor.getWord());
        }

        controlPanelView.getResultsListView().setItems(resultItems);
        controlPanelView.getResultsTitleLabel().setText("Subspace Centroid Neighbors");
        controlPanelView.getStatusLabel().setText("Showing centroid neighbors for a selected group of " + selectedGroup.size() + " words.");
    }

    @Override
    public void onPcaAxesChanged(int pcaX, int pcaY, int pcaZ) {
        // Observer callback: update only the active visualization view.
        currentView.updateData(EmbeddingRepository.INSTANCE.getAllWords(), new int[]{pcaX, pcaY, pcaZ});
    }

    private void handleViewToggle() {
        if (mainLayout == null) {
            return;
        }

        if (controlPanelView.getToggle3dButton().isSelected()) {
            controlPanelView.getToggle3dButton().setText("Switch to 2D View");
            currentView = view3D;
        } else {
            controlPanelView.getToggle3dButton().setText("Switch to 3D View");
            currentView = view2D;
        }

        // Re-attach the listener and switch the polymorphic center component.
        currentView.setOnWordClicked(this::handlePointClick);
        mainLayout.setCenter(currentView.getUIComponent());
        attachCenterInteractionHandlers();
        redrawChart();
    }

    private void handleMeasureButtonClick() {
        if (interactionModel.getSourceNode() == null) {
            controlPanelView.getDistanceResultLabel().setText("Select a source point first.");
            controlPanelView.getMeasureButton().setDisable(!interactionModel.isMeasureButtonEnabled());
            return;
        }

        // Move into measuring mode and wait for the second click.
        interactionModel.setMeasuringMode(true);
        controlPanelView.getDistanceResultLabel().setText("Select second point...");
        controlPanelView.getMeasureButton().setDisable(true);
    }

    private void handlePanMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !isThreeDModeActive()) {
            return;
        }

        // Delegate anchor tracking to the 3D manager to keep state consistent across clicks on spheres.
        scene3DManager.setDragAnchor(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void handlePanMouseDragged(MouseEvent event) {
        if (!event.isPrimaryButtonDown() || !isThreeDModeActive()) {
            return;
        }

        scene3DManager.rotateFromScenePosition(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void redrawChart() {
        int xIndex = getSelectedXAxisIndex();
        int yIndex = getSelectedYAxisIndex();

        // Always pass the full dataset to the views; each view handles rendering internally.
        List<WordNode> allWords = EmbeddingRepository.INSTANCE.getAllWords().stream()
                .filter(node -> node != null && node.getWord() != null)
                .sorted(Comparator.comparing(WordNode::getWord, String.CASE_INSENSITIVE_ORDER))
                .toList();

        graph2DView.setSelectedWord(interactionModel.getSourceNode());
        graph2DView.setHighlightedWords(buildHighlightedNodes(xIndex, yIndex));
        scene3DManager.setSelectedWord(interactionModel.getSourceNode());

        int[] selectedAxes = new int[]{xIndex, yIndex, getSelectedZAxisIndex()};
        view2D.updateData(allWords, selectedAxes);
        view3D.updateData(allWords, selectedAxes);
    }

    private void handlePointClick(WordNode clickedWord) {
        InteractionModel.InteractionResult result = interactionModel.handlePointClick(
                clickedWord,
                createSelectedDistanceStrategy()
        );

        if (result.getType() == InteractionModel.ResultType.ERROR) {
            controlPanelView.getDistanceResultLabel().setText(result.getMessage());
            controlPanelView.getMeasureButton().setDisable(!interactionModel.isMeasureButtonEnabled());
            return;
        }

        if (result.getType() == InteractionModel.ResultType.SOURCE_SELECTED) {
            controlPanelView.getSelectedWordLabel().setText("Selected: " + getDisplayWord(interactionModel.getSourceNode()));
            controlPanelView.getMeasureButton().setDisable(!interactionModel.isMeasureButtonEnabled());
            if (!interactionModel.isMeasuringMode()) {
                controlPanelView.getDistanceResultLabel().setText("");

                // Nearest Neighbor Probe: fetch and display nearest neighbors using dynamic K value.
                String selectedMetric = controlPanelView.getDistanceMetricCombo().getValue();
                int kValue = controlPanelView.getKValue();
                List<WordNode> neighbors = facade.findSimilarWords(
                        clickedWord.getWord(),
                        kValue,
                        selectedMetric
                );
                currentView.showNearestNeighbors(clickedWord, neighbors);

                ObservableList<String> names = FXCollections.observableArrayList();
                for (WordNode neighbor : neighbors) {
                    names.add(neighbor.getWord());
                }
                controlPanelView.getResultsListView().setItems(names);
                controlPanelView.getResultsTitleLabel().setText("Showing: Nearest Neighbors");
            }
            redrawActiveView();
            return;
        }

        if (result.getType() == InteractionModel.ResultType.DISTANCE_MEASURED) {
            controlPanelView.getDistanceResultLabel().setText(String.format(
                    Locale.US,
                    "Distance between '%s' and '%s': %.6f",
                    getDisplayWord(result.getSourceNode()),
                    getDisplayWord(result.getTargetNode()),
                    result.getDistance()
            ));
            controlPanelView.getSelectedWordLabel().setText("Selected: None");
            controlPanelView.getMeasureButton().setDisable(!interactionModel.isMeasureButtonEnabled());
            redrawActiveView();
            return;
        }

        if (result.getType() == InteractionModel.ResultType.AWAITING_SECOND_POINT) {
            controlPanelView.getDistanceResultLabel().setText(result.getMessage());
            controlPanelView.getMeasureButton().setDisable(true);
        }
    }

    /**
     * Creates the distance strategy selected in the control panel.
     */
    private DistanceStrategy createSelectedDistanceStrategy() {
        String selectedMetric = controlPanelView.getDistanceMetricCombo().getValue();
        if (COSINE_METRIC_NAME.equalsIgnoreCase(selectedMetric)) {
            return new CosineDistance();
        }

        return new EuclideanDistance();
    }

    private void redrawActiveView() {
        redrawChart();
        if (mainLayout != null) {
            mainLayout.setCenter(currentView.getUIComponent());
            attachCenterInteractionHandlers();
        }
    }

    private void attachCenterInteractionHandlers() {
        // 2D has its own built-in pan/zoom handlers, so only attach controller handlers for 3D.
        if (!isThreeDModeActive()) {
            return;
        }

        Node centerNode = currentView.getUIComponent();
        centerNode.setOnMousePressed(this::handlePanMousePressed);
        centerNode.setOnMouseDragged(this::handlePanMouseDragged);
        centerNode.setOnScroll(this::handleThreeDScroll);
    }

    private void handleThreeDScroll(ScrollEvent event) {
        if (!isThreeDModeActive() || event.getDeltaY() == 0) {
            return;
        }

        if (event.getDeltaY() > 0) {
            scene3DManager.zoom(THREE_D_ZOOM_STEP);
        } else {
            scene3DManager.zoom(-THREE_D_ZOOM_STEP);
        }
        event.consume();
    }

    private void handleZoomIn() {
        if (isThreeDModeActive()) {
            scene3DManager.zoom(THREE_D_ZOOM_STEP);
        } else {
            graph2DView.zoomIn();
        }
    }

    private void handleZoomOut() {
        if (isThreeDModeActive()) {
            scene3DManager.zoom(-THREE_D_ZOOM_STEP);
        } else {
            graph2DView.zoomOut();
        }
    }

    private boolean isThreeDModeActive() {
        return currentView == view3D;
    }

    private int getSelectedXAxisIndex() {
        return pcaStateSubject.getPcaX();
    }

    private int getSelectedYAxisIndex() {
        return pcaStateSubject.getPcaY();
    }

    private int getSelectedZAxisIndex() {
        return pcaStateSubject.getPcaZ();
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
        List<WordNode> highlightedNodes = new java.util.ArrayList<>();

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


    private boolean isSameWord(WordNode firstNode, WordNode secondNode) {
        return firstNode != null
                && secondNode != null
                && firstNode.getWord() != null
                && secondNode.getWord() != null
                && firstNode.getWord().equalsIgnoreCase(secondNode.getWord());
    }

    private String getDisplayWord(WordNode wordNode) {
        if (wordNode == null || wordNode.getWord() == null || wordNode.getWord().isBlank()) {
            return "<unnamed>";
        }

        return wordNode.getWord();
    }

    private void updateStatusWithRepositoryInfo() {
        int totalWords = EmbeddingRepository.INSTANCE.getAllWords().size();
        if (totalWords == 0) {
            controlPanelView.getStatusLabel().setText("No embedding data is loaded yet. Launch the application with a JSON file path to display data.");
            return;
        }

        controlPanelView.getStatusLabel().setText("Loaded " + totalWords + " words. The visualization renders the full dataset.");
    }
}

