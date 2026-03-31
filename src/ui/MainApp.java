package ui;

import integration.StartupDataBootstrap;
import javafx.application.Platform;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.controller.MainController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entry point for the LatentSpace Explorer JavaFX application.
 *
 * <p>The application keeps startup responsibilities minimal:
 * it optionally loads repository data, creates the main controller,
 * and displays the primary window.</p>
 */
public class MainApp extends Application {

    private static final String APPLICATION_TITLE = "LatentSpace Explorer";
    private static final double DEFAULT_WINDOW_WIDTH = 1200;
    private static final double DEFAULT_WINDOW_HEIGHT = 800;
    private static final String DEFAULT_SCRIPT_PATH = "src/embedder.py";
    private static final String DEFAULT_DATA_DIRECTORY = "src";

    @Override
    public void start(Stage primaryStage) {
        StartupConfig startupConfig = resolveStartupConfig();
        StartupDataBootstrap.StartupResult startupResult = new StartupDataBootstrap().initializeData(
                startupConfig.runPythonPreprocessing(),
                startupConfig.pythonScriptPath(),
                startupConfig.inputDataPath(),
                startupConfig.dataDirectory()
        );

        if (!startupResult.success()) {
            showStartupErrorAndExit(startupResult.message());
            return;
        }

        System.out.println(startupResult.message());

        MainController mainController = new MainController();
        Parent rootView = mainController.getView();
        Scene scene = new Scene(rootView, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private StartupConfig resolveStartupConfig() {
        Path dataDirectory = resolveDataDirectory();
        Path pythonScriptPath = resolvePythonScriptPath();
        String inputDataPath = System.getProperty("latentspace.python.input.path");

        return new StartupConfig(
                shouldRunPythonPreprocessing(),
                pythonScriptPath,
                inputDataPath,
                dataDirectory
        );
    }

    private Path resolveDataDirectory() {
        Path defaultPath = Paths.get(DEFAULT_DATA_DIRECTORY).toAbsolutePath().normalize();

        String propertyPath = System.getProperty("latentspace.data.path");
        if (propertyPath != null && !propertyPath.isBlank()) {
            Path configuredPath = Paths.get(propertyPath).toAbsolutePath().normalize();
            return Files.isDirectory(configuredPath) ? configuredPath : configuredPath.getParent();
        }

        for (String argument : getParameters().getRaw()) {
            if (argument == null || argument.isBlank() || argument.startsWith("--")) {
                continue;
            }

            Path configuredPath = Paths.get(argument).toAbsolutePath().normalize();
            Path directory = Files.isDirectory(configuredPath) ? configuredPath : configuredPath.getParent();
            if (directory != null) {
                return directory;
            }
        }

        return defaultPath;
    }

    private Path resolvePythonScriptPath() {
        String scriptProperty = System.getProperty("latentspace.python.script", DEFAULT_SCRIPT_PATH);
        return Paths.get(scriptProperty).toAbsolutePath().normalize();
    }

    private boolean shouldRunPythonPreprocessing() {
        String propertyValue = System.getProperty("latentspace.python.enabled", "false");
        if (Boolean.parseBoolean(propertyValue)) {
            return true;
        }

        List<String> rawArguments = getParameters().getRaw();
        return rawArguments.stream().anyMatch("--run-python"::equalsIgnoreCase);
    }

    private void showStartupErrorAndExit(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Startup Error");
        alert.setHeaderText("LatentSpace Explorer could not start");
        alert.showAndWait();
        Platform.exit();
    }

    private record StartupConfig(
            boolean runPythonPreprocessing,
            Path pythonScriptPath,
            String inputDataPath,
            Path dataDirectory
    ) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}

