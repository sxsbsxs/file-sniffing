package com.sxsbsxs.filesniffing;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Platform-independent URL/MIME resource classification.
 *
 * Kotlin owns Android state and UI; this Java component owns the reusable
 * resource-type and filename inference policy so it can be tested by the
 * offline JDK regression suite without an Android runtime.
 */
public final class ResourceClassifier {
    private ResourceClassifier() {}

    public enum Kind {
        VIDEO,
        SEGMENT,
        AUDIO,
        IMAGE,
        PLAYLIST,
        OTHER
    }

    public static final class Detection {
        private final String url;
        private final Kind kind;
        private final String fileName;
        private final String mimeType;

        private Detection(String url, Kind kind, String fileName, String mimeType) {
            this.url = url;
            this.kind = kind;
            this.fileName = fileName;
            this.mimeType = mimeType;
        }

        public String getUrl() {
            return url;
        }

        public Kind getKind() {
            return kind;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    public static Detection detect(String rawUrl, String hintedKind, String hintedMime) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return null;
        }

        final URI parsed;
        try {
            parsed = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        String scheme = parsed.getScheme();
        String host = parsed.getHost();
        if (scheme == null || host == null || host.isEmpty()) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }

        String rawPath = parsed.getRawPath();
        String rawName = rawPath == null ? "" : rawPath.substring(rawPath.lastIndexOf('/') + 1);
        if (rawName.isEmpty()) {
            return null;
        }

        String decodedName;
        try {
            // URLDecoder normally maps '+' to a space; preserve literal pluses in URL paths.
            decodedName = URLDecoder.decode(
                    rawName.replace("+", "%2B"),
                    StandardCharsets.UTF_8.name()
            );
        } catch (Exception ignored) {
            decodedName = rawName;
        }

        String extension = extensionOf(decodedName);
        String mime = normalizeMime(hintedMime);
        Kind kind = classify(extension, mime, hintedKind);
        if (kind == null) {
            return null;
        }

        String inferredExtension = extension.isEmpty()
                ? inferExtension(mime, kind)
                : extension;
        String safeName = FileNameSanitizer.sanitize(decodedName, "media_resource");
        String fileName = extensionOf(safeName).isEmpty()
                ? safeName + "." + inferredExtension
                : safeName;

        return new Detection(parsed.toString(), kind, fileName, mime);
    }

    private static Kind classify(String extension, String mime, String hintedKind) {
        if ("m3u8".equals(extension) || "mpd".equals(extension)
                || contains(mime, "mpegurl") || contains(mime, "dash+xml")) {
            return Kind.PLAYLIST;
        }
        if ("ts".equals(extension) || "m4s".equals(extension)) {
            return Kind.SEGMENT;
        }
        if (isOneOf(extension, "mp4", "webm", "mov", "m4v", "3gp", "mkv")
                || startsWith(mime, "video/")
                || equalsIgnoreCase(hintedKind, "video")) {
            return Kind.VIDEO;
        }
        if (isOneOf(extension, "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus")
                || startsWith(mime, "audio/")
                || equalsIgnoreCase(hintedKind, "audio")) {
            return Kind.AUDIO;
        }
        if (isOneOf(extension, "jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
                || startsWith(mime, "image/")
                || equalsIgnoreCase(hintedKind, "image")) {
            return Kind.IMAGE;
        }
        return null;
    }

    private static String normalizeMime(String mime) {
        if (mime == null) {
            return null;
        }
        String normalized = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String inferExtension(String mime, Kind kind) {
        if (contains(mime, "webp")) return "webp";
        if (contains(mime, "png")) return "png";
        if (contains(mime, "jpeg")) return "jpg";
        if (contains(mime, "gif")) return "gif";
        if (contains(mime, "mp4")) return "mp4";
        if (contains(mime, "webm")) return "webm";
        if (contains(mime, "mpegurl")) return "m3u8";
        if (contains(mime, "dash+xml")) return "mpd";
        switch (kind) {
            case AUDIO: return "mp3";
            case IMAGE: return "jpg";
            case VIDEO: return "mp4";
            case PLAYLIST: return "m3u8";
            case SEGMENT: return "ts";
            default: return "bin";
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1
                ? ""
                : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isOneOf(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) return true;
        }
        return false;
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static boolean contains(String value, String fragment) {
        return value != null && value.contains(fragment);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }
}
