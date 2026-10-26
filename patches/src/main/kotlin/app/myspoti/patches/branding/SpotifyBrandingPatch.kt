package app.myspoti.patches.branding

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

private val myspotiBeta = Compatibility(
    name = "MySpoti",
    packageName = "app.myspoti",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1ED760,
    targets = listOf(AppTarget(version = null)), // Any MySpoti version; this patch changes resources only.
)

private val myspotiStable = Compatibility(
    name = "MySpoti",
    packageName = "app.myspoti.stable",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1ED760,
    targets = listOf(AppTarget(version = null)),
)

private val myspotiDev = Compatibility(
    name = "MySpoti dev",
    packageName = "app.myspoti.dev",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1ED760,
    targets = listOf(AppTarget(version = null)),
)

private val myspotiStableDev = Compatibility(
    name = "MySpoti stable dev",
    packageName = "app.myspoti.stable.dev",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1ED760,
    targets = listOf(AppTarget(version = null)),
)

private val spotifyForeground = """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="36"
        android:viewportHeight="36">
        <group android:translateX="6" android:translateY="6">
            <!-- Spotify's classic circle-and-waves mark; the wave gaps reveal the dark icon background. -->
            <path
                android:fillColor="#1ED760"
                android:pathData="M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301a1.125 1.125 0 1 1-.653-2.153c4.394-1.335 11.698-1.077 16.31 1.661a1.125 1.125 0 0 1-1.156 1.935z" />
        </group>
    </vector>
""".trimIndent() + "\n"

private val spotifyMonochrome = """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="36"
        android:viewportHeight="36">
        <group android:translateX="6" android:translateY="6">
            <!-- Same logomark in a single color for Android themed icons. -->
            <path
                android:fillColor="#FFFFFFFF"
                android:pathData="M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301a1.125 1.125 0 1 1-.653-2.153c4.394-1.335 11.698-1.077 16.31 1.661a1.125 1.125 0 0 1-1.156 1.935z" />
        </group>
    </vector>
""".trimIndent() + "\n"


private val spotifyNotification = """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24">
        <!-- Status-bar notifications use a monochrome Spotify wave mark. -->
        <path
            android:pathData="M4.2,7.3 C8.8,5.9 15.2,6.8 19.5,9.2 M5.2,11.1 C9.1,9.8 14.1,10.5 17.9,12.7 M6.2,14.8 C9.5,13.7 12.9,14.3 16.1,16.1"
            android:strokeColor="#FFFFFFFF"
            android:strokeWidth="1.9"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:fillColor="#00000000" />
    </vector>
""".trimIndent() + "\n"

/** Renames MySpoti to Spotify and replaces all app-identifying icon variants. */
@Suppress("unused")
val spotifyBrandingPatch = resourcePatch(
    name = "Spotify branding",
    description = "Renames MySpoti to Spotify and replaces every launcher, themed, supporter-preview, splash, widget, and notification mark with Spotify branding.",
    default = true,
) {
    compatibleWith(myspotiBeta, myspotiStable, myspotiDev, myspotiStableDev)

    execute {
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: error("MySpoti manifest has no application element")
            application.setAttributeNS(ANDROID_NAMESPACE, "android:label", "Spotify")
        }

        // app_name is the fallback title for the now-playing widget. Update localized copies too.
        var foundAppName = false
        val appNameElementPattern = Regex("""<string\b(?=[^>]*\bname\s*=\s*["']app_name["'])[^>]*>""")
        listDecodableFiles("res/values*/strings.xml").forEach { path ->
            val source = get(path).readText()
            if (!appNameElementPattern.containsMatchIn(source)) return@forEach

            document(path).use { strings ->
                val resources = strings.getElementsByTagName("string")
                val appName = (0 until resources.length)
                    .mapNotNull { resources.item(it) as? Element }
                    .firstOrNull { it.getAttribute("name") == "app_name" }
                if (appName != null) {
                    appName.textContent = "Spotify"
                    foundAppName = true
                }
            }
        }
        check(foundAppName) { "MySpoti app_name string was not found" }

        // Update every launcher foreground. MySpoti has green, blue, purple, pink, and
        // orange variants used by its launcher aliases and supporter-icon previews; all
        // should show the same Spotify brand mark after this patch.
        listOf(
            "res/drawable/ic_launcher_foreground.xml",
            "res/drawable/ic_launcher_foreground_blue.xml",
            "res/drawable/ic_launcher_foreground_purple.xml",
            "res/drawable/ic_launcher_foreground_pink.xml",
            "res/drawable/ic_launcher_foreground_orange.xml",
        ).forEach { path -> get(path).writeText(spotifyForeground) }

        // Android themed icons, the About/supporter previews, splash screen, and widget
        // consume the shared resources above or the monochrome layer below.
        get("res/drawable/ic_launcher_monochrome.xml").writeText(spotifyMonochrome)

        // The small status-bar / media notification mark is a separate resource, so patch
        // it independently rather than leaving the old MySpoti wave in notifications.
        get("res/drawable/msp_ic_notification.xml").writeText(spotifyNotification)
    }
}
