package ui.threed;

import javafx.scene.Group;

/**
 * Composite contract for all 3D scene components.
 */
public interface IComponent3D {
    void attachTo(Group parent);
}

