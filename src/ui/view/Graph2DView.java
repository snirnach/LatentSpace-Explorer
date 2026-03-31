package ui.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import model.WordNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * High-performance 2D visualization based on Canvas rendering.
 */
public class Graph2DView implements IVisualizationView {

    private static final double TWO_D_ZOOM_STEP = 1.10;
    private static final double TWO_D_MIN_SCALE = 0.50;
    private static final double TWO_D_MAX_SCALE = 20.00;
    private static final double CLICK_RADIUS_PIXELS = 8.0;
    private static final double DRAG_THRESHOLD_PIXELS = 2.0;

    private final Pane centerWrapper;
    private final Canvas canvas;
    private final Graph2DRenderer renderer;

    private Consumer<WordNode> onWordClicked;

    private double lastMouseX;
    private double lastMouseY;
    private double pressMouseX;
    private double pressMouseY;
    private double zoomFactor = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private boolean suppressPanUntilRelease;
    private boolean isShiftClickSequence;

    private WordNode focusedWord = null;

    private List<WordNode> cachedWords;
    private int axisX;
    private int axisY;

    private WordNode probeSource;
    private List<WordNode> probeNeighbors = new ArrayList<>();
    private List<WordNode> mathPathWords;
    private WordNode mathResultWord;
    private WordNode semanticPoleA;
    private WordNode semanticPoleB;
    private Set<WordNode> selectedGroup = new HashSet<>();

    private boolean hasValidRange;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private final Map<String, Double> semanticScoreByWord = new java.util.HashMap<>();

    public Graph2DView() {
        this.centerWrapper = new Pane();
        this.canvas = new Canvas();
        this.renderer = new Graph2DRenderer(canvas.getGraphicsContext2D());
        this.centerWrapper.setFocusTraversable(false);
        this.canvas.setFocusTraversable(false);
        this.centerWrapper.getChildren().add(canvas);

        canvas.widthProperty().bind(centerWrapper.widthProperty());
        canvas.heightProperty().bind(centerWrapper.heightProperty());

        Rectangle clipRect = new Rectangle();
        clipRect.widthProperty().bind(centerWrapper.widthProperty());
        clipRect.heightProperty().bind(centerWrapper.heightProperty());
        centerWrapper.setClip(clipRect);

        this.onWordClicked = ignoredWord -> {
        };
        this.cachedWords = List.of();
        this.axisX = 0;
        this.axisY = 1;
        this.suppressPanUntilRelease = false;
        this.isShiftClickSequence = false;

        wireInteractions();

        centerWrapper.widthProperty().addListener(ignoredObservable -> redraw());
        centerWrapper.heightProperty().addListener(ignoredObservable -> redraw());
    }

    @Override
    public Pane getUIComponent() {
        return centerWrapper;
    }

    @Override
    public void setOnWordClicked(Consumer<WordNode> listener) {
        this.onWordClicked = listener == null ? ignoredWord -> {
        } : listener;
    }

    @Override
    public void focusOnWord(WordNode word) {
        this.focusedWord = word;
        if (word == null || !hasValidRange) {
            redraw();
            return;
        }

        Double axisXValue = getAxisValueOrNull(word, axisX);
        Double axisYValue = getAxisValueOrNull(word, axisY);
        if (axisXValue == null || axisYValue == null) {
            redraw();
            return;
        }

        double rawPixelX = renderer.toCanvasX(axisXValue, canvas.getWidth(), minX, maxX, 0.0, zoomFactor);
        double rawPixelY = renderer.toCanvasY(axisYValue, canvas.getHeight(), minY, maxY, 0.0, zoomFactor);

        this.offsetX = (canvas.getWidth() / 2.0) - rawPixelX;
        this.offsetY = (canvas.getHeight() / 2.0) - rawPixelY;
        redraw();
    }

    @Override
    public void clearVisualSelection() {
        this.focusedWord = null;
        this.probeSource = null;
        this.probeNeighbors = new ArrayList<>();
        this.mathPathWords = null;
        this.mathResultWord = null;
        this.selectedGroup.clear();
        redraw();
    }

    @Override
    public void updateData(Collection<WordNode> words, int[] selectedAxes) {
        this.axisX = (selectedAxes != null && selectedAxes.length > 0) ? selectedAxes[0] : 0;
        this.axisY = (selectedAxes != null && selectedAxes.length > 1) ? selectedAxes[1] : 1;
        this.cachedWords = words == null ? List.of() : new ArrayList<>(words);

        computeDataRange();

        if (this.focusedWord != null) {
            focusOnWord(this.focusedWord);
        } else {
            redraw();
        }
    }

    @Override
    public void showNearestNeighbors(WordNode source, List<WordNode> neighbors) {
        this.probeSource = source;
        this.probeNeighbors = neighbors != null ? new ArrayList<>(neighbors) : new ArrayList<>();
        this.focusedWord = source;
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

        computeDataRange();
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        if (hasValidRange && width > 0 && height > 0) {
            double rawXSum = 0.0;
            double rawYSum = 0.0;
            int nodeCount = 0;

            if (equationWords != null) {
                for (WordNode wordNode : equationWords) {
                    if (!hasAxisCoordinates(wordNode, axisX, axisY)) {
                        continue;
                    }
                    rawXSum += getAxisValue(wordNode, axisX);
                    rawYSum += getAxisValue(wordNode, axisY);
                    nodeCount++;
                }
            }

            if (hasAxisCoordinates(closestResult, axisX, axisY)) {
                rawXSum += getAxisValue(closestResult, axisX);
                rawYSum += getAxisValue(closestResult, axisY);
                nodeCount++;
            }

            if (nodeCount > 0) {
                double averageRawX = rawXSum / nodeCount;
                double averageRawY = rawYSum / nodeCount;

                double baseX = ((averageRawX - minX) / (maxX - minX)) * width;
                double baseY = (1.0 - ((averageRawY - minY) / (maxY - minY))) * height;

                double centerX = width / 2.0;
                double centerY = height / 2.0;

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

    @Override
    public void setSemanticScores(List<Map.Entry<String, Double>> projections) {
        semanticScoreByWord.clear();
        renderer.setSemanticScores(projections);

        if (projections == null) {
            return;
        }

        for (Map.Entry<String, Double> entry : projections) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            semanticScoreByWord.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
    }

    @Override
    public void setSemanticPoles(WordNode poleA, WordNode poleB) {
        this.semanticPoleA = poleA;
        this.semanticPoleB = poleB;
        renderer.setSemanticPoles(poleA, poleB);
    }

    public void zoomIn() {
        applyZoomAt(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0, TWO_D_ZOOM_STEP);
    }

    public void zoomOut() {
        applyZoomAt(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0, 1.0 / TWO_D_ZOOM_STEP);
    }

    private void wireInteractions() {
        canvas.setOnScroll(this::handleCanvasScroll);
        canvas.setOnMousePressed(this::handleCanvasMousePressed);
        canvas.setOnMouseDragged(this::handlePanMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
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

        pressMouseX = event.getSceneX();
        pressMouseY = event.getSceneY();
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();

        // Detect and isolate shift-click operations from regular single clicks.
        if (event.isShiftDown()) {
            isShiftClickSequence = true;
            WordNode clickedWord = findClosestWord(event.getX(), event.getY());
            if (clickedWord != null) {
                if (selectedGroup.stream().anyMatch(node -> node != null && node.isSameWord(clickedWord))) {
                    selectedGroup.removeIf(node -> node != null && node.isSameWord(clickedWord));
                } else {
                    selectedGroup.add(clickedWord);
                }
                redraw();
                suppressPanUntilRelease = true;
                event.consume();
                return;
            }
        }

        isShiftClickSequence = false;
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

        offsetX += deltaX;
        offsetY += deltaY;
        redraw();

        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!event.getButton().equals(MouseButton.PRIMARY)) {
            suppressPanUntilRelease = false;
            return;
        }

        // Prevent shift-click mouse release from triggering a standard single click.
        if (isShiftClickSequence) {
            isShiftClickSequence = false;
            suppressPanUntilRelease = false;
            return;
        }

        double deltaX = event.getSceneX() - pressMouseX;
        double deltaY = event.getSceneY() - pressMouseY;
        double distanceMoved = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        if (distanceMoved < DRAG_THRESHOLD_PIXELS) {
            WordNode closestWord = findClosestWord(event.getX(), event.getY());
            if (closestWord != null) {
                selectedGroup.clear();
                focusOnWord(closestWord);
                if (onWordClicked != null) {
                    onWordClicked.accept(closestWord);
                }
                suppressPanUntilRelease = true;
                event.consume();
            } else {
                clearVisualSelection();
                if (onWordClicked != null) {
                    onWordClicked.accept(null);
                }
                suppressPanUntilRelease = false;
                event.consume();
            }
        } else {
            suppressPanUntilRelease = false;
        }
    }

    private void applyZoomAt(double mouseX, double mouseY, double scaleMultiplier) {
        double targetZoom = zoomFactor * scaleMultiplier;
        double clampedZoom = Math.max(TWO_D_MIN_SCALE, Math.min(TWO_D_MAX_SCALE, targetZoom));
        double appliedMultiplier = clampedZoom / zoomFactor;

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

        if (width <= 0 || height <= 0 || cachedWords.isEmpty()) {
            hasValidRange = false;
            renderer.render(
                    width,
                    height,
                    offsetX,
                    offsetY,
                    zoomFactor,
                    cachedWords,
                    new int[]{axisX, axisY},
                    focusedWord,
                    probeSource,
                    probeNeighbors,
                    mathPathWords,
                    mathResultWord,
                    selectedGroup
            );
            return;
        }

        computeDataRange();

        WordNode effectiveFocusedWord = focusedWord;
        if (effectiveFocusedWord != null && !hasAxisCoordinates(effectiveFocusedWord, axisX, axisY)) {
            effectiveFocusedWord = null;
        }

        renderer.render(
                width,
                height,
                offsetX,
                offsetY,
                zoomFactor,
                cachedWords,
                new int[]{axisX, axisY},
                effectiveFocusedWord,
                probeSource,
                probeNeighbors,
                mathPathWords,
                mathResultWord,
                selectedGroup
        );
    }

    private void computeDataRange() {
        hasValidRange = false;
        minX = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;

        for (WordNode wordNode : cachedWords) {
            if (!hasAxisCoordinates(wordNode, axisX, axisY)) {
                continue;
            }

            double x = getAxisValue(wordNode, axisX);
            double y = getAxisValue(wordNode, axisY);

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
            if (!hasAxisCoordinates(wordNode, axisX, axisY)) {
                continue;
            }

            double pixelX = renderer.toCanvasX(getAxisValue(wordNode, axisX), width, minX, maxX, offsetX, zoomFactor);
            double pixelY = renderer.toCanvasY(getAxisValue(wordNode, axisY), height, minY, maxY, offsetY, zoomFactor);

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

    private boolean hasAxisCoordinates(WordNode wordNode, int xAxis, int yAxis) {
        return getAxisValueOrNull(wordNode, xAxis) != null && getAxisValueOrNull(wordNode, yAxis) != null;
    }

    private double getAxisValue(WordNode wordNode, int axisIndex) {
        Double value = getAxisValueOrNull(wordNode, axisIndex);
        return value == null ? 0.0 : value;
    }

    private Double getAxisValueOrNull(WordNode wordNode, int axisIndex) {
        if (wordNode == null || wordNode.getWord() == null) {
            return null;
        }

        if (axisIndex == Graph2DRenderer.SEMANTIC_AXIS_INDEX) {
            return semanticScoreByWord.get(wordNode.getWord().toLowerCase(Locale.ROOT));
        }

        if (!wordNode.hasValidPcaCoordinates(axisIndex)) {
            return null;
        }

        return wordNode.getPcaVector()[axisIndex];
    }
}