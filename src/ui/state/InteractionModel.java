package ui.state;

import model.WordNode;
import model.distance.DistanceStrategy;

import java.util.List;

/**
 * Stores interaction state and encapsulates point-selection measurement flow.
 */
public class InteractionModel {

    private WordNode sourceNode;
    private boolean isMeasuringMode;
    private WordNode activeTargetNode;
    private List<WordNode> activeNeighborNodes;

    public InteractionModel() {
        this.activeNeighborNodes = List.of();
    }

    /**
     * Handles click behavior for both source selection and second-point distance measurement.
     */
    public InteractionResult handlePointClick(WordNode clickedWord, DistanceStrategy currentStrategy) {
        if (clickedWord == null) {
            return InteractionResult.error("No point selected.");
        }

        // Normal click path: select a source point.
        if (!isMeasuringMode) {
            sourceNode = clickedWord;
            return InteractionResult.sourceSelected(clickedWord);
        }

        // Measuring path: second click measures from the stored source point.
        if (sourceNode == null) {
            isMeasuringMode = false;
            return InteractionResult.error("Select a source point first.");
        }

        WordNode firstNode = sourceNode;
        double distance = calculateDistance(firstNode, clickedWord, currentStrategy);

        // Reset the measuring workflow after the second click.
        isMeasuringMode = false;
        sourceNode = null;

        if (Double.isNaN(distance)) {
            return InteractionResult.error("Distance: N/A (incompatible vectors)");
        }

        return InteractionResult.distanceMeasured(firstNode, clickedWord, distance);
    }

    public InteractionResult beginMeasurement() {
        if (sourceNode == null) {
            return InteractionResult.error("Select a source point first.");
        }

        isMeasuringMode = true;
        return InteractionResult.awaitingSecondPoint();
    }

    public void clearMeasurementState() {
        sourceNode = null;
        isMeasuringMode = false;
    }

    public boolean isMeasureButtonEnabled() {
        return sourceNode != null && !isMeasuringMode;
    }

    private double calculateDistance(WordNode firstWord, WordNode secondWord, DistanceStrategy strategy) {
        if (firstWord == null || secondWord == null || strategy == null) {
            return Double.NaN;
        }

        double[] firstVector = firstWord.getOriginalVector();
        double[] secondVector = secondWord.getOriginalVector();
        if (firstVector == null || secondVector == null || firstVector.length != secondVector.length) {
            return Double.NaN;
        }

        return strategy.calculateDistance(firstVector, secondVector);
    }

    public WordNode getSourceNode() {
        return sourceNode;
    }


    public boolean isMeasuringMode() {
        return isMeasuringMode;
    }

    public void setMeasuringMode(boolean measuringMode) {
        isMeasuringMode = measuringMode;
    }

    public WordNode getActiveTargetNode() {
        return activeTargetNode;
    }

    public void setActiveTargetNode(WordNode activeTargetNode) {
        this.activeTargetNode = activeTargetNode;
    }

    public List<WordNode> getActiveNeighborNodes() {
        return activeNeighborNodes;
    }

    public void setActiveNeighborNodes(List<WordNode> activeNeighborNodes) {
        this.activeNeighborNodes = activeNeighborNodes == null ? List.of() : List.copyOf(activeNeighborNodes);
    }

    public static final class InteractionResult {
        private final ResultType type;
        private final String message;
        private final WordNode sourceNode;
        private final WordNode targetNode;
        private final Double distance;

        private InteractionResult(ResultType type, String message, WordNode sourceNode, WordNode targetNode, Double distance) {
            this.type = type;
            this.message = message;
            this.sourceNode = sourceNode;
            this.targetNode = targetNode;
            this.distance = distance;
        }

        public static InteractionResult sourceSelected(WordNode selectedNode) {
            return new InteractionResult(ResultType.SOURCE_SELECTED, null, selectedNode, null, null);
        }

        public static InteractionResult awaitingSecondPoint() {
            return new InteractionResult(ResultType.AWAITING_SECOND_POINT, "Select a second point to measure...", null, null, null);
        }

        public static InteractionResult distanceMeasured(WordNode sourceNode, WordNode targetNode, double distance) {
            return new InteractionResult(ResultType.DISTANCE_MEASURED, null, sourceNode, targetNode, distance);
        }

        public static InteractionResult error(String message) {
            return new InteractionResult(ResultType.ERROR, message, null, null, null);
        }

        public ResultType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public WordNode getSourceNode() {
            return sourceNode;
        }

        public WordNode getTargetNode() {
            return targetNode;
        }

        public Double getDistance() {
            return distance;
        }
    }

    public enum ResultType {
        SOURCE_SELECTED,
        AWAITING_SECOND_POINT,
        DISTANCE_MEASURED,
        ERROR
    }
}

