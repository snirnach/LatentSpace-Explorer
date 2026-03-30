package ui;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import model.WordNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages the 3D visualization view and implements the shared visualization contract.
 * This class uses a Composite structure to build the 3D point cloud and connections.
 */
public class Scene3DManager implements IVisualizationView {

    // Click-vs-drag threshold: movements below this distance are treated as clicks.
    // Prevents drag release from triggering empty-click selection clearing.
    private static final double DRAG_THRESHOLD_PIXELS = 2.0;

    private static final double THREE_D_NEAREST_Z = -200.0;
    private static final double THREE_D_FARTHEST_Z = -5000.0;
    private static final double THREE_D_DEFAULT_CAMERA_Z = -1200.0;

    private final Pane rootPane;
    private final SubScene subScene;
    private final PerspectiveCamera threeDCamera;
    private final Scene3DRenderer renderer = new Scene3DRenderer();

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
    private WordNode semanticPoleA;
    private WordNode semanticPoleB;
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

    @Override
    public void setSemanticScores(List<Map.Entry<String, Double>> projections) {
        renderer.setSemanticScores(projections);
    }

    @Override
    public void setSemanticPoles(WordNode poleA, WordNode poleB) {
        this.semanticPoleA = poleA;
        this.semanticPoleB = poleB;
        renderer.setSemanticPoles(poleA, poleB);
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
        Group pointCloudRoot = renderer.buildPointCloudGroup(
                currentWords,
                currentAxes,
                selectedWord,
                probeSource,
                probeNeighbors,
                mathPathWords,
                mathResultWord,
                selectedGroup,
                this::handleWordLeafPressed
        );

        pointCloudRoot.getTransforms().clear();
        if (renderer.hasRenderablePoints()) {
            Point3D center = renderer.getLastComputedCenter();
            pointCloudRoot.getTransforms().addAll(
                    rotateX,
                    rotateY,
                    new Translate(-center.getX(), -center.getY(), -center.getZ())
            );
        } else {
            pointCloudRoot.getTransforms().addAll(rotateX, rotateY);
        }

        subScene.setRoot(pointCloudRoot);
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
            if (word == null) {
                continue;
            }

            Point3D wordPoint = renderer.projectWordPoint(word, currentAxes);
            if (wordPoint == null) {
                continue;
            }

            double distance = Math.sqrt(
                    Math.pow(wordPoint.getX() - sphereX, 2) +
                            Math.pow(wordPoint.getY() - sphereY, 2) +
                            Math.pow(wordPoint.getZ() - sphereZ, 2)
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
}
