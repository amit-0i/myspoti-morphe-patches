# MySpoti Spotify Branding Patch

A Morphe patch for the MySpoti Android app. It changes the Android launcher/display name to **Spotify**, updates the `app_name` string used by the now-playing widget, and replaces the standard and monochrome launcher artwork with a Spotify-style green mark.

## Supported package variants

- `app.myspoti` (beta/default builds)
- `app.myspoti.stable` (stable release builds)
- `app.myspoti.dev` (debug/dev builds)
- `app.myspoti.stable.dev` (stable debug/dev builds)

The patch uses resource-only changes and does not alter the package name, app logic, or sign-in/playback behavior.

## Available patches

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/amit-0i/myspoti-morphe-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 MySpoti&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Spotify branding](#spotify-branding) | Renames MySpoti to Spotify and replaces every launcher, themed, supporter-preview, splash, widget, and notification mark with Spotify branding. |  |

</details>

<details open>
<summary>📦 MySpoti&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Spotify branding](#spotify-branding) | Renames MySpoti to Spotify and replaces every launcher, themed, supporter-preview, splash, widget, and notification mark with Spotify branding. |  |

</details>

<details open>
<summary>📦 MySpoti dev&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Spotify branding](#spotify-branding) | Renames MySpoti to Spotify and replaces every launcher, themed, supporter-preview, splash, widget, and notification mark with Spotify branding. |  |

</details>

<details open>
<summary>📦 MySpoti stable dev&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Spotify branding](#spotify-branding) | Renames MySpoti to Spotify and replaces every launcher, themed, supporter-preview, splash, widget, and notification mark with Spotify branding. |  |

</details>

<!-- PATCHES_END -->

## Build

Use Java 21 and the Gradle wrapper from this repository:

```bash
./gradlew buildAndroid
```

The patch bundle (`.mpp`) is written to `patches/build/libs/`.

Then add the `.mpp` bundle as a local patch source in Morphe Desktop / Morphe Manager and apply **Spotify branding** to a MySpoti APK. Use a normal install, not a root-mounted install, for branding changes to take effect.
