package integration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PythonRunner {

    /**
     * Legacy API kept for compatibility with existing callers.
     */
    public boolean runScript(String pythonScriptPath, String inputDataPath, String outputJsonPath) {
        return runScriptWithResult(pythonScriptPath, inputDataPath, outputJsonPath).success();
    }

    /**
     * Executes a Python script and returns structured process details.
     * The output path parameter is used both as an optional argument and as
     * working directory for scripts that write fixed output filenames.
     */
    public RunResult runScriptWithResult(String pythonScriptPath, String inputDataPath, String outputPathOrDirectory) {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add(pythonScriptPath);
        if (inputDataPath != null && !inputDataPath.isBlank()) {
            command.add(inputDataPath);
        }
        if (outputPathOrDirectory != null && !outputPathOrDirectory.isBlank()) {
            command.add(outputPathOrDirectory);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        if (outputPathOrDirectory != null && !outputPathOrDirectory.isBlank()) {
            File outputDir = new File(outputPathOrDirectory);
            if (outputDir.exists() && outputDir.isDirectory()) {
                pb.directory(outputDir);
            }
        }

        try {
            Process process = pb.start();
            StringBuilder processLog = new StringBuilder();

            // Stream combined output so failures are visible and not swallowed.
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processLog.append(line).append(System.lineSeparator());
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return RunResult.success("Python preprocessing completed successfully.", exitCode);
            }

            String message = processLog.length() == 0
                    ? "Python process exited with code " + exitCode + "."
                    : processLog.toString().trim();
            return RunResult.failure(message, exitCode, false);

        } catch (IOException e) {
            return RunResult.failure("Failed to start Python process: " + e.getMessage(), -1, false);
        } catch (InterruptedException e) {
            // Restore interrupted state and report interruption clearly.
            Thread.currentThread().interrupt();
            return RunResult.failure("Python process was interrupted.", -1, true);
        }
    }

    public record RunResult(boolean success, String message, int exitCode, boolean interrupted) {
        static RunResult success(String message, int exitCode) {
            return new RunResult(true, message, exitCode, false);
        }

        static RunResult failure(String message, int exitCode, boolean interrupted) {
            return new RunResult(false, message, exitCode, interrupted);
        }
    }
}
