package ui;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import model.EmbeddingRepository;
import util.DataLoader;

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

    @Override
    public void start(Stage primaryStage) {
        // Loading the generated JSON files into our Singleton Repository
        util.DataLoader dataLoader = new util.DataLoader();
        String basePath = "src/"; // The folder where you ran the Python script
        dataLoader.loadDataToRepository(basePath + "full_vectors.json", basePath + "pca_vectors.json");

        System.out.println("Loaded " + model.EmbeddingRepository.INSTANCE.getAllWords().size() + " words into the repository.");
        loadRepositoryDataIfConfigured();

        MainController mainController = new MainController();
        Parent rootView = mainController.getView();
        Scene scene = new Scene(rootView, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Loads JSON data into the repository only when a data path is provided.
     * This keeps the GUI reusable in environments where another bootstrapper
     * has already populated the repository.
     */
    private void loadRepositoryDataIfConfigured() {
        if (!EmbeddingRepository.INSTANCE.getAllWords().isEmpty()) {
            return;
        }

        String jsonDataPath = resolveJsonDataPath();
        if (jsonDataPath == null || jsonDataPath.isBlank()) {
            return;
        }

        DataLoader dataLoader = new DataLoader();
        dataLoader.loadJsonToRepository(jsonDataPath);
    }

    /**
     * Resolves the optional JSON path from the first application argument.
     * If no argument is present, a JVM system property can also be used.
     *
     * @return the configured JSON path, or {@code null} when none is provided
     */
    private String resolveJsonDataPath() {
        List<String> rawArguments = getParameters().getRaw();
        if (!rawArguments.isEmpty()) {
            return rawArguments.get(0);
        }

        return System.getProperty("latentspace.data.path");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

