package ui.threed;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Leaf component for rendering a persistent 3D text label.
 * Labels appear offset above word points so they are visible without obscuring the points.
 */
public class TextLabel3D implements IComponent3D {
    private final String wordText;
    private final Point3D wordPoint;
    private final Color color;

    public TextLabel3D(String wordText, Point3D wordPoint, Color color) {
        this.wordText = wordText;
        this.wordPoint = wordPoint;
        this.color = color;
    }

    @Override
    public void attachTo(Group parent) {
        Text label = new Text(wordText);
        label.setFont(Font.font("Arial", 12));
        label.setFill(color);
        label.setStyle("-fx-font-weight: bold;");

        double offsetX = 15;
        double offsetY = -15;
        label.setTranslateX(wordPoint.getX() + offsetX);
        label.setTranslateY(wordPoint.getY() + offsetY);
        label.setTranslateZ(wordPoint.getZ());

        parent.getChildren().add(label);
    }
}