package ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import model.WordNode;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated canvas renderer for Graph2DView.
 */
public class Graph2DRenderer {

    private static final double BASE_POINT_SIZE = 5.0;
    private static final double HIGHLIGHT_POINT_SIZE = 7.0;
    private static final String BASE_COLOR = "#7F8C8D";
    private static final String HIGHLIGHT_COLOR = "#1D6FEA";
    private static final String SOURCE_COLOR = "#E74C3C";

    private static final Color MATH_PATH_COLOR = Color.MAGENTA;
    private static final Color MATH_RESULT_COLOR = Color.DODGERBLUE;
    private static final Color GROUP_COLOR = Color.PURPLE;
    private static final Color PROBE_SOURCE_COLOR = Color.ORANGE;
    private static final Color PROBE_NEIGHBOR_COLOR = Color.GREEN;
    private static final Color FOCUSED_COLOR = Color.RED;

    private static final double PROBE_LINE_WIDTH = 1.5;
    private static final double MATH_PATH_LINE_WIDTH = 3.0;

    private final GraphicsContext gc;

    private Set<String> highlightedWordKeys = Set.of();
    private String sourceWordKey;

    public Graph2DRenderer(GraphicsContext gc) {
        this.gc = gc;
    }

    public void setHighlightState(Set<String> highlightedWordKeys, String sourceWordKey) {
        this.highlightedWordKeys = highlightedWordKeys == null ? Set.of() : new HashSet<>(highlightedWordKeys);
        this.sourceWordKey = sourceWordKey;
    }

    public void render(
            double width,
            double height,
            double offsetX,
            double offsetY,
            double zoomFactor,
            Collection<WordNode> words,
            int[] axes,
            WordNode focusedWord,
            WordNode probeSource,
            List<WordNode> probeNeighbors,
            List<WordNode> mathPathWords,
            WordNode mathResultWord,
            Set<WordNode> selectedGroup
    ) {
        gc.clearRect(0, 0, width, height);

        if (width <= 0 || height <= 0 || words == null || words.isEmpty() || axes == null || axes.length < 2) {
            return;
        }

        int axisX = axes[0];
        int axisY = axes[1];

        DataRange range = computeDataRange(words, axisX, axisY);
        if (!range.hasValidRange) {
            return;
        }

        List<WordNode> safeProbeNeighbors = probeNeighbors == null ? List.of() : probeNeighbors;
        Set<WordNode> safeSelectedGroup = selectedGroup == null ? Set.of() : selectedGroup;

        // Draw vector arithmetic path before marker rendering.
        if (mathPathWords != null && !mathPathWords.isEmpty()) {
            gc.save();
            gc.setStroke(MATH_PATH_COLOR);
            gc.setLineWidth(MATH_PATH_LINE_WIDTH);
            gc.setLineDashes(10.0, 6.0);

            WordNode previous = null;
            for (WordNode pathWord : mathPathWords) {
                if (pathWord == null || !pathWord.hasValidPcaCoordinates(axisX, axisY)) {
                    continue;
                }

                if (previous != null && previous.hasValidPcaCoordinates(axisX, axisY)) {
                    double[] previousVector = previous.getPcaVector();
                    double[] currentVector = pathWord.getPcaVector();
                    double x1 = toCanvasX(previousVector[axisX], width, range.minX, range.maxX, offsetX, zoomFactor);
                    double y1 = toCanvasY(previousVector[axisY], height, range.minY, range.maxY, offsetY, zoomFactor);
                    double x2 = toCanvasX(currentVector[axisX], width, range.minX, range.maxX, offsetX, zoomFactor);
                    double y2 = toCanvasY(currentVector[axisY], height, range.minY, range.maxY, offsetY, zoomFactor);
                    gc.strokeLine(x1, y1, x2, y2);
                }

                previous = pathWord;
            }

            if (previous != null
                    && previous.hasValidPcaCoordinates(axisX, axisY)
                    && mathResultWord != null
                    && mathResultWord.hasValidPcaCoordinates(axisX, axisY)) {
                double[] previousVector = previous.getPcaVector();
                double[] resultVector = mathResultWord.getPcaVector();
                double x1 = toCanvasX(previousVector[axisX], width, range.minX, range.maxX, offsetX, zoomFactor);
                double y1 = toCanvasY(previousVector[axisY], height, range.minY, range.maxY, offsetY, zoomFactor);
                double x2 = toCanvasX(resultVector[axisX], width, range.minX, range.maxX, offsetX, zoomFactor);
                double y2 = toCanvasY(resultVector[axisY], height, range.minY, range.maxY, offsetY, zoomFactor);
                gc.strokeLine(x1, y1, x2, y2);
            }
            gc.restore();
        }

        // Draw probe connection lines before marker rendering.
        if (!safeProbeNeighbors.isEmpty() && probeSource != null && probeSource.hasValidPcaCoordinates(axisX, axisY)) {
            double[] sourceVector = probeSource.getPcaVector();
            double sourceX = toCanvasX(sourceVector[axisX], width, range.minX, range.maxX, offsetX, zoomFactor);
            double sourceY = toCanvasY(sourceVector[axisY], height, range.minY, range.maxY, offsetY, zoomFactor);

            gc.setStroke(Color.DARKGRAY);
            gc.setLineWidth(PROBE_LINE_WIDTH);

            for (WordNode neighbor : safeProbeNeighbors) {
                if (neighbor == null || !neighbor.hasValidPcaCoordinates(axisX, axisY)) {
                    continue;
                }

                double[] neighborVector = neighbor.getPcaVector();
                double neighborX = toCanvasX(neighborVector[axisX], width, range.minX, range.maxX, offsetX, zoomFactor);
                double neighborY = toCanvasY(neighborVector[axisY], height, range.minY, range.maxY, offsetY, zoomFactor);

                gc.strokeLine(sourceX, sourceY, neighborX, neighborY);
            }
        }

        for (WordNode wordNode : words) {
            if (wordNode == null || !wordNode.hasValidPcaCoordinates(axisX, axisY)) {
                continue;
            }

            double[] vector = wordNode.getPcaVector();
            double pixelX = toCanvasX(vector[axisX], width, range.minX, range.maxX, offsetX, zoomFactor);
            double pixelY = toCanvasY(vector[axisY], height, range.minY, range.maxY, offsetY, zoomFactor);

            boolean isProbeSource = isStrictWordMatch(wordNode, probeSource);
            boolean isProbeNeighbor = safeProbeNeighbors.stream().anyMatch(neighbor -> isStrictWordMatch(wordNode, neighbor));
            boolean isMathPathWord = mathPathWords != null
                    && mathPathWords.stream().anyMatch(pathWord -> isStrictWordMatch(wordNode, pathWord));
            boolean isMathResultWord = isStrictWordMatch(wordNode, mathResultWord);
            boolean isSelectedGroupWord = safeSelectedGroup.stream()
                    .anyMatch(groupNode -> isStrictWordMatch(groupNode, wordNode));

            if (isSelectedGroupWord) {
                gc.setFill(GROUP_COLOR);
                double groupSize = HIGHLIGHT_POINT_SIZE + 2.0;
                gc.fillOval(pixelX - (groupSize / 2.0), pixelY - (groupSize / 2.0), groupSize, groupSize);
                gc.setFill(Color.BLACK);
                gc.fillText(wordNode.getWord(), pixelX + 6, pixelY - 6);
                continue;
            }

            if (isMathPathWord || isMathResultWord) {
                if (isMathResultWord) {
                    gc.setFill(MATH_RESULT_COLOR);
                    double resultSize = HIGHLIGHT_POINT_SIZE + 3.0;
                    gc.fillOval(pixelX - (resultSize / 2.0), pixelY - (resultSize / 2.0), resultSize, resultSize);
                } else {
                    gc.setFill(MATH_PATH_COLOR);
                    double pathSize = HIGHLIGHT_POINT_SIZE + 1.5;
                    gc.fillOval(pixelX - (pathSize / 2.0), pixelY - (pathSize / 2.0), pathSize, pathSize);
                }

                gc.setFill(Color.BLACK);
                gc.fillText(wordNode.getWord(), pixelX + 6, pixelY - 6);
                continue;
            }

            if (isProbeSource || isProbeNeighbor) {
                if (isProbeSource) {
                    gc.setFill(PROBE_SOURCE_COLOR);
                    double probeSize = HIGHLIGHT_POINT_SIZE + 2.0;
                    gc.fillOval(pixelX - (probeSize / 2.0), pixelY - (probeSize / 2.0), probeSize, probeSize);
                    gc.setFill(Color.BLACK);
                    gc.fillText(wordNode.getWord(), pixelX + 5, pixelY - 5);
                } else {
                    gc.setFill(PROBE_NEIGHBOR_COLOR);
                    double neighborSize = HIGHLIGHT_POINT_SIZE;
                    gc.fillOval(pixelX - (neighborSize / 2.0), pixelY - (neighborSize / 2.0), neighborSize, neighborSize);
                    gc.setFill(Color.BLACK);
                    gc.fillText(wordNode.getWord(), pixelX + 5, pixelY - 5);
                }
                continue;
            }

            if (isStrictWordMatch(wordNode, focusedWord)) {
                gc.setFill(FOCUSED_COLOR);
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

    public double toCanvasX(double valueX, double width, double minX, double maxX, double offsetX, double zoomFactor) {
        double normalized = (valueX - minX) / (maxX - minX);
        double baseX = normalized * width;
        return offsetX + (baseX * zoomFactor);
    }

    public double toCanvasY(double valueY, double height, double minY, double maxY, double offsetY, double zoomFactor) {
        double normalized = (valueY - minY) / (maxY - minY);
        double baseY = (1.0 - normalized) * height;
        return offsetY + (baseY * zoomFactor);
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

    private boolean isStrictWordMatch(WordNode currentWord, WordNode otherWord) {
        if (currentWord == null || otherWord == null) {
            return false;
        }

        String current = currentWord.getWord();
        String other = otherWord.getWord();
        if (current == null || current.isBlank() || other == null || other.isBlank()) {
            return false;
        }

        return currentWord.isSameWord(otherWord);
    }

    private DataRange computeDataRange(Collection<WordNode> words, int axisX, int axisY) {
        DataRange range = new DataRange();

        for (WordNode wordNode : words) {
            if (wordNode == null || !wordNode.hasValidPcaCoordinates(axisX, axisY)) {
                continue;
            }

            double[] vector = wordNode.getPcaVector();
            double x = vector[axisX];
            double y = vector[axisY];

            range.minX = Math.min(range.minX, x);
            range.maxX = Math.max(range.maxX, x);
            range.minY = Math.min(range.minY, y);
            range.maxY = Math.max(range.maxY, y);
            range.hasValidRange = true;
        }

        if (!range.hasValidRange) {
            return range;
        }

        if (Double.compare(range.minX, range.maxX) == 0) {
            range.minX -= 1.0;
            range.maxX += 1.0;
        }

        if (Double.compare(range.minY, range.maxY) == 0) {
            range.minY -= 1.0;
            range.maxY += 1.0;
        }

        return range;
    }


    private static class MarkerStyle {
        private final String color;
        private final double size;

        private MarkerStyle(String color, double size) {
            this.color = color;
            this.size = size;
        }
    }

    private static class DataRange {
        private boolean hasValidRange;
        private double minX = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
    }
}



