package ui.view;

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
    private boolean isShiftClickSequence;

    private List<WordNode> currentWords;
    private int[] currentAxes;

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
        this.isShiftClickSequence = false;

        Group initialRoot = new Group();
        this.subScene = new SubScene(initialRoot, 1, 1, true, SceneAntialiasing.BALANCED);
        this.subScene.widthProperty().bind(rootPane.widthProperty());
        this.subScene.heightProperty().bind(rootPane.heightProperty());
        this.subScene.setFill(Color.web("#111111"));

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

    @Override
    public void focusOnWord(WordNode word) {
        this.selectedWord = word;
        rebuildPointCloud();
    }

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

    public void zoom(double delta) {
        threeDCameraZ = Math.max(THREE_D_FARTHEST_Z, Math.min(THREE_D_NEAREST_Z, threeDCameraZ + delta));
        threeDCamera.setTranslateZ(threeDCameraZ);
    }

    public void rotate(double deltaX, double deltaY) {
        rotateX.setAngle(rotateX.getAngle() - deltaY * 0.5);
        rotateY.setAngle(rotateY.getAngle() + deltaX * 0.5);
    }

    public void setDragAnchor(double sceneX, double sceneY) {
        this.anchorX = sceneX;
        this.anchorY = sceneY;
    }

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
        pressX = event.getSceneX();
        pressY = event.getSceneY();
        anchorX = event.getSceneX();
        anchorY = event.getSceneY();
        isDragging = false;

        // Detect and isolate shift-click operations from regular single clicks.
        if (event.isShiftDown()) {
            isShiftClickSequence = true;
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

        isShiftClickSequence = false;
    }

    private void handleSubSceneMouseDragged(MouseEvent event) {
        if (!event.isPrimaryButtonDown()) {
            return;
        }

        double deltaX = event.getSceneX() - pressX;
        double deltaY = event.getSceneY() - pressY;
        double distanceMoved = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        if (distanceMoved >= DRAG_THRESHOLD_PIXELS) {
            isDragging = true;
        }

        if (isDragging) {
            rotateFromScenePosition(event.getSceneX(), event.getSceneY());
            event.consume();
        }
    }

    private void handleSubSceneMouseReleased(MouseEvent event) {
        // Prevent shift-click mouse release from triggering a standard single click.
        if (isShiftClickSequence) {
            isShiftClickSequence = false;
            isDragging = false;
            return;
        }

        double deltaX = event.getSceneX() - pressX;
        double deltaY = event.getSceneY() - pressY;
        double distanceMoved = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        if (distanceMoved < DRAG_THRESHOLD_PIXELS) {
            if (event.getTarget() instanceof Sphere) {
                WordNode clickedWord = findWordNodeFromSphere((Sphere) event.getTarget());
                if (clickedWord != null) {
                    selectedGroup.clear();
                    focusOnWord(clickedWord);
                    if (pointClickListener != null) {
                        pointClickListener.accept(clickedWord);
                    }
                    event.consume();
                    return;
                }
            }

            clearVisualSelection();
            if (pointClickListener != null) {
                pointClickListener.accept(null);
            }
            event.consume();
        }

        isDragging = false;
    }

    private WordNode findWordNodeFromSphere(Sphere sphere) {
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
        pressX = event.getSceneX();
        pressY = event.getSceneY();
        isDragging = false;

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