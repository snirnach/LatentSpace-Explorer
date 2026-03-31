package ui.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Subject that stores PCA axis state and notifies subscribed observers.
 */
public class PcaStateSubject {

    private final List<IPcaObserver> observers = new ArrayList<>();

    private int pcaX = 0;
    private int pcaY = 1;
    private int pcaZ = 2;

    /**
     * Registers an observer.
     */
    public void attach(IPcaObserver observer) {
        if (observer == null || observers.contains(observer)) {
            return;
        }
        observers.add(observer);
    }


    /**
     * Notifies all observers with the latest PCA axis values.
     */
    public void notifyObservers() {
        for (IPcaObserver observer : observers) {
            observer.onPcaAxesChanged(pcaX, pcaY, pcaZ);
        }
    }

    /**
     * Updates axis state and immediately notifies all observers.
     */
    public void updateAxes(int x, int y, int z) {
        this.pcaX = x;
        this.pcaY = y;
        this.pcaZ = z;
        notifyObservers();
    }

    public int getPcaX() {
        return pcaX;
    }

    public int getPcaY() {
        return pcaY;
    }

    public int getPcaZ() {
        return pcaZ;
    }
}

