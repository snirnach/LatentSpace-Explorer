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
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import model.WordNode;

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

    private static final Color BASE_COLOR = Color.BLACK;
    private static final Color MATH_PATH_COLOR = Color.MAGENTA;
    private static final Color MATH_RESULT_COLOR = Color.DODGERBLUE;
    private static final Color GROUP_COLOR = Color.PURPLE;
    private static final Color PROBE_SOURCE_COLOR = Color.ORANGE;
    private static final Color PROBE_NEIGHBOR_COLOR = Color.GREEN;
    private static final Color FOCUSED_COLOR = Color.RED;
    private static final Color PROBE_CONNECTION_COLOR = Color.DARKGRAY;

    private static final double THREE_D_NEAREST_Z = -200.0;
    private static final double THREE_D_FARTHEST_Z = -100000.0;
    private static final double THREE_D_DEFAULT_CAMERA_Z = -2500.0;

    private final Pane rootPane;
    private final SubScene subScene;
    private final PerspectiveCamera threeDCamera;

    private Consumer<WordNode> pointClickListener;

    private double threeDCameraZ = THREE_D_DEFAULT_CAMERA_Z;
    private double anchorX;
    private double anchorY;
    private Rotate rotateX;
    private Rotate rotateY;

    private List<WordNode> currentWords;
    private int[] currentAxes;

    // Shared state with 2D view behavior.
    private WordNode selectedWord;
    private WordNode probeSource;
    private List<WordNode> probeNeighbors;
    private List<WordNode> mathPathWords;
    private WordNode mathResultWord;
    private Set<WordNode> selectedGroup;

    private boolean lastPressedWithShift;

    public Scene3DManager() {
        this.rootPane = new Pane();
        this.pointClickListener = ignoredWord -> {
        };
        this.currentWords = List.of();
        this.currentAxes = new int[]{0, 1, 2};
        this.probeNeighbors = new ArrayList<>();
        this.selectedGroup = new HashSet<>();

        Group initialRoot = new Group();
        this.subScene = new SubScene(initialRoot, 1, 1, true, SceneAntialiasing.BALANCED);
        this.subScene.widthProperty().bind(rootPane.widthProperty());
        this.subScene.heightProperty().bind(rootPane.heightProperty());
        this.subScene.setFill(Color.web("#111111"));

        // Capture anchor coordinates at SubScene level so clicks on spheres cannot desync drag state.
        this.subScene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleSubSceneMousePressed);
        this.subScene.setOnMouseClicked(event -> {
            if (!(event.getTarget() instanceof Sphere) && pointClickListener != null) {
                pointClickListener.accept(null);
            }
        });

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

    @Override
    public void showNearestNeighbors(WordNode source, List<WordNode> neighbors) {
        this.probeSource = source;
        this.probeNeighbors = neighbors == null ? new ArrayList<>() : new ArrayList<>(neighbors);
        this.selectedWord = null;
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
        if (rotateX == null || rotateY == null) {
            return;
        }

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

        rootCluster.add(baseWordsCluster);
        rootCluster.add(pathCluster);
        rootCluster.add(highlightCluster);

        rotateX = new Rotate(0, Rotate.X_AXIS);
        rotateY = new Rotate(0, Rotate.Y_AXIS);

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
                    this::handleWordLeafClicked,
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

        Group pointCloudRoot = new Group();
        rootCluster.attachTo(pointCloudRoot);

        if (renderedPointCount > 0) {
            double averageX = totalX / renderedPointCount;
            double averageY = totalY / renderedPointCount;
            double averageZ = totalZ / renderedPointCount;
            pointCloudRoot.getTransforms().addAll(
                    new Translate(-averageX, -averageY, -averageZ),
                    rotateX,
                    rotateY
            );
        } else {
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

    private void handleSubSceneMousePressed(MouseEvent event) {
        // Always refresh anchors so subsequent drag deltas are stable.
        anchorX = event.getSceneX();
        anchorY = event.getSceneY();
    }

    private void handleWordLeafPressed(MouseEvent event) {
        // Keep drag anchors synchronized even when press starts directly on a sphere.
        lastPressedWithShift = event.isShiftDown();
        setDragAnchor(event.getSceneX(), event.getSceneY());
    }

    private void handleWordLeafClicked(WordNode wordNode) {
        if (wordNode == null) {
            return;
        }

        if (lastPressedWithShift) {
            if (selectedGroup.stream().anyMatch(existing -> existing != null && existing.isSameWord(wordNode))) {
                selectedGroup.removeIf(existing -> existing != null && existing.isSameWord(wordNode));
            } else {
                selectedGroup.add(wordNode);
            }
            rebuildPointCloud();
            return;
        }

        selectedGroup.clear();
        pointClickListener.accept(wordNode);
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

    /**
     * Composite contract for all 3D scene components.
     */
    public interface IComponent3D {
        void attachTo(Group parent);
    }

    /**
     * Composite container that delegates attachment to child components.
     */
    public static class CompositeCluster3D implements IComponent3D {
        private final List<IComponent3D> children = new ArrayList<>();

        public void add(IComponent3D component) {
            if (component != null) {
                children.add(component);
            }
        }

        @Override
        public void attachTo(Group parent) {
            for (IComponent3D child : children) {
                child.attachTo(parent);
            }
        }
    }

    /**
     * Leaf component for rendering a single word point.
     */
    public static class WordLeaf3D implements IComponent3D {
        private final WordNode node;
        private final double x;
        private final double y;
        private final double z;
        private final Color color;
        private final double radius;
        private final Consumer<WordNode> onClick;
        private final Consumer<MouseEvent> onPress;

        public WordLeaf3D(
                WordNode node,
                double x,
                double y,
                double z,
                Color color,
                double radius,
                Consumer<WordNode> onClick,
                Consumer<MouseEvent> onPress
        ) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.radius = radius;
            this.onClick = onClick;
            this.onPress = onPress;
        }

        @Override
        public void attachTo(Group parent) {
            Sphere sphere = new Sphere(radius, THREE_D_SPHERE_DIVISIONS);
            sphere.setFocusTraversable(false);
            sphere.setMaterial(new PhongMaterial(color));
            sphere.setTranslateX(x);
            sphere.setTranslateY(y);
            sphere.setTranslateZ(z);
            Tooltip.install(sphere, new Tooltip(node == null ? "<unnamed>" : node.getWord()));

            // Keep press updates available for drag anchor synchronization.
            sphere.setOnMousePressed(event -> {
                if (onPress != null) {
                    onPress.accept(event);
                }
            });

            sphere.setOnMouseClicked(event -> {
                if (onClick != null) {
                    onClick.accept(node);
                }
                event.consume();
            });

            parent.getChildren().add(sphere);
        }
    }

    /**
     * Leaf component for rendering a cylindrical connection between two points.
     */
    public static class ConnectionLeaf3D implements IComponent3D {
        private final Point3D p1;
        private final Point3D p2;
        private final Color color;
        private final double width;

        public ConnectionLeaf3D(Point3D p1, Point3D p2, Color color, double width) {
            this.p1 = p1;
            this.p2 = p2;
            this.color = color;
            this.width = width;
        }

        @Override
        public void attachTo(Group parent) {
            if (p1 == null || p2 == null) {
                return;
            }

            Point3D diff = p2.subtract(p1);
            double distance = diff.magnitude();
            if (distance <= 1e-6) {
                return;
            }

            Cylinder cylinder = new Cylinder(width, distance);
            cylinder.setMaterial(new PhongMaterial(color));
            cylinder.setFocusTraversable(false);

            Point3D yAxis = new Point3D(0, 1, 0);
            Point3D midpoint = p1.midpoint(p2);
            Point3D axis = yAxis.crossProduct(diff);
            double dot = yAxis.normalize().dotProduct(diff.normalize());
            double clampedDot = Math.max(-1.0, Math.min(1.0, dot));
            double angle = Math.toDegrees(Math.acos(clampedDot));

            Translate moveToMidpoint = new Translate(midpoint.getX(), midpoint.getY(), midpoint.getZ());
            if (axis.magnitude() <= 1e-6) {
                axis = new Point3D(1, 0, 0);
            }
            Rotate rotateToVector = new Rotate(angle, axis);

            cylinder.getTransforms().addAll(moveToMidpoint, rotateToVector);
            parent.getChildren().add(cylinder);
        }
    }

    private record Marker3DStyle(Color color, double radius, boolean highlighted) {
    }
}
