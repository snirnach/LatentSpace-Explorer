package ui.threed;

import javafx.scene.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite container that delegates attachment to child components.
 */
public class CompositeCluster3D implements IComponent3D {
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

