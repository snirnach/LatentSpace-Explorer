package command;

/**
 * Interface defining the Command Design Pattern contract.
 * All concrete commands must implement this interface to support
 * Undo/Redo functionality.
 */
public interface ICommand {

    /**
     * Executes the command.
     */
    void execute();

    /**
     * Undoes the command, reverting to the previous state.
     */
    void undo();
}

