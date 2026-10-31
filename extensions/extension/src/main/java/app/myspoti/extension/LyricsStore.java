package app.myspoti.extension;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bridge to MySpoti 2.2.0's own LyricsApi and its private offline cache.
 *
 * APK mappings verified from the supplied release DEX:
 * LyricsApi = Lpg4; LyricsApi.Track = Lfg4; cache = Lpg4.b;
 * in-flight requests = Lpg4.c; prefetch(Track) = Lpg4.n(Lfg4;).
 *
 * The private JSON schema and SHA-1 filename match LyricsApi.LyricsDisk, while
 * portable JSON and LRC sidecars make lyrics survive uninstall and work in other players.
 */
final class LyricsStore {
    private static final String TAG = "MyspotiStoragePatch";
    private static final String API_CLASS = "Lpg4";
    private static final String TRACK_CLASS = "Lfg4";
    private static final String LYRICS_ENTITY = "com.music.spotui.data.entity.Lyrics";
    private static final long PREFETCH_WAIT_MS = 8_000L;
    private static final long NATIVE_CACHE_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L;

    private LyricsStore() {}

    /** Obtain lyrics from the native cache or the same asynchronous resolver used by the player. */
    static JSONObject obtain(Context context, String trackId, String title, String artist,
                             String album, long durationMs, String coverUrl) {
        if (trackId == null || trackId.trim().isEmpty()) return null;
        String key = trackId.trim();
        try {
            JSONObject disk = readNativeCache(context, key);
            if (disk != null) {
                disk.put("at", System.currentTimeMillis());
                writeNativeCache(context, key, disk);
                return disk;
            }

            Class<?> api = Class.forName(API_CLASS);
            Class<?> trackType = Class.forName(TRACK_CLASS);
            Constructor<?> constructor = trackType.getDeclaredConstructor(long.class, String.class,
                    String.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            // Compact constructor order in this APK: duration, id, title, artist, album, cover URL.
            Object track = constructor.newInstance(Math.max(0L, durationMs), key,
                    safe(title), safe(artist), safe(album), safe(coverUrl));
            Method keyMethod = trackType.getDeclaredMethod("a");
            keyMethod.setAccessible(true);
            String cacheKey = String.valueOf(keyMethod.invoke(track));

            Map<?, ?> cache = staticMap(api, "b");
            Map<?, ?> inFlight = staticMap(api, "c");
            Object cached = cache == null ? null : cache.get(cacheKey);
            if (cached != null && isLyrics(cached)) {
                JSONObject encoded = encodeRuntimeLyrics(cached, key);
                if (encoded != null) {
                    writeNativeCache(context, key, encoded);
                    return encoded;
                }
            }

            // LyricsApi remembers a negative lookup for two minutes. Clear only this track's miss.
            if (cache != null && cached != null && !isLyrics(cached)
                    && (inFlight == null || !inFlight.containsKey(cacheKey))) {
                try { cache.remove(cacheKey); } catch (Throwable ignored) {}
            }

            boolean alreadyRunning = inFlight != null && inFlight.containsKey(cacheKey);
            if (!alreadyRunning) {
                Method prefetch = api.getDeclaredMethod("n", trackType);
                prefetch.setAccessible(true);
                prefetch.invoke(null, track);
            }

            long deadline = System.currentTimeMillis() + PREFETCH_WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                JSONObject onDisk = readNativeCache(context, key);
                if (onDisk != null) {
                    onDisk.put("at", System.currentTimeMillis());
                    writeNativeCache(context, key, onDisk);
                    return onDisk;
                }
                Object resolved = cache == null ? null : cache.get(cacheKey);
                if (resolved != null && isLyrics(resolved)) {
                    JSONObject encoded = encodeRuntimeLyrics(resolved, key);
                    if (encoded != null) {
                        // MySpoti normally persists only synced lyrics; keep complete static lyrics too.
                        writeNativeCache(context, key, encoded);
                        return encoded;
                    }
                }
                try { Thread.sleep(200L); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            Log.i(TAG, "Lyrics not available yet for " + safe(title) + "; a later sync can retry");
        } catch (Throwable t) {
            Log.w(TAG, "Could not obtain lyrics for " + safe(title), t);
        }
        return null;
    }

    private static boolean isLyrics(Object value) {
        return value != null && LYRICS_ENTITY.equals(value.getClass().getName());
    }

    private static Map<?, ?> staticMap(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof Map ? (Map<?, ?>) value : null;
        } catch (Throwable ignored) { return null; }
    }

    private static JSONObject encodeRuntimeLyrics(Object value, String key) throws Exception {
        if (!booleanProperty(value, "uncapped")) return null; // Never export a preview as complete lyrics.
        Object rawLines = property(value, "lines");
        if (!(rawLines instanceof Iterable)) return null;
        JSONArray lines = new JSONArray();
        for (Object line : (Iterable<?>) rawLines) {
            if (line == null) continue;
            long time = longProperty(line, "timeMs");
            String text = stringProperty(line, "text");
            if (text.trim().isEmpty()) continue;
            JSONObject item = new JSONObject().put("t", time).put("x", text);
            Object rawWords = property(line, "words");
            if (rawWords instanceof Iterable) {
                JSONArray words = new JSONArray();
                for (Object word : (Iterable<?>) rawWords) {
                    if (word == null) continue;
                    words.put(new JSONArray().put(longProperty(word, "startMs"))
                            .put(longProperty(word, "endMs")).put(stringProperty(word, "text")));
                }
                if (words.length() > 0) item.put("w", words);
            }
            lines.put(item);
        }
        if (lines.length() == 0) return null;

        JSONObject encoded = new JSONObject();
        encoded.put("synced", booleanProperty(value, "synced"));
        Object language = property(value, "language");
        Object provider = property(value, "provider");
        encoded.put("language", language == null ? JSONObject.NULL : String.valueOf(language));
        encoded.put("provider", provider == null ? JSONObject.NULL : String.valueOf(provider));
        Object colors = property(value, "colors");
        if (colors != null) {
            encoded.put("colors", new JSONArray().put(intProperty(colors, "background"))
                    .put(intProperty(colors, "text")).put(intProperty(colors, "highlight")));
        } else encoded.put("colors", JSONObject.NULL);
        encoded.put("lines", lines);
        encoded.put("key", key);
        encoded.put("at", System.currentTimeMillis());
        return encoded;
    }

    private static Object property(Object owner, String name) throws Exception {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (NoSuchFieldException ignored) {
            String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Method method = owner.getClass().getMethod(getter);
            method.setAccessible(true);
            return method.invoke(owner);
        }
    }

    private static String stringProperty(Object owner, String name) throws Exception {
        Object value = property(owner, name);
        return value == null ? "" : String.valueOf(value);
    }

    private static long longProperty(Object owner, String name) throws Exception {
        Object value = property(owner, name);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static int intProperty(Object owner, String name) throws Exception {
        Object value = property(owner, name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean booleanProperty(Object owner, String name) throws Exception {
        return Boolean.TRUE.equals(property(owner, name));
    }

    static JSONObject readNativeCache(Context context, String key) {
        try {
            File file = nativeCacheFile(context, key);
            if (!file.isFile() || file.length() <= 0L || file.length() > 8L * 1024L * 1024L) return null;
            JSONObject object = new JSONObject(readText(file));
            long savedAt = object.optLong("at", 0L);
            if (savedAt <= 0L || System.currentTimeMillis() - savedAt > NATIVE_CACHE_MAX_AGE_MS) {
                file.delete();
                return null;
            }
            return key.equals(object.optString("key", "")) && validLyricsJson(object) ? object : null;
        } catch (Throwable ignored) { return null; }
    }

    static boolean restoreNativeCache(Context context, String key, JSONObject portable) {
        if (portable == null || key == null || key.trim().isEmpty()) return false;
        try {
            JSONObject copy = new JSONObject(portable.toString());
            copy.put("key", key);
            copy.put("at", System.currentTimeMillis());
            if (!validLyricsJson(copy)) return false;
            writeNativeCache(context, key, copy);
            invalidateMemoryCache(key);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Could not restore native lyrics for " + key, t);
            return false;
        }
    }

    private static void invalidateMemoryCache(String key) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Map<?, ?> inFlight = staticMap(api, "c");
            Object pending = inFlight == null ? null : inFlight.remove(key);
            if (pending != null) {
                try {
                    for (Method method : pending.getClass().getMethods()) {
                        if (!"cancel".equals(method.getName())) continue;
                        Class<?>[] parameters = method.getParameterTypes();
                        method.setAccessible(true);
                        if (parameters.length == 0) {
                            method.invoke(pending);
                            break;
                        }
                        if (parameters.length == 1 && java.util.concurrent.CancellationException.class
                                .isAssignableFrom(parameters[0])) {
                            method.invoke(pending, new java.util.concurrent.CancellationException("Lyrics restored from portable archive"));
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            Map<?, ?> cache = staticMap(api, "b");
            if (cache != null) cache.remove(key);
        } catch (Throwable t) {
            Log.w(TAG, "Could not invalidate the in-memory lyrics entry for " + key, t);
        }
    }

    static JSONObject fromLrc(String key, String lrc, String provider) {
        if (lrc == null || lrc.trim().isEmpty()) return null;
        try {
            JSONArray lines = new JSONArray();
            boolean anyTimed = false;
            String[] rawLines = lrc.replace("\r", "").split("\n");
            java.util.regex.Pattern timestamp = java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
            for (String raw : rawLines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.matches("(?i)^\\[(ti|ar|al|by|la|length|re|ve):.*")) continue;
                java.util.regex.Matcher matcher = timestamp.matcher(line);
                List<Long> times = new java.util.ArrayList<>();
                int end = 0;
                while (matcher.find()) {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    String fraction = matcher.group(3);
                    long millis = 0L;
                    if (fraction != null) {
                        if (fraction.length() == 1) millis = Long.parseLong(fraction) * 100L;
                        else if (fraction.length() == 2) millis = Long.parseLong(fraction) * 10L;
                        else millis = Long.parseLong(fraction.substring(0, 3));
                    }
                    times.add(minutes * 60_000L + seconds * 1_000L + millis);
                    end = matcher.end();
                }
                String text = times.isEmpty() ? line : line.substring(end).trim();
                if (text.isEmpty()) continue;
                if (times.isEmpty()) lines.put(new JSONObject().put("t", 0L).put("x", text));
                else {
                    anyTimed = true;
                    for (Long time : times) lines.put(new JSONObject().put("t", time).put("x", text));
                }
            }
            if (lines.length() == 0) return null;
            return new JSONObject().put("synced", anyTimed)
                    .put("language", JSONObject.NULL)
                    .put("provider", safe(provider).isEmpty() ? "LRC" : provider)
                    .put("colors", JSONObject.NULL).put("lines", lines)
                    .put("key", safe(key)).put("at", System.currentTimeMillis());
        } catch (Throwable t) {
            Log.w(TAG, "Could not parse portable LRC lyrics", t);
            return null;
        }
    }

    static String toLrc(JSONObject lyrics, String title, String artist, String album) {
        if (!validLyricsJson(lyrics)) return "";
        StringBuilder out = new StringBuilder();
        appendHeader(out, "ti", title);
        appendHeader(out, "ar", artist);
        appendHeader(out, "al", album);
        appendHeader(out, "by", lyrics.optString("provider", "MySpoti"));
        JSONArray lines = lyrics.optJSONArray("lines");
        boolean synced = lyrics.optBoolean("synced", false);
        if (lines != null) for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.optJSONObject(i);
            if (line == null) continue;
            String text = line.optString("x", "").replace('\r', ' ').replace('\n', ' ').trim();
            if (text.isEmpty()) continue;
            if (synced) out.append(formatTimestamp(Math.max(0L, line.optLong("t", 0L))));
            out.append(text).append('\n');
        }
        return out.toString();
    }

    static String toPlainText(JSONObject lyrics) {
        if (!validLyricsJson(lyrics)) return "";
        StringBuilder out = new StringBuilder();
        JSONArray lines = lyrics.optJSONArray("lines");
        if (lines != null) for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.optJSONObject(i);
            if (line == null) continue;
            String text = line.optString("x", "").trim();
            if (!text.isEmpty()) out.append(text).append('\n');
        }
        return out.toString().trim();
    }

    static boolean validLyricsJson(JSONObject object) {
        if (object == null) return false;
        JSONArray lines = object.optJSONArray("lines");
        if (lines == null || lines.length() == 0) return false;
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.optJSONObject(i);
            if (line != null && !line.optString("x", "").trim().isEmpty()) return true;
        }
        return false;
    }

    private static void appendHeader(StringBuilder out, String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        out.append('[').append(key).append(':').append(value.replace('\n', ' ').replace('\r', ' ').replace("]", "")).append("]\n");
    }

    private static String formatTimestamp(long ms) {
        long totalCentiseconds = Math.max(0L, ms) / 10L;
        long minutes = totalCentiseconds / 6_000L;
        long seconds = (totalCentiseconds / 100L) % 60L;
        long centiseconds = totalCentiseconds % 100L;
        return String.format(Locale.ROOT, "[%02d:%02d.%02d]", minutes, seconds, centiseconds);
    }

    private static File nativeCacheFile(Context context, String key) throws Exception {
        File directory = new File(context.getApplicationContext().getFilesDir(), "lyrics_cache2");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create MySpoti lyrics cache");
        return new File(directory, sha1(key) + ".json");
    }

    private static void writeNativeCache(Context context, String key, JSONObject object) throws Exception {
        JSONObject copy = new JSONObject(object.toString());
        copy.put("key", key);
        copy.put("at", System.currentTimeMillis());
        File destination = nativeCacheFile(context, key);
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(copy.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (!temporary.renameTo(destination)) {
            if (destination.exists() && !destination.delete()) throw new IllegalStateException("Cannot replace MySpoti lyrics cache file");
            if (!temporary.renameTo(destination)) throw new IllegalStateException("Cannot activate MySpoti lyrics cache file");
        }
    }

    private static String readText(File file) throws Exception {
        if (file.length() > 8L * 1024L * 1024L) throw new IllegalArgumentException("Lyrics cache file is too large");
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) if (count > 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String sha1(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
