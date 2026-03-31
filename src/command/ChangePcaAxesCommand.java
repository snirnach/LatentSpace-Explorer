package command;

import ui.view.ControlPanelView;
import ui.state.PcaStateSubject;

/**
 * Concrete Command that encapsulates changing PCA axes.
 * Supports undo by storing the old axes state before applying new axes.
 */
public class ChangePcaAxesCommand implements ICommand {

    private final PcaStateSubject pcaStateSubject;
    private final ControlPanelView controlPanelView;
    private final int oldX;
    private final int oldY;
    private final int oldZ;
    private final int newX;
    private final int newY;
    private final int newZ;

    /**
     * Constructs a ChangePcaAxesCommand.
     *
     * @param pcaStateSubject the PCA state subject to update
     * @param controlPanelView the control panel view to update UI
     * @param oldX            previous X axis index
     * @param oldY            previous Y axis index
     * @param oldZ            previous Z axis index
     * @param newX            new X axis index
     * @param newY            new Y axis index
     * @param newZ            new Z axis index
     */
    public ChangePcaAxesCommand(PcaStateSubject pcaStateSubject,
                                ControlPanelView controlPanelView,
                                int oldX, int oldY, int oldZ,
                                int newX, int newY, int newZ) {
        this.pcaStateSubject = pcaStateSubject;
        this.controlPanelView = controlPanelView;
        this.oldX = oldX;
        this.oldY = oldY;
        this.oldZ = oldZ;
        this.newX = newX;
        this.newY = newY;
        this.newZ = newZ;
    }

    /**
     * Executes the command by updating the PCA axes to the new values.
     */
    @Override
    public void execute() {
        pcaStateSubject.updateAxes(newX, newY, newZ);
        controlPanelView.setPcaComboBoxValues(newX, newY, newZ);
    }

    /**
     * Undoes the command by reverting to the old PCA axes.
     */
    @Override
    public void undo() {
        pcaStateSubject.updateAxes(oldX, oldY, oldZ);
        controlPanelView.setPcaComboBoxValues(oldX, oldY, oldZ);
    }
}

