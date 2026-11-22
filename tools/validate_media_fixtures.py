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
    """The resolver must accept shared text URLs, not register as a web browser."""
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

    send_plain_text = False
    has_launcher = False
    has_view_filter = False
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
            has_view_filter = True
        if "android.intent.action.SEND" in actions:
            send_plain_text = any(
                element.get(android_ns + "mimeType") == "text/plain"
                for element in data_elements
            )
        if (
            "android.intent.action.MAIN" in actions
            and "android.intent.category.LAUNCHER" in categories
        ):
            has_launcher = True

    if has_view_filter:
        raise ValueError("MainActivity must not register as an HTTP/HTTPS webpage browser")
    if not send_plain_text:
        raise ValueError("MainActivity must retain its text/plain SEND filter")
    if not has_launcher:
        raise ValueError("MainActivity must retain its launcher intent")


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

        var pdf = ResourceClassifier.detect(
            "https://cdn.example/guide.pdf?signature=private",
            null,
            null
        );
        check(ResourceClassifier.Kind.DOCUMENT, pdf.getKind());
        check("guide.pdf", pdf.getFileName());

        var extensionlessPdf = ResourceClassifier.detect(
            "https://cdn.example/download?id=42",
            null,
            "application/pdf; charset=binary"
        );
        check(ResourceClassifier.Kind.DOCUMENT, extensionlessPdf.getKind());
        check("download.pdf", extensionlessPdf.getFileName());

        var officeDocument = ResourceClassifier.detect(
            "https://cdn.example/report.docx",
            null,
            "application/octet-stream"
        );
        check(ResourceClassifier.Kind.DOCUMENT, officeDocument.getKind());

        var archive = ResourceClassifier.detect(
            "https://cdn.example/backup.7z",
            null,
            null
        );
        check(ResourceClassifier.Kind.ARCHIVE, archive.getKind());

        var extensionlessArchive = ResourceClassifier.detect(
            "https://cdn.example/export?id=8",
            null,
            "application/zip"
        );
        check(ResourceClassifier.Kind.ARCHIVE, extensionlessArchive.getKind());
        check("export.zip", extensionlessArchive.getFileName());

        var apk = ResourceClassifier.detect(
            "https://cdn.example/app.apk",
            null,
            "application/vnd.android.package-archive"
        );
        check(ResourceClassifier.Kind.APPLICATION, apk.getKind());

        var font = ResourceClassifier.detect(
            "https://cdn.example/typeface.woff2",
            null,
            "font/woff2"
        );
        check(ResourceClassifier.Kind.FONT, font.getKind());

        var json = ResourceClassifier.detect(
            "https://cdn.example/catalog.json",
            null,
            "application/json"
        );
        check(ResourceClassifier.Kind.TEXT_DATA, json.getKind());

        var sourceCode = ResourceClassifier.detect(
            "https://cdn.example/tool.cpp",
            null,
            "text/plain"
        );
        check(ResourceClassifier.Kind.TEXT_DATA, sourceCode.getKind());

        var downloadName = ResourceClassifier.detect(
            "https://cdn.example/get?id=91",
            "file",
            null,
            "../../final-report.pdf"
        );
        check(ResourceClassifier.Kind.DOCUMENT, downloadName.getKind());
        check("final-report.pdf", downloadName.getFileName());

        var extensionlessDownload = ResourceClassifier.detect(
            "https://cdn.example/get?id=92",
            "file",
            null,
            "exported-data"
        );
        check(ResourceClassifier.Kind.OTHER, extensionlessDownload.getKind());
        check("exported-data", extensionlessDownload.getFileName());

        check(null, ResourceClassifier.detect(
            "https://cdn.example/app.js",
            null,
            "application/javascript"
        ));
        check(null, ResourceClassifier.detect(
            "https://cdn.example/page.html",
            null,
            "text/html"
        ));

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
            "json": b'  {"error":"access denied","status":403}',
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
    print("URL-only share/launcher intent filters: OK")
    validate_java_resource_classifier()
    print("Java resource classifier: OK")
    validate_java_filename_policy()
    print("Java filename policy: OK")
    validate_native_probe()
    print("C++ media signature probe: OK")


if __name__ == "__main__":
    main()
