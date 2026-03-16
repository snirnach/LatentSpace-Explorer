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
}
