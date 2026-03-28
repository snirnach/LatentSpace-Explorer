package ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles view-level interactions such as view switching, zooming, panning, and PCA axis updates.
 */
public class ViewController implements IPcaObserver {

    private static final double THREE_D_ZOOM_STEP = 120.0;

    private final ControlPanelView controlPanelView;
    private final PcaStateSubject pcaStateSubject;
    private final Consumer<Boolean> onToggle3D;

    private Graph2DView graph2DView;
    private Scene3DManager scene3DManager;
    private Supplier<Boolean> isThreeDModeActiveSupplier;
    private Runnable onAxesChangedCallback;

    public ViewController(
            ControlPanelView controlPanelView,
            PcaStateSubject pcaStateSubject,
            Consumer<Boolean> onToggle3D
    ) {
        this.controlPanelView = controlPanelView;
        this.pcaStateSubject = pcaStateSubject;
        this.onToggle3D = onToggle3D;

        setupListeners();
        this.pcaStateSubject.attach(this);
    }

    public void configureViewAccess(
            Graph2DView graph2DView,
            Scene3DManager scene3DManager,
            Supplier<Boolean> isThreeDModeActiveSupplier,
            Runnable onAxesChangedCallback
    ) {
        this.graph2DView = graph2DView;
        this.scene3DManager = scene3DManager;
        this.isThreeDModeActiveSupplier = isThreeDModeActiveSupplier;
        this.onAxesChangedCallback = onAxesChangedCallback;
    }

    private void setupListeners() {
        controlPanelView.setOnToggleViewAction(this::handleToggleAction);
        controlPanelView.setOnZoomInAction(this::handleZoomInAction);
        controlPanelView.setOnZoomOutAction(this::handleZoomOutAction);
        controlPanelView.setOnPcaAxesChanged(pcaStateSubject::updateAxes);
    }

    private void handleToggleAction(ActionEvent event) {
        onToggle3D.accept(controlPanelView.isToggle3DSelected());
    }

    private void handleZoomInAction(ActionEvent event) {
        if (isThreeDModeActive()) {
            if (scene3DManager != null) {
                scene3DManager.zoom(THREE_D_ZOOM_STEP);
            }
            return;
        }

        if (graph2DView != null) {
            graph2DView.zoomIn();
        }
    }

    private void handleZoomOutAction(ActionEvent event) {
        if (isThreeDModeActive()) {
            if (scene3DManager != null) {
                scene3DManager.zoom(-THREE_D_ZOOM_STEP);
            }
            return;
        }

        if (graph2DView != null) {
            graph2DView.zoomOut();
        }
    }

    @Override
    public void onPcaAxesChanged(int pcaX, int pcaY, int pcaZ) {
        // Main controller callback refreshes active view data for the selected PCA axes.
        if (onAxesChangedCallback != null) {
            onAxesChangedCallback.run();
        }
    }

    /**
     * Attaches 3D-only panning and zoom handlers to the active center node.
     */
    public void attachCenterInteractionHandlers(Node centerNode) {
        if (centerNode == null) {
            return;
        }

        if (!isThreeDModeActive()) {
            centerNode.setOnMousePressed(null);
            centerNode.setOnMouseDragged(null);
            centerNode.setOnScroll(null);
            return;
        }

        centerNode.setOnMousePressed(this::handlePanMousePressed);
        centerNode.setOnMouseDragged(this::handlePanMouseDragged);
        centerNode.setOnScroll(this::handleThreeDScroll);
    }

    private void handlePanMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !isThreeDModeActive() || scene3DManager == null) {
            return;
        }

        // Keep 3D drag anchor stable by always recording scene coordinates.
        scene3DManager.setDragAnchor(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void handlePanMouseDragged(MouseEvent event) {
        if (!event.isPrimaryButtonDown() || !isThreeDModeActive() || scene3DManager == null) {
            return;
        }

        scene3DManager.rotateFromScenePosition(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void handleThreeDScroll(ScrollEvent event) {
        if (!isThreeDModeActive() || scene3DManager == null || event.getDeltaY() == 0) {
            return;
        }

        scene3DManager.zoom(event.getDeltaY() > 0 ? THREE_D_ZOOM_STEP : -THREE_D_ZOOM_STEP);
        event.consume();
    }

    private boolean isThreeDModeActive() {
        return isThreeDModeActiveSupplier != null && Boolean.TRUE.equals(isThreeDModeActiveSupplier.get());
    }
}


