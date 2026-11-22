package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpUpdatePolicyTest {
    private val week = YtDlpUpdatePolicy.UPDATE_INTERVAL_MILLIS
    private val retryCooldown = YtDlpUpdatePolicy.RETRY_COOLDOWN_MILLIS

    @Test
    fun firstLaunchUpdatesBundledExecutable() {
        assertTrue(
            YtDlpUpdatePolicy.shouldAttemptUpdate(
                nowMillis = 1_000L,
                lastAttemptMillis = 0L,
                lastSuccessMillis = 0L,
                hasInstalledVersion = false
            )
        )
    }

    @Test
    fun successfulUpdateIsNotCheckedAgainBeforeAWeek() {
        assertFalse(
            YtDlpUpdatePolicy.shouldAttemptUpdate(
                nowMillis = 10_000L + week - 1L,
                lastAttemptMillis = 10_000L,
                lastSuccessMillis = 10_000L,
                hasInstalledVersion = true
            )
        )
    }

    @Test
    fun successfulUpdateIsCheckedAgainAfterAWeek() {
        assertTrue(
            YtDlpUpdatePolicy.shouldAttemptUpdate(
                nowMillis = 10_000L + week,
                lastAttemptMillis = 10_000L,
                lastSuccessMillis = 10_000L,
                hasInstalledVersion = true
            )
        )
    }

    @Test
    fun failedUpdateWaitsForRetryCooldown() {
        assertFalse(
            YtDlpUpdatePolicy.shouldAttemptUpdate(
                nowMillis = 10_000L + retryCooldown - 1L,
                lastAttemptMillis = 10_000L,
                lastSuccessMillis = 0L,
                hasInstalledVersion = false
            )
        )
        assertTrue(
            YtDlpUpdatePolicy.shouldAttemptUpdate(
                nowMillis = 10_000L + retryCooldown,
                lastAttemptMillis = 10_000L,
                lastSuccessMillis = 0L,
                hasInstalledVersion = false
            )
        )
    }
}
