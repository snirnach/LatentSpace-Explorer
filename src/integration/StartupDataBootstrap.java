package integration;

import model.EmbeddingRepository;
import util.DataLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Coordinates startup data preparation without mixing process execution,
 * file validation, and JSON parsing responsibilities.
 */
public class StartupDataBootstrap {

    private static final String FULL_VECTORS_FILE = "full_vectors.json";
    private static final String PCA_VECTORS_FILE = "pca_vectors.json";

    private final PythonRunner pythonRunner;
    private final DataLoader dataLoader;

    public StartupDataBootstrap() {
        this(new PythonRunner(), new DataLoader());
    }

    public StartupDataBootstrap(PythonRunner pythonRunner, DataLoader dataLoader) {
        this.pythonRunner = pythonRunner;
        this.dataLoader = dataLoader;
    }

    /**
     * Optionally runs preprocessing, validates output artifacts, and loads data once.
     */
    public StartupResult initializeData(
            boolean runPythonPreprocessing,
            Path pythonScriptPath,
            String inputDataPath,
            Path dataDirectory
    ) {
        if (dataDirectory == null) {
            return StartupResult.failure("Data directory is not configured.");
        }

        Path fullVectorsPath = dataDirectory.resolve(FULL_VECTORS_FILE);
        Path pcaVectorsPath = dataDirectory.resolve(PCA_VECTORS_FILE);

        if (runPythonPreprocessing) {
            if (pythonScriptPath == null) {
                return StartupResult.failure("Python preprocessing is enabled, but script path is missing.");
            }

            PythonRunner.RunResult processResult = pythonRunner.runScriptWithResult(
                    pythonScriptPath.toAbsolutePath().toString(),
                    inputDataPath,
                    dataDirectory.toAbsolutePath().toString()
            );

            if (!processResult.success()) {
                String interruptionSuffix = processResult.interrupted()
                        ? " (startup thread was interrupted)."
                        : "";
                return StartupResult.failure("Python preprocessing failed: "
                        + processResult.message()
                        + interruptionSuffix);
            }
        }

        ValidationResult validationResult = validateOutputFiles(fullVectorsPath, pcaVectorsPath);
        if (!validationResult.valid()) {
            return StartupResult.failure(validationResult.message());
        }

        // Do not reload if data is already available in memory.
        if (!EmbeddingRepository.INSTANCE.getAllWords().isEmpty()) {
            return StartupResult.success("Repository already populated. Skipped duplicate loading.");
        }

        dataLoader.loadDataToRepository(fullVectorsPath.toString(), pcaVectorsPath.toString());

        int loadedCount = EmbeddingRepository.INSTANCE.getAllWords().size();
        if (loadedCount <= 0) {
            return StartupResult.failure("Data loading completed but repository is empty. Check JSON content format.");
        }

        return StartupResult.success("Loaded " + loadedCount + " words into the repository.");
    }

    private ValidationResult validateOutputFiles(Path fullVectorsPath, Path pcaVectorsPath) {
        ValidationResult fullValidation = validateSingleFile(fullVectorsPath, "full_vectors.json");
        if (!fullValidation.valid()) {
            return fullValidation;
        }

        ValidationResult pcaValidation = validateSingleFile(pcaVectorsPath, "pca_vectors.json");
        if (!pcaValidation.valid()) {
            return pcaValidation;
        }

        return ValidationResult.ok();
    }

    private ValidationResult validateSingleFile(Path filePath, String displayName) {
        try {
            if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ValidationResult.failed("Required output file not found: " + displayName + " (" + filePath + ")");
            }

            if (Files.size(filePath) <= 0) {
                return ValidationResult.failed("Required output file is empty: " + displayName + " (" + filePath + ")");
            }

            return ValidationResult.ok();
        } catch (Exception exception) {
            return ValidationResult.failed("Failed to validate output file " + displayName + ": " + exception.getMessage());
        }
    }

    public record StartupResult(boolean success, String message) {
        public static StartupResult success(String message) {
            return new StartupResult(true, message);
        }

        public static StartupResult failure(String message) {
            return new StartupResult(false, message);
        }
    }

    private record ValidationResult(boolean valid, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        static ValidationResult failed(String message) {
            return new ValidationResult(false, message);
        }
    }
}


