package integration;

import java.io.*;

public class PythonRunner {

    public boolean runScript(String pythonScriptPath, String inputDataPath, String outputJsonPath) {
        ProcessBuilder pb = new ProcessBuilder("python", pythonScriptPath, inputDataPath, outputJsonPath);
        try {
            Process process = pb.start();

            // Use try-with-resources to ensure the reader is ALWAYS closed
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println(line);
                }
            }

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            // Restore interrupted state - this fixes the Sonar violation
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
