# WorkflowLens

A privacy-first Android productivity tracker. It listens for taps and app/window switches
with an `AccessibilityService`, captures the screen at the exact moment of the action with
the native `takeScreenshot()` API, and shows everything in a Material 3 timeline —
all stored **on-device only**.

## Features
- **Tracker service** — monitors `TYPE_VIEW_CLICKED` and `TYPE_WINDOW_STATE_CHANGED`, builds
  a semantic description ("Clicked 'Submit' in Chrome"), and captures the display
  asynchronously (API 30+ `AccessibilityService.takeScreenshot`).
- **Timeline engine** — Room database (`workflow_events` table) plus JPEG-80% screenshots in
  app-private storage (`filesDir/screenshots/`).
- **Retention** — entries and images older than 7 days are pruned automatically on every
  capture; storage usage is shown on the dashboard.
- **Dashboard** — Material 3, Compose: stylized enable-toggle (deep-links to the system
  Accessibility settings), lazy timeline cards with app icon, relative timestamp, action
  label and a thumbnail that opens a full-screen light-box.

## Project structure
```
WorkflowLens/
├── .github/workflows/android.yml      # CI: JDK 17 + cached Gradle + APK artifact
├── build.gradle                       # AGP 8.5 / Kotlin 1.9.24 / KSP
├── settings.gradle
├── gradle/wrapper/                    # Gradle 8.7
└── app/
    ├── build.gradle                   # compose, room+ksp, coroutines
    └── src/main/
        ├── AndroidManifest.xml        # BIND_ACCESSIBILITY_SERVICE service declaration
        ├── res/xml/accessibility_service_config.xml
        └── java/com/unsmah/workflowlens/
            ├── data/                  # Room entity/DAO/DB, ScreenshotStore, RetentionManager, Repository
            ├── service/               # TrackerService (Accessibility) + HandlerExecutor
            └── ui/                    # MainActivity, Dashboard, EventCard, Lightbox, Theme, ViewModel
```

## Build
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Install & first run
1. Install the debug APK (it is signed with the debug key).
2. Open **Workflow Lens** and flip the toggle — you land in *Settings ▸ Accessibility ▸
   Workflow Lens tracker*; switch the service on there.
3. Interact with any app; events appear on the timeline in real time.
4. Tap a card's thumbnail to inspect the full screenshot. Use the broom icon to wipe
   history (rows + images).

## Privacy
- Everything stays in the app's sandbox: Room DB + `filesDir/screenshots/`.
- No INTERNET permission is requested at all, so nothing can leave the device.
- Uninstalling the app removes the database and every screenshot.

## Architecture (MVVM)
```
AccessibilityService ──► WorkflowRepository ──► Room DB + ScreenshotStore
        ▲                                            │
        │                                    Flow<List<WorkflowEvent>>
        │                                            ▼
   user events                              TimelineViewModel ──► Compose Dashboard
```
The screenshot callback arrives on the service's executor thread; the repository's
`suspend fun record(...)` moves the blob write and the Room transaction onto
`Dispatchers.IO` (see `TrackerService.onScreenshot` for the hand-off).
