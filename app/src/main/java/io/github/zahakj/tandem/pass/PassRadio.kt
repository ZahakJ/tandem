package io.github.zahakj.tandem.pass

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.util.Log
import java.security.SecureRandom

/**
 * The Bluetooth LE side of Tandem Pass: advertising, the duty-cycled scan, the GATT server that answers and the GATT
 * client that finds. Thin on purpose: it moves PassProtocol's bytes and asks [Host] for everything else. Every
 * callback is moved onto one thread ("tandem-pass"), so nothing here races.
 *
 * Defensive about handhelds' Bluetooth stacks: every call is wrapped (a SecurityException after a permission is
 * taken back, an IllegalStateException with the radio switching off), one connection at a time, a deadline on each
 * exchange, both API 33's and the older read/write callbacks, long reads and prepared (long) writes on the server,
 * and the server ignores the connection events its own client causes.
 */
@SuppressLint("MissingPermission")
class PassRadio(private val ctx: Context, private val host: Host) {
    interface Host {
        /** Our card, signed over [nonce]. Called on the radio thread. */
        fun sealedCard(nonce: ByteArray): ByteArray
        val publicKey: ByteArray
        fun blocked(key: String): Boolean
        fun waveFor(key: String): Boolean
        /** A card checked out. */
        fun met(card: PassCard)
        /** The waves of an exchange with [key]: whether ours went out, and theirs. */
        fun waves(key: String, sentOurs: Boolean, theirWave: Boolean)
        /** Something worth a line in the log (never personal data). */
        fun note(what: String) = Log.i("TandemPass", what)
    }

    private val thread = HandlerThread("tandem-pass").apply { start() }
    private val handler = Handler(thread.looper)
    private val random = SecureRandom()
    private val planner = PassPlanner()
    private val manager = ctx.getSystemService(BluetoothManager::class.java)

    private var running = false
    private var duty = PassPlanner.Duty.Front
    private var token = ByteArray(Beacon.TOKEN_BYTES)
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var server: BluetoothGattServer? = null
    private var scanning = false

    /** True when this handheld can be found (it can advertise), not only find others. */
    @Volatile var canAdvertise = true
        private set

    // --- Switching on and off --------------------------------------------------------------------------------

    fun start(d: PassPlanner.Duty) = handler.post {
        if (running) return@post
        duty = d
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) { host.note("bluetooth off"); return@post }
        running = true
        scanner = adapter.bluetoothLeScanner
        advertiser = if (adapter.isMultipleAdvertisementSupported) adapter.bluetoothLeAdvertiser else null
        canAdvertise = advertiser != null
        openServer()
        rotate()
        scanOn()
    }

    fun setDuty(d: PassPlanner.Duty) = handler.post {
        val faster = d == PassPlanner.Duty.Front && duty != d
        duty = d
        // Back in Tandem: look now rather than after the slow pace's long rest.
        if (faster && running && !scanning) { handler.removeCallbacks(scanOnRun); scanOn() }
    }

    fun stop() = handler.post {
        if (!running) return@post
        running = false
        // Only its own timers: a start queued after this stop must still run.
        for (r in listOf(rotation, scanOnRun, scanOffRun, deadline, mtuLate)) handler.removeCallbacks(r)
        safe { if (scanning) scanner?.stopScan(scanCallback) }
        scanning = false
        safe { advertiser?.stopAdvertising(advertiseCallback) }
        client?.let { safe { it.gatt?.disconnect(); it.gatt?.close() } }
        client = null
        safe { server?.close() }
        server = null
        sessions.clear()
        writes.clear()
        planner.clear()
    }

    /** Stops for good (the process is leaving). */
    fun quit() { stop(); handler.post { thread.quitSafely() } }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (t: Throwable) { Log.w("TandemPass", "radio: ${t.javaClass.simpleName}") }
    }

    // --- Advertising and rotation ------------------------------------------------------------------------------

    private val rotation = Runnable { rotate() }

    /** A fresh token, advertised from now; the next rotation is planned. */
    private fun rotate() {
        if (!running) return
        random.nextBytes(token)
        val adv = advertiser
        if (adv != null) {
            safe { adv.stopAdvertising(advertiseCallback) }
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            val data = AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(PassProtocol.SERVICE)).build()
            val response = AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
                .addServiceData(ParcelUuid(PassProtocol.SERVICE), Beacon.data(token)).build()
            safe { adv.startAdvertising(settings, data, response, advertiseCallback) }
        }
        handler.removeCallbacks(rotation)
        handler.postAtTime(rotation, android.os.SystemClock.uptimeMillis() + (PassPlanner.nextRotation(0, random.nextDouble())))
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            host.note("advertise failed $errorCode")
            if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) canAdvertise = false
        }
    }

    // --- Scanning ----------------------------------------------------------------------------------------------

    private val scanOnRun = Runnable { scanOn() }
    private val scanOffRun = Runnable { scanOff() }

    private fun scanOn() {
        if (!running) return
        val s = scanner ?: return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(PassProtocol.SERVICE)).build())
        val mode = if (duty == PassPlanner.Duty.Front) ScanSettings.SCAN_MODE_BALANCED else ScanSettings.SCAN_MODE_LOW_POWER
        val settings = ScanSettings.Builder().setScanMode(mode).setReportDelay(0).build()
        safe { s.startScan(filters, settings, scanCallback); scanning = true }
        handler.postDelayed(scanOffRun, duty.onMs)
    }

    private fun scanOff() {
        safe { if (scanning) scanner?.stopScan(scanCallback) }
        scanning = false
        if (running) handler.postDelayed(scanOnRun, duty.offMs)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val theirs = Beacon.token(result.scanRecord?.getServiceData(ParcelUuid(PassProtocol.SERVICE)))
            handler.post { heard(device, theirs) }
        }

        override fun onScanFailed(errorCode: Int) {
            host.note("scan failed $errorCode")
            scanning = false
        }
    }

    private fun heard(device: BluetoothDevice, theirs: ByteArray?) {
        if (!running || client != null) return
        val id = theirs?.let { Beacon.hex(it) } ?: ("addr:" + device.address)
        if (theirs != null && theirs.contentEquals(token)) return
        if (!planner.shouldConnect(id, token, theirs, System.currentTimeMillis())) return
        connect(device, id)
    }

    // --- Finding: the GATT client ------------------------------------------------------------------------------

    private inner class Client(val id: String, val device: BluetoothDevice) {
        var gatt: BluetoothGatt? = null
        val session = ClientSession(ByteArray(CardCodec.NONCE_BYTES).also { random.nextBytes(it) }, host.publicKey, token.copyOf(), { host.sealedCard(it) }, { host.blocked(it) })
        var met: PassCard? = null
        var theirWave = false
        var ourWave = false
        /** Services asked for once: after the MTU, or without it when a stack never answers the MTU request. */
        var discovering = false
        /** Discovery answered: services are known (some stacks drop that answer, so it is asked again once). */
        var discovered = false
        var rediscovered = false
    }

    private var client: Client? = null
    private val deadline = Runnable { client?.let { host.note("exchange timed out"); endClient() } }

    /** Some stacks never call back after an MTU request: go on with the default MTU after a moment. */
    private val mtuLate = Runnable { client?.let { c -> c.gatt?.let { discover(c, it) } } }

    private fun discover(c: Client, g: BluetoothGatt) {
        handler.removeCallbacks(mtuLate)
        if (c.discovering) return
        c.discovering = true
        if (!(try { g.discoverServices() } catch (t: Throwable) { false })) endClient()
        else handler.postDelayed(discoverLate, 3_000)
    }

    /**
     * Discovery that never answers: the AYN Thor's stack drops the answer when the MTU reply lands during discovery
     * (seen 2026-11-26: "Unhandled event (8)", then nothing). Asked once more; the second answer comes from its cache.
     */
    private val discoverLate = Runnable { client?.let { c -> c.gatt?.let { rediscover(c, it) } } }

    private fun rediscover(c: Client, g: BluetoothGatt) {
        handler.removeCallbacks(discoverLate)
        if (c.discovered || c.rediscovered) return
        c.rediscovered = true
        if (!(try { g.discoverServices() } catch (t: Throwable) { false })) endClient()
    }

    private fun connect(device: BluetoothDevice, id: String) {
        val c = Client(id, device)
        client = c
        planner.started(id, System.currentTimeMillis())
        handler.postDelayed(deadline, 20_000)
        safe { c.gatt = device.connectGatt(ctx, false, clientCallback, BluetoothDevice.TRANSPORT_LE) }
        if (c.gatt == null) endClient()
    }

    private fun endClient() {
        val c = client ?: return
        client = null
        handler.removeCallbacks(deadline)
        handler.removeCallbacks(mtuLate)
        handler.removeCallbacks(discoverLate)
        safe { c.gatt?.disconnect() }
        safe { c.gatt?.close() }
    }

    private fun clientService(g: BluetoothGatt) = g.getService(PassProtocol.SERVICE)

    private fun read(g: BluetoothGatt, uuid: java.util.UUID): Boolean {
        val ch = clientService(g)?.getCharacteristic(uuid) ?: return false
        return try { g.readCharacteristic(ch) } catch (t: Throwable) { false }
    }

    @Suppress("DEPRECATION")
    private fun write(g: BluetoothGatt, uuid: java.util.UUID, value: ByteArray): Boolean {
        val ch = clientService(g)?.getCharacteristic(uuid) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
            } else {
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ch.value = value
                g.writeCharacteristic(ch)
            }
        } catch (t: Throwable) { false }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = handler.post {
            val c = client?.takeIf { it.gatt == gatt || it.gatt == null } ?: run { safe { gatt.close() }; return@post }
            if (c.gatt == null) c.gatt = gatt
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    // A bigger MTU lets a card go in one write; without it Android splits the write and the read.
                    if (try { gatt.requestMtu(PassProtocol.MAX_VALUE + 5) } catch (t: Throwable) { false }) handler.postDelayed(mtuLate, 4_000)
                    else discover(c, gatt)
                }
                else -> endClient()
            }
        }.let { }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) = handler.post {
            val c = client?.takeIf { it.gatt == gatt } ?: return@post
            // The MTU reply came after discovery had started (the fallback ran first): ask again now, as that answer
            // is the one some stacks drop.
            if (c.discovering && !c.discovered) rediscover(c, gatt) else discover(c, gatt)
        }.let { }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = handler.post {
            val c = client?.takeIf { it.gatt == gatt } ?: return@post
            if (c.discovered) return@post
            c.discovered = true
            handler.removeCallbacks(discoverLate)
            if (status != BluetoothGatt.GATT_SUCCESS || !read(gatt, PassProtocol.NONCE)) endClient()
        }.let { }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            handler.post { onRead(gatt, characteristic.uuid, value, status) }.let { }

        @Deprecated("Android 12 and older")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= 33) return
            val value = characteristic.value ?: ByteArray(0)
            handler.post { onRead(gatt, characteristic.uuid, value, status) }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            handler.post { onWrote(gatt, characteristic.uuid, status) }.let { }
    }

    private fun onRead(gatt: BluetoothGatt, uuid: java.util.UUID, value: ByteArray, status: Int) {
        val c = client?.takeIf { it.gatt == gatt } ?: return
        if (status != BluetoothGatt.GATT_SUCCESS) { endClient(); return }
        when (uuid) {
            PassProtocol.NONCE -> {
                val hello = c.session.hello(value)
                if (hello == null || !write(gatt, PassProtocol.HELLO, hello)) endClient()
            }
            PassProtocol.CARD -> when (val r = c.session.onCard(value, System.currentTimeMillis())) {
                is PassResult.Refused -> { host.note("card refused: ${r.problem}"); endClient() }
                is PassResult.Met -> {
                    planner.finished(c.id, System.currentTimeMillis())
                    c.met = r.card
                    c.theirWave = r.waved
                    host.met(r.card)
                    val key = r.card.keyId
                    if (host.blocked(key)) { endClient(); return }
                    c.ourWave = host.waveFor(key)
                    if (!write(gatt, PassProtocol.WAVE, c.session.wave(c.ourWave))) {
                        host.waves(key, false, c.theirWave)
                        endClient()
                    }
                }
            }
        }
    }

    private fun onWrote(gatt: BluetoothGatt, uuid: java.util.UUID, status: Int) {
        val c = client?.takeIf { it.gatt == gatt } ?: return
        if (status != BluetoothGatt.GATT_SUCCESS) { endClient(); return }
        when (uuid) {
            PassProtocol.HELLO -> if (!read(gatt, PassProtocol.CARD)) endClient()
            PassProtocol.WAVE -> {
                c.met?.let { host.waves(it.keyId, c.ourWave, c.theirWave) }
                endClient()
            }
        }
    }

    // --- Being found: the GATT server --------------------------------------------------------------------------

    private class Answer(val session: ServerSession, var ourWave: Boolean = false)

    private val sessions = HashMap<String, Answer>()
    /** Prepared (long) writes being put together, by device and characteristic. */
    private val writes = HashMap<String, java.io.ByteArrayOutputStream>()

    private fun openServer() {
        if (advertiser == null) return
        val s = try { manager?.openGattServer(ctx, serverCallback) } catch (t: Throwable) { null } ?: return
        val service = BluetoothGattService(PassProtocol.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        fun ch(uuid: java.util.UUID, read: Boolean) = BluetoothGattCharacteristic(
            uuid,
            if (read) BluetoothGattCharacteristic.PROPERTY_READ else BluetoothGattCharacteristic.PROPERTY_WRITE,
            if (read) BluetoothGattCharacteristic.PERMISSION_READ else BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(ch(PassProtocol.NONCE, true))
        service.addCharacteristic(ch(PassProtocol.HELLO, false))
        service.addCharacteristic(ch(PassProtocol.CARD, true))
        service.addCharacteristic(ch(PassProtocol.WAVE, false))
        safe { s.addService(service) }
        server = s
    }

    private fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray?) {
        safe { server?.sendResponse(device, requestId, status, offset, value) }
    }

    /** Our own client's connection shows up here too: never treat it as someone finding us. */
    private fun ours(device: BluetoothDevice) = client?.device?.address == device.address

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) = handler.post {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sessions.remove(device.address)
                writes.keys.removeAll { it.startsWith(device.address) }
            }
        }.let { }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) =
            handler.post { serverRead(device, requestId, offset, characteristic.uuid) }.let { }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) = handler.post { serverWrite(device, requestId, characteristic.uuid, preparedWrite, responseNeeded, offset, value ?: ByteArray(0)) }.let { }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) = handler.post {
            val prefix = device.address + "/"
            val pending = writes.filterKeys { it.startsWith(prefix) }
            writes.keys.removeAll { it.startsWith(prefix) }
            var ok = true
            if (execute) for ((k, buf) in pending) ok = handleWrite(device, java.util.UUID.fromString(k.removePrefix(prefix)), buf.toByteArray()) && ok
            respond(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
        }.let { }
    }

    private fun serverRead(device: BluetoothDevice, requestId: Int, offset: Int, uuid: java.util.UUID) {
        if (!running || ours(device)) { respond(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null); return }
        val value: ByteArray? = when (uuid) {
            PassProtocol.NONCE -> {
                var a = sessions[device.address]
                if (a == null && offset == 0) {
                    // Someone found us: it counts against the hour, like a connection we make.
                    if (planner.answered(System.currentTimeMillis())) {
                        a = Answer(
                            ServerSession(
                                ByteArray(CardCodec.NONCE_BYTES).also { random.nextBytes(it) }, host.publicKey,
                                { host.sealedCard(it) }, { host.waveFor(it.keyId).also { w -> sessions[device.address]?.ourWave = w } }, { host.blocked(it) },
                            ),
                        )
                        sessions[device.address] = a
                    }
                }
                a?.session?.nonceMessage
            }
            PassProtocol.CARD -> sessions[device.address]?.session?.cardMessage()
            else -> null
        }
        if (value == null) { respond(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null); return }
        if (offset > value.size) { respond(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null); return }
        respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
    }

    private fun serverWrite(device: BluetoothDevice, requestId: Int, uuid: java.util.UUID, prepared: Boolean, needed: Boolean, offset: Int, value: ByteArray) {
        if (!running || ours(device)) { if (needed) respond(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null); return }
        if (prepared) {
            val buf = writes.getOrPut(device.address + "/" + uuid) { java.io.ByteArrayOutputStream() }
            if (offset != buf.size() || buf.size() + value.size > PassProtocol.MAX_VALUE) {
                if (needed) respond(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                return
            }
            buf.write(value)
            if (needed) respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        }
        val ok = offset == 0 && handleWrite(device, uuid, value)
        if (needed) respond(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, offset, value)
    }

    private fun handleWrite(device: BluetoothDevice, uuid: java.util.UUID, value: ByteArray): Boolean {
        val a = sessions[device.address] ?: return false
        val now = System.currentTimeMillis()
        return when (uuid) {
            PassProtocol.HELLO -> when (val r = a.session.onHello(value, now)) {
                is PassResult.Refused -> { host.note("hello refused: ${r.problem}"); false }
                is PassResult.Met -> {
                    a.session.peerToken?.let { planner.finished(Beacon.hex(it), now) }
                    host.met(r.card)
                    true
                }
            }
            PassProtocol.WAVE -> {
                val ok = a.session.onWave(value)
                val peer = a.session.peer
                if (ok && peer != null) host.waves(peer.keyId, a.ourWave && a.session.cardMessage() != null, a.session.theirWave)
                ok
            }
            else -> false
        }
    }
}
