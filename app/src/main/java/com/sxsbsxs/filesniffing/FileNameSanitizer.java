package com.sxsbsxs.filesniffing;

/**
 * Shared filename policy for remote resource names and local document names.
 * This is intentionally small and Android-independent.
 */
public final class FileNameSanitizer {
    private FileNameSanitizer() {}

    public static String sanitize(String value, String fallback) {
        String safeFallback = fallback == null || fallback.trim().isEmpty()
                ? "download.bin"
                : fallback.trim();
        if (value == null || value.trim().isEmpty()) {
            return safeFallback;
        }

        String normalized = value.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1);
        }

        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            boolean forbidden = current < 0x20 || current == 0x7f ||
                    current == '\\' || current == '/' || current == ':' ||
                    current == '*' || current == '?' || current == '"' ||
                    current == '<' || current == '>' || current == '|';
            result.append(forbidden ? '_' : current);
        }

        String sanitized = result.toString().trim();
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        while (sanitized.endsWith(".")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized.isEmpty() ? safeFallback : sanitized;
    }
}
