package model;

public class WordNode {

    private String word;

    private double[] originalVector;

    private double[] pcaVector;

    // Constructor
    public WordNode(String word, double[] originalVector, double[] pcaVector) {
        this.word = word;
        this.originalVector = originalVector;
        this.pcaVector = pcaVector;
    }

    // Getters and setters
    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public double[] getOriginalVector() {
        return originalVector;
    }

    public void setOriginalVector(double[] originalVector) {
        this.originalVector = originalVector;
    }

    public double[] getPcaVector() {
        return pcaVector;
    }

    public void setPcaVector(double[] pcaVector) {
        this.pcaVector = pcaVector;
    }

    /**
     * Compares this node with another node by word value, ignoring case.
     *
     * @param other the other node to compare
     * @return true when both nodes have non-null words that are equal ignoring case
     */
    public boolean isSameWord(WordNode other) {
        return other != null
                && this.word != null
                && !this.word.isBlank()
                && other.word != null
                && !other.word.isBlank()
                && this.word.equalsIgnoreCase(other.word);
    }

    /**
     * Checks if the word has valid PCA coordinates for the requested axes.
     * Supports checking any number of dimensions (e.g., 2D or 3D).
     *
     * @param axes the PCA indices to check (e.g., axisX, axisY, axisZ)
     * @return true if the word has valid finite values for all requested axes
     */
    public boolean hasValidPcaCoordinates(int... axes) {
        if (this.word == null || this.word.isBlank() || this.pcaVector == null) {
            return false;
        }
        for (int axis : axes) {
            if (axis < 0 || axis >= this.pcaVector.length) {
                return false;
            }
            if (!Double.isFinite(this.pcaVector[axis])) {
                return false;
            }
        }
        return true;
    }
}
