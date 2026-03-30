package ui.threed;

import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import model.WordNode;

import java.util.function.Consumer;

/**
 * Leaf component for rendering a single word point.
 */
public class WordLeaf3D implements IComponent3D {
    private static final int DEFAULT_SPHERE_DIVISIONS = 16;

    private final WordNode node;
    private final double x;
    private final double y;
    private final double z;
    private final Color color;
    private final double radius;
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
        this.onPress = onPress;
    }

    @Override
    public void attachTo(Group parent) {
        Sphere sphere = new Sphere(radius, DEFAULT_SPHERE_DIVISIONS);
        sphere.setFocusTraversable(false);

        // Keep material simple and predictable for the restored dark-blue theme.
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

        parent.getChildren().add(sphere);
    }
}

