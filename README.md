# GitSnow ❄️

A native Android GitHub repository file manager built in Kotlin with Jetpack Compose.

## Features in v0.1.0
- Browse repositories and directories, select branches
- Upload multiple files selected from Android storage, including binary BIN, XDF, ADX and ZIP files
- Create directories (via .gitkeep), create/edit text files and delete files
- Create repositories if the token has adequate permissions
- AES-GCM encrypted GitHub token storage using Android Keystore

## Get the APK
Open [Android APK Actions](https://github.com/snow663/GitSnow/actions/workflows/android.yml), open a **successful** build, and download the `GitSnow-debug-apk` artifact. Extract its ZIP and install `app-debug.apk` on Android.

The first build is **not yet verified**. This is a debug APK for testing, not a signed production release.

## Authentication
Enter a GitHub personal access token inside the app. For a fine-grained PAT, give selected repositories **Contents: Read and write** and **Metadata: Read**. Repository creation requires additional authorization (for example, a classic token with `repo` scope). Never paste a token into issues or repository files.

## Build locally
Open in Android Studio with JDK 17 and Gradle 8.9, or build from the command line with `gradle assembleDebug` using Android SDK 35. The GitHub Actions runner installs Gradle automatically, so a Gradle wrapper is not currently checked in.

## Current limitations
- File uploads are individual commits, not atomic batches.
- No whole-folder upload, offline repository cloning, branch creation, moving/renaming, PR UI, or syntax highlighting yet.
- GitHub's Contents API does not store empty directories; GitSnow creates a `.gitkeep` file.
- Files larger than 15 MB are deliberately rejected in this initial implementation.
- Repository and branch lists currently fetch up to 100 items each.
- Only UTF-8 text editing is supported; binary files are uploaded byte-for-byte.

## Security
The app talks directly to `https://api.github.com`. It has no analytics server. Token bytes are encrypted on-device and Android application backup is disabled.

## License
MIT — see [LICENSE](LICENSE).
