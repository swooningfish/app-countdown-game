package com.xrmx.countdown_game.platform

import com.xrmx.countdown_game.game.PacketFraming
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PacketLinkTest {
    @Test
    fun sendsSplitPacketsAndJoinsReceivedOnes() = runTest {
        val sent = mutableListOf<ByteArray>()
        val link = PacketLink(packetSize = { 4 }, sendPacket = { sent += it }, onClose = {})
        link.send("hello world")
        assertEquals(4, sent.size)
        assertTrue(sent.all { it.size <= 4 })

        sent.forEach { link.received(it) }
        assertEquals("hello world", link.incoming.receive())
    }

    @Test
    fun sendFailsAndIncomingEndsOnceDisconnected() = runTest {
        var closed = false
        val link = PacketLink(packetSize = { 20 }, sendPacket = {}, onClose = { closed = true })
        link.disconnected()
        assertFailsWith<IllegalStateException> { link.send("hi") }
        assertTrue(link.incoming.isClosedForReceive)
        link.close()
        assertTrue(closed)
    }

    @Test
    fun emptyPacketsAreIgnored() = runTest {
        val link = PacketLink(packetSize = { 20 }, sendPacket = {}, onClose = {})
        link.received(ByteArray(0))
        PacketFraming.split("ok", 20).forEach { link.received(it) }
        assertEquals("ok", link.incoming.receive())
    }
}
