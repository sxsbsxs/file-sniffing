#!/usr/bin/env python3
"""Offline CI regression checks for HLS/DASH fixtures and the native C++ probe."""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIXTURES = pathlib.Path(__file__).resolve().parent / "fixtures"
NATIVE_SOURCE = ROOT / "app/src/main/cpp/native_media_probe.cpp"


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def validate_hls(path: pathlib.Path) -> None:
    lines = [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    if not lines or lines[0] != "#EXTM3U":
        raise ValueError(f"{path.name}: missing #EXTM3U header")

    pending_segment = False
    segment_count = 0
    for line in lines[1:]:
        if line.startswith("#EXTINF:"):
            duration = line.split(":", 1)[1].split(",", 1)[0]
            if float(duration) <= 0:
                raise ValueError(f"{path.name}: EXTINF duration must be positive")
            if pending_segment:
                raise ValueError(f"{path.name}: previous EXTINF has no segment URI")
            pending_segment = True
        elif pending_segment and not line.startswith("#"):
            segment_count += 1
            pending_segment = False

    if pending_segment:
        raise ValueError(f"{path.name}: EXTINF entry is missing its segment URI")
    if segment_count == 0:
        raise ValueError(f"{path.name}: no media segments found")


def validate_dash(path: pathlib.Path) -> None:
    root = ET.parse(path).getroot()
    if local_name(root.tag) != "MPD":
        raise ValueError(f"{path.name}: document root is not MPD")
    elements = list(root.iter())
    if not any(local_name(element.tag) == "Period" for element in elements):
        raise ValueError(f"{path.name}: MPD has no Period")
    if not any(local_name(element.tag) == "Representation" for element in elements):
        raise ValueError(f"{path.name}: MPD has no Representation")


def validate_manifest_link_intents() -> None:
    manifest = ROOT / "app/src/main/AndroidManifest.xml"
    root = ET.parse(manifest).getroot()
    android_ns = "{http://schemas.android.com/apk/res/android}"
    application = next(
        (element for element in root if local_name(element.tag) == "application"),
        None,
    )
    if application is None:
        raise ValueError("AndroidManifest.xml: missing application")

    main_activity = next(
        (
            element
            for element in application
            if local_name(element.tag) == "activity"
            and element.get(android_ns + "name") == ".MainActivity"
        ),
        None,
    )
    if main_activity is None:
        raise ValueError("AndroidManifest.xml: MainActivity not registered")

    view_schemes: set[str] = set()
    view_categories: set[str] = set()
    send_plain_text = False
    for intent_filter in main_activity:
        if local_name(intent_filter.tag) != "intent-filter":
            continue
        actions = {
            element.get(android_ns + "name")
            for element in intent_filter
            if local_name(element.tag) == "action"
        }
        categories = {
            element.get(android_ns + "name")
            for element in intent_filter
            if local_name(element.tag) == "category"
        }
        data_elements = [
            element for element in intent_filter
            if local_name(element.tag) == "data"
        ]

        if "android.intent.action.VIEW" in actions:
            view_categories.update(category for category in categories if category)
            view_schemes.update(
                element.get(android_ns + "scheme")
                for element in data_elements
                if element.get(android_ns + "scheme")
            )
        if "android.intent.action.SEND" in actions:
            send_plain_text = any(
                element.get(android_ns + "mimeType") == "text/plain"
                for element in data_elements
            )

    required_categories = {
        "android.intent.category.DEFAULT",
        "android.intent.category.BROWSABLE",
    }
    if not required_categories.issubset(view_categories):
        raise ValueError("MainActivity VIEW filter must be DEFAULT and BROWSABLE")
    if not {"http", "https"}.issubset(view_schemes):
        raise ValueError("MainActivity VIEW filter must accept both HTTP and HTTPS")
    if not send_plain_text:
        raise ValueError("MainActivity must retain its text/plain SEND filter")


def validate_youtube_request_diagnostics() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if javac is None or java is None:
        raise RuntimeError("A JDK is required for YouTube request-diagnostics tests")

    source = ROOT / "app/src/main/java/com/sxsbsxs/filesniffing/YouTubeRequestDiagnostics.java"
    harness = """
import com.sxsbsxs.filesniffing.YouTubeRequestDiagnostics;
import java.util.Map;

public final class YouTubeRequestDiagnosticsTest {
    public static void main(String[] args) {
        var headers = Map.of(
            "Cookie", "COOKIE_SECRET_MUST_NOT_BE_STORED",
            "Authorization", "TOKEN_SECRET_MUST_NOT_BE_STORED"
        );
        var media = YouTubeRequestDiagnostics.inspect(
            "https://r2---sn-a5mekn6l.googlevideo.com/videoplayback?signature=PRIVATE_SIGNATURE",
            headers
        );
        check(YouTubeRequestDiagnostics.RequestType.GOOGLE_VIDEO, media.getRequestType());
        check(true, media.isCookieHeaderPresent());
        check(true, media.isAuthorizationHeaderPresent());

        var player = YouTubeRequestDiagnostics.inspect(
            "https://www.youtube.com/youtubei/v1/player?key=PRIVATE_KEY",
            Map.of()
        );
        check(YouTubeRequestDiagnostics.RequestType.PLAYER_API, player.getRequestType());

        var playerGoogleApi = YouTubeRequestDiagnostics.inspect(
            "https://youtubei.googleapis.com/youtubei/v1/player",
            Map.of()
        );
        check(YouTubeRequestDiagnostics.RequestType.PLAYER_API, playerGoogleApi.getRequestType());

        var shortLink = YouTubeRequestDiagnostics.inspect("https://youtu.be/abc123", Map.of());
        check(true, YouTubeRequestDiagnostics.isYouTubePageUrl("https://youtu.be/abc123"));
        check(YouTubeRequestDiagnostics.RequestType.YOUTUBE_PAGE, shortLink.getRequestType());

        var unrelated = YouTubeRequestDiagnostics.inspect(
            "https://example.com/videoplayback?signature=not-youtube",
            Map.of()
        );
        check(YouTubeRequestDiagnostics.RequestType.NOT_RELATED, unrelated.getRequestType());
        if (media.toString().contains("PRIVATE_SIGNATURE")
                || media.toString().contains("COOKIE_SECRET")
                || media.toString().contains("TOKEN_SECRET")) {
            throw new AssertionError("diagnostic observation leaked request secrets");
        }
    }

    private static void check(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="file-sniffing-youtube-diagnostics-") as temp_dir:
        temp = pathlib.Path(temp_dir)
        harness_file = temp / "YouTubeRequestDiagnosticsTest.java"
        harness_file.write_text(harness, encoding="utf-8")
        subprocess.run(
            [javac, "-d", str(temp), str(source), str(harness_file)],
            check=True,
        )
        subprocess.run(
            [java, "-cp", str(temp), "YouTubeRequestDiagnosticsTest"],
            check=True,
        )


def validate_java_resource_classifier() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if javac is None or java is None:
        raise RuntimeError("A JDK is required for Java resource-classifier tests")

    source_dir = ROOT / "app/src/main/java/com/sxsbsxs/filesniffing"
    classifier_source = source_dir / "ResourceClassifier.java"
    sanitizer_source = source_dir / "FileNameSanitizer.java"
    harness = """
import com.sxsbsxs.filesniffing.ResourceClassifier;

public final class ResourceClassifierTest {
    public static void main(String[] args) {
        var video = ResourceClassifier.detect(
            "https://cdn.example/video.mp4?token=private",
            null,
            null
        );
        check(ResourceClassifier.Kind.VIDEO, video.getKind());
        check("video.mp4", video.getFileName());

        var extensionlessVideo = ResourceClassifier.detect(
            "https://r.example/videoplayback?range=0-100",
            null,
            "video/mp4; codecs=avc1"
        );
        check(ResourceClassifier.Kind.VIDEO, extensionlessVideo.getKind());
        check("videoplayback.mp4", extensionlessVideo.getFileName());
        check("video/mp4", extensionlessVideo.getMimeType());

        var hls = ResourceClassifier.detect(
            "https://cdn.example/master?token=private",
            null,
            "application/vnd.apple.mpegurl"
        );
        check(ResourceClassifier.Kind.PLAYLIST, hls.getKind());
        check("master.m3u8", hls.getFileName());

        var dash = ResourceClassifier.detect(
            "https://cdn.example/manifest?token=private",
            null,
            "application/dash+xml"
        );
        check(ResourceClassifier.Kind.PLAYLIST, dash.getKind());
        check("manifest.mpd", dash.getFileName());

        var segment = ResourceClassifier.detect(
            "https://cdn.example/chunk.m4s?range=0-500",
            null,
            "application/octet-stream"
        );
        check(ResourceClassifier.Kind.SEGMENT, segment.getKind());

        var audio = ResourceClassifier.detect(
            "https://cdn.example/audio-track",
            "audio",
            null
        );
        check(ResourceClassifier.Kind.AUDIO, audio.getKind());
        check("audio-track.mp3", audio.getFileName());

        var image = ResourceClassifier.detect(
            "https://cdn.example/cover",
            null,
            "image/webp"
        );
        check(ResourceClassifier.Kind.IMAGE, image.getKind());
        check("cover.webp", image.getFileName());

        var plusName = ResourceClassifier.detect(
            "https://cdn.example/clip+final.mp4",
            null,
            null
        );
        check("clip+final.mp4", plusName.getFileName());

        var traversal = ResourceClassifier.detect(
            "https://cdn.example/%2e%2e%2fmovie%3f.mp4",
            null,
            null
        );
        check("movie_.mp4", traversal.getFileName());

        check(null, ResourceClassifier.detect("ftp://cdn.example/video.mp4", null, null));
        check(null, ResourceClassifier.detect("https://cdn.example/page", null, null));
    }

    private static void check(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="file-sniffing-resource-classifier-") as temp_dir:
        temp = pathlib.Path(temp_dir)
        harness_file = temp / "ResourceClassifierTest.java"
        harness_file.write_text(harness, encoding="utf-8")
        subprocess.run(
            [javac, "-d", str(temp), str(sanitizer_source), str(classifier_source), str(harness_file)],
            check=True,
        )
        subprocess.run(
            [java, "-cp", str(temp), "ResourceClassifierTest"],
            check=True,
        )


def validate_java_filename_policy() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if javac is None or java is None:
        raise RuntimeError("A JDK is required for Java filename-policy tests")

    source = ROOT / "app/src/main/java/com/sxsbsxs/filesniffing/FileNameSanitizer.java"
    harness = """
import com.sxsbsxs.filesniffing.FileNameSanitizer;

public final class FileNameSanitizerTest {
    public static void main(String[] args) {
        check("video.mp4", FileNameSanitizer.sanitize("../video.mp4", "fallback.bin"));
        check("movie_.mp4", FileNameSanitizer.sanitize("folder/movie?.mp4", "fallback.bin"));
        check("fallback.bin", FileNameSanitizer.sanitize("..", "fallback.bin"));
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="file-sniffing-java-test-") as temp_dir:
        temp = pathlib.Path(temp_dir)
        harness_file = temp / "FileNameSanitizerTest.java"
        harness_file.write_text(harness, encoding="utf-8")
        subprocess.run(
            [javac, "-d", str(temp), str(source), str(harness_file)],
            check=True,
        )
        subprocess.run([java, "-cp", str(temp), "FileNameSanitizerTest"], check=True)


def validate_native_probe() -> None:
    compiler = shutil.which("c++") or shutil.which("g++")
    if compiler is None:
        raise RuntimeError("A C++17 compiler is required for native signature tests")

    with tempfile.TemporaryDirectory(prefix="file-sniffing-native-test-") as temp_dir:
        temp = pathlib.Path(temp_dir)
        executable = temp / "media_probe"
        subprocess.run(
            [compiler, "-std=c++17", "-O2", str(NATIVE_SOURCE), "-o", str(executable)],
            check=True,
        )

        transport_stream = bytearray(377)
        transport_stream[0] = 0x47
        transport_stream[188] = 0x47
        transport_stream[376] = 0x47
        cases = {
            "mp4": bytes.fromhex("000000186674797069736f6d0000020069736f6d69736f32"),
            "mp4-fragment": bytes.fromhex("00000018737479706d7364680000000069736f6d69736f36"),
            "webm": bytes.fromhex("1a45dfa39342868101"),
            "mp3": bytes([0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00]),
            "mpeg-ts": bytes(transport_stream),
            "html": b"<!doctype html><html><body>not media</body></html>",
        }
        for expected, payload in cases.items():
            sample = temp / f"sample.{expected}"
            sample.write_bytes(payload)
            actual = subprocess.run(
                [str(executable), str(sample)],
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
            if actual != expected:
                raise AssertionError(f"native probe: expected {expected}, got {actual}")

        moof_sample = temp / "sample-moof-fragment.bin"
        moof_sample.write_bytes(bytes.fromhex("000000106d6f6f6600000000"))
        moof_actual = subprocess.run(
            [str(executable), str(moof_sample)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        if moof_actual != "mp4-fragment":
            raise AssertionError(f"native probe: expected mp4-fragment, got {moof_actual}")


def main() -> None:
    validate_hls(FIXTURES / "sample_hls.m3u8")
    print("HLS fixture: OK")
    validate_dash(FIXTURES / "sample_dash.mpd")
    print("DASH fixture: OK")
    validate_manifest_link_intents()
    print("Android link intent filters: OK")
    validate_youtube_request_diagnostics()
    print("YouTube request diagnostics: OK")
    validate_java_resource_classifier()
    print("Java resource classifier: OK")
    validate_java_filename_policy()
    print("Java filename policy: OK")
    validate_native_probe()
    print("C++ media signature probe: OK")


if __name__ == "__main__":
    main()
