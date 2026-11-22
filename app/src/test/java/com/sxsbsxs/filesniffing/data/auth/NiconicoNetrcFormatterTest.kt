package com.sxsbsxs.filesniffing.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class NiconicoNetrcFormatterTest {
    @Test
    fun formatsNiconicoMachineCredentials() {
        assertEquals(
            "machine niconico login \"user@example.com\" password \"pass word\"\n",
            NiconicoNetrcFormatter.format("user@example.com", "pass word")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLineBreaksThatCouldInjectAnotherMachine() {
        NiconicoNetrcFormatter.format("user@example.com", "pass\nmachine youtube login attacker")
    }
}
