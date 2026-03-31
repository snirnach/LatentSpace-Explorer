package ui.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Dedicated view class for the right-hand control panel.
 *
 * <p>This class owns sidebar layout and styling, while the controller owns behavior.</p>
 */
public class ControlPanelView {

    @FunctionalInterface
    public interface PcaAxesChangeListener {
        void onAxesChanged(int x, int y, int z);
    }

    private static final int PCA_COMPONENT_COUNT = 50;
    private static final String EUCLIDEAN_METRIC_NAME = "Euclidean";
    private static final String COSINE_METRIC_NAME = "Cosine";

    private final TextField searchField;
    private final ComboBox<String> distanceMetricCombo;
    private final Spinner<Integer> kSpinner;
    private final Button searchButton;
    private final ToggleButton toggle3dButton;
    private final ComboBox<Integer> pcaXCombo;
    private final ComboBox<Integer> pcaYCombo;
    private final ComboBox<Integer> pcaZCombo;
    private final Button zoomInButton;
    private final Button zoomOutButton;
    private final ListView<String> resultsListView;
    private final Label resultsTitleLabel;
    private final Label selectedWordLabel;
    private final Button measureButton;
    private final Label distanceResultLabel;
    private final Label statusLabel;
    private final TextField equationField;
    private final Button calculateEquationButton;
    private final TextField semanticAxisWordAField;
    private final TextField semanticAxisWordBField;
    private final Button projectOnAxisButton;
    private final Button analyzeSubspaceGroupButton;
    private final Button undoButton;
    private final Button redoButton;
    private PcaAxesChangeListener pcaAxesChangeListener;
    private boolean isUpdatingProgrammatically = false;

    public ControlPanelView() {
        this.searchField = new TextField();
        this.distanceMetricCombo = new ComboBox<>(FXCollections.observableArrayList(
                EUCLIDEAN_METRIC_NAME,
                COSINE_METRIC_NAME
        ));
        this.distanceMetricCombo.setValue(EUCLIDEAN_METRIC_NAME);
        this.distanceMetricCombo.setMaxWidth(Double.MAX_VALUE);

        this.kSpinner = new Spinner<>(1, 100, 10);
        this.kSpinner.setEditable(true);

        this.searchButton = new Button("Find Nearest Neighbors");
        this.toggle3dButton = new ToggleButton("Switch to 3D View");

        this.pcaXCombo = createPcaSelector(0);
        this.pcaYCombo = createPcaSelector(1);
        this.pcaZCombo = createPcaSelector(2);

        this.zoomInButton = new Button("+");
        this.zoomOutButton = new Button("-");

        this.resultsListView = new ListView<>();
        this.resultsListView.setPlaceholder(new Label("No results yet"));
        this.resultsListView.setPrefHeight(200);
        this.resultsListView.setMinHeight(200);
        this.resultsListView.setMaxHeight(200);
        this.resultsListView.setFocusTraversable(false);

        this.resultsTitleLabel = new Label("Showing: Nearest Neighbors");
        this.resultsTitleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");

        this.selectedWordLabel = new Label("Selected: None");
        this.measureButton = new Button("Measure Distance");
        this.measureButton.setDisable(true);
        this.distanceResultLabel = new Label("");
        this.statusLabel = new Label();

        this.equationField = new TextField();
        this.equationField.setPromptText("e.g., king - man + woman");
        this.calculateEquationButton = new Button("Calculate Equation");

        this.semanticAxisWordAField = new TextField();
        this.semanticAxisWordAField.setPromptText("Positive pole, e.g., rich");
        this.semanticAxisWordBField = new TextField();
        this.semanticAxisWordBField.setPromptText("Negative pole, e.g., poor");
        this.projectOnAxisButton = new Button("Project onto Axis");
        this.analyzeSubspaceGroupButton = new Button("Analyze Subspace Group");

        this.undoButton = new Button("Undo");
        this.undoButton.setDisable(true);
        this.undoButton.setFocusTraversable(false);

        this.redoButton = new Button("Redo");
        this.redoButton.setDisable(true);
        this.redoButton.setFocusTraversable(false);

        // ...existing code...
        this.searchButton.setFocusTraversable(false);
        this.toggle3dButton.setFocusTraversable(false);
        this.zoomInButton.setFocusTraversable(false);
        this.zoomOutButton.setFocusTraversable(false);
        this.measureButton.setFocusTraversable(false);
        this.calculateEquationButton.setFocusTraversable(false);
        this.projectOnAxisButton.setFocusTraversable(false);
        this.analyzeSubspaceGroupButton.setFocusTraversable(false);

        wirePcaSelectionUpdates();
    }

    private void wirePcaSelectionUpdates() {
        pcaXCombo.valueProperty().addListener((ignoredObservable, ignoredOldValue, ignoredNewValue) -> publishAxes());
        pcaYCombo.valueProperty().addListener((ignoredObservable, ignoredOldValue, ignoredNewValue) -> publishAxes());
        pcaZCombo.valueProperty().addListener((ignoredObservable, ignoredOldValue, ignoredNewValue) -> publishAxes());
    }

    private void publishAxes() {
        if (isUpdatingProgrammatically) {
            return;
        }
        if (pcaAxesChangeListener == null) {
            return;
        }
        int x = pcaXCombo.getValue() == null ? 0 : pcaXCombo.getValue();
        int y = pcaYCombo.getValue() == null ? 1 : pcaYCombo.getValue();
        int z = pcaZCombo.getValue() == null ? 2 : pcaZCombo.getValue();
        pcaAxesChangeListener.onAxesChanged(x, y, z);
    }

    /**
     * Builds and returns the full sidebar wrapped in a ScrollPane.
     */
    public ScrollPane getView() {
        VBox controlPanel = buildControlPanel();
        ScrollPane scrollPane = new ScrollPane(controlPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    private VBox buildControlPanel() {
        Label panelTitle = new Label("Control Panel");
        panelTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333333;");

        HBox historyPanel = new HBox(8);
        historyPanel.setStyle("-fx-padding: 5; -fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 3;");
        undoButton.setMaxWidth(Double.MAX_VALUE);
        redoButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(undoButton, Priority.ALWAYS);
        HBox.setHgrow(redoButton, Priority.ALWAYS);
        historyPanel.getChildren().addAll(undoButton, redoButton);

        Label distanceMetricLabel = new Label("Distance Metric");
        distanceMetricLabel.setStyle("-fx-text-fill: #333333;");
        
        Label kLabel = new Label("K Neighbors");
        kLabel.setStyle("-fx-text-fill: #333333;");
        
        Label targetWordLabel = new Label("Target Word");
        targetWordLabel.setStyle("-fx-text-fill: #333333;");
        searchField.setPromptText("Enter a word from the repository");

        selectedWordLabel.setWrapText(true);
        selectedWordLabel.setStyle("-fx-text-fill: #333333;");
        distanceResultLabel.setWrapText(true);
        distanceResultLabel.setStyle("-fx-text-fill: #333333;");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #333333;");

        VBox controlPanel = new VBox(10,
                panelTitle,
                historyPanel,
                toggle3dButton,
                createPcaSelectionPanel(),
                distanceMetricLabel,
                distanceMetricCombo,
                kLabel,
                kSpinner,
                targetWordLabel,
                searchField,
                searchButton,
                resultsTitleLabel,
                resultsListView,
                createVectorArithmeticPanel(),
                createSemanticAxisPanel(),
                analyzeSubspaceGroupButton,
                createZoomPanel(),
                selectedWordLabel,
                measureButton,
                distanceResultLabel,
                statusLabel
        );
        // Keep sidebar width stable so internal content updates do not shift layout bounds.
        controlPanel.setMinWidth(320);
        controlPanel.setPrefWidth(320);
        controlPanel.setMaxWidth(320);
        controlPanel.setPadding(new Insets(0, 0, 0, 12));
        return controlPanel;
    }

    private VBox createPcaSelectionPanel() {
        Label sectionTitle = new Label("PCA Components");
        sectionTitle.setStyle("-fx-text-fill: #333333;");
        Label xLabel = new Label("X axis");
        xLabel.setStyle("-fx-text-fill: #333333;");
        Label yLabel = new Label("Y axis");
        yLabel.setStyle("-fx-text-fill: #333333;");
        Label zLabel = new Label("Z axis");
        zLabel.setStyle("-fx-text-fill: #333333;");

        return new VBox(6,
                sectionTitle,
                xLabel,
                pcaXCombo,
                yLabel,
                pcaYCombo,
                zLabel,
                pcaZCombo
        );
    }

    private HBox createZoomPanel() {
        Label zoomLabel = new Label("Zoom");
        zoomLabel.setStyle("-fx-text-fill: #333333;");
        zoomInButton.setPrefWidth(40);
        zoomOutButton.setPrefWidth(40);
        return new HBox(8, zoomLabel, zoomInButton, zoomOutButton);
    }

    private VBox createSemanticAxisPanel() {
        Label sectionTitle = new Label("Semantic Axis (Projection)");
        sectionTitle.setStyle("-fx-text-fill: #333333; -fx-font-weight: bold;");

        Label wordALabel = new Label("Positive Pole");
        wordALabel.setStyle("-fx-text-fill: #333333;");

        Label wordBLabel = new Label("Negative Pole");
        wordBLabel.setStyle("-fx-text-fill: #333333;");

        projectOnAxisButton.setMaxWidth(Double.MAX_VALUE);

        return new VBox(6,
                sectionTitle,
                wordALabel,
                semanticAxisWordAField,
                wordBLabel,
                semanticAxisWordBField,
                projectOnAxisButton
        );
    }

    private VBox createVectorArithmeticPanel() {
        Label sectionTitle = new Label("Vector Arithmetic Lab");
        sectionTitle.setStyle("-fx-text-fill: #333333; -fx-font-weight: bold;");

        calculateEquationButton.setMaxWidth(Double.MAX_VALUE);

        return new VBox(6,
                sectionTitle,
                equationField,
                calculateEquationButton
        );
    }

    private ComboBox<Integer> createPcaSelector(int defaultValue) {
        ObservableList<Integer> values = FXCollections.observableArrayList();
        for (int i = 0; i < PCA_COMPONENT_COUNT; i++) {
            values.add(i);
        }

        ComboBox<Integer> selector = new ComboBox<>(values);
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setValue(defaultValue);
        return selector;
    }

    public void setOnSearchAction(EventHandler<ActionEvent> handler) {
        searchButton.setOnAction(handler);
        searchField.setOnAction(handler);
    }

    public void setOnToggleViewAction(EventHandler<ActionEvent> handler) {
        toggle3dButton.setOnAction(handler);
    }

    public void setOnZoomInAction(EventHandler<ActionEvent> handler) {
        zoomInButton.setOnAction(handler);
    }

    public void setOnZoomOutAction(EventHandler<ActionEvent> handler) {
        zoomOutButton.setOnAction(handler);
    }

    public void setOnMeasureAction(EventHandler<ActionEvent> handler) {
        measureButton.setOnAction(handler);
    }

    public void setOnCalculateEquationAction(EventHandler<ActionEvent> handler) {
        calculateEquationButton.setOnAction(handler);
    }

    public void setOnProjectAxisAction(EventHandler<ActionEvent> handler) {
        projectOnAxisButton.setOnAction(handler);
    }

    public void setOnAnalyzeSubspaceGroupAction(EventHandler<ActionEvent> handler) {
        analyzeSubspaceGroupButton.setOnAction(handler);
    }

    public void setOnUndoAction(EventHandler<ActionEvent> handler) {
        undoButton.setOnAction(handler);
    }

    public void setOnRedoAction(EventHandler<ActionEvent> handler) {
        redoButton.setOnAction(handler);
    }

    public void setUndoButtonDisabled(boolean disabled) {
        undoButton.setDisable(disabled);
    }

    public void setRedoButtonDisabled(boolean disabled) {
        redoButton.setDisable(disabled);
    }

    public void setOnPcaAxesChanged(PcaAxesChangeListener listener) {
        this.pcaAxesChangeListener = listener;
    }

    public String getSearchText() {
        return searchField.getText();
    }

    public String getEquationText() {
        return equationField.getText();
    }

    public String getSemanticAxisWordAText() {
        return semanticAxisWordAField.getText();
    }

    public String getSemanticAxisWordBText() {
        return semanticAxisWordBField.getText();
    }

    public String getDistanceMetric() {
        return distanceMetricCombo.getValue();
    }

    public int getKValue() {
        return kSpinner.getValue();
    }

    public void displayResults(ObservableList<String> results) {
        resultsListView.setItems(results);
    }

    public void clearResults() {
        resultsListView.getItems().clear();
    }

    public void setResultsTitle(String title) {
        resultsTitleLabel.setText(title);
    }

    public void setStatusMessage(String message) {
        statusLabel.setText(message);
    }

    public void setSelectedWordText(String text) {
        selectedWordLabel.setText(text);
    }

    public void setDistanceResultText(String text) {
        distanceResultLabel.setText(text);
    }

    public void setMeasureButtonDisabled(boolean disabled) {
        measureButton.setDisable(disabled);
    }

    public boolean isToggle3DSelected() {
        return toggle3dButton.isSelected();
    }

    public void setToggleButtonText(String text) {
        toggle3dButton.setText(text);
    }

    public ListView<String> getResultsListView() {
        return resultsListView;
    }

    public Label getResultsTitleLabel() {
        return resultsTitleLabel;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    /**
     * Updates the PCA ComboBox values without triggering change listeners.
     * Used during undo/redo to update the UI without creating new commands.
     *
     * @param x the X axis component index
     * @param y the Y axis component index
     * @param z the Z axis component index
     */
    public void setPcaComboBoxValues(int x, int y, int z) {
        try {
            isUpdatingProgrammatically = true;
            pcaXCombo.setValue(x);
            pcaYCombo.setValue(y);
            pcaZCombo.setValue(z);
        } finally {
            isUpdatingProgrammatically = false;
        }
    }

    /**
     * Sets the search field text.
     * Used during undo/redo to restore the search text.
     *
     * @param text the search text to display
     */
    public void setSearchText(String text) {
        searchField.setText(text != null ? text : "");
    }

    /**
     * Sets the equation field text.
     * Used during undo/redo to restore the equation text.
     *
     * @param text the equation text to display
     */
    public void setEquationText(String text) {
        equationField.setText(text != null ? text : "");
    }
}

