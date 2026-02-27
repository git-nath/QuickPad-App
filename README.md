# QuickPad Android App

A minimal utility-style video pad app built with Kotlin + Jetpack Compose.

## Features
- Home screen with a vertical list of saved videos.
- Each item shows a video thumbnail and caption preview.
- Floating action button to add a new video.
- Add screen with system file picker, preview, caption input, and save.
- Local persistence with Room (video URI + caption).
- Material 3 design.

## Architecture
A lightweight clean setup:
- `VideoEntity`, `VideoDao`, `AppDatabase` for local storage.
- `VideoRepository` to encapsulate data access.
- `VideoViewModel` for state + save actions.
- Compose UI with simple navigation (`home` and `add`).

## Notes
This project stores URI permissions using `takePersistableUriPermission` so selected files remain accessible across app restarts.
