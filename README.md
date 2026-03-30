# LatentSpace Explorer 🌌

LatentSpace Explorer is an advanced, high-performance JavaFX application designed for the interactive visualization and mathematical analysis of high-dimensional word embeddings. It allows users to explore semantic relationships, perform vector arithmetic, and navigate complex datasets in both 2D and 3D environments using Principal Component Analysis (PCA) projections.

## 🚀 Core Features

### 1. Interactive 3D Visualization (`SubScene`)
* **Orbit Camera:** A fully navigable 3D space with smooth panning, zooming, and rotation capabilities.
* **Smart Auto-Centering:** Automatically locks the camera focus onto searched words, math equation results, or nearest-neighbor clusters.
* **Intelligent Rendering:** Utilizes a custom-built 3D rendering engine (via JavaFX `Sphere` and `Cylinder`) to draw nodes, connect relationships with 3D lines, and display floating text labels without cluttering the viewport.

### 2. High-Performance 2D Canvas 
* **Rapid Exploration:** A fast, lightweight 2D scatter plot view built on JavaFX `Canvas`.
* **Dynamic Scaling:** Supports deep zooming and panning with automatic bounds adjustments to ensure data is always in view.

### 3. Semantic Analytics & Vector Math
* **K-Nearest Neighbors (KNN):** Search for the closest semantic concepts in the latent space. Users can dynamically switch between **Euclidean** and **Cosine** distance metrics.
* **Vector Arithmetic (Word Analogies):** Solve complex semantic equations directly in the UI (e.g., calculating `king - man + woman` to find `queen`).
* **Semantic Axis Projection:** Define custom axes using polar opposite words (e.g., "good" vs "bad") and project the entire vocabulary onto this axis to measure semantic alignment.

### 4. Seamless Feature Parity
* State management is completely synchronized. Selecting a word, running an equation, or changing the active PCA axes (X, Y, Z) instantly updates both the 2D and 3D views.

---

## 🏗️ Software Architecture & Design Patterns

This project was engineered with a strict adherence to **SOLID principles**, Clean Code standards, and Object-Oriented Programming (OOP) design patterns.

* **Composite Pattern:** The 3D scene generation relies on a robust Composite tree structure (`IComponent3D`, `CompositeCluster3D`, `WordLeaf3D`, `ConnectionLeaf3D`). This eliminates "God Classes" and allows uniform attachment of diverse 3D elements to the scene graph.
* **Strategy Pattern:** Distance metrics are fully decoupled from the KNN algorithm. The `DistanceStrategy` interface allows runtime swapping between `EuclideanDistance` and `CosineDistance` without modifying the core search logic.
* **Facade Pattern:** The `LatentSpaceFacade` shields the UI controllers from the complexities of backend services, data loading, and vector mathematics, drastically reducing coupling.
* **Observer / Subject Pattern:** A `PcaStateSubject` manages the active PCA dimensions. Both the 2D and 3D views subscribe to this subject, reacting instantly to dimension changes.
* **Factory Pattern:** `DistanceStrategyFactory` centralizes the instantiation of distance metrics based on UI inputs.
* **Singleton Pattern:** The `EmbeddingRepository` is implemented as an enum-based, thread-safe Singleton, acting as the single source of truth for the dataset and preventing memory duplication.
* **MVC/MVP Architecture:** Strict separation of concerns between state management (`InteractionModel`), view rendering (`Scene3DRenderer`, `Graph2DRenderer`), and user input handling (`Controllers`).

---

## ⚡ Algorithmic Optimizations

* **$O(N \log K)$ KNN Search:** Instead of sorting the entire dataset $O(N \log N)$ to find neighbors, the KNN algorithm utilizes a **Max-Heap (`PriorityQueue`)**. It maintains a fixed-size heap of the closest $K$ elements, rejecting further elements efficiently to achieve significant performance gains.
* **Pure Functions:** Mathematical operations (`VectorMathUtils`) are designed as pure, static functions to prevent side-effects, eliminate memory leaks, and guarantee highly predictable behavior.

---

## 📸 Screenshots
*(Recommended: Add 2-3 screenshots here showing the 3D Orbit view, the 2D Canvas, and the Math Analytics panel)*
* `![3D View Example](link-to-image)`
* `![Math Analogy Example](link-to-image)`

---

## 💻 Getting Started

### Prerequisites
* **Java 17** or higher.
* **JavaFX SDK** configured in your environment.
* A generated dataset containing `full_vectors.json` and `pca_vectors.json`.

### Running the Application
1. Clone the repository:
   ```bash
   git clone [https://github.com/snirnach/LatentSpace-Explorer.git](https://github.com/snirnach/LatentSpace-Explorer.git)
2. Compile and run MainApp.java.
3. The application will automatically load the JSON embeddings from the src/ directory.

👤 Author
Snir Nachmany B.Sc. Computer Science Student, Ariel University
