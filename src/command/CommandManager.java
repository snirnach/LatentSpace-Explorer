package command;

import java.util.Stack;

/**
 * Invoker class that manages command execution, undo, and redo operations.
 * Maintains separate stacks for undo and redo history.
 */
public class CommandManager {

    private final Stack<ICommand> undoStack = new Stack<>();
    private final Stack<ICommand> redoStack = new Stack<>();

    /**
     * Executes a command and pushes it onto the undo stack.
     * Clears the redo stack when a new command is executed.
     *
     * @param command the command to execute
     */
    public void executeCommand(ICommand command) {
        if (command == null) {
            return;
        }
        command.execute();
        undoStack.push(command);
        // Clear redo stack when a new command is executed
        redoStack.clear();
    }

    /**
     * Undoes the most recent command.
     * Pushes the undone command onto the redo stack.
     */
    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        ICommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
    }

    /**
     * Redoes the most recently undone command.
     * Pushes the redone command back onto the undo stack.
     */
    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        ICommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);
    }

    /**
     * Checks if there are commands available to undo.
     *
     * @return true if undo stack is not empty
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Checks if there are commands available to redo.
     *
     * @return true if redo stack is not empty
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Clears all undo and redo history.
     */
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Returns the size of the undo stack.
     *
     * @return number of commands in undo history
     */
    public int getUndoCount() {
        return undoStack.size();
    }

    /**
     * Returns the size of the redo stack.
     *
     * @return number of commands in redo history
     */
    public int getRedoCount() {
        return redoStack.size();
    }
}

