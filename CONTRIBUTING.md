# Contributing

Thanks for helping! This is a small Android TV app, so keep changes focused.

## Before opening a PR

- Build and run the tests:

  ```bash
  ./gradlew :app:testDebugUnitTest :app:assembleDebug
  ```

- Test on a TV (or an emulator) with a remote. Focus navigation matters more than looks.
- Keep the app working without any TMDB key: the key is always entered at runtime.

## Code style

- Kotlin, no new dependencies unless they replace more code than they add.
- UI strings go in `app/src/main/res/values*/strings.xml` for **all four languages**
  (English is the default; `values-pt`, `values-es`, `values-fr`).
- Rows are stored as JSON in `SharedPreferences`; keep the model changes backward compatible.
- New logic that can be wrong (parsing, URL building, filters) needs a unit test.

## Reporting a bug

Include:

- TV model and Android version;
- the row configuration (source, filters or IDs);
- what you expected and what the launcher showed;
- if possible, `adb logcat -s TmdbRows` right after opening the app.
