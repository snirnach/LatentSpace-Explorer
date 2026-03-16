# LatentSpace Explorer

## Current GUI Entry Point

The JavaFX application entry point is `src/ui/MainApp.java`.

## Optional Startup Data Argument

`MainApp` can load embedding data automatically if you provide the JSON file path in one of these ways:

1. As the first program argument.
2. As the JVM system property `latentspace.data.path`.

Expected JSON structure:

```json
[
  {
    "word": "dog",
    "originalVector": [0.1, 0.2, 0.3],
    "pcaVector": [1.4, -0.8]
  }
]
```

## IntelliJ Setup Notes

This workspace currently contains only the IntelliJ module file and source files.
To run the GUI successfully, make sure the project classpath includes:

- JavaFX SDK
- Gson

You can then run `ui.MainApp` from IntelliJ.

## GUI Behavior

- The scatter chart renders up to the first 500 words with valid 2D PCA coordinates.
- The right panel allows the user to search for a target word.
- The application lists the 10 nearest neighbors and highlights them on the chart.

