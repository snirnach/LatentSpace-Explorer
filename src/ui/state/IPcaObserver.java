package ui.state;

/**
 * Observer contract for PCA axis selection changes.
 */
public interface IPcaObserver {

    /**
     * Called when PCA axis values change.
     */
    void onPcaAxesChanged(int pcaX, int pcaY, int pcaZ);
}

