package com.fyp.crowdlink.domain.model

import org.junit.Assert.*
import org.junit.Test

class FriendTest {

    @Test
    fun `Friend entity holds correct values`() {
        val friend = Friend(
            deviceId = "ABC123",
            displayName = "Test Friend",
            pairedAt = 1234567890L,
            lastSeen = 1234567900L
        )

        assertEquals("ABC123", friend.deviceId)
        assertEquals("Test Friend", friend.displayName)
        assertEquals(1234567890L, friend.pairedAt)
        assertEquals(1234567900L, friend.lastSeen)
    }
}
