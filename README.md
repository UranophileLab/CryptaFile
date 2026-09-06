# CryptaFile

CryptaFile is a secure, high-performance vault application for Android designed to protect your sensitive files using military-grade encryption. Combining modern Kotlin-based UI with a powerful C++ native security layer, CryptaFile ensures your data remains private and secure.

## 🚀 Features

- **Advanced Encryption**: Leverages high-performance C++ implementations of cryptographic algorithms (AES-256) via OpenSSL.
- **Secure Vault**: Centralized storage for your encrypted documents, photos, and files.
- **Biometric Authentication**: Seamlessly unlock your vault using Fingerprint or Face recognition.
- **Double-Layer Security**: Uses `EncryptedSharedPreferences` for secure metadata storage and a native core for file processing.
- **Secure Shredding**: Automatically and securely deletes original files after successful encryption to prevent recovery.
- **Recovery System**: Secure password recovery via hashed recovery codes (SHA-256).
- **Modern UI**: A fast, responsive, and beautiful interface built with Jetpack Compose and Material 3.
- **Hardware Integration**: Smart security triggers using device sensors (e.g., shake-to-lock).

## 🛠 Tech Stack

- **Kotlin**: The primary language for Android development.
- **C++ (JNI)**: Native layer for high-speed cryptographic operations.
- **Jetpack Compose**: Modern declarative UI toolkit.
- **Material 3**: The latest Android design system.
- **Android Biometric API**: Secure user authentication.
- **Android Security Crypto**: Key management and encrypted storage.
- **OpenSSL**: Industrial-strength cryptographic library.
- **Kotlin Coroutines**: Efficient asynchronous processing.

## 🏗 Project Structure

- `app/src/main/java`: Kotlin source code, including UI (Compose), ViewModels, and Security management.
- `app/src/main/cpp`: Native C++ source code for core cryptographic logic.
- `app/src/main/res`: Android resources and styling.

## ⚙️ Building the Project

1. Clone the repository.
2. Open the project in **Android Studio**.
3. Ensure you have the **NDK** and **CMake** installed (check SDK Manager).
4. Sync the project with Gradle files.
5. Build and run the app on a physical device or emulator.

---
Developed by **UranophileLab**.
