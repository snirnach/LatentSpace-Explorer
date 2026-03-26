package ui;

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
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import model.WordNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages the 3D visualization view and implements the shared visualization contract.
 */
public class Scene3DManager implements IVisualizationView {

    private static final double THREE_D_SCALE_FACTOR = 160.0;
    private static final double THREE_D_SPHERE_RADIUS = 2.5;
    private static final int THREE_D_SPHERE_DIVISIONS = 16;
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
    private WordNode selectedWord;

    public Scene3DManager() {
        this.rootPane = new Pane();
        this.pointClickListener = ignoredWord -> {
        };
        this.currentWords = List.of();
        this.currentAxes = new int[]{0, 1, 2};

        Group initialRoot = new Group();
        this.subScene = new SubScene(initialRoot, 1, 1, true, SceneAntialiasing.BALANCED);
        this.subScene.widthProperty().bind(rootPane.widthProperty());
        this.subScene.heightProperty().bind(rootPane.heightProperty());
        this.subScene.setFill(Color.web("#111111"));
        // Capture anchor coordinates at SubScene level so clicks on spheres cannot desync drag state.
        this.subScene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleSubSceneMousePressed);

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
     * Focuses the 3D view on the provided word by updating the selected marker state.
     */
    @Override
    public void focusOnWord(WordNode word) {
        this.selectedWord = word;
        rebuildPointCloud();
    }

    /**
     * Displays the nearest neighbors probe visualization for a source word
     * with connections/highlights to its neighbors.
     * Note: 3D implementation placeholder. Can be extended to visualize
     * neighbor connections with lines or highlights in 3D space.
     */
    @Override
    public void showNearestNeighbors(WordNode source, List<WordNode> neighbors) {
        // 3D probe visualization can be implemented here (e.g., draw connecting lines or highlight neighbors)
        // For now, this is a placeholder that accepts the probe data without visualization
    }

    @Override
    public void showMathPath(List<WordNode> equationWords, WordNode closestResult) {
        // 3D math-path visualization is not implemented in this phase.
    }

    @Override
    public Set<WordNode> getSelectedGroup() {
        return Set.of();
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

        Group pointCloudRoot = new Group();
        rotateX = new Rotate(0, Rotate.X_AXIS);
        rotateY = new Rotate(0, Rotate.Y_AXIS);

        PhongMaterial defaultMaterial = new PhongMaterial(Color.web("#1D6FEA"));
        PhongMaterial selectedMaterial = new PhongMaterial(Color.web("#E74C3C"));

        double totalX = 0.0;
        double totalY = 0.0;
        double totalZ = 0.0;
        int renderedPointCount = 0;

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

            // Use lower sphere divisions to reduce geometry cost for thousands of points.
            Sphere point = new Sphere(THREE_D_SPHERE_RADIUS, THREE_D_SPHERE_DIVISIONS);
            point.setFocusTraversable(false);
            point.setMaterial(isSameWord(selectedWord, wordNode) ? selectedMaterial : defaultMaterial);
            point.setTranslateX(pointX);
            point.setTranslateY(pointY);
            point.setTranslateZ(pointZ);
            Tooltip.install(point, new Tooltip(getDisplayWord(wordNode)));

            // Keep press/drag events bubbling so camera anchors stay synchronized.
            point.setOnMousePressed(event -> setDragAnchor(event.getSceneX(), event.getSceneY()));
            point.setOnMouseClicked(event -> {
                // Consume selection clicks so parent containers do not process micro-drags from this click.
                event.consume();
                pointClickListener.accept(wordNode);
            });

            pointCloudRoot.getChildren().add(point);
            totalX += pointX;
            totalY += pointY;
            totalZ += pointZ;
            renderedPointCount++;
        }

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

    private void handleSubSceneMousePressed(MouseEvent event) {
        // Always refresh anchors so subsequent drag deltas are stable.
        anchorX = event.getSceneX();
        anchorY = event.getSceneY();
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
}
