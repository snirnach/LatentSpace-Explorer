package ui;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import model.WordNode;
import ui.threed.CompositeCluster3D;
import ui.threed.ConnectionLeaf3D;
import ui.threed.TextLabel3D;
import ui.threed.WordLeaf3D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages the 3D visualization view and implements the shared visualization contract.
 * This class uses a Composite structure to build the 3D point cloud and connections.
 */
public class Scene3DManager implements IVisualizationView {

    private static final double THREE_D_SCALE_FACTOR = 160.0;
    private static final int THREE_D_SPHERE_DIVISIONS = 16;

    private static final double BASE_POINT_RADIUS = 2.5;
    private static final double HIGHLIGHT_POINT_RADIUS = 3.5;
    private static final double GROUP_POINT_RADIUS = 4.5;
    private static final double MATH_PATH_POINT_RADIUS = 4.25;
    private static final double MATH_RESULT_POINT_RADIUS = 5.0;
    private static final double PROBE_SOURCE_POINT_RADIUS = 4.5;
    private static final double PROBE_NEIGHBOR_POINT_RADIUS = 3.5;
    private static final double FOCUSED_POINT_RADIUS = 4.5;

    private static final double PROBE_CONNECTION_WIDTH = 0.7;
    private static final double MATH_CONNECTION_WIDTH = 1.0;

    // Click-vs-drag threshold: movements below this distance are treated as clicks.
    // Prevents drag release from triggering empty-click selection clearing.
    private static final double DRAG_THRESHOLD_PIXELS = 2.0;

    // Base points use blue for clear contrast on the dark background.
    private static final Color BASE_COLOR = Color.DODGERBLUE;
    private static final Color MATH_PATH_COLOR = Color.MAGENTA;
    private static final Color MATH_RESULT_COLOR = Color.DODGERBLUE;
    private static final Color GROUP_COLOR = Color.PURPLE;
    private static final Color PROBE_SOURCE_COLOR = Color.ORANGE;
    private static final Color PROBE_NEIGHBOR_COLOR = Color.GREEN;
    private static final Color FOCUSED_COLOR = Color.RED;
    private static final Color PROBE_CONNECTION_COLOR = Color.DARKGRAY;

    private static final double THREE_D_NEAREST_Z = -200.0;
    private static final double THREE_D_FARTHEST_Z = -5000.0;
    private static final double THREE_D_DEFAULT_CAMERA_Z = -1200.0;

    private final Pane rootPane;
    private final SubScene subScene;
    private final PerspectiveCamera threeDCamera;

    private Consumer<WordNode> pointClickListener;

    private double threeDCameraZ = THREE_D_DEFAULT_CAMERA_Z;
    private double anchorX;
    private double anchorY;
    private double pressX;
    private double pressY;
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
    private boolean isDragging;

    private List<WordNode> currentWords;
    private int[] currentAxes;

    // Shared state with 2D view behavior.
    private WordNode selectedWord;
    private WordNode probeSource;
    private List<WordNode> probeNeighbors;
    private List<WordNode> mathPathWords;
    private WordNode mathResultWord;
    private Set<WordNode> selectedGroup;

    public Scene3DManager() {
        this.rootPane = new Pane();
        this.pointClickListener = ignoredWord -> {
        };
        this.currentWords = List.of();
        this.currentAxes = new int[]{0, 1, 2};
        this.probeNeighbors = new ArrayList<>();
        this.selectedGroup = new HashSet<>();
        this.pressX = 0;
        this.pressY = 0;
        this.isDragging = false;

        // Rotation transforms are final fields so camera angle persists across rebuilds.

        Group initialRoot = new Group();
        this.subScene = new SubScene(initialRoot, 1, 1, true, SceneAntialiasing.BALANCED);
        this.subScene.widthProperty().bind(rootPane.widthProperty());
        this.subScene.heightProperty().bind(rootPane.heightProperty());
        this.subScene.setFill(Color.web("#111111"));

        // Wire mouse interactions with proper click-vs-drag detection like the 2D view.
        this.subScene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleSubSceneMousePressed);
        this.subScene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleSubSceneMouseDragged);
        this.subScene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleSubSceneMouseReleased);

        this.threeDCamera = new PerspectiveCamera(true);
        this.threeDCamera.setNearClip(0.1);
        this.threeDCamera.setFarClip(100000);
        this.threeDCamera.setTranslateX(0);
        this.threeDCamera.setTranslateY(0);
        this.threeDCamera.setTranslateZ(threeDCameraZ);
        this.subScene.setCamera(threeDCamera);

        this.rootPane.getChildren().add(subScene);
    }

    @Override
    public Node getUIComponent() {
        return rootPane;
    }

    @Override
    public void updateData(Collection<WordNode> words, int[] selectedAxes) {
        this.currentWords = words == null ? List.of() : new ArrayList<>(words);
        this.currentAxes = normalizeAxes(selectedAxes);
        rebuildPointCloud();
    }

    @Override
    public void setOnWordClicked(Consumer<WordNode> listener) {
        this.pointClickListener = listener == null ? ignoredWord -> {
        } : listener;
    }

    /**
     * Focuses the 3D view on the provided word by updating marker style state.
     */
    @Override
    public void focusOnWord(WordNode word) {
        this.selectedWord = word;
        rebuildPointCloud();
    }

    /**
     * Clears every active visual selection layer in the 3D view.
     */
    @Override
    public void clearVisualSelection() {
        this.selectedWord = null;
        this.probeSource = null;
        this.probeNeighbors.clear();
        this.mathPathWords = null;
        this.mathResultWord = null;
        this.selectedGroup.clear();
        rebuildPointCloud();
    }

    @Override
    public void showNearestNeighbors(WordNode source, List<WordNode> neighbors) {
        this.probeSource = source;
        this.probeNeighbors = neighbors == null ? new ArrayList<>() : new ArrayList<>(neighbors);
        this.selectedWord = source;
        this.mathPathWords = null;
        this.mathResultWord = null;
        rebuildPointCloud();
    }

    @Override
    public void showMathPath(List<WordNode> equationWords, WordNode closestResult) {
        this.mathPathWords = equationWords == null ? null : new ArrayList<>(equationWords);
        this.mathResultWord = closestResult;
        this.selectedWord = null;
        this.probeSource = null;
        this.probeNeighbors.clear();
        rebuildPointCloud();
    }

    @Override
    public Set<WordNode> getSelectedGroup() {
        return new HashSet<>(selectedGroup);
    }

    /**
     * Sets the selected source node so it can be highlighted in the 3D view.
     */
    public void setSelectedWord(WordNode selectedWord) {
        this.selectedWord = selectedWord;
    }

    /**
     * Zooms the 3D scene by moving the camera along the Z axis.
     */
    public void zoom(double delta) {
        threeDCameraZ = Math.max(THREE_D_FARTHEST_Z, Math.min(THREE_D_NEAREST_Z, threeDCameraZ + delta));
        threeDCamera.setTranslateZ(threeDCameraZ);
    }

    /**
     * Rotates the 3D cloud around X and Y axes.
     */
    public void rotate(double deltaX, double deltaY) {
        rotateX.setAngle(rotateX.getAngle() - deltaY * 0.5);
        rotateY.setAngle(rotateY.getAngle() + deltaX * 0.5);
    }

    /**
     * Stores the latest drag anchor used by the controller-side drag workflow.
     */
    public void setDragAnchor(double sceneX, double sceneY) {
        this.anchorX = sceneX;
        this.anchorY = sceneY;
    }

    /**
     * Rotates using absolute scene coordinates and updates anchor state.
     */
    public void rotateFromScenePosition(double sceneX, double sceneY) {
        double deltaX = sceneX - anchorX;
        double deltaY = sceneY - anchorY;
        rotate(deltaX, deltaY);
        anchorX = sceneX;
        anchorY = sceneY;
    }

    private void rebuildPointCloud() {
        int xIndex = currentAxes[0];
        int yIndex = currentAxes[1];
        int zIndex = currentAxes[2];

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

        List<WordNode> nodesFor3D = currentWords.stream()
                .filter(node -> hasUsableThreeDimensionalPcaCoordinates(node, xIndex, yIndex, zIndex))
                .filter(node -> node.getWord() != null)
                .sorted(Comparator.comparing(WordNode::getWord, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (WordNode wordNode : nodesFor3D) {
            double[] vector = wordNode.getPcaVector();
            double pointX = vector[xIndex] * THREE_D_SCALE_FACTOR;
            double pointY = vector[yIndex] * THREE_D_SCALE_FACTOR;
            double pointZ = vector[zIndex] * THREE_D_SCALE_FACTOR;

            pointByWord.put(normalizeWord(wordNode), new Point3D(pointX, pointY, pointZ));

            Marker3DStyle style = resolveMarkerStyle(wordNode);
            WordLeaf3D leaf = new WordLeaf3D(
                    wordNode,
                    pointX,
                    pointY,
                    pointZ,
                    style.color(),
                    style.radius(),
                    null,
                    this::handleWordLeafPressed
            );

            if (style.highlighted()) {
                highlightCluster.add(leaf);
            } else {
                baseWordsCluster.add(leaf);
            }

            totalX += pointX;
            totalY += pointY;
            totalZ += pointZ;
            renderedPointCount++;
        }

        addMathPathConnections(pathCluster, pointByWord);
        addProbeConnections(highlightCluster, pointByWord);
        addTextLabels(labelCluster, pointByWord);

        Group pointCloudRoot = new Group();
        rootCluster.attachTo(pointCloudRoot);

        if (renderedPointCount > 0) {
            double centerX = totalX / renderedPointCount;
            double centerY = totalY / renderedPointCount;
            double centerZ = totalZ / renderedPointCount;

            Point3D focusPoint = null;
            if (selectedWord != null) {
                focusPoint = pointByWord.get(normalizeWord(selectedWord));
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

            // Keep transform order stable so orbit behavior remains centered on the chosen target.
            pointCloudRoot.getTransforms().clear();
            pointCloudRoot.getTransforms().addAll(
                    rotateX,
                    rotateY,
                    new Translate(-centerX, -centerY, -centerZ)
            );
        } else {
            pointCloudRoot.getTransforms().clear();
            pointCloudRoot.getTransforms().addAll(rotateX, rotateY);
        }

        subScene.setRoot(pointCloudRoot);
    }

    private void addMathPathConnections(CompositeCluster3D pathCluster, Map<String, Point3D> pointByWord) {
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

    private void addProbeConnections(CompositeCluster3D highlightCluster, Map<String, Point3D> pointByWord) {
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
     * Adds persistent 3D text labels for selected, probe, and math path words.
     * Labels appear offset from word points so they don't obscure the points themselves.
     */
    private void addTextLabels(CompositeCluster3D labelCluster, Map<String, Point3D> pointByWord) {
        // Label selected/focused word in RED
        if (selectedWord != null && selectedWord.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(selectedWord));
            if (point != null) {
                labelCluster.add(new TextLabel3D(selectedWord.getWord(), point, Color.RED));
            }
        }

        // Label probe source word in ORANGE
        if (probeSource != null && probeSource.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(probeSource));
            if (point != null) {
                labelCluster.add(new TextLabel3D(probeSource.getWord(), point, Color.ORANGE));
            }
        }

        // Label probe neighbor words in GREEN
        if (probeNeighbors != null && !probeNeighbors.isEmpty()) {
            for (WordNode neighbor : probeNeighbors) {
                if (neighbor != null && neighbor.getWord() != null) {
                    Point3D point = pointByWord.get(normalizeWord(neighbor));
                    if (point != null) {
                        labelCluster.add(new TextLabel3D(neighbor.getWord(), point, Color.GREEN));
                    }
                }
            }
        }

        // Label math path words in MAGENTA
        if (mathPathWords != null && !mathPathWords.isEmpty()) {
            for (WordNode pathWord : mathPathWords) {
                if (pathWord != null && pathWord.getWord() != null) {
                    Point3D point = pointByWord.get(normalizeWord(pathWord));
                    if (point != null) {
                        labelCluster.add(new TextLabel3D(pathWord.getWord(), point, Color.MAGENTA));
                    }
                }
            }
        }

        // Label math result word in DODGERBLUE
        if (mathResultWord != null && mathResultWord.getWord() != null) {
            Point3D point = pointByWord.get(normalizeWord(mathResultWord));
            if (point != null) {
                labelCluster.add(new TextLabel3D(mathResultWord.getWord(), point, Color.DODGERBLUE));
            }
        }
    }

    private void handleSubSceneMousePressed(MouseEvent event) {
        // Store press anchor in scene coordinates for robust click-vs-drag detection.
        pressX = event.getSceneX();
        pressY = event.getSceneY();

        // Initialize drag anchor for smooth rotation deltas.
        anchorX = event.getSceneX();
        anchorY = event.getSceneY();
        isDragging = false;

        // Handle shift-click for subspace group membership (toggle immediately).
        if (event.isShiftDown()) {
            if (event.getTarget() instanceof Sphere) {
                WordNode clickedWord = findWordNodeFromSphere((Sphere) event.getTarget());
                if (clickedWord != null) {
                    if (selectedGroup.stream().anyMatch(node -> node != null && node.isSameWord(clickedWord))) {
                        selectedGroup.removeIf(node -> node != null && node.isSameWord(clickedWord));
                    } else {
                        selectedGroup.add(clickedWord);
                    }
                    rebuildPointCloud();
                    isDragging = true;
                    event.consume();
                    return;
                }
            }
        }
    }

    private void handleSubSceneMouseDragged(MouseEvent event) {
        if (!event.isPrimaryButtonDown()) {
            return;
        }

        // Calculate movement from press point to current position.
        double deltaX = event.getSceneX() - pressX;
        double deltaY = event.getSceneY() - pressY;
        double distanceMoved = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        // If movement exceeds threshold, it's a drag rotation, not a click.
        if (distanceMoved >= DRAG_THRESHOLD_PIXELS) {
            isDragging = true;
        }

        // Only apply rotation if we've exceeded the drag threshold.
        if (isDragging) {
            rotateFromScenePosition(event.getSceneX(), event.getSceneY());
            event.consume();
        }
    }

    private void handleSubSceneMouseReleased(MouseEvent event) {
        // Calculate total movement from press anchor to release position.
        double deltaX = event.getSceneX() - pressX;
        double deltaY = event.getSceneY() - pressY;
        double distanceMoved = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        // Only process selection if the distance is below the drag threshold.
        // This prevents drag release from being treated as empty click.
        if (distanceMoved < DRAG_THRESHOLD_PIXELS) {
            if (event.getTarget() instanceof Sphere) {
                WordNode clickedWord = findWordNodeFromSphere((Sphere) event.getTarget());
                if (clickedWord != null) {
                    // A regular click starts standard probe selection and clears any existing subspace group.
                    selectedGroup.clear();
                    focusOnWord(clickedWord);
                    if (pointClickListener != null) {
                        pointClickListener.accept(clickedWord);
                    }
                    event.consume();
                    return;
                }
            }

            // Click on empty area: clear selection.
            clearVisualSelection();
            if (pointClickListener != null) {
                pointClickListener.accept(null);
            }
            event.consume();
        }

        // Reset drag state for next interaction.
        isDragging = false;
    }

    /**
     * Extracts the WordNode associated with a Sphere by searching the current word list.
     * Returns null if the sphere does not correspond to any word in the visualization.
     */
    private WordNode findWordNodeFromSphere(Sphere sphere) {
        // The sphere's position in 3D space uniquely identifies which word it represents.
        // We search the current words list for a match based on position and color.
        // For now, we use a heuristic: find word at closest distance to sphere position.
        double sphereX = sphere.getTranslateX();
        double sphereY = sphere.getTranslateY();
        double sphereZ = sphere.getTranslateZ();

        double minDistance = Double.MAX_VALUE;
        WordNode closestWord = null;

        for (WordNode word : currentWords) {
            if (!hasUsableThreeDimensionalPcaCoordinates(word, currentAxes[0], currentAxes[1], currentAxes[2])) {
                continue;
            }

            double[] vector = word.getPcaVector();
            double wordX = vector[currentAxes[0]] * THREE_D_SCALE_FACTOR;
            double wordY = vector[currentAxes[1]] * THREE_D_SCALE_FACTOR;
            double wordZ = vector[currentAxes[2]] * THREE_D_SCALE_FACTOR;

            double distance = Math.sqrt(
                    Math.pow(wordX - sphereX, 2) +
                            Math.pow(wordY - sphereY, 2) +
                            Math.pow(wordZ - sphereZ, 2)
            );

            if (distance < minDistance) {
                minDistance = distance;
                closestWord = word;
            }
        }

        return closestWord;
    }

    private void handleWordLeafPressed(MouseEvent event) {
        // Store press position for click-vs-drag detection.
        pressX = event.getSceneX();
        pressY = event.getSceneY();
        isDragging = false;

        // Store drag anchor for rotation updates.
        anchorX = event.getSceneX();
        anchorY = event.getSceneY();
        event.consume();
    }

    private Marker3DStyle resolveMarkerStyle(WordNode wordNode) {
        boolean isSelectedGroupWord = selectedGroup.stream()
                .anyMatch(groupNode -> groupNode != null && groupNode.isSameWord(wordNode));
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

        boolean isProbeSource = probeSource != null && probeSource.isSameWord(wordNode);
        boolean isProbeNeighbor = probeNeighbors != null
                && probeNeighbors.stream().anyMatch(neighbor -> neighbor != null && neighbor.isSameWord(wordNode));
        if (isProbeSource) {
            return new Marker3DStyle(PROBE_SOURCE_COLOR, PROBE_SOURCE_POINT_RADIUS, true);
        }
        if (isProbeNeighbor) {
            return new Marker3DStyle(PROBE_NEIGHBOR_COLOR, PROBE_NEIGHBOR_POINT_RADIUS, true);
        }

        if (selectedWord != null && selectedWord.isSameWord(wordNode)) {
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

    private String normalizeWord(WordNode node) {
        if (node == null || node.getWord() == null) {
            return "";
        }
        return node.getWord().toLowerCase(Locale.ROOT);
    }

    private String getDisplayWord(WordNode wordNode) {
        if (wordNode == null || wordNode.getWord() == null || wordNode.getWord().isBlank()) {
            return "<unnamed>";
        }

        return wordNode.getWord();
    }


    private record Marker3DStyle(Color color, double radius, boolean highlighted) {
    }
}
