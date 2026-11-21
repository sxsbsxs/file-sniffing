package com.sxsbsxs.filesniffing;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Classifies YouTube-related WebView requests without retaining URLs or
 * reading sensitive header values. This is diagnostic metadata, not an extractor.
 */
public final class YouTubeRequestDiagnostics {
    private YouTubeRequestDiagnostics() {}

    public enum RequestType {
        NOT_RELATED,
        YOUTUBE_PAGE,
        PLAYER_API,
        GOOGLE_VIDEO
    }

    public static final class Observation {
        private final RequestType requestType;
        private final boolean cookieHeaderPresent;
        private final boolean authorizationHeaderPresent;

        private Observation(
                RequestType requestType,
                boolean cookieHeaderPresent,
                boolean authorizationHeaderPresent
        ) {
            this.requestType = requestType;
            this.cookieHeaderPresent = cookieHeaderPresent;
            this.authorizationHeaderPresent = authorizationHeaderPresent;
        }

        public RequestType getRequestType() {
            return requestType;
        }

        public boolean isYouTubeRelated() {
            return requestType != RequestType.NOT_RELATED;
        }

        public boolean isPlayerApi() {
            return requestType == RequestType.PLAYER_API;
        }

        public boolean isGoogleVideo() {
            return requestType == RequestType.GOOGLE_VIDEO;
        }

        public boolean isCookieHeaderPresent() {
            return cookieHeaderPresent;
        }

        public boolean isAuthorizationHeaderPresent() {
            return authorizationHeaderPresent;
        }
    }

    public static boolean isYouTubePageUrl(String rawUrl) {
        Observation observation = inspect(rawUrl, null);
        return observation.requestType == RequestType.YOUTUBE_PAGE
                || observation.requestType == RequestType.PLAYER_API;
    }

    public static Observation inspect(String rawUrl, Map<String, String> requestHeaders) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return emptyObservation();
        }

        final URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (Exception ignored) {
            return emptyObservation();
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return emptyObservation();
        }
        host = host.toLowerCase(Locale.ROOT);

        RequestType requestType;
        if (isGoogleVideoHost(host)) {
            requestType = RequestType.GOOGLE_VIDEO;
        } else if (isYouTubeHost(host)) {
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            requestType = path.equals("/youtubei/v1/player")
                    || path.startsWith("/youtubei/v1/player/")
                    ? RequestType.PLAYER_API
                    : RequestType.YOUTUBE_PAGE;
        } else {
            return emptyObservation();
        }

        // Only header names are inspected. Values can contain session credentials.
        boolean cookiePresent = containsHeader(requestHeaders, "cookie");
        boolean authorizationPresent = containsHeader(requestHeaders, "authorization");
        return new Observation(requestType, cookiePresent, authorizationPresent);
    }

    private static boolean containsHeader(Map<String, String> headers, String expected) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        for (String name : headers.keySet()) {
            if (name != null && name.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isYouTubeHost(String host) {
        return host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("youtu.be")
                || host.equals("youtube-nocookie.com")
                || host.endsWith(".youtube-nocookie.com")
                || host.equals("youtube.googleapis.com")
                || host.endsWith(".youtube.googleapis.com")
                || host.equals("youtubei.googleapis.com")
                || host.endsWith(".youtubei.googleapis.com");
    }

    private static boolean isGoogleVideoHost(String host) {
        return host.equals("googlevideo.com") || host.endsWith(".googlevideo.com");
    }

    private static Observation emptyObservation() {
        return new Observation(RequestType.NOT_RELATED, false, false);
    }
}
