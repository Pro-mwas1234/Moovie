# Moovie

A streaming application built with Android/Kotlin.

## Overview

Moovie is a streaming app that allows users to browse, search, and watch movies and TV shows.

## Features

- Browse catalog of movies and TV shows
- Search functionality
- User authentication
- Playback controls
- Download for offline viewing
- Personalized recommendations

## Project Structure

```
Moovie/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/
│   │       └── res/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── gradle.properties
├── local.properties
├── settings.gradle.kts
├── how-it-works.md
└── plan.md
```

## Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 11
- Android SDK 33 or higher

### Installation

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle
4. Run on an emulator or device

### Configuration

- Update `local.properties` with your SDK path if needed
- Adjust API keys in `gradle.properties` or `local.properties` as required

## Building the Project

Use Gradle to build the APK:

```bash
./gradlew assembleDebug
```

## Running Tests

```bash
./gradlew test
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For questions or support, please open an issue on the repository.