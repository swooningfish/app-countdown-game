@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.xrmx.countdown_game.platform

import androidx.compose.runtime.Composable
import com.xrmx.countdown_game.game.MatchLink
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.darwin.NSObject
import platform.posix.memcpy

private val SERVICE: CBUUID = CBUUID.UUIDWithString(BluetoothIds.SERVICE)
private val TO_GUEST: CBUUID = CBUUID.UUIDWithString(BluetoothIds.TO_GUEST)
private val TO_HOST: CBUUID = CBUUID.UUIDWithString(BluetoothIds.TO_HOST)

private const val DEFAULT_PACKET = 20
private const val STATE_TIMEOUT_MS = 10_000L
private const val CONNECT_TIMEOUT_MS = 20_000L
private const val PACKET_TIMEOUT_MS = 5_000L

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}

private fun stateProblem(state: Long): String? = when (state) {
    CBManagerStatePoweredOn -> null
    CBManagerStatePoweredOff -> "Turn on Bluetooth to play on two phones."
    CBManagerStateUnauthorized -> "Allow Bluetooth for Countdown in the Settings app to play on two phones."
    CBManagerStateUnsupported -> "This device can't play Bluetooth games."
    else -> "Bluetooth isn't ready yet. Try again in a moment."
}

private val games = IosBluetoothGames()

actual fun bluetoothGames(): BluetoothGames? = games

/** iOS asks for Bluetooth permission itself the first time it is used. */
@Composable
actual fun rememberBluetoothPermission(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }

private class IosBluetoothGames : BluetoothGames {
    override fun problem(): String? = when (CBManager.authorization) {
        CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted ->
            "Allow Bluetooth for Countdown in the Settings app to play on two phones."
        else -> null
    }

    // Keep the managers and their delegates alive while they are in use.
    private var hostSide: HostSide? = null
    private val guestSide by lazy { GuestSide() }

    override suspend fun host(name: String): MatchLink {
        problem()?.let { error(it) }
        hostSide?.stop()
        val side = HostSide(BluetoothIds.shortName(name).ifEmpty { "Countdown" })
        hostSide = side
        return side.start()
    }

    override fun scan(): Flow<List<FoundHost>> = guestSide.scan()

    override suspend fun join(host: FoundHost): MatchLink = guestSide.join(host)
}

/** Hosting: a GATT server that the guest subscribes to and writes to. */
private class HostSide(private val name: String) : NSObject(), CBPeripheralManagerDelegateProtocol {
    private val poweredOn = CompletableDeferred<Unit>()
    private val ready = CompletableDeferred<MatchLink>()
    private val manager = CBPeripheralManager(delegate = this, queue = null)
    private val toGuest = CBMutableCharacteristic(TO_GUEST, CBCharacteristicPropertyNotify, null, CBAttributePermissionsReadable)
    private val toHost = CBMutableCharacteristic(TO_HOST, CBCharacteristicPropertyWrite, null, CBAttributePermissionsWriteable)
    private var guest: CBCentral? = null
    private var link: PacketLink? = null
    private var canSend: CompletableDeferred<Unit>? = null

    suspend fun start(): MatchLink {
        try {
            withTimeout(STATE_TIMEOUT_MS) { poweredOn.await() }
        } catch (e: TimeoutCancellationException) {
            error(stateProblem(manager.state) ?: "Bluetooth didn't start.")
        }
        val service = CBMutableService(SERVICE, true)
        service.setCharacteristics(listOf(toGuest, toHost))
        manager.addService(service)
        try {
            return ready.await()
        } catch (e: Throwable) {
            stop()
            throw e
        }
    }

    fun stop() {
        manager.stopAdvertising()
        manager.removeAllServices()
        link?.disconnected()
    }

    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        val problem = stateProblem(peripheral.state)
        if (problem == null) {
            poweredOn.complete(Unit)
        } else if (peripheral.state == CBManagerStatePoweredOff || peripheral.state == CBManagerStateUnauthorized ||
            peripheral.state == CBManagerStateUnsupported
        ) {
            poweredOn.completeExceptionally(IllegalStateException(problem))
            ready.completeExceptionally(IllegalStateException(problem))
            link?.disconnected()
        }
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
        if (error != null) {
            ready.completeExceptionally(IllegalStateException("Couldn't start hosting: ${error.localizedDescription}"))
            return
        }
        peripheral.startAdvertising(
            mapOf<Any?, Any?>(
                CBAdvertisementDataServiceUUIDsKey to listOf(SERVICE),
                CBAdvertisementDataLocalNameKey to name,
            ),
        )
    }

    override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
        if (error != null) ready.completeExceptionally(IllegalStateException("Couldn't start hosting: ${error.localizedDescription}"))
    }

    @ObjCSignatureOverride
    override fun peripheralManager(peripheral: CBPeripheralManager, central: CBCentral, didSubscribeToCharacteristic: CBCharacteristic) {
        if (guest != null) return
        // The first phone to subscribe becomes the guest.
        guest = central
        peripheral.stopAdvertising()
        val newLink = PacketLink(
            packetSize = { (guest?.maximumUpdateValueLength?.toInt() ?: DEFAULT_PACKET).coerceAtLeast(DEFAULT_PACKET) },
            sendPacket = { send(it) },
            onClose = { stop() },
        )
        link = newLink
        ready.complete(newLink)
    }

    @ObjCSignatureOverride
    override fun peripheralManager(peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFromCharacteristic: CBCharacteristic) {
        if (central.identifier == guest?.identifier) {
            link?.disconnected()
            canSend?.completeExceptionally(IllegalStateException("The connection was lost"))
        }
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
        val requests = didReceiveWriteRequests.filterIsInstance<CBATTRequest>()
        val first = requests.firstOrNull() ?: return
        val allowed = requests.all { it.central.identifier == guest?.identifier && it.characteristic.UUID == TO_HOST }
        if (allowed) requests.forEach { r -> r.value?.let { link?.received(it.toByteArray()) } }
        peripheral.respondToRequest(first, if (allowed) CBATTErrorSuccess else CBATTErrorWriteNotPermitted)
    }

    override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
        canSend?.complete(Unit)
    }

    private suspend fun send(packet: ByteArray) {
        val central = guest ?: error("Not connected")
        val data = packet.toNSData()
        while (!manager.updateValue(data, toGuest, listOf(central))) {
            // The send queue is full: wait until iOS says there is room again.
            val wait = CompletableDeferred<Unit>()
            canSend = wait
            withTimeout(PACKET_TIMEOUT_MS) { wait.await() }
        }
    }
}

/** Joining: scans for hosts and connects to one as a GATT client. */
private class GuestSide : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {
    private val manager = CBCentralManager(delegate = this, queue = null)
    private var poweredOn = CompletableDeferred<Unit>()
    private val peripherals = mutableMapOf<String, CBPeripheral>()
    private var onFound: ((FoundHost) -> Unit)? = null

    private var connecting: CBPeripheral? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var link: PacketLink? = null
    private var toHost: CBCharacteristic? = null
    private var writeDone: CompletableDeferred<Unit>? = null

    private suspend fun awaitPoweredOn() {
        if (manager.state == CBManagerStatePoweredOn) return
        if (poweredOn.isCompleted) poweredOn = CompletableDeferred()
        try {
            withTimeout(STATE_TIMEOUT_MS) { poweredOn.await() }
        } catch (e: TimeoutCancellationException) {
            error(stateProblem(manager.state) ?: "Bluetooth didn't start.")
        }
    }

    fun scan(): Flow<List<FoundHost>> = callbackFlow {
        awaitPoweredOn()
        val found = linkedMapOf<String, FoundHost>()
        onFound = { host ->
            if (found[host.id] != host) {
                found[host.id] = host
                trySend(found.values.toList())
            }
        }
        manager.scanForPeripheralsWithServices(listOf(SERVICE), null)
        awaitClose {
            manager.stopScan()
            onFound = null
        }
    }

    suspend fun join(host: FoundHost): MatchLink {
        awaitPoweredOn()
        val peripheral = peripherals[host.id] ?: error("${host.name} is no longer nearby.")
        val done = CompletableDeferred<Unit>()
        ready = done
        connecting = peripheral
        toHost = null
        val newLink = PacketLink(
            packetSize = {
                (connecting?.maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse)?.toInt() ?: DEFAULT_PACKET)
                    .coerceAtLeast(DEFAULT_PACKET)
            },
            sendPacket = { write(it) },
            onClose = { connecting?.let { manager.cancelPeripheralConnection(it) } },
        )
        link = newLink
        peripheral.delegate = this
        manager.connectPeripheral(peripheral, null)
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { done.await() }
        } catch (e: Throwable) {
            newLink.close()
            throw if (e is TimeoutCancellationException) IllegalStateException("Couldn't connect to ${host.name}.") else e
        }
        return newLink
    }

    private suspend fun write(packet: ByteArray) {
        val peripheral = connecting ?: error("Not connected")
        val characteristic = toHost ?: error("Not connected")
        val done = CompletableDeferred<Unit>()
        writeDone = done
        peripheral.writeValue(packet.toNSData(), characteristic, CBCharacteristicWriteWithResponse)
        withTimeout(PACKET_TIMEOUT_MS) { done.await() }
    }

    private fun fail(message: String) {
        ready?.completeExceptionally(IllegalStateException(message))
    }

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        if (central.state == CBManagerStatePoweredOn) poweredOn.complete(Unit)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        val id = didDiscoverPeripheral.identifier.UUIDString
        peripherals[id] = didDiscoverPeripheral
        // An Android host puts its name in the service data; an iPhone host uses the local name.
        val serviceData = (advertisementData[CBAdvertisementDataServiceDataKey] as? Map<*, *>)
            ?.entries?.firstOrNull { (it.key as? CBUUID)?.UUIDString.equals(BluetoothIds.SERVICE, ignoreCase = true) }
            ?.value as? NSData
        val name = serviceData?.toByteArray()?.decodeToString()?.takeIf { it.isNotBlank() }
            ?: (advertisementData[CBAdvertisementDataLocalNameKey] as? String)?.takeIf { it.isNotBlank() }
            ?: didDiscoverPeripheral.name?.takeIf { it.isNotBlank() }
            ?: "Countdown player"
        onFound?.invoke(FoundHost(id, name))
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        didConnectPeripheral.discoverServices(listOf(SERVICE))
    }

    @ObjCSignatureOverride
    override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
        fail("Couldn't connect: ${error?.localizedDescription ?: "unknown error"}")
    }

    @ObjCSignatureOverride
    override fun centralManager(central: CBCentralManager, didDisconnectPeripheral: CBPeripheral, error: NSError?) {
        if (didDisconnectPeripheral.identifier == connecting?.identifier) {
            fail("The connection was lost.")
            link?.disconnected()
            writeDone?.completeExceptionally(IllegalStateException("The connection was lost"))
        }
    }

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        val service = peripheral.services?.filterIsInstance<CBService>()?.firstOrNull { it.UUID == SERVICE }
        if (didDiscoverServices != null || service == null) {
            fail("That phone isn't hosting a game.")
            return
        }
        peripheral.discoverCharacteristics(listOf(TO_GUEST, TO_HOST), service)
    }

    override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) {
        val characteristics = didDiscoverCharacteristicsForService.characteristics?.filterIsInstance<CBCharacteristic>().orEmpty()
        val notify = characteristics.firstOrNull { it.UUID == TO_GUEST }
        toHost = characteristics.firstOrNull { it.UUID == TO_HOST }
        if (error != null || notify == null || toHost == null) {
            fail("That phone isn't hosting a game.")
            return
        }
        peripheral.setNotifyValue(true, notify)
    }

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didUpdateNotificationStateForCharacteristic: CBCharacteristic, error: NSError?) {
        if (error == null && didUpdateNotificationStateForCharacteristic.isNotifying) {
            ready?.complete(Unit)
        } else {
            fail("Couldn't connect: ${error?.localizedDescription ?: "notifications are off"}")
        }
    }

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) {
        if (error == null && didUpdateValueForCharacteristic.UUID == TO_GUEST) {
            didUpdateValueForCharacteristic.value?.let { link?.received(it.toByteArray()) }
        }
    }

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didWriteValueForCharacteristic: CBCharacteristic, error: NSError?) {
        if (error == null) {
            writeDone?.complete(Unit)
        } else {
            writeDone?.completeExceptionally(IllegalStateException("The connection was lost"))
        }
    }
}
