package com.sxsbsxs.filesniffing.data.extractor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpOutputFilePolicyTest {
    @Test
    fun acceptsOnlyTheExactFinalBasenameAndKnownMediaExtensions() {
        val directory = createTempDirectory()
        try {
            val prefix = "ytdlp-session"
            val final = File(directory, "$prefix.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val videoTrack = File(directory, "$prefix.f137.mp4").apply { writeBytes(byteArrayOf(1)) }
            val audioTrack = File(directory, "$prefix.f140.m4a").apply { writeBytes(byteArrayOf(2)) }
            val tempMux = File(directory, "$prefix.temp.mp4").apply { writeBytes(byteArrayOf(3)) }
            val partial = File(directory, "$prefix.mp4.part").apply { writeBytes(byteArrayOf(4)) }
            val metadata = File(directory, "$prefix.info.json").apply { writeBytes(byteArrayOf(5)) }

            assertTrue(YtDlpOutputFilePolicy.isFinalOutputCandidate(final, prefix))
            assertFalse(YtDlpOutputFilePolicy.isFinalOutputCandidate(videoTrack, prefix))
            assertFalse(YtDlpOutputFilePolicy.isFinalOutputCandidate(audioTrack, prefix))
            assertFalse(YtDlpOutputFilePolicy.isFinalOutputCandidate(tempMux, prefix))
            assertFalse(YtDlpOutputFilePolicy.isFinalOutputCandidate(partial, prefix))
            assertFalse(YtDlpOutputFilePolicy.isFinalOutputCandidate(metadata, prefix))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun interruptedOutputIsRemovedButResumableStreamPartsAreKept() {
        val directory = createTempDirectory()
        try {
            val prefix = "ytdlp-session"
            val partialMux = File(directory, "$prefix.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val videoTrack = File(directory, "$prefix.f137.mp4").apply { writeBytes(byteArrayOf(1)) }
            val audioTrack = File(directory, "$prefix.f140.m4a").apply { writeBytes(byteArrayOf(2)) }
            val videoPart = File(directory, "$prefix.f137.mp4.part").apply { writeBytes(byteArrayOf(3)) }

            YtDlpOutputFilePolicy.prepareForDownload(directory, prefix)

            assertFalse(partialMux.exists())
            assertTrue(videoTrack.exists())
            assertTrue(audioTrack.exists())
            assertTrue(videoPart.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun onlyAnOutputMarkedAfterSuccessfulCompletionCanBeReused() {
        val directory = createTempDirectory()
        try {
            val prefix = "ytdlp-session"
            val final = File(directory, "$prefix.mp4").apply {
                writeBytes(ByteArray(128) { it.toByte() })
            }
            val rawVideo = File(directory, "$prefix.f137.mp4").apply {
                writeBytes(ByteArray(1024) { it.toByte() })
            }

            assertNull(YtDlpOutputFilePolicy.findCompletedOutput(directory, prefix))

            YtDlpOutputFilePolicy.markCompleted(directory, prefix, final)
            val reused = YtDlpOutputFilePolicy.findCompletedOutput(directory, prefix)
            assertNotNull(reused)
            assertEquals(final.name, reused?.name)
            assertEquals(128L, reused?.length())

            // A modified/truncated output invalidates the completion marker.
            final.appendBytes(byteArrayOf(9))
            assertNull(YtDlpOutputFilePolicy.findCompletedOutput(directory, prefix))
            assertTrue(rawVideo.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File =
        Files.createTempDirectory("ytdlp-output-policy").toFile()
}
