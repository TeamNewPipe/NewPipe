<p align="center"><a href="https://github.com/yashajagiya/NewPipe"><img src="assets/new_pipe_icon_5.png" width="150"></a></p>
<h2 align="center"><b>NewPipe Kotlin Alpha</b></h2>
<h4 align="center">A modern, Jetpack Compose-based migration of the NewPipe lightweight streaming front-end.</h4>

---

> [!CAUTION]
> **‼️ DISCLAIMER**
> No fork of this project will receive support. If you use this fork, please ask the maintainer of this repository for support.
>
> This project is a migration of NewPipe from Java to Kotlin, developed using modern tools and AI assistance. As it is currently in an **Alpha Stage**, you may encounter bugs and inconsistencies. This is a personal project dedicated to exploring improved UI/UX and serving as a foundation for a future TV application. Future versions will focus on refined logic flow, high-quality documentation, and better file organization.

---

## ✨ Features

### 🎨 Modern UI/UX
*   **Jetpack Compose** - Fully declarative UI built with modern Android standards.
*   **Material You** - Dynamic color theming that adapts to your device's wallpaper and style.
*   **Smooth Animations** - Fluid transitions and micro-interactions for a premium feel.
*   **Material Design 3** - Leveraging the latest M3 components for a clean, consistent look.

### 🎵 Powerful Playback
*   **Media3 ExoPlayer** - High-performance audio and video engine with advanced format support.
*   **Background Playback** - Listen to your favorite content while using other apps or with the screen off.
*   **Popup Mode** - Continue watching in a floating window (Picture-in-Picture).
*   **Queue Management** - Easily manage and reorder your playback queue.

### 📚 Core Functionality
*   **No Account Required** - Browse YouTube, SoundCloud, and more without needing a Google account.
*   **Subscription Management** - Subscribe to channels and organize them into groups locally.
*   **Local Playlists** - Create and manage your own playlists without service-side limitations.
*   **Privacy Focused** - No proprietary libraries (like Google Play Services) used.

---

## 🛠️ Tech Stack

| Category | Technology |
| :--- | :--- |
| **Language** | Kotlin 100% |
| **UI Framework** | Jetpack Compose |
| **Design System** | Material Design 3 (Material You) |
| **Audio Engine** | Media3 ExoPlayer |
| **Architecture** | MVVM with StateFlow/SharedFlow |
| **DI** | Koin |
| **Database** | Room |
| **Networking** | OkHttp + NewPipeExtractor |
| **Image Loading** | Coil |
| **Async** | Kotlin Coroutines & Flow |

---

## 🚀 Getting Started

### Prerequisites
*   **Android Studio Ladybug** | 2024.2.1 or newer
*   **Android SDK 30+**
*   **JDK 21**

### Installation
1.  **Clone the repository**
    ```bash
    git clone https://github.com/yashajagiya/NewPipe.git
    ```
2.  **Open in Android Studio**
3.  **Sync and Build** - Wait for Gradle to sync dependencies and build the project.
4.  **Run** - Connect your device and click the Run (▶️) button.

---

## 📂 Project Structure
The core logic resides in `app/src/main/java/org/schabi/newpipe/`:
*   `database/` - Room entities, DAOs, and migrations.
*   `ui/` - Compose screens and reusable components.
*   `viewmodel/` - Screen-level logic and state management.
*   `player/` - Media3 ExoPlayer integration and services.
*   `streams/` - Content parsing and stream handling.
*   `util/` - Extension functions and utility classes.

---

## 🤝 Contributing
Contributions are welcome! If you find a bug or have a suggestion, feel free to open a Pull Request. As this project is in alpha, your help in refining the codebase is greatly appreciated.

## 📄 License
This project is licensed under the **GPLv3 License**. See the [LICENSE](LICENSE) file for details.
