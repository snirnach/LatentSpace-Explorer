package integration;

import java.io.*;

public class PythonRunner {

    public boolean runScript(String pythonScriptPath, String inputDataPath, String outputJsonPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", pythonScriptPath, inputDataPath, outputJsonPath);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            } else {
                // Print errors
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println(line);
                    }
                }
                return false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
}
