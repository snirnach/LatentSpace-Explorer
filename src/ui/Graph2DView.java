package ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import model.WordNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * High-performance 2D visualization based on Canvas rendering.
 */
public class Graph2DView implements IVisualizationView {

    private static final double BASE_POINT_SIZE = 5.0;
    private static final double HIGHLIGHT_POINT_SIZE = 7.0;
    private static final String BASE_COLOR = "#7F8C8D";
    private static final String HIGHLIGHT_COLOR = "#1D6FEA";
    private static final String SOURCE_COLOR = "#E74C3C";

    private static final double TWO_D_ZOOM_STEP = 1.10;
    private static final double TWO_D_MIN_SCALE = 0.50;
    private static final double TWO_D_MAX_SCALE = 20.00;
    private static final double CLICK_RADIUS_PIXELS = 8.0;
    private static final double DRAG_THRESHOLD_PIXELS = 2.0;

    private final Pane centerWrapper;
    private final Canvas canvas;

    private Consumer<WordNode> onWordClicked;

    private double lastMouseX;
    private double lastMouseY;
    private double zoomFactor = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private boolean suppressPanUntilRelease;

    private Set<String> highlightedWordKeys;
    private String sourceWordKey;
    private WordNode focusedWord = null;

    private List<WordNode> cachedWords;
    private int axisX;
    private int axisY;

    private WordNode probeSource;
    private List<WordNode> probeNeighbors = new ArrayList<>();
    private List<WordNode> mathPathWords;
    private WordNode mathResultWord;
    private Set<WordNode> selectedGroup = new HashSet<>();

    private boolean hasValidRange;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;

    public Graph2DView() {
        this.centerWrapper = new Pane();
        this.canvas = new Canvas();
        this.centerWrapper.setFocusTraversable(false);
        this.canvas.setFocusTraversable(false);
        this.centerWrapper.getChildren().add(canvas);

        // Bind canvas size to wrapper size so rendering always fills the center area.
        canvas.widthProperty().bind(centerWrapper.widthProperty());
        canvas.heightProperty().bind(centerWrapper.heightProperty());

        Rectangle clipRect = new Rectangle();
        clipRect.widthProperty().bind(centerWrapper.widthProperty());
        clipRect.heightProperty().bind(centerWrapper.heightProperty());
        centerWrapper.setClip(clipRect);

        this.onWordClicked = ignoredWord -> {
        };
        this.highlightedWordKeys = Set.of();
        this.cachedWords = List.of();
        this.axisX = 0;
        this.axisY = 1;
        this.suppressPanUntilRelease = false;

        wireInteractions();

        // Redraw when layout size changes.
        centerWrapper.widthProperty().addListener(ignoredObservable -> redraw());
        centerWrapper.heightProperty().addListener(ignoredObservable -> redraw());
    }

    /**
     * Returns the clipped 2D canvas container.
     */
    @Override
    public Pane getUIComponent() {
        return centerWrapper;
    }

    /**
     * Registers a callback that is invoked when a rendered point is clicked.
     */
    @Override
    public void setOnWordClicked(Consumer<WordNode> listener) {
        this.onWordClicked = listener == null ? ignoredWord -> {
        } : listener;
    }

    /**
     * Centers the current 2D camera transform on the given word, if it is plottable.
     */
    @Override
    public void focusOnWord(WordNode word) {
        this.focusedWord = word;
        this.probeSource = null;
        if (this.probeNeighbors != null) {
            this.probeNeighbors.clear();
        }
        this.mathPathWords = null;
        this.mathResultWord = null;

        if (!isWordPlottable(word)) {
            redraw();
            return;
        }

        setSelectedWord(word);
        computeDataRange();
        if (!hasValidRange) {
            redraw();
            return;
        }

        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            redraw();
            return;
        }

        double[] vector = word.getPcaVector();
        double baseX = ((vector[axisX] - minX) / (maxX - minX)) * width;
        double baseY = (1.0 - ((vector[axisY] - minY) / (maxY - minY))) * height;

        double centerX = width / 2.0;
        double centerY = height / 2.0;

        // Keep current zoom level and adjust offsets so the target word lands at the center.
        offsetX = centerX - (baseX * zoomFactor);
        offsetY = centerY - (baseY * zoomFactor);
        redraw();
    }

    /**
     * Updates highlighted words rendered in emphasized style.
     */
    public void setHighlightedWords(Collection<WordNode> highlightedWords) {
        if (highlightedWords == null || highlightedWords.isEmpty()) {
            this.highlightedWordKeys = Set.of();
            return;
        }

        Set<String> normalizedKeys = new HashSet<>();
        for (WordNode wordNode : highlightedWords) {
            if (wordNode != null && wordNode.getWord() != null) {
                normalizedKeys.add(wordNode.getWord().toLowerCase(Locale.ROOT));
            }
        }
        this.highlightedWordKeys = normalizedKeys;
    }

    /**
     * Updates the currently selected source word rendered in dedicated style.
     */
    public void setSelectedWord(WordNode selectedWord) {
        if (selectedWord == null || selectedWord.getWord() == null) {
            this.sourceWordKey = null;
            return;
        }

        this.sourceWordKey = selectedWord.getWord().toLowerCase(Locale.ROOT);
    }

    /**
     * Caches new data and redraws all points on canvas using the selected PCA axes.
     */
    @Override
    public void updateData(Collection<WordNode> words, int[] selectedAxes) {
        this.axisX = (selectedAxes != null && selectedAxes.length > 0) ? selectedAxes[0] : 0;
        this.axisY = (selectedAxes != null && selectedAxes.length > 1) ? selectedAxes[1] : 1;
        this.cachedWords = words == null ? List.of() : new ArrayList<>(words);
        redraw();
    }

    /**
     * Displays the nearest neighbors probe visualization for a source word
     * with connections/highlights to its neighbors.
     */
    @Override
    public void showNearestNeighbors(WordNode source, List<WordNode> neighbors) {
        this.probeSource = source;
        this.probeNeighbors = neighbors != null ? new ArrayList<>(neighbors) : new ArrayList<>();
        this.focusedWord = null;
        this.mathPathWords = null;
        this.mathResultWord = null;
        redraw();
    }

    @Override
    public void showMathPath(List<WordNode> equationWords, WordNode closestResult) {
        this.mathPathWords = equationWords != null ? new ArrayList<>(equationWords) : null;
        this.mathResultWord = closestResult;
        this.focusedWord = null;
        this.probeSource = null;
        if (this.probeNeighbors != null) {
            this.probeNeighbors.clear();
        }

        // Center the view around the arithmetic path centroid before rendering.
        computeDataRange();
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        if (hasValidRange && width > 0 && height > 0) {
            double rawXSum = 0.0;
            double rawYSum = 0.0;
            int nodeCount = 0;

            if (equationWords != null) {
                for (WordNode wordNode : equationWords) {
                    if (!isWordPlottable(wordNode)) {
                        continue;
                    }
                    double[] vector = wordNode.getPcaVector();
                    rawXSum += vector[axisX];
                    rawYSum += vector[axisY];
                    nodeCount++;
                }
            }

            if (isWordPlottable(closestResult)) {
                double[] resultVector = closestResult.getPcaVector();
                rawXSum += resultVector[axisX];
                rawYSum += resultVector[axisY];
                nodeCount++;
            }

            if (nodeCount > 0) {
                double averageRawX = rawXSum / nodeCount;
                double averageRawY = rawYSum / nodeCount;

                double baseX = ((averageRawX - minX) / (maxX - minX)) * width;
                double baseY = (1.0 - ((averageRawY - minY) / (maxY - minY))) * height;

                double centerX = width / 2.0;
                double centerY = height / 2.0;

                // Use the same centering formula as focusOnWord.
                offsetX = centerX - (baseX * zoomFactor);
                offsetY = centerY - (baseY * zoomFactor);
            }
        }

        redraw();
    }

    @Override
    public Set<WordNode> getSelectedGroup() {
        return new HashSet<>(selectedGroup);
    }

    /**
     * Performs zoom-in with predefined step.
     */
    public void zoomIn() {
        applyZoomAt(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0, TWO_D_ZOOM_STEP);
    }

    /**
     * Performs zoom-out with predefined step.
     */
    public void zoomOut() {
        applyZoomAt(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0, 1.0 / TWO_D_ZOOM_STEP);
    }


    private MarkerStyle resolveStyle(WordNode wordNode) {
        String key = wordNode.getWord() == null ? null : wordNode.getWord().toLowerCase(Locale.ROOT);
        if (key != null && key.equals(sourceWordKey)) {
            return new MarkerStyle(SOURCE_COLOR, HIGHLIGHT_POINT_SIZE);
        }

        if (key != null && highlightedWordKeys.contains(key)) {
            return new MarkerStyle(HIGHLIGHT_COLOR, HIGHLIGHT_POINT_SIZE);
        }

        return new MarkerStyle(BASE_COLOR, BASE_POINT_SIZE);
    }

    private void wireInteractions() {
        canvas.setOnScroll(this::handleCanvasScroll);
        canvas.setOnMousePressed(this::handleCanvasMousePressed);
        canvas.setOnMouseDragged(this::handlePanMouseDragged);
        canvas.setOnMouseReleased(ignoredEvent -> suppressPanUntilRelease = false);
    }

    private void handleCanvasScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) {
            return;
        }

        double scaleMultiplier = (event.getDeltaY() > 0) ? 1.1 : (1.0 / 1.1);
        applyZoomAt(event.getX(), event.getY(), scaleMultiplier);
        event.consume();
    }

    private void handleCanvasMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        // Always refresh pan anchors using absolute scene coordinates.
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();

        WordNode clickedWord = findClosestWord(event.getX(), event.getY());
        if (clickedWord != null) {
            if (event.isShiftDown()) {
                // Shift-click toggles subspace group membership without triggering the standard probe flow.
                if (selectedGroup.stream().anyMatch(node -> isSameWord(node, clickedWord))) {
                    selectedGroup.removeIf(node -> isSameWord(node, clickedWord));
                } else {
                    selectedGroup.add(clickedWord);
                }
                redraw();
                suppressPanUntilRelease = true;
                event.consume();
                return;
            }

            // A regular click starts standard probe selection and clears any existing subspace group selection.
            selectedGroup.clear();
            if (onWordClicked != null) {
                onWordClicked.accept(clickedWord);
            }
            // Prevent a micro-drag after point selection from shifting the whole view.
            suppressPanUntilRelease = true;
            event.consume();
            return;
        }

        suppressPanUntilRelease = false;
    }

    private void handlePanMouseDragged(MouseEvent event) {
        if (!event.isPrimaryButtonDown()) {
            return;
        }

        if (suppressPanUntilRelease) {
            event.consume();
            return;
        }

        double deltaX = event.getSceneX() - lastMouseX;
        double deltaY = event.getSceneY() - lastMouseY;

        if (Math.abs(deltaX) <= DRAG_THRESHOLD_PIXELS && Math.abs(deltaY) <= DRAG_THRESHOLD_PIXELS) {
            return;
        }

        // Dragging only pans by updating the viewport offsets.
        offsetX += deltaX;
        offsetY += deltaY;
        redraw();

        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
        event.consume();
    }

    private void applyZoomAt(double mouseX, double mouseY, double scaleMultiplier) {
        double targetZoom = zoomFactor * scaleMultiplier;
        double clampedZoom = Math.max(TWO_D_MIN_SCALE, Math.min(TWO_D_MAX_SCALE, targetZoom));
        double appliedMultiplier = clampedZoom / zoomFactor;

        // Keep the point under the cursor fixed while zooming.
        double dx = mouseX - offsetX;
        double dy = mouseY - offsetY;
        offsetX -= dx * (appliedMultiplier - 1.0);
        offsetY -= dy * (appliedMultiplier - 1.0);
        zoomFactor = clampedZoom;

        redraw();
    }

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);

        if (width <= 0 || height <= 0 || cachedWords.isEmpty()) {
            hasValidRange = false;
            return;
        }

        computeDataRange();
        if (!hasValidRange) {
            return;
        }

        // Draw vector arithmetic path before marker rendering.
        if (mathPathWords != null && !mathPathWords.isEmpty()) {
            gc.save();
            gc.setStroke(Color.MAGENTA);
            gc.setLineWidth(3.0);
            gc.setLineDashes(10.0, 6.0);

            WordNode previous = null;
            for (WordNode pathWord : mathPathWords) {
                if (!isWordPlottable(pathWord)) {
                    continue;
                }

                if (previous != null && isWordPlottable(previous)) {
                    double[] previousVector = previous.getPcaVector();
                    double[] currentVector = pathWord.getPcaVector();
                    double x1 = toCanvasX(previousVector[axisX], width);
                    double y1 = toCanvasY(previousVector[axisY], height);
                    double x2 = toCanvasX(currentVector[axisX], width);
                    double y2 = toCanvasY(currentVector[axisY], height);
                    gc.strokeLine(x1, y1, x2, y2);
                }

                previous = pathWord;
            }

            if (previous != null && isWordPlottable(previous) && isWordPlottable(mathResultWord)) {
                double[] previousVector = previous.getPcaVector();
                double[] resultVector = mathResultWord.getPcaVector();
                double x1 = toCanvasX(previousVector[axisX], width);
                double y1 = toCanvasY(previousVector[axisY], height);
                double x2 = toCanvasX(resultVector[axisX], width);
                double y2 = toCanvasY(resultVector[axisY], height);
                gc.strokeLine(x1, y1, x2, y2);
            }
            gc.restore();
        }

        // Draw probe connection lines before marker rendering.
        if (!probeNeighbors.isEmpty() && isWordPlottable(probeSource)) {
            double[] sourceVector = probeSource.getPcaVector();
            double sourceX = toCanvasX(sourceVector[axisX], width);
            double sourceY = toCanvasY(sourceVector[axisY], height);

            gc.setStroke(Color.DARKGRAY);
            gc.setLineWidth(1.5);

            for (WordNode neighbor : probeNeighbors) {
                if (!isWordPlottable(neighbor)) {
                    continue;
                }

                double[] neighborVector = neighbor.getPcaVector();
                double neighborX = toCanvasX(neighborVector[axisX], width);
                double neighborY = toCanvasY(neighborVector[axisY], height);

                gc.strokeLine(sourceX, sourceY, neighborX, neighborY);
            }
        }

        // Draw markers and apply probe-specific highlighting.
        for (WordNode wordNode : cachedWords) {
            if (!isWordPlottable(wordNode)) {
                continue;
            }

            double[] vector = wordNode.getPcaVector();
            double pixelX = toCanvasX(vector[axisX], width);
            double pixelY = toCanvasY(vector[axisY], height);

            // Check if this word is the probe source or a neighbor
            boolean isProbeSource = probeSource != null && isSameWord(wordNode, probeSource);
            boolean isProbeNeighbor = probeNeighbors.stream().anyMatch(neighbor -> isSameWord(wordNode, neighbor));
            boolean isMathPathWord = mathPathWords != null
                    && mathPathWords.stream().anyMatch(pathWord -> isSameWord(wordNode, pathWord));
            boolean isMathResultWord = mathResultWord != null && isSameWord(wordNode, mathResultWord);
            boolean isSelectedGroupWord = selectedGroup.stream().anyMatch(groupNode -> isSameWord(groupNode, wordNode));

            if (isSelectedGroupWord) {
                gc.setFill(Color.PURPLE);
                double groupSize = HIGHLIGHT_POINT_SIZE + 2.0;
                gc.fillOval(pixelX - (groupSize / 2.0), pixelY - (groupSize / 2.0), groupSize, groupSize);
                gc.setFill(Color.BLACK);
                gc.fillText(wordNode.getWord(), pixelX + 6, pixelY - 6);
                continue;
            }

            if (isMathPathWord || isMathResultWord) {
                if (isMathResultWord) {
                    gc.setFill(Color.DODGERBLUE);
                    double resultSize = HIGHLIGHT_POINT_SIZE + 3.0;
                    gc.fillOval(pixelX - (resultSize / 2.0), pixelY - (resultSize / 2.0), resultSize, resultSize);
                } else {
                    gc.setFill(Color.MAGENTA);
                    double pathSize = HIGHLIGHT_POINT_SIZE + 1.5;
                    gc.fillOval(pixelX - (pathSize / 2.0), pixelY - (pathSize / 2.0), pathSize, pathSize);
                }

                gc.setFill(Color.BLACK);
                gc.fillText(wordNode.getWord(), pixelX + 6, pixelY - 6);
                continue;
            }

            if (isProbeSource || isProbeNeighbor) {
                // Highlight probe words with larger dots, distinct colors, and labels
                if (isProbeSource) {
                    gc.setFill(Color.ORANGE);
                    double probeSize = HIGHLIGHT_POINT_SIZE + 2.0;
                    gc.fillOval(pixelX - (probeSize / 2.0), pixelY - (probeSize / 2.0), probeSize, probeSize);
                    gc.setFill(Color.BLACK);
                    gc.fillText(wordNode.getWord(), pixelX + 5, pixelY - 5);
                } else {
                    gc.setFill(Color.GREEN);
                    double neighborSize = HIGHLIGHT_POINT_SIZE;
                    gc.fillOval(pixelX - (neighborSize / 2.0), pixelY - (neighborSize / 2.0), neighborSize, neighborSize);
                    gc.setFill(Color.BLACK);
                    gc.fillText(wordNode.getWord(), pixelX + 5, pixelY - 5);
                }
                continue;
            }

            if (isSameWord(wordNode, focusedWord)) {
                // Highlight and label the actively focused word.
                gc.setFill(Color.RED);
                double focusedSize = Math.max(HIGHLIGHT_POINT_SIZE + 2.0, 9.0);
                gc.fillOval(pixelX - (focusedSize / 2.0), pixelY - (focusedSize / 2.0), focusedSize, focusedSize);
                gc.fillText(wordNode.getWord(), pixelX + 10, pixelY - 10);
                continue;
            }

            MarkerStyle style = resolveStyle(wordNode);
            if (BASE_COLOR.equals(style.color)) {
                gc.setFill(Color.BLACK);
            } else {
                gc.setFill(Color.web(style.color));
            }
            gc.fillOval(pixelX - (style.size / 2.0), pixelY - (style.size / 2.0), style.size, style.size);
        }
    }

    private void computeDataRange() {
        hasValidRange = false;
        minX = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;

        for (WordNode wordNode : cachedWords) {
            if (!isWordPlottable(wordNode)) {
                continue;
            }

            double[] vector = wordNode.getPcaVector();
            double x = vector[axisX];
            double y = vector[axisY];

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            hasValidRange = true;
        }

        if (!hasValidRange) {
            return;
        }

        if (Double.compare(minX, maxX) == 0) {
            minX -= 1.0;
            maxX += 1.0;
        }

        if (Double.compare(minY, maxY) == 0) {
            minY -= 1.0;
            maxY += 1.0;
        }
    }

    private boolean isWordPlottable(WordNode wordNode) {
        if (wordNode == null || wordNode.getPcaVector() == null || wordNode.getWord() == null) {
            return false;
        }

        int neededLength = Math.max(axisX, axisY) + 1;
        if (wordNode.getPcaVector().length < neededLength) {
            return false;
        }

        double x = wordNode.getPcaVector()[axisX];
        double y = wordNode.getPcaVector()[axisY];
        return Double.isFinite(x) && Double.isFinite(y);
    }

    private double toCanvasX(double valueX, double width) {
        double normalized = (valueX - minX) / (maxX - minX);
        double baseX = normalized * width;
        return applyViewTransformX(baseX, width);
    }

    private double toCanvasY(double valueY, double height) {
        double normalized = (valueY - minY) / (maxY - minY);
        double baseY = (1.0 - normalized) * height;
        return applyViewTransformY(baseY, height);
    }

    private double applyViewTransformX(double x, double width) {
        return offsetX + (x * zoomFactor);
    }

    private double applyViewTransformY(double y, double height) {
        return offsetY + (y * zoomFactor);
    }

    private WordNode findClosestWord(double clickX, double clickY) {
        if (!hasValidRange || cachedWords.isEmpty()) {
            return null;
        }

        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double radiusSquared = CLICK_RADIUS_PIXELS * CLICK_RADIUS_PIXELS;

        WordNode closestWord = null;
        double closestDistanceSquared = Double.MAX_VALUE;

        for (WordNode wordNode : cachedWords) {
            if (!isWordPlottable(wordNode)) {
                continue;
            }

            double[] vector = wordNode.getPcaVector();
            double pixelX = toCanvasX(vector[axisX], width);
            double pixelY = toCanvasY(vector[axisY], height);

            double dx = pixelX - clickX;
            double dy = pixelY - clickY;
            double distanceSquared = (dx * dx) + (dy * dy);

            if (distanceSquared <= radiusSquared && distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestWord = wordNode;
            }
        }

        return closestWord;
    }

    private boolean isSameWord(WordNode first, WordNode second) {
        return first != null
                && second != null
                && first.getWord() != null
                && second.getWord() != null
                && first.getWord().equalsIgnoreCase(second.getWord());
    }

    private static class MarkerStyle {
        private final String color;
        private final double size;

        private MarkerStyle(String color, double size) {
            this.color = color;
            this.size = size;
        }
    }
}


