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


def main() -> None:
    validate_hls(FIXTURES / "sample_hls.m3u8")
    print("HLS fixture: OK")
    validate_dash(FIXTURES / "sample_dash.mpd")
    print("DASH fixture: OK")
    validate_manifest_link_intents()
    print("Android link intent filters: OK")
    validate_java_filename_policy()
    print("Java filename policy: OK")
    validate_native_probe()
    print("C++ media signature probe: OK")


if __name__ == "__main__":
    main()
