# Theme Selection and Dark Mode Stability Design

Date: 2026-07-27

## Goal

Make app-wide appearance selection stable and user-controlled. The default must follow the system, while users can immediately switch to light or dark appearance from Mine > App settings.

## Product Behavior

- The Theme setting lives in the existing `settingsGroup` of the Mine screen.
- Its summary always shows one of: Follow system, Light mode, or Dark mode.
- Tapping the row opens a single-choice Material dialog with those three options.
- The app starts with Follow system when no choice was previously stored.
- Selecting an option persists it and immediately applies the corresponding AppCompat night mode. Activity recreation is delegated to AppCompat.
- Returning to Follow system removes the override and resumes responding to the device appearance.

## Architecture

- Add a small, pure `ThemeMode` enum that maps persisted values to `AppCompatDelegate` night-mode constants.
- Add `ThemePreferenceStore`, backed by ordinary app-private `SharedPreferences`. It owns the preference name, key, default, malformed-value fallback, and applying a selected mode.
- `AiyifanApp.onCreate` reads and applies the stored preference before initializing the application graph. This ensures all activities receive a consistent AppCompat configuration during creation, including after process death.
- `MineFragment` renders the current preference as the setting summary and opens the selection dialog. It delegates persistence and application to the store and refreshes its summary after selection.

## Dark Mode Stability

- Do not alter the existing semantic day/night color palettes or the edge-to-edge system-bar policy.
- Applying the delegate mode before activity creation removes the late-switch inconsistency that can occur when an activity inflates resources before the preference is restored.
- Invalid or legacy stored values resolve to Follow system so corrupted preferences cannot cause an invalid delegate mode or a crash.

## Testing

- Unit-test the default, each explicit selection, and invalid stored values using a small pure mode resolver.
- Extend the Mine layout contract test with the theme row and its summary view IDs.
- Build the debug variant, run the affected tests, the complete local test suite, and Android Lint.

## Scope

This change adds one preference and one Mine-screen setting row. It does not add a new settings activity, alter authentication, or change the existing day/night color resources.
