package io.github.zahakj.tandem.pass

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.data.PlazaStore
import java.util.concurrent.Executors

/**
 * Tandem Pass on the device: decides when the radio runs, and turns what it hears into Plaza visitors.
 *
 * It runs only while all of these hold: the switch is on, Me is made, Android is 12 or newer (11 would need the
 * location permission just to scan, which Tandem won't ask for), the three Bluetooth permissions are granted,
 * Bluetooth is on, private mode is off, and either Tandem is in front or "Pass while playing" keeps it going with its
 * notification (PassService). Nothing but the card goes out, and only over Bluetooth LE to another Tandem.
 */
object TandemPass {
    /** What the switch row and the Plaza say about passing. */
    enum class Status { Off, NotMade, OldAndroid, NoBluetooth, BluetoothOff, NeedsPermission, Private, Paused, Passing }

    var status by mutableStateOf(Status.Off)
        private set

    /** Bumped for every new visitor (or a repeat that counts) while Tandem is in front: the Plaza greets them. */
    var arrivals by mutableIntStateOf(0)
        private set
    /** The last one, for the toast and the Plaza's welcome. */
    var lastArrival by mutableStateOf<String?>(null)
        private set

    /** True when this handheld can be found as well as find (some can't advertise). */
    val findable: Boolean get() = radio?.canAdvertise != false

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var app: Context? = null
    private var radio: PassRadio? = null
    private var front = false
    private var serviceOn = false
    private var receiver: BroadcastReceiver? = null

    /** What goes on our card besides the character: worked out on the main thread, read by the radio's. */
    @Volatile private var outgoing: Outgoing? = null
    private class Outgoing(val me: MeCard, val passes: Int, val favorite: String?, val recent: String?)

    @Volatile private var identity: PassIdentity? = null

    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        else emptyArray()

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 31

    fun granted(ctx: Context): Boolean = supported() && permissions.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun bluetooth(ctx: Context): BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    /** MainActivity in front ([on]) or gone behind a game, the screen off, another app. */
    fun front(ctx: Context, on: Boolean) {
        front = on
        refresh(ctx)
    }

    /** PassService started or stopped. */
    internal fun service(ctx: Context, on: Boolean) {
        serviceOn = on
        refresh(ctx)
    }

    /** Works out again whether to pass, and how. Call it after any change to the switches, Me or permissions. */
    fun refresh(ctx: Context) {
        val c = ctx.applicationContext
        app = c
        PlazaStore.init(c)
        watchBluetooth(c)
        val d = PlazaStore.data
        val bt = bluetooth(c)
        val s = when {
            !d.on -> Status.Off
            !d.made -> Status.NotMade
            !supported() -> Status.OldAndroid
            bt == null -> Status.NoBluetooth
            !granted(c) -> Status.NeedsPermission
            !bt.isEnabled -> Status.BluetoothOff
            Privacy.enabled -> Status.Private
            // Screen off: nothing runs (the CPU would sleep through the token's rotation).
            !front && !(d.whilePlaying && serviceOn) || !interactive(c) -> Status.Paused
            else -> Status.Passing
        }
        status = s
        // The notification comes with Pass while playing, from the moment it can pass at all.
        val wantService = d.on && d.made && d.whilePlaying && s != Status.Off && s != Status.NotMade && s != Status.OldAndroid &&
            s != Status.NoBluetooth && s != Status.NeedsPermission && s != Status.Private
        if (wantService && !serviceOn && front) PassService.start(c)
        if (!wantService && serviceOn) PassService.stop(c)
        if (s == Status.Passing) {
            outgoing = outgoingNow()
            val duty = if (front) PassPlanner.Duty.Front else PassPlanner.Duty.Background
            val r = radio ?: PassRadio(c, Host).also { radio = it }
            worker.execute {
                // The identity's first use reads the keystore (and may make the key): never on the main thread.
                identity = identity ?: IdentityStore.get(c)?.also { id -> main.post { known = id } } ?: return@execute
                r.start(duty)
                r.setDuty(duty)
            }
        } else {
            // Through the same worker as a start, so a stop can never land before a start asked for earlier.
            val r = radio
            worker.execute { r?.stop() }
        }
    }

    private fun watchBluetooth(c: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Bluetooth switched, or the screen went off or on: work it out again (refresh stops what must stop).
                main.post { refresh(context) }
            }
        }
        receiver = r
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching { c.registerReceiver(r, filter) }
    }

    private fun interactive(c: Context): Boolean = c.getSystemService(android.os.PowerManager::class.java)?.isInteractive != false

    /** The titles our card may carry: never a hidden game, and the recent one only when shared. */
    private fun outgoingNow(): Outgoing {
        val d = PlazaStore.data
        val fav = d.me.favorite?.let { Libraries.byKey(it) }?.takeIf { !Privacy.isHidden(it) }?.title
        val recent = if (!d.me.shareRecent) null else
            Libraries.games.filter { it.meta.lastPlayed > 0 && !Privacy.isHidden(it) }.maxByOrNull { it.meta.lastPlayed }?.title
        return Outgoing(d.me, d.passes, fav, recent)
    }

    /** Our card as it goes out now (also the "what others see" preview's data). */
    fun cardNow(publicKey: ByteArray, now: Long = System.currentTimeMillis()): PassCard {
        val o = outgoing ?: outgoingNow().also { outgoing = it }
        return PassCard(publicKey, now, o.me.avatar, o.me.name, o.me.greeting, o.passes, o.favorite, o.recent)
    }

    /** The titles the card would carry, for the preview. */
    fun previewTitles(): Pair<String?, String?> = outgoingNow().let { it.favorite to it.recent }

    /** The identity, once made (null before Tandem Pass was first switched on). Safe on any thread after the first. */
    fun identityOrNull(): PassIdentity? = known

    /** The identity once loaded, as Compose state (Settings' Pass ID row). */
    private var known by mutableStateOf<PassIdentity?>(null)

    /** Loads (or makes) the identity off the main thread, then calls [then] on the main thread. */
    fun withIdentity(ctx: Context, then: (PassIdentity) -> Unit) {
        val c = ctx.applicationContext
        identity?.let { then(it); return }
        worker.execute {
            val id = IdentityStore.get(c) ?: return@execute
            identity = id
            main.post { known = id }
            main.post { then(id) }
        }
    }

    private object Host : PassRadio.Host {
        override val publicKey: ByteArray get() = identity!!.publicKey

        override fun sealedCard(nonce: ByteArray): ByteArray {
            val id = identity!!
            return CardCodec.seal(cardNow(id.publicKey), id, nonce)
        }

        override fun blocked(key: String): Boolean = PassBook.isBlocked(PlazaStore.data, key)

        override fun waveFor(key: String): Boolean = PassBook.waveFor(PlazaStore.data, key)

        override fun met(card: PassCard) {
            main.post {
                var outcome = PassOutcome.Blocked
                PlazaStore.update { d -> PassBook.record(d, card, System.currentTimeMillis()).also { outcome = it.second }.first }
                if (outcome == PassOutcome.New || outcome == PassOutcome.Again) {
                    // The counter on our own card goes up with it.
                    outgoing = outgoingNow()
                    if (front) {
                        lastArrival = card.name
                        arrivals++
                    }
                }
            }
        }

        override fun waves(key: String, sentOurs: Boolean, theirWave: Boolean) {
            main.post { PlazaStore.update { PassBook.waves(it, key, sentOurs, theirWave) } }
        }
    }
}
