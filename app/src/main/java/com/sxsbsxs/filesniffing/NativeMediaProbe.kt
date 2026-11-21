package com.sxsbsxs.filesniffing

/**
 * Native signature inspection for downloaded payloads.
 * Media3 remains responsible for decoding and adaptive-stream export.
 */
object NativeMediaProbe {
    init {
        System.loadLibrary("media_probe")
    }

    external fun detectContainer(prefix: ByteArray): String
}
