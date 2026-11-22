package com.sxsbsxs.filesniffing;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Platform-independent URL/MIME resource classification.
 *
 * Kotlin owns Android state and UI; this Java component owns reusable
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
        DOCUMENT,
        ARCHIVE,
        APPLICATION,
        FONT,
        TEXT_DATA,
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

        public String getUrl() { return url; }
        public Kind getKind() { return kind; }
        public String getFileName() { return fileName; }
        public String getMimeType() { return mimeType; }
    }

    /** Backwards-compatible entry point for existing callers and offline tests. */
    public static Detection detect(String rawUrl, String hintedKind, String hintedMime) {
        return detect(rawUrl, hintedKind, hintedMime, null);
    }

    /**
     * Detect a resource and optionally use an HTML download attribute as the
     * preferred filename. The hint is reduced to a safe basename before use.
     */
    public static Detection detect(
            String rawUrl,
            String hintedKind,
            String hintedMime,
            String hintedFileName
    ) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;

        final URI parsed;
        try {
            parsed = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        String scheme = parsed.getScheme();
        String host = parsed.getHost();
        if (scheme == null || host == null || host.isEmpty()) return null;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) return null;

        String rawPath = parsed.getRawPath();
        String rawName = rawPath == null ? "" : rawPath.substring(rawPath.lastIndexOf('/') + 1);
        String decodedName;
        try {
            decodedName = URLDecoder.decode(
                    rawName.replace("+", "%2B"),
                    StandardCharsets.UTF_8.name()
            );
        } catch (Exception ignored) {
            decodedName = rawName;
        }

        String safeHint = sanitizeOptionalName(hintedFileName);
        String candidateName = safeHint.isEmpty() ? decodedName : safeHint;
        String extension = extensionOf(candidateName);
        String mime = normalizeMime(hintedMime);
        Kind kind = classify(extension, mime, hintedKind, !safeHint.isEmpty());
        if (kind == null) return null;

        String inferredExtension = extension.isEmpty() ? inferExtension(mime, kind) : "";
        String fallbackName = "download" + (inferredExtension.isEmpty() ? "" : "." + inferredExtension);
        String safeName = FileNameSanitizer.sanitize(candidateName, fallbackName);
        if (extensionOf(safeName).isEmpty() && !inferredExtension.isEmpty()) {
            safeName += "." + inferredExtension;
        }

        return new Detection(parsed.toString(), kind, safeName, mime);
    }

    private static Kind classify(
            String extension,
            String mime,
            String hintedKind,
            boolean hasFilenameHint
    ) {
        if ("m3u8".equals(extension) || "mpd".equals(extension)
                || contains(mime, "mpegurl") || contains(mime, "dash+xml")) {
            return Kind.PLAYLIST;
        }
        if (isOneOf(extension, "ts", "m4s", "cmfv", "cmfa")) {
            return Kind.SEGMENT;
        }
        if (isOneOf(extension, "mp4", "webm", "mov", "m4v", "3gp", "mkv", "avi", "mpeg", "mpg")
                || startsWith(mime, "video/")
                || equalsIgnoreCase(hintedKind, "video")) {
            return Kind.VIDEO;
        }
        if (isOneOf(extension, "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus",
                        "wma", "aiff", "aif", "mid", "midi")
                || startsWith(mime, "audio/")
                || equalsIgnoreCase(hintedKind, "audio")) {
            return Kind.AUDIO;
        }
        if (isOneOf(extension, "jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "svg", "tif", "tiff")
                || startsWith(mime, "image/")
                || equalsIgnoreCase(hintedKind, "image")) {
            return Kind.IMAGE;
        }
        if (isDocumentExtension(extension) || isDocumentMime(mime)) {
            return Kind.DOCUMENT;
        }
        if (isArchiveExtension(extension) || isArchiveMime(mime)) {
            return Kind.ARCHIVE;
        }
        if (isApplicationExtension(extension) || isApplicationMime(mime)) {
            return Kind.APPLICATION;
        }
        if (isFontExtension(extension) || isFontMime(mime)) {
            return Kind.FONT;
        }
        if (isTextDataExtension(extension)
                || (equalsIgnoreCase(hintedKind, "file") && isBrowserAssetExtension(extension))
                || isTextDataMime(mime)) {
            return Kind.TEXT_DATA;
        }
        if (isOneOf(extension, "bin", "dat", "swf", "wasm")
                || isGenericBinaryMime(mime)
                || equalsIgnoreCase(hintedKind, "file") && hasFilenameHint) {
            return Kind.OTHER;
        }
        return null;
    }

    private static boolean isDocumentExtension(String ext) {
        return isOneOf(ext, "pdf", "epub", "mobi", "azw", "azw3", "doc", "docx", "xls",
                "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "wps", "pages",
                "numbers", "key");
    }

    private static boolean isDocumentMime(String mime) {
        return isOneOf(mime,
                "application/pdf",
                "application/epub+zip",
                "application/x-mobipocket-ebook",
                "application/msword",
                "application/vnd.ms-excel",
                "application/vnd.ms-powerpoint",
                "application/rtf",
                "text/rtf",
                "application/vnd.oasis.opendocument.text",
                "application/vnd.oasis.opendocument.spreadsheet",
                "application/vnd.oasis.opendocument.presentation",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    private static boolean isArchiveExtension(String ext) {
        return isOneOf(ext, "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "tbz2",
                "xz", "txz", "zst", "lz", "lz4", "cab", "iso");
    }

    private static boolean isArchiveMime(String mime) {
        return isOneOf(mime,
                "application/zip",
                "application/x-zip-compressed",
                "application/vnd.rar",
                "application/x-rar-compressed",
                "application/x-7z-compressed",
                "application/x-tar",
                "application/gzip",
                "application/x-gzip",
                "application/x-bzip2",
                "application/x-xz",
                "application/zstd",
                "application/x-lz4");
    }

    private static boolean isApplicationExtension(String ext) {
        return isOneOf(ext, "apk", "aab", "apks", "xapk", "ipa", "exe", "msi", "dmg",
                "deb", "rpm", "appimage", "jar", "war");
    }

    private static boolean isApplicationMime(String mime) {
        return isOneOf(mime,
                "application/vnd.android.package-archive",
                "application/x-android-package-archive",
                "application/java-archive",
                "application/x-msdownload",
                "application/x-msi",
                "application/x-debian-package",
                "application/x-rpm");
    }

    private static boolean isFontExtension(String ext) {
        return isOneOf(ext, "ttf", "otf", "woff", "woff2", "eot");
    }

    private static boolean isFontMime(String mime) {
        return startsWith(mime, "font/")
                || isOneOf(mime, "application/font-woff", "application/x-font-ttf",
                        "application/x-font-otf", "application/vnd.ms-fontobject");
    }

    private static boolean isTextDataExtension(String ext) {
        return isOneOf(ext, "txt", "csv", "tsv", "json", "xml", "yaml", "yml", "toml",
                "ini", "conf", "cfg", "md", "markdown", "log", "properties", "sql",
                "py", "java", "kt", "kts", "c", "h", "cc", "cpp", "hpp", "cs", "go",
                "rs", "sh", "bash", "bat", "ps1", "rb", "php", "swift", "dart",
                "gradle", "groovy", "ipynb");
    }

    /** Web assets are listed only when an anchor explicitly marks them as downloads. */
    private static boolean isBrowserAssetExtension(String ext) {
        return isOneOf(ext, "js", "mjs", "jsx", "tsx", "css", "html", "htm", "map");
    }

    private static boolean isTextDataMime(String mime) {
        return isOneOf(mime, "text/plain", "text/csv", "text/tab-separated-values",
                "text/markdown", "text/xml", "application/json", "application/xml",
                "application/yaml", "application/x-yaml");
    }

    private static boolean isGenericBinaryMime(String mime) {
        return isOneOf(mime, "application/octet-stream", "application/x-download",
                "application/download", "binary/octet-stream");
    }

    private static String normalizeMime(String mime) {
        if (mime == null) return null;
        String normalized = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String inferExtension(String mime, Kind kind) {
        if (mime == null) {
            switch (kind) {
                case AUDIO: return "mp3";
                case IMAGE: return "jpg";
                case VIDEO: return "mp4";
                case PLAYLIST: return "m3u8";
                case SEGMENT: return "ts";
                default: return "";
            }
        }

        if (contains(mime, "mpegurl")) return "m3u8";
        if (contains(mime, "dash+xml")) return "mpd";
        if (contains(mime, "pdf")) return "pdf";
        if (contains(mime, "epub")) return "epub";
        if (contains(mime, "wordprocessingml.document")) return "docx";
        if (contains(mime, "spreadsheetml.sheet")) return "xlsx";
        if (contains(mime, "presentationml.presentation")) return "pptx";
        if (equalsIgnoreCase(mime, "application/msword")) return "doc";
        if (equalsIgnoreCase(mime, "application/vnd.ms-excel")) return "xls";
        if (equalsIgnoreCase(mime, "application/vnd.ms-powerpoint")) return "ppt";
        if (contains(mime, "opendocument.text")) return "odt";
        if (contains(mime, "opendocument.spreadsheet")) return "ods";
        if (contains(mime, "opendocument.presentation")) return "odp";
        if (contains(mime, "rtf")) return "rtf";
        if (contains(mime, "7z")) return "7z";
        if (contains(mime, "rar")) return "rar";
        if (contains(mime, "zip")) return "zip";
        if (contains(mime, "gzip")) return "gz";
        if (contains(mime, "x-tar")) return "tar";
        if (contains(mime, "x-bzip2")) return "bz2";
        if (contains(mime, "x-xz")) return "xz";
        if (contains(mime, "android.package-archive")) return "apk";
        if (contains(mime, "java-archive")) return "jar";
        if (contains(mime, "x-msdownload")) return "exe";
        if (contains(mime, "woff2")) return "woff2";
        if (contains(mime, "woff")) return "woff";
        if (contains(mime, "font-ttf")) return "ttf";
        if (contains(mime, "font-otf")) return "otf";
        if (contains(mime, "json")) return "json";
        if (contains(mime, "csv")) return "csv";
        if (contains(mime, "xml")) return "xml";
        if (contains(mime, "yaml")) return "yaml";
        if (contains(mime, "plain")) return "txt";
        if (contains(mime, "webp")) return "webp";
        if (contains(mime, "png")) return "png";
        if (contains(mime, "jpeg")) return "jpg";
        if (contains(mime, "gif")) return "gif";
        if (contains(mime, "avif")) return "avif";
        if (contains(mime, "svg")) return "svg";
        if (contains(mime, "quicktime")) return "mov";
        if (contains(mime, "3gpp")) return "3gp";
        if (contains(mime, "matroska")) return "mkv";
        if (contains(mime, "webm")) return kind == Kind.AUDIO ? "webm" : "webm";
        if (contains(mime, "mp4")) return kind == Kind.AUDIO ? "m4a" : "mp4";
        if (contains(mime, "mpeg")) return kind == Kind.AUDIO ? "mp3" : "mpeg";
        if (contains(mime, "flac")) return "flac";
        if (contains(mime, "ogg")) return "ogg";
        if (contains(mime, "opus")) return "opus";
        if (contains(mime, "wav")) return "wav";
        if (contains(mime, "aac")) return "aac";
        return "";
    }

    private static String sanitizeOptionalName(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return FileNameSanitizer.sanitize(value, "");
    }

    private static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1
                ? ""
                : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isOneOf(String value, String... candidates) {
        if (value == null) return false;
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
