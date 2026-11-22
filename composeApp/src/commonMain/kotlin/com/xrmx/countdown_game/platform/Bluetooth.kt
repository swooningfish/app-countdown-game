package com.xrmx.countdown_game.platform

import androidx.compose.runtime.Composable
import com.xrmx.countdown_game.game.MatchLink
import com.xrmx.countdown_game.game.PacketFraming
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A phone found nearby that is hosting a two-player game. */
data class FoundHost(val id: String, val name: String)

/** Two-player games over Bluetooth Low Energy. One phone hosts, the other joins. */
interface BluetoothGames {
    /** Why Bluetooth games can't be played right now, or null if they can. */
    fun problem(): String?

    /** Advertises a game under [name] and suspends until another phone joins. Cancel to stop advertising. */
    suspend fun host(name: String): MatchLink

    /** Phones nearby hosting a game, updated while collected. */
    fun scan(): Flow<List<FoundHost>>

    /** Connects to a phone found by [scan]. */
    suspend fun join(host: FoundHost): MatchLink
}

/** Shared by both platforms so Android and iPhone can play each other. */
object BluetoothIds {
    const val SERVICE = "7c1e0001-5b9a-4c39-9b0e-6f3c2d1a4e10"

    /** Host to guest, sent as notifications. */
    const val TO_GUEST = "7c1e0002-5b9a-4c39-9b0e-6f3c2d1a4e10"

    /** Guest to host, written with response. */
    const val TO_HOST = "7c1e0003-5b9a-4c39-9b0e-6f3c2d1a4e10"

    /** Longest host name put in the advertisement, in bytes. */
    const val MAX_NAME_BYTES = 12

    fun shortName(name: String): String {
        var result = name.trim()
        while (result.encodeToByteArray().size > MAX_NAME_BYTES) result = result.dropLast(1)
        return result
    }
}

/** This platform's Bluetooth games, or null where there are none (the desktop build). */
expect fun bluetoothGames(): BluetoothGames?

/**
 * Returns a function that asks for the Bluetooth permissions if needed, then calls [onResult]
 * with whether they were granted.
 */
@Composable
expect fun rememberBluetoothPermission(onResult: (Boolean) -> Unit): () -> Unit

/**
 * A [MatchLink] over a Bluetooth connection that carries small packets. The platform code passes
 * packets in with [received], sends them with [sendPacket] (which must wait until the packet has
 * gone) and calls [disconnected] when the connection drops.
 */
class PacketLink(
    private val packetSize: () -> Int,
    private val sendPacket: suspend (ByteArray) -> Unit,
    private val onClose: () -> Unit,
) : MatchLink {
    private val inbox = Channel<String>(Channel.UNLIMITED)
    private val joiner = PacketFraming.Joiner()
    private val sendLock = Mutex()

    override val incoming: ReceiveChannel<String> get() = inbox

    fun received(packet: ByteArray) {
        joiner.add(packet)?.let { inbox.trySend(it) }
    }

    fun disconnected() {
        inbox.close()
    }

    override suspend fun send(message: String) = sendLock.withLock {
        if (inbox.isClosedForSend) error("Not connected")
        PacketFraming.split(message, packetSize()).forEach { sendPacket(it) }
    }

    override fun close() {
        inbox.close()
        onClose()
    }
}
