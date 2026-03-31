package ui.view;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import model.WordNode;
import ui.threed.CompositeCluster3D;
import ui.threed.ConnectionLeaf3D;
import ui.threed.TextLabel3D;
import ui.threed.WordLeaf3D;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dedicated renderer for building 3D point-cloud nodes and visual overlays.
 * This class owns all rendering constants and node-construction rules.
 */
public class Scene3DRenderer {

    public static final int SEMANTIC_AXIS_INDEX = 999;
    private static final double THREE_D_SCALE_FACTOR = 160.0;

    private static final double BASE_POINT_RADIUS = 2.5;
    private static final double HIGHLIGHT_POINT_RADIUS = 3.5;
    private static final double GROUP_POINT_RADIUS = 4.5;
    private static final double MATH_PATH_POINT_RADIUS = 4.25;
    private static final double MATH_RESULT_POINT_RADIUS = 5.0;
    private static final double PROBE_SOURCE_POINT_RADIUS = 4.5;
    private static final double PROBE_NEIGHBOR_POINT_RADIUS = HIGHLIGHT_POINT_RADIUS;
    private static final double FOCUSED_POINT_RADIUS = 4.5;

    private static final double PROBE_CONNECTION_WIDTH = 0.7;
    private static final double MATH_CONNECTION_WIDTH = 1.0;

    private static final Color BASE_COLOR = Color.DODGERBLUE;
    private static final Color MATH_PATH_COLOR = Color.MAGENTA;
    private static final Color MATH_RESULT_COLOR = Color.DODGERBLUE;
    private static final Color GROUP_COLOR = Color.PURPLE;
    private static final Color PROBE_SOURCE_COLOR = Color.ORANGE;
    private static final Color PROBE_NEIGHBOR_COLOR = Color.GREEN;
    private static final Color FOCUSED_COLOR = Color.RED;
    private static final Color PROBE_CONNECTION_COLOR = Color.DARKGRAY;
    private static final Color SEMANTIC_POLE_A_COLOR = Color.GREEN;
    private static final Color SEMANTIC_POLE_B_COLOR = Color.RED;
    private static final double SEMANTIC_POLE_POINT_RADIUS = HIGHLIGHT_POINT_RADIUS + 2.0;

    private Point3D lastComputedCenter = Point3D.ZERO;
    private boolean hasRenderablePoints;
    private final Map<String, Double> semanticScoreByWord = new HashMap<>();
    private WordNode semanticPoleA;
    private WordNode semanticPoleB;

    /**
     * Caches semantic projection scores for optional semantic-axis rendering.
     */
    public void setSemanticScores(List<Map.Entry<String, Double>> projections) {
        semanticScoreByWord.clear();
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

    /**
     * Stores semantic pole words for explicit visual highlighting.
     */
    public void setSemanticPoles(WordNode poleA, WordNode poleB) {
        this.semanticPoleA = poleA;
        this.semanticPoleB = poleB;
    }

    /**
     * Builds a full point-cloud group containing base markers, highlights, paths, and labels.
     */
    public Group buildPointCloudGroup(
            List<WordNode> words,
            int[] axes,
            WordNode focusedWord,
            WordNode probeSource,
            List<WordNode> probeNeighbors,
            List<WordNode> mathPathWords,
            WordNode mathResultWord,
            Set<WordNode> selectedGroup,
            Consumer<MouseEvent> onWordPress
    ) {
        int[] safeAxes = normalizeAxes(axes);
        int xIndex = safeAxes[0];
        int yIndex = safeAxes[1];
        int zIndex = safeAxes[2];

        CompositeCluster3D rootCluster = new CompositeCluster3D();
        CompositeCluster3D baseWordsCluster = new CompositeCluster3D();
        CompositeCluster3D highlightCluster = new CompositeCluster3D();
        CompositeCluster3D pathCluster = new CompositeCluster3D();
        CompositeCluster3D labelCluster = new CompositeCluster3D();

        rootCluster.add(baseWordsCluster);
        rootCluster.add(pathCluster);
        rootCluster.add(highlightCluster);
        rootCluster.add(labelCluster);

        double totalX = 0.0;
        double totalY = 0.0;
        double totalZ = 0.0;
        int renderedPointCount = 0;

        Map<String, Point3D> pointByWord = new HashMap<>();

        List<WordNode> nodesFor3D = (words == null ? List.<WordNode>of() : words).stream()
                .filter(node -> hasCoordinate(node, xIndex) && hasCoordinate(node, yIndex) && hasCoordinate(node, zIndex))
                .filter(node -> node.getWord() != null)
                .sorted(Comparator.comparing(WordNode::getWord, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (WordNode wordNode : nodesFor3D) {
            Point3D point = projectWordPoint(wordNode, safeAxes);
            if (point == null) {
                continue;
            }

            pointByWord.put(normalizeWord(wordNode), point);

            Marker3DStyle style = resolveMarkerStyle(wordNode, focusedWord, probeSource, probeNeighbors, mathPathWords, mathResultWord, selectedGroup);
            WordLeaf3D leaf = new WordLeaf3D(
                    wordNode,
                    point.getX(),
                    point.getY(),
                    point.getZ(),
                    style.color(),
                    style.radius(),
                    null,
                    onWordPress
            );

            if (style.highlighted()) {
                highlightCluster.add(leaf);
            } else {
                baseWordsCluster.add(leaf);
            }

            totalX += point.getX();
            totalY += point.getY();
            totalZ += point.getZ();
            renderedPointCount++;
        }

        addMathPathConnections(pathCluster, pointByWord, mathPathWords, mathResultWord);
        addProbeConnections(highlightCluster, pointByWord, probeSource, probeNeighbors);
        addTextLabels(labelCluster, pointByWord, focusedWord, probeSource, probeNeighbors, mathPathWords, mathResultWord);

        Group pointCloudRoot = new Group();
        rootCluster.attachTo(pointCloudRoot);

        this.hasRenderablePoints = renderedPointCount > 0;
        this.lastComputedCenter = resolveCenterPoint(
                renderedPointCount,
                totalX,
                totalY,
                totalZ,
                pointByWord,
                focusedWord,
                probeSource,
                mathResultWord,
                mathPathWords
        );

        return pointCloudRoot;
    }

    public Point3D getLastComputedCenter() {
        return lastComputedCenter;
    }

    public boolean hasRenderablePoints() {
        return hasRenderablePoints;
    }

    public Point3D projectWordPoint(WordNode wordNode, int[] axes) {
        if (wordNode == null || axes == null || axes.length < 3) {
            return null;
        }

        int xIndex = axes[0];
        int yIndex = axes[1];
        int zIndex = axes[2];

        if (!hasCoordinate(wordNode, xIndex) || !hasCoordinate(wordNode, yIndex) || !hasCoordinate(wordNode, zIndex)) {
            return null;
        }
        return new Point3D(
                getCoordinate(wordNode, xIndex) * THREE_D_SCALE_FACTOR,
                getCoordinate(wordNode, yIndex) * THREE_D_SCALE_FACTOR,
                getCoordinate(wordNode, zIndex) * THREE_D_SCALE_FACTOR
        );
    }

    private boolean hasCoordinate(WordNode wordNode, int axisIndex) {
        return getCoordinateOrNull(wordNode, axisIndex) != null;
    }

    private double getCoordinate(WordNode wordNode, int axisIndex) {
        Double value = getCoordinateOrNull(wordNode, axisIndex);
        return value == null ? 0.0 : value;
    }

    private Double getCoordinateOrNull(WordNode wordNode, int axisIndex) {
        if (wordNode == null || wordNode.getWord() == null) {
            return null;
        }

        if (axisIndex == SEMANTIC_AXIS_INDEX) {
            return semanticScoreByWord.get(wordNode.getWord().toLowerCase(Locale.ROOT));
        }

        if (!wordNode.hasValidPcaCoordinates(axisIndex)) {
            return null;
        }

        return wordNode.getPcaVector()[axisIndex];
    }

    private Point3D resolveCenterPoint(
            int renderedPointCount,
            double totalX,
            double totalY,
            double totalZ,
            Map<String, Point3D> pointByWord,
            WordNode focusedWord,
            WordNode probeSource,
            WordNode mathResultWord,
            List<WordNode> mathPathWords
    ) {
        if (renderedPointCount <= 0) {
            return Point3D.ZERO;
        }

        double centerX = totalX / renderedPointCount;
        double centerY = totalY / renderedPointCount;
        double centerZ = totalZ / renderedPointCount;

        Point3D focusPoint = null;
        if (focusedWord != null) {
            focusPoint = pointByWord.get(normalizeWord(focusedWord));
        } else if (probeSource != null) {
            focusPoint = pointByWord.get(normalizeWord(probeSource));
        } else if (mathResultWord != null) {
            focusPoint = pointByWord.get(normalizeWord(mathResultWord));
        } else if (mathPathWords != null && !mathPathWords.isEmpty()) {
            focusPoint = pointByWord.get(normalizeWord(mathPathWords.get(mathPathWords.size() - 1)));
        }

        if (focusPoint != null) {
            centerX = focusPoint.getX();
            centerY = focusPoint.getY();
            centerZ = focusPoint.getZ();
        }

        return new Point3D(centerX, centerY, centerZ);
    }

    private void addMathPathConnections(
            CompositeCluster3D pathCluster,
            Map<String, Point3D> pointByWord,
            List<WordNode> mathPathWords,
            WordNode mathResultWord
    ) {
        if (mathPathWords == null || mathPathWords.isEmpty()) {
            return;
        }

        WordNode previous = null;
        for (WordNode current : mathPathWords) {
            if (previous != null) {
                Point3D p1 = pointByWord.get(normalizeWord(previous));
                Point3D p2 = pointByWord.get(normalizeWord(current));
                if (p1 != null && p2 != null) {
                    pathCluster.add(new ConnectionLeaf3D(p1, p2, MATH_PATH_COLOR, MATH_CONNECTION_WIDTH));
                }
            }
            previous = current;
        }

        if (previous != null && mathResultWord != null) {
            Point3D p1 = pointByWord.get(normalizeWord(previous));
            Point3D p2 = pointByWord.get(normalizeWord(mathResultWord));
            if (p1 != null && p2 != null) {
                pathCluster.add(new ConnectionLeaf3D(p1, p2, MATH_PATH_COLOR, MATH_CONNECTION_WIDTH));
            }
        }
    }

    private void addProbeConnections(
            CompositeCluster3D highlightCluster,
            Map<String, Point3D> pointByWord,
            WordNode probeSource,
            List<WordNode> probeNeighbors
    ) {
        if (probeSource == null || probeNeighbors == null || probeNeighbors.isEmpty()) {
            return;
        }

        Point3D sourcePoint = pointByWord.get(normalizeWord(probeSource));
        if (sourcePoint == null) {
            return;
        }

        for (WordNode neighbor : probeNeighbors) {
            Point3D neighborPoint = pointByWord.get(normalizeWord(neighbor));
            if (neighborPoint == null) {
                continue;
            }
            highlightCluster.add(new ConnectionLeaf3D(sourcePoint, neighborPoint, PROBE_CONNECTION_COLOR, PROBE_CONNECTION_WIDTH));
        }
    }

    /**
     * Adds persistent 3D text labels. All labels are now rendered in white for dark background contrast.
     */
    private void addTextLabels(
            CompositeCluster3D labelCluster,
            Map<String, Point3D> pointByWord,
            WordNode focusedWord,
            WordNode probeSource,
            List<WordNode> probeNeighbors,
            List<WordNode> mathPathWords,
            WordNode mathResultWord
    ) {
        // Updated to explicitly use Color.WHITE for all label instances
        if (focusedWord != null && focusedWord.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(focusedWord));
            if (point != null) {
                labelCluster.add(new TextLabel3D(focusedWord.getWord(), point, Color.WHITE));
            }
        }

        if (semanticPoleA != null && semanticPoleA.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(semanticPoleA));
            if (point != null) {
                labelCluster.add(new TextLabel3D(semanticPoleA.getWord(), point, Color.WHITE));
            }
        }

        if (semanticPoleB != null && semanticPoleB.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(semanticPoleB));
            if (point != null) {
                labelCluster.add(new TextLabel3D(semanticPoleB.getWord(), point, Color.WHITE));
            }
        }

        if (probeSource != null && probeSource.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(probeSource));
            if (point != null) {
                labelCluster.add(new TextLabel3D(probeSource.getWord(), point, Color.WHITE));
            }
        }

        if (probeNeighbors != null && !probeNeighbors.isEmpty()) {
            for (WordNode neighbor : probeNeighbors) {
                if (neighbor != null && neighbor.getWord() != null) {
                    Point3D point = pointByWord.get(normalizeWord(neighbor));
                    if (point != null) {
                        labelCluster.add(new TextLabel3D(neighbor.getWord(), point, Color.WHITE));
                    }
                }
            }
        }

        if (mathPathWords != null && !mathPathWords.isEmpty()) {
            for (WordNode pathWord : mathPathWords) {
                if (pathWord != null && pathWord.getWord() != null) {
                    Point3D point = pointByWord.get(normalizeWord(pathWord));
                    if (point != null) {
                        labelCluster.add(new TextLabel3D(pathWord.getWord(), point, Color.WHITE));
                    }
                }
            }
        }

        if (mathResultWord != null && mathResultWord.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(mathResultWord));
            if (point != null) {
                labelCluster.add(new TextLabel3D(mathResultWord.getWord(), point, Color.WHITE));
            }
        }
    }

    private Marker3DStyle resolveMarkerStyle(
            WordNode wordNode,
            WordNode focusedWord,
            WordNode probeSource,
            List<WordNode> probeNeighbors,
            List<WordNode> mathPathWords,
            WordNode mathResultWord,
            Set<WordNode> selectedGroup
    ) {
        if (semanticPoleA != null && semanticPoleA.isSameWord(wordNode)) {
            return new Marker3DStyle(SEMANTIC_POLE_A_COLOR, SEMANTIC_POLE_POINT_RADIUS, true);
        }

        if (semanticPoleB != null && semanticPoleB.isSameWord(wordNode)) {
            return new Marker3DStyle(SEMANTIC_POLE_B_COLOR, SEMANTIC_POLE_POINT_RADIUS, true);
        }

        boolean isSelectedGroupWord = selectedGroup != null
                && selectedGroup.stream().anyMatch(groupNode -> groupNode != null && groupNode.isSameWord(wordNode));
        if (isSelectedGroupWord) {
            return new Marker3DStyle(GROUP_COLOR, GROUP_POINT_RADIUS, true);
        }

        boolean isMathPathWord = mathPathWords != null
                && mathPathWords.stream().anyMatch(pathWord -> pathWord != null && pathWord.isSameWord(wordNode));
        boolean isMathResultWord = mathResultWord != null && mathResultWord.isSameWord(wordNode);
        if (isMathResultWord) {
            return new Marker3DStyle(MATH_RESULT_COLOR, MATH_RESULT_POINT_RADIUS, true);
        }
        if (isMathPathWord) {
            return new Marker3DStyle(MATH_PATH_COLOR, MATH_PATH_POINT_RADIUS, true);
        }

        boolean isProbeSourceWord = probeSource != null && probeSource.isSameWord(wordNode);
        boolean isProbeNeighborWord = probeNeighbors != null
                && probeNeighbors.stream().anyMatch(neighbor -> neighbor != null && neighbor.isSameWord(wordNode));
        if (isProbeSourceWord) {
            return new Marker3DStyle(PROBE_SOURCE_COLOR, PROBE_SOURCE_POINT_RADIUS, true);
        }
        if (isProbeNeighborWord) {
            return new Marker3DStyle(PROBE_NEIGHBOR_COLOR, PROBE_NEIGHBOR_POINT_RADIUS, true);
        }

        if (focusedWord != null && focusedWord.isSameWord(wordNode)) {
            return new Marker3DStyle(FOCUSED_COLOR, FOCUSED_POINT_RADIUS, true);
        }

        return new Marker3DStyle(BASE_COLOR, BASE_POINT_RADIUS, false);
    }

    private int[] normalizeAxes(int[] selectedAxes) {
        if (selectedAxes == null || selectedAxes.length < 3) {
            return new int[]{0, 1, 2};
        }

        return new int[]{
                Math.max(0, selectedAxes[0]),
                Math.max(0, selectedAxes[1]),
                Math.max(0, selectedAxes[2])
        };
    }

    private String normalizeWord(WordNode node) {
        if (node == null || node.getWord() == null) {
            return "";
        }
        return node.getWord().toLowerCase(Locale.ROOT);
    }

    public record Marker3DStyle(Color color, double radius, boolean highlighted) {
    }
}