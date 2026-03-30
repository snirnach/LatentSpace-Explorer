package ui.threed;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Leaf component for rendering a cylindrical connection between two points.
 */
public class ConnectionLeaf3D implements IComponent3D {
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

