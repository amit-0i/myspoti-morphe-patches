# MySpoti Spotify Branding Patch

A Morphe patch for the MySpoti Android app. It changes the Android launcher/display name to **Spotify**, updates the `app_name` string used by the now-playing widget, and replaces the standard and monochrome launcher artwork with a Spotify-style green mark.

## Supported package variants

- `app.myspoti` (beta/default builds)
- `app.myspoti.stable` (stable release builds)
- `app.myspoti.dev` (debug/dev builds)
- `app.myspoti.stable.dev` (stable debug/dev builds)

The patch uses resource-only changes and does not alter the package name, app logic, or sign-in/playback behavior.

## Build

Use Java 21 and the Gradle wrapper from this repository:

```bash
./gradlew buildAndroid
```

The patch bundle (`.mpp`) is written to `patches/build/libs/`.

Then add the `.mpp` bundle as a local patch source in Morphe Desktop / Morphe Manager and apply **Spotify branding** to a MySpoti APK. Use a normal install, not a root-mounted install, for branding changes to take effect.
