# BridgeFy

BridgeFy is a decentralized, offline-first Android communication application designed to keep users connected when traditional cellular or Wi-Fi networks fail or are unavailable. It leverages peer-to-peer (P2P) mesh networking through Bluetooth Low Energy (BLE) and Wi-Fi Direct to enable text messaging, voice notes, file sharing, and location tracking entirely offline.

## 🚀 Features

*   **Offline Mesh Networking:** Robust custom mesh architecture that dynamically routes messages across multiple hops (peers) using both BLE and Wi-Fi Direct.
*   **Peer-to-Peer Messaging:** Send and receive secure text messages without an internet connection.
*   **Media & File Transfer:** Share voice recordings and files directly with nearby peers.
*   **Offline Mapping & Location Sharing:** View peers on an offline map using MapLibre and share your GPS coordinates securely. Includes granular location privacy settings.
*   **Background Services:** Persistent mesh connectivity and location updates powered by optimized Android Foreground Services.
*   **Security & Encryption:** Built-in cryptographic components for secure key management and encrypted peer-to-peer communication.
*   **Modern UI:** A clean, responsive interface built entirely with Jetpack Compose, following Material 3 design guidelines.
..
## 🛠 Tech Stack

*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose (Material 3)
*   **Architecture:** MVVM / MVI with Jetpack ViewModels and Kotlin Coroutines/Flows
*   **Networking:** Custom P2P Mesh (Wi-Fi Direct / BLE APIs)
*   **Local Storage:** SQLite (via standard queries) & Jetpack DataStore (Preferences)
*   **Mapping:** MapLibre GL Android SDK for offline vector maps
*   **Location:** Google Play Services Location SDK
*   **Concurrency:** Kotlin Coroutines

## 📂 Project Structure

*   `db/`: SQLite database helpers, mappers, and queries for storing messages, peers, and network state.
*   `media/`: Managers for voice recording and playback capabilities.
*   `mesh/`: Core mesh networking logic, including peer discovery, message routing, deduplication, and acknowledgment tracking.
*   `model/`: Data classes and domain models.
*   `security/`: Cryptographic utilities and key management.
*   `service/`: Android Foreground Services for maintaining background mesh connections and location tracking.
*   `transfer/`: File storage and transfer managers for handling media over the mesh.
*   `transport/`: Abstraction layers for BLE and Wi-Fi Direct connections, managing underlying connection events and data streams.
*   `ui/`: Jetpack Compose UI layer organized by screens (`ChatScreen`, `MeshMapScreen`, `PeersScreen`, etc.), components, and viewmodels.

## ⚙️ Getting Started

### Prerequisites
*   Android Studio (Latest stable version recommended)
*   Android SDK Platform 36 (targetSdk 36)
*   Min SDK 26 (Android 8.0 Oreo)

### Installation
1.  Clone the repository:
    ```bash
    git clone https://github.com/PankajGupta-dev/BridgeFy.git
    ```
2.  Open the project in Android Studio.
3.  Sync the project with Gradle files.
4.  Build and run on a physical Android device. *(Note: Emulators generally do not support Wi-Fi Direct or BLE testing).*

## 🔒 Permissions

The app requires the following key permissions to function properly:
*   `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
*   `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `NEARBY_WIFI_DEVICES`
*   `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
*   `RECORD_AUDIO`

Ensure these permissions are granted upon the first launch for the mesh network to initialize correctly.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!
Feel free to check the [issues page](../../issues).

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
