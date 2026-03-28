package util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.EmbeddingRepository;
import model.WordNode;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataLoader {

    private static final Gson gson = new Gson();

    /**
     * Loads data from the full-vector and PCA-vector JSON files into the repository.
     * The files are matched by word, then merged into {@link WordNode} objects.
     */
    public void loadDataToRepository(String fullVectorsPath, String pcaVectorsPath) {
        EmbeddingRepository repository = EmbeddingRepository.INSTANCE;
        repository.clearData();

        try {
            Map<String, double[]> originalVectorsByWord = new HashMap<>();
            List<VectorEntry> fullVectorEntries = parseVectorEntries(fullVectorsPath);

            for (VectorEntry entry : fullVectorEntries) {
                if (entry == null || entry.word == null || entry.vector == null) {
                    continue;
                }
                originalVectorsByWord.put(entry.word, toPrimitiveArray(entry.vector));
            }

            List<VectorEntry> pcaVectorEntries = parseVectorEntries(pcaVectorsPath);
            for (VectorEntry entry : pcaVectorEntries) {
                if (entry == null || entry.word == null || entry.vector == null) {
                    continue;
                }

                double[] originalVector = originalVectorsByWord.get(entry.word);
                if (originalVector == null) {
                    // Skip words that do not exist in the full vectors file.
                    continue;
                }

                double[] pcaVector = toPrimitiveArray(entry.vector);
                repository.addWord(new WordNode(entry.word, originalVector, pcaVector));
            }
        } catch (IOException e) {
            System.err.println("Failed to load embedding data: " + e.getMessage());
        }
    }


    private List<VectorEntry> parseVectorEntries(String jsonFilePath) throws IOException {
        try (FileReader reader = new FileReader(jsonFilePath)) {
            Type listType = new TypeToken<List<VectorEntry>>() {}.getType();
            List<VectorEntry> parsedEntries = gson.fromJson(reader, listType);
            return parsedEntries == null ? List.of() : parsedEntries;
        }
    }

    private double[] toPrimitiveArray(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // DTO used only for deserializing JSON entries.
    private static class VectorEntry {
        String word;
        List<Double> vector;
    }
}
