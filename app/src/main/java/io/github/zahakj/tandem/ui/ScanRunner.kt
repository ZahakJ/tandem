package io.github.zahakj.tandem.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs library scans one at a time in [scope], which outlives every screen. A request made while a scan runs becomes
 * one more pass after it. Whoever asked may stop waiting (its screen left the composition) without stopping the scan
 * or losing a request made meanwhile.
 *
 * The scan must not run in the caller's coroutine: on a cold start with the screens swapped, the top screen starts
 * the scan and the bottom screen takes the library over two frames later, so cancelling the top screen's composition
 * would drop the scan and the pass queued behind it, leaving the bottom screen with no library.
 *
 * Call [request] from one thread (the main one); [scope] should run there too.
 */
class ScanRunner(
    private val scope: CoroutineScope,
    private val onScanning: (Boolean) -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
    private val pass: suspend (announce: Boolean, onlyIfChanged: Boolean) -> Unit,
) {
    private var job: Job? = null
    private var again = false
    private var againAnnounce = false

    /** True while a scan (or a pass queued after it) is running. */
    val running: Boolean get() = job?.isActive == true

    /**
     * Starts a scan and returns it to wait on, or, with one already running, queues one more full pass after it and
     * returns null.
     */
    fun request(announce: Boolean, onlyIfChanged: Boolean): Job? {
        if (running) {
            // Something changed while scanning (a system turned off, say): scan once more when this one ends.
            again = true
            againAnnounce = againAnnounce || announce
            return null
        }
        again = false
        againAnnounce = false
        onScanning(true)
        val started = scope.launch {
            var say = announce
            var quiet = onlyIfChanged
            try {
                while (true) {
                    try {
                        pass(say, quiet)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        onError(t)
                    }
                    if (!again) break
                    say = againAnnounce
                    again = false
                    againAnnounce = false
                    quiet = false
                }
            } finally {
                onScanning(false)
            }
        }
        job = started
        return started
    }
}
