package ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import model.EmbeddingRepository;
import model.WordNode;
import model.distance.EuclideanDistance;
import service.KNNService;
import service.VectorMathService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Main JavaFX controller for the LatentSpace Explorer interface.
 *
 * <p>This controller follows MVC responsibilities by limiting itself to
 * presentation logic, user interaction handling, and service orchestration.
 * All embedding data remains in the repository, while neighbor lookup and
 * vector operations remain delegated to the service layer.</p>
 */
public class MainController {

    private static final int DEFAULT_NEIGHBOR_COUNT = 10;
    private static final int INITIAL_WORD_LIMIT = 500;
    private static final int PCA_COMPONENT_COUNT = 50;
    private static final double BASE_POINT_SIZE = 6.0;
    private static final double HIGHLIGHT_POINT_SIZE = 10.0;
    private static final double THREE_D_SCALE_FACTOR = 160.0;
    private static final double THREE_D_SPHERE_RADIUS = 2.0;
    private static final int THREE_D_WORD_LIMIT = 500;
    private static final double THREE_D_DRAG_ROTATION_SPEED = 0.35;
    private static final double TWO_D_ZOOM_STEP = 1.10;
    private static final double TWO_D_MIN_SCALE = 0.30;
    private static final double TWO_D_MAX_SCALE = 4.00;
    private static final double THREE_D_ZOOM_STEP = 120.0;
    private static final double THREE_D_NEAREST_Z = -200.0;
    private static final double THREE_D_FARTHEST_Z = -5000.0;
    private static final double THREE_D_DEFAULT_CAMERA_Z = -1400.0;

    private final KNNService knnService;
    private final VectorMathService vectorMathService;
    private final EuclideanDistance euclideanDistance;

    private BorderPane mainLayout;
    private final ScatterChart<Number, Number> scatterChart;
    private final StackPane chartContainer;
    private final XYChart.Series<Number, Number> baseWordsSeries;
    private final XYChart.Series<Number, Number> highlightedWordsSeries;
    private final TextField targetWordField;
    private final Button findNeighborsButton;
    private final ToggleButton viewToggleButton;
    private final ComboBox<Integer> xAxisSelector;
    private final ComboBox<Integer> yAxisSelector;
    private final ComboBox<Integer> zAxisSelector;
    private final Button zoomInButton;
    private final Button zoomOutButton;
    private final ListView<String> resultsListView;
    private final Label selectedWordLabel;
    private final Label distanceLabel;
    private final Label statusLabel;
    private SubScene threeDSubScene;
    private PerspectiveCamera threeDCamera;

    private double lastMouseX;
    private double lastMouseY;
    private double twoDScale = 1.0;
    private double threeDCameraZ = THREE_D_DEFAULT_CAMERA_Z;
    private WordNode firstSelected;
    private Node firstSelectedVisualNode;
    private WordNode activeTargetNode;
    private List<WordNode> activeNeighborNodes;

    /**
     * Creates the controller and initializes reusable UI components.
     */
    public MainController() {
        this.knnService = new KNNService();
        this.vectorMathService = new VectorMathService();
        this.euclideanDistance = new EuclideanDistance();
        this.activeNeighborNodes = List.of();

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("PCA X");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("PCA Y");

        this.scatterChart = new ScatterChart<>(xAxis, yAxis);
        this.scatterChart.setTitle("Word Embeddings in PCA Space");
        this.scatterChart.setLegendVisible(false);
        this.scatterChart.setAnimated(false);

        this.baseWordsSeries = new XYChart.Series<>();
        this.highlightedWordsSeries = new XYChart.Series<>();
        this.scatterChart.getData().addAll(baseWordsSeries, highlightedWordsSeries);
        this.chartContainer = new StackPane(scatterChart);
        this.chartContainer.setPadding(new Insets(0, 12, 0, 0));

        this.targetWordField = new TextField();
        this.findNeighborsButton = new Button("Find Nearest Neighbors");
        this.viewToggleButton = new ToggleButton("Switch to 3D View");
        this.xAxisSelector = createPcaSelector(0);
        this.yAxisSelector = createPcaSelector(1);
        this.zAxisSelector = createPcaSelector(2);
        this.zoomInButton = new Button("+");
        this.zoomOutButton = new Button("-");
        this.resultsListView = new ListView<>();
        this.selectedWordLabel = new Label("Selected Word: N/A");
        this.distanceLabel = new Label("Distance: N/A");
        this.statusLabel = new Label();
    }

    /**
     * Builds and returns the main application view.
     *
     * @return the root JavaFX node for the main window
     */
    public Parent getView() {
        mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(12));
        mainLayout.setCenter(buildChartArea());
        mainLayout.setRight(buildControlPanel());

        redrawChart();
        wireEvents();
        updateStatusWithRepositoryInfo();

        return mainLayout;
    }

    private Parent buildChartArea() {
        return chartContainer;
    }

    private Parent buildControlPanel() {
        Label panelTitle = new Label("Control Panel");
        panelTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label targetWordLabel = new Label("Target Word");
        targetWordField.setPromptText("Enter a word from the repository");

        Label resultLabel = new Label("Nearest Neighbors");
        resultsListView.setPlaceholder(new Label("No results yet"));
        VBox.setVgrow(resultsListView, Priority.ALWAYS);

        selectedWordLabel.setWrapText(true);
        distanceLabel.setWrapText(true);
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #444444;");

        VBox controlPanel = new VBox(10,
                panelTitle,
                targetWordLabel,
                targetWordField,
                findNeighborsButton,
                createPcaSelectionPanel(),
                createZoomPanel(),
                viewToggleButton,
                selectedWordLabel,
                distanceLabel,
                resultLabel,
                resultsListView,
                statusLabel
        );
        controlPanel.setPrefWidth(320);
        controlPanel.setPadding(new Insets(0, 0, 0, 12));

        return controlPanel;
    }

    /**
     * Connects UI controls to controller actions.
     */
    private void wireEvents() {
        findNeighborsButton.setOnAction(event -> handleFindNearestNeighbors());
        targetWordField.setOnAction(event -> handleFindNearestNeighbors());
        viewToggleButton.setOnAction(event -> handleViewToggle());
        zoomInButton.setOnAction(event -> handleZoomIn());
        zoomOutButton.setOnAction(event -> handleZoomOut());

        xAxisSelector.valueProperty().addListener((observable, oldValue, newValue) -> handlePcaAxisChange());
        yAxisSelector.valueProperty().addListener((observable, oldValue, newValue) -> handlePcaAxisChange());
        zAxisSelector.valueProperty().addListener((observable, oldValue, newValue) -> handlePcaAxisChange());

        scatterChart.addEventFilter(ScrollEvent.SCROLL, this::handleZoomScroll);
    }

    private VBox createPcaSelectionPanel() {
        Label sectionTitle = new Label("PCA Components");
        Label xLabel = new Label("X axis");
        Label yLabel = new Label("Y axis");
        Label zLabel = new Label("Z axis");

        return new VBox(6,
                sectionTitle,
                xLabel,
                xAxisSelector,
                yLabel,
                yAxisSelector,
                zLabel,
                zAxisSelector
        );
    }

    private HBox createZoomPanel() {
        Label zoomLabel = new Label("Zoom");
        zoomInButton.setPrefWidth(40);
        zoomOutButton.setPrefWidth(40);
        return new HBox(8, zoomLabel, zoomInButton, zoomOutButton);
    }

    private ComboBox<Integer> createPcaSelector(int defaultValue) {
        ObservableList<Integer> values = FXCollections.observableArrayList();
        for (int index = 0; index < PCA_COMPONENT_COUNT; index++) {
            values.add(index);
        }

        ComboBox<Integer> selector = new ComboBox<>(values);
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setValue(defaultValue);
        return selector;
    }

    private void handlePcaAxisChange() {
        resetDistanceSelection();
        redrawActiveView();
    }

    private void handleViewToggle() {
        if (mainLayout == null) {
            return;
        }

        if (viewToggleButton.isSelected()) {
            viewToggleButton.setText("Switch to 2D View");
            threeDSubScene = create3DView();
            mainLayout.setCenter(threeDSubScene);
            return;
        }

        viewToggleButton.setText("Switch to 3D View");
        mainLayout.setCenter(chartContainer);
    }

    /**
     * Builds a lightweight 3D point cloud from PCA vectors to enable depth exploration.
     */
    private SubScene create3DView() {
        Group pointCloudRoot = new Group();
        Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
        pointCloudRoot.getTransforms().addAll(rotateX, rotateY);

        PhongMaterial pointMaterial = new PhongMaterial(Color.web("#1D6FEA"));

        int xIndex = getSelectedXAxisIndex();
        int yIndex = getSelectedYAxisIndex();
        int zIndex = getSelectedZAxisIndex();

        List<WordNode> nodesFor3D = EmbeddingRepository.INSTANCE.getAllWords().stream()
                .filter(node -> hasUsableThreeDimensionalPcaCoordinates(node, xIndex, yIndex, zIndex))
                .filter(node -> node.getWord() != null)
                .sorted(Comparator.comparing(WordNode::getWord, String.CASE_INSENSITIVE_ORDER))
                .limit(THREE_D_WORD_LIMIT)
                .collect(Collectors.toList());

        for (WordNode wordNode : nodesFor3D) {
            double[] vector = wordNode.getPcaVector();
            Sphere point = new Sphere(THREE_D_SPHERE_RADIUS);
            point.setMaterial(pointMaterial);
            point.setTranslateX(vector[xIndex] * THREE_D_SCALE_FACTOR);
            point.setTranslateY(vector[yIndex] * THREE_D_SCALE_FACTOR);
            point.setTranslateZ(vector[zIndex] * THREE_D_SCALE_FACTOR);
            point.setUserData(wordNode);
            Tooltip.install(point, new Tooltip(getDisplayWord(wordNode)));
            point.setOnMouseClicked(event -> {
                event.consume();
                handleWordSelection(wordNode, point);
            });
            pointCloudRoot.getChildren().add(point);
        }

        SubScene subScene = new SubScene(pointCloudRoot, 900, 760, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#111111"));

        threeDCamera = new PerspectiveCamera(true);
        threeDCamera.setNearClip(0.1);
        threeDCamera.setFarClip(10000);
        threeDCamera.setTranslateZ(threeDCameraZ);
        subScene.setCamera(threeDCamera);

        // Dragging rotates the entire cloud around X/Y to inspect neighborhood depth.
        subScene.setOnMousePressed(this::handle3DMousePressed);
        subScene.setOnMouseDragged(event -> handle3DMouseDragged(event, rotateX, rotateY));
        subScene.addEventFilter(ScrollEvent.SCROLL, this::handleZoomScroll);

        return subScene;
    }

    private void handle3DMousePressed(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
    }

    private void handle3DMouseDragged(MouseEvent event, Rotate rotateX, Rotate rotateY) {
        double deltaX = event.getSceneX() - lastMouseX;
        double deltaY = event.getSceneY() - lastMouseY;

        rotateY.setAngle(rotateY.getAngle() + deltaX * THREE_D_DRAG_ROTATION_SPEED);
        rotateX.setAngle(rotateX.getAngle() - deltaY * THREE_D_DRAG_ROTATION_SPEED);

        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
    }

    /**
     * Populates the chart with a limited subset of words to keep the first render responsive.
     * Only words with two valid PCA coordinates are displayed.
     */
    private void redrawChart() {
        int xIndex = getSelectedXAxisIndex();
        int yIndex = getSelectedYAxisIndex();

        baseWordsSeries.getData().clear();
        highlightedWordsSeries.getData().clear();

        NumberAxis xAxis = (NumberAxis) scatterChart.getXAxis();
        NumberAxis yAxis = (NumberAxis) scatterChart.getYAxis();
        xAxis.setLabel("PCA [" + xIndex + "]");
        yAxis.setLabel("PCA [" + yIndex + "]");

        List<WordNode> initialWords = EmbeddingRepository.INSTANCE.getAllWords().stream()
                .filter(node -> hasUsablePcaCoordinates(node, xIndex, yIndex))
                .filter(wordNode -> wordNode.getWord() != null)
                .sorted(Comparator.comparing(WordNode::getWord, String.CASE_INSENSITIVE_ORDER))
                .limit(INITIAL_WORD_LIMIT)
                .collect(Collectors.toList());

        for (WordNode wordNode : initialWords) {
            baseWordsSeries.getData().add(createChartPoint(wordNode, "#7F8C8D", BASE_POINT_SIZE));
        }

        if (activeTargetNode != null) {
            highlightTargetAndNeighbors(activeTargetNode, activeNeighborNodes);
        }

        scatterChart.setScaleX(twoDScale);
        scatterChart.setScaleY(twoDScale);
    }

    /**
     * Handles the nearest-neighbor search initiated from the UI.
     */
    private void handleFindNearestNeighbors() {
        String requestedWord = targetWordField.getText() == null ? "" : targetWordField.getText().trim();
        if (requestedWord.isEmpty()) {
            resultsListView.getItems().clear();
            highlightedWordsSeries.getData().clear();
            statusLabel.setText("Please enter a target word before searching.");
            return;
        }

        WordNode targetNode = findWordNode(requestedWord);
        if (targetNode == null) {
            resultsListView.getItems().clear();
            highlightedWordsSeries.getData().clear();
            statusLabel.setText("The requested word was not found in the repository.");
            return;
        }

        if (targetNode.getOriginalVector() == null) {
            resultsListView.getItems().clear();
            highlightedWordsSeries.getData().clear();
            statusLabel.setText("The selected word does not contain an original embedding vector.");
            return;
        }

        List<WordNode> nearestNeighbors = knnService.findNearestNeighbors(
                        targetNode.getOriginalVector(),
                        DEFAULT_NEIGHBOR_COUNT + 1
                ).stream()
                .filter(candidate -> !isSameWord(targetNode, candidate))
                .limit(DEFAULT_NEIGHBOR_COUNT)
                .collect(Collectors.toList());

        activeTargetNode = targetNode;
        activeNeighborNodes = nearestNeighbors;

        updateResultsList(targetNode, nearestNeighbors);
        highlightTargetAndNeighbors(targetNode, nearestNeighbors);
        statusLabel.setText("Showing " + nearestNeighbors.size() + " nearest neighbors for '" + targetNode.getWord() + "'.");
    }

    /**
     * Updates the right-side result list using the currently active distance strategy.
     * The service owns the distance calculation so the UI remains decoupled from concrete math implementations.
     */
    private void updateResultsList(WordNode targetNode, List<WordNode> nearestNeighbors) {
        ObservableList<String> items = FXCollections.observableArrayList();

        for (int index = 0; index < nearestNeighbors.size(); index++) {
            WordNode neighbor = nearestNeighbors.get(index);
            double distance = knnService.calculateDistance(targetNode.getOriginalVector(), neighbor.getOriginalVector());
            String itemText = String.format(
                    Locale.US,
                    "%d. %s (distance: %.6f)",
                    index + 1,
                    getDisplayWord(neighbor),
                    distance
            );
            items.add(itemText);
        }

        resultsListView.setItems(items);
    }

    /**
     * Draws the target word and its nearest neighbors in a dedicated highlight series.
     * This keeps the base plot unchanged while visually emphasizing the current query context.
     */
    private void highlightTargetAndNeighbors(WordNode targetNode, List<WordNode> nearestNeighbors) {
        highlightedWordsSeries.getData().clear();

        int xIndex = getSelectedXAxisIndex();
        int yIndex = getSelectedYAxisIndex();

        if (hasUsablePcaCoordinates(targetNode, xIndex, yIndex)) {
            highlightedWordsSeries.getData().add(createChartPoint(targetNode, "#E74C3C", HIGHLIGHT_POINT_SIZE));
        }

        for (WordNode neighbor : nearestNeighbors) {
            if (hasUsablePcaCoordinates(neighbor, xIndex, yIndex)) {
                highlightedWordsSeries.getData().add(createChartPoint(neighbor, "#1D6FEA", HIGHLIGHT_POINT_SIZE));
            }
        }
    }

    /**
     * Creates a scatter-chart point with a styled marker and a tooltip.
     */
    private XYChart.Data<Number, Number> createChartPoint(WordNode wordNode, String color, double pointSize) {
        int xIndex = getSelectedXAxisIndex();
        int yIndex = getSelectedYAxisIndex();
        double[] pcaVector = wordNode.getPcaVector();
        XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(pcaVector[xIndex], pcaVector[yIndex]);

        StackPane marker = new StackPane();
        styleTwoDMarker(marker, color, pointSize);
        Tooltip.install(marker, new Tooltip(getDisplayWord(wordNode)));
        marker.setOnMouseClicked(event -> {
            event.consume();
            handleWordSelection(wordNode, marker);
        });

        dataPoint.setNode(marker);
        return dataPoint;
    }

    private void styleTwoDMarker(StackPane marker, String color, double pointSize) {
        marker.setPrefSize(pointSize, pointSize);
        marker.setMinSize(pointSize, pointSize);
        marker.setMaxSize(pointSize, pointSize);
        marker.setStyle(String.format(Locale.US,
                "-fx-background-color: %s; -fx-background-radius: %.1fpx;",
                color,
                pointSize));
    }

    private void handleWordSelection(WordNode clickedWord, Node clickedVisualNode) {
        selectedWordLabel.setText("Selected Word: " + getDisplayWord(clickedWord));

        if (firstSelected == null) {
            firstSelected = clickedWord;
            firstSelectedVisualNode = clickedVisualNode;
            applySelectedStyle(clickedVisualNode);
            distanceLabel.setText("Distance: Select one more point");
            return;
        }

        double distance = calculateEuclideanDistance(firstSelected, clickedWord);
        if (Double.isNaN(distance)) {
            distanceLabel.setText("Distance: N/A (incompatible vectors)");
        } else {
            distanceLabel.setText(String.format(Locale.US, "Distance: %.6f", distance));
        }

        resetDistanceSelection();
        redrawActiveView();
    }

    private double calculateEuclideanDistance(WordNode firstWord, WordNode secondWord) {
        if (firstWord == null || secondWord == null) {
            return Double.NaN;
        }

        double[] firstVector = firstWord.getOriginalVector();
        double[] secondVector = secondWord.getOriginalVector();
        if (firstVector == null || secondVector == null || firstVector.length != secondVector.length) {
            return Double.NaN;
        }

        return euclideanDistance.calculateDistance(firstVector, secondVector);
    }

    private void applySelectedStyle(Node visualNode) {
        if (visualNode instanceof StackPane marker) {
            double markerSize = Math.max(marker.getPrefWidth(), BASE_POINT_SIZE);
            styleTwoDMarker(marker, "#E74C3C", markerSize);
            return;
        }

        if (visualNode instanceof Sphere sphere) {
            sphere.setMaterial(new PhongMaterial(Color.web("#E74C3C")));
        }
    }

    private void resetDistanceSelection() {
        firstSelected = null;
        firstSelectedVisualNode = null;
    }

    private void redrawActiveView() {
        redrawChart();
        if (viewToggleButton.isSelected() && mainLayout != null) {
            threeDSubScene = create3DView();
            mainLayout.setCenter(threeDSubScene);
        }
    }

    private void handleZoomIn() {
        if (isThreeDModeActive()) {
            zoomThreeD(THREE_D_ZOOM_STEP);
        } else {
            zoomTwoD(TWO_D_ZOOM_STEP);
        }
    }

    private void handleZoomOut() {
        if (isThreeDModeActive()) {
            zoomThreeD(-THREE_D_ZOOM_STEP);
        } else {
            zoomTwoD(1.0 / TWO_D_ZOOM_STEP);
        }
    }

    private void handleZoomScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) {
            return;
        }

        if (event.getDeltaY() > 0) {
            handleZoomIn();
        } else {
            handleZoomOut();
        }
        event.consume();
    }

    private void zoomTwoD(double factor) {
        double updatedScale = twoDScale * factor;
        twoDScale = Math.max(TWO_D_MIN_SCALE, Math.min(TWO_D_MAX_SCALE, updatedScale));
        scatterChart.setScaleX(twoDScale);
        scatterChart.setScaleY(twoDScale);
    }

    private void zoomThreeD(double cameraDelta) {
        threeDCameraZ = Math.max(THREE_D_FARTHEST_Z, Math.min(THREE_D_NEAREST_Z, threeDCameraZ + cameraDelta));
        if (threeDCamera != null) {
            threeDCamera.setTranslateZ(threeDCameraZ);
        }
    }

    private boolean isThreeDModeActive() {
        return viewToggleButton.isSelected();
    }

    private int getSelectedXAxisIndex() {
        Integer value = xAxisSelector.getValue();
        return value == null ? 0 : value;
    }

    private int getSelectedYAxisIndex() {
        Integer value = yAxisSelector.getValue();
        return value == null ? 1 : value;
    }

    private int getSelectedZAxisIndex() {
        Integer value = zAxisSelector.getValue();
        return value == null ? 2 : value;
    }

    /**
     * Looks up a word using exact and case-insensitive matching.
     */
    private WordNode findWordNode(String requestedWord) {
        WordNode directMatch = EmbeddingRepository.INSTANCE.getWord(requestedWord);
        if (directMatch != null) {
            return directMatch;
        }

        WordNode lowerCaseMatch = EmbeddingRepository.INSTANCE.getWord(requestedWord.toLowerCase(Locale.ROOT));
        if (lowerCaseMatch != null) {
            return lowerCaseMatch;
        }

        return EmbeddingRepository.INSTANCE.getAllWords().stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getWord() != null)
                .filter(node -> node.getWord().equalsIgnoreCase(requestedWord))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks whether a word contains the two PCA dimensions required for 2D plotting.
     */
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

    private boolean hasUsableThreeDimensionalPcaCoordinates(WordNode wordNode, int xIndex, int yIndex, int zIndex) {
        if (wordNode == null || wordNode.getPcaVector() == null) {
            return false;
        }

        int neededLength = Math.max(xIndex, Math.max(yIndex, zIndex)) + 1;
        if (wordNode.getPcaVector().length < neededLength) {
            return false;
        }

        double x = wordNode.getPcaVector()[xIndex];
        double y = wordNode.getPcaVector()[yIndex];
        double z = wordNode.getPcaVector()[zIndex];
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
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

    /**
     * Updates the status label based on the repository contents.
     */
    private void updateStatusWithRepositoryInfo() {
        int totalWords = EmbeddingRepository.INSTANCE.getAllWords().size();
        if (totalWords == 0) {
            statusLabel.setText("No embedding data is loaded yet. Launch the application with a JSON file path to display data.");
            return;
        }

        statusLabel.setText("Loaded " + totalWords + " words. The chart initially renders up to " + INITIAL_WORD_LIMIT + " words.");
    }
}


