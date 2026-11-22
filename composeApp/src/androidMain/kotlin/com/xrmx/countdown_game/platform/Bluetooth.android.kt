package com.xrmx.countdown_game.platform

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.xrmx.countdown_game.game.MatchLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID

private val SERVICE: UUID = UUID.fromString(BluetoothIds.SERVICE)
private val TO_GUEST: UUID = UUID.fromString(BluetoothIds.TO_GUEST)
private val TO_HOST: UUID = UUID.fromString(BluetoothIds.TO_HOST)
private val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** Packet size before the MTU has been agreed: the BLE minimum of 23 less 3 bytes of header. */
private const val DEFAULT_PACKET = 20
private const val WANTED_MTU = 247
private const val CONNECT_TIMEOUT_MS = 20_000L
private const val PACKET_TIMEOUT_MS = 5_000L

/**
 * State shared between the Bluetooth callbacks (on a binder thread) and the coroutines that send
 * packets, so every field is volatile.
 */
private class HostConnection {
    @Volatile var server: BluetoothGattServer? = null
    @Volatile var guest: BluetoothDevice? = null
    @Volatile var link: PacketLink? = null
    @Volatile var packetSize = DEFAULT_PACKET
    @Volatile var notifySent: CompletableDeferred<Boolean>? = null

    /** Phones connected to the server, including any that never became the guest. */
    val connected: MutableSet<BluetoothDevice> = java.util.Collections.synchronizedSet(mutableSetOf())
}

private class GuestConnection {
    @Volatile var gatt: BluetoothGatt? = null
    @Volatile var link: PacketLink? = null
    @Volatile var toHost: BluetoothGattCharacteristic? = null
    @Volatile var packetSize = DEFAULT_PACKET
    @Volatile var writeDone: CompletableDeferred<Boolean>? = null
}

private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun hasPermissions(context: Context) = bluetoothPermissions().all {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

actual fun bluetoothGames(): BluetoothGames? {
    val context = AndroidPlatform.context
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return null
    return AndroidBluetoothGames(context)
}

@Composable
actual fun rememberBluetoothPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        latest(results.values.all { it })
    }
    return {
        if (hasPermissions(AndroidPlatform.context)) latest(true) else launcher.launch(bluetoothPermissions())
    }
}

// Every Bluetooth call below runs only after rememberBluetoothPermission has granted access.
@SuppressLint("MissingPermission")
private class AndroidBluetoothGames(private val context: Context) : BluetoothGames {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    override fun problem(): String? {
        val adapter = adapter ?: return "This device doesn't have Bluetooth."
        return when {
            !hasPermissions(context) -> "Allow Bluetooth access to play on two phones."
            !adapter.isEnabled -> "Turn on Bluetooth to play on two phones."
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun locationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    override suspend fun host(name: String): MatchLink {
        problem()?.let { error(it) }
        val adapter = adapter!!
        val advertiser = adapter.bluetoothLeAdvertiser ?: error("This phone can't host games. Ask the other player to host instead.")
        val ready = CompletableDeferred<MatchLink>()
        val h = HostConnection()
        val toGuest = BluetoothGattCharacteristic(
            TO_GUEST,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(CLIENT_CONFIG, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE),
            )
        }
        val toHost = BluetoothGattCharacteristic(
            TO_HOST,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        val advertising = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                ready.completeExceptionally(IllegalStateException("Couldn't start hosting (error $errorCode)."))
            }
        }

        fun stopAdvertising() = runCatching { advertiser.stopAdvertising(advertising) }

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        h.connected += device
                        // Only one other phone can play; turn away any that connect after the guest.
                        val guest = h.guest
                        if (guest != null && device != guest) runCatching { h.server?.cancelConnection(device) }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        h.connected -= device
                        if (device == h.guest) {
                            h.link?.disconnected()
                            h.notifySent?.complete(false)
                        }
                    }
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                val guest = h.guest
                if (device == guest || guest == null) h.packetSize = (mtu - 3).coerceAtLeast(DEFAULT_PACKET)
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
            ) {
                synchronized(h) {
                    val accept = h.guest == null || device == h.guest
                    if (responseNeeded) {
                        h.server?.sendResponse(device, requestId, if (accept) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                    if (!accept || descriptor.uuid != CLIENT_CONFIG || h.guest != null) return
                    // The first phone to subscribe becomes the guest.
                    h.guest = device
                }
                stopAdvertising()
                // Turn away any other phone that connected while the game was being advertised.
                synchronized(h.connected) { h.connected.filter { it != device } }.forEach {
                    runCatching { h.server?.cancelConnection(it) }
                }
                val newLink = PacketLink(
                    packetSize = { h.packetSize },
                    sendPacket = { packet -> sendNotification(device, packet) },
                    onClose = {
                        runCatching { h.server?.cancelConnection(device) }
                        runCatching { h.server?.close() }
                    },
                )
                h.link = newLink
                ready.complete(newLink)
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
            ) {
                // Long (prepared) writes aren't supported: the guest sizes its packets to fit one write.
                val ok = device == h.guest && characteristic.uuid == TO_HOST && !preparedWrite
                if (responseNeeded) {
                    h.server?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
                }
                if (ok && value != null) h.link?.received(value)
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                h.notifySent?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Suppress("DEPRECATION")
            suspend fun sendNotification(device: BluetoothDevice, packet: ByteArray) {
                val sent = CompletableDeferred<Boolean>()
                h.notifySent = sent
                val s = h.server ?: error("Not connected")
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    s.notifyCharacteristicChanged(device, toGuest, false, packet) == BluetoothStatusCodes.SUCCESS
                } else {
                    toGuest.value = packet
                    s.notifyCharacteristicChanged(device, toGuest, false)
                }
                if (!started || !withTimeout(PACKET_TIMEOUT_MS) { sent.await() }) error("The connection was lost")
            }
        }

        val gattServer = manager!!.openGattServer(context, callback) ?: error("Couldn't start hosting.")
        h.server = gattServer
        val service = BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(toGuest)
            addCharacteristic(toHost)
        }
        gattServer.addService(service)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE)).build()
        // The player's name goes in the scan response, so the other phone can list it.
        val response = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(SERVICE), BluetoothIds.shortName(name).encodeToByteArray())
            .build()
        advertiser.startAdvertising(settings, data, response, advertising)

        try {
            return ready.await()
        } catch (e: Throwable) {
            stopAdvertising()
            runCatching { gattServer.close() }
            throw e
        }
    }

    override fun scan(): Flow<List<FoundHost>> = callbackFlow {
        problem()?.let { error(it) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !locationEnabled()) {
            error("Turn on Location to look for games. This version of Android needs it to find Bluetooth devices.")
        }
        val scanner = adapter!!.bluetoothLeScanner ?: error("Turn on Bluetooth to play on two phones.")
        val found = linkedMapOf<String, FoundHost>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                val name = record?.getServiceData(ParcelUuid(SERVICE))?.decodeToString()?.takeIf { it.isNotBlank() }
                    ?: record?.deviceName?.takeIf { it.isNotBlank() }
                    ?: "Countdown player"
                val host = FoundHost(result.device.address, name)
                if (found[host.id] != host) {
                    found[host.id] = host
                    trySend(found.values.toList())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Couldn't look for games (error $errorCode)."))
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    override suspend fun join(host: FoundHost): MatchLink {
        problem()?.let { error(it) }
        val device = adapter!!.getRemoteDevice(host.id)
        val ready = CompletableDeferred<Unit>()
        val c = GuestConnection()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> if (!g.requestMtu(WANTED_MTU)) g.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        ready.completeExceptionally(IllegalStateException("Couldn't connect to ${host.name}."))
                        c.link?.disconnected()
                        c.writeDone?.complete(false)
                        g.close()
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) c.packetSize = (mtu - 3).coerceAtLeast(DEFAULT_PACKET)
                g.discoverServices()
            }

            @Suppress("DEPRECATION")
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val service = g.getService(SERVICE)
                val notify = service?.getCharacteristic(TO_GUEST)
                val toHost = service?.getCharacteristic(TO_HOST)
                c.toHost = toHost
                val config = notify?.getDescriptor(CLIENT_CONFIG)
                if (notify == null || toHost == null || config == null) {
                    ready.completeExceptionally(IllegalStateException("${host.name} isn't hosting a game."))
                    return
                }
                g.setCharacteristicNotification(notify, true)
                val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(config, value)
                } else {
                    config.value = value
                    g.writeDescriptor(config)
                }
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    ready.complete(Unit)
                } else {
                    ready.completeExceptionally(IllegalStateException("Couldn't connect to ${host.name}."))
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid == TO_GUEST) c.link?.received(value)
            }

            @Deprecated("Used before Android 13")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == TO_GUEST) characteristic.value?.let { c.link?.received(it) }
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                c.writeDone?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        @Suppress("DEPRECATION")
        suspend fun write(packet: ByteArray) {
            val g = c.gatt ?: error("Not connected")
            val toHost = c.toHost ?: error("Not connected")
            val done = CompletableDeferred<Boolean>()
            c.writeDone = done
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(toHost, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                toHost.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                toHost.value = packet
                g.writeCharacteristic(toHost)
            }
            if (!started || !withTimeout(PACKET_TIMEOUT_MS) { done.await() }) error("The connection was lost")
        }

        // Create the link before connecting so no early notification is lost.
        val newLink = PacketLink(
            packetSize = { c.packetSize },
            sendPacket = { write(it) },
            onClose = {
                runCatching { c.gatt?.disconnect() }
                runCatching { c.gatt?.close() }
            },
        )
        c.link = newLink
        c.gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { ready.await() }
        } catch (e: Throwable) {
            newLink.close()
            throw if (e is kotlinx.coroutines.TimeoutCancellationException) IllegalStateException("Couldn't connect to ${host.name}.") else e
        }
        return newLink
    }
}
