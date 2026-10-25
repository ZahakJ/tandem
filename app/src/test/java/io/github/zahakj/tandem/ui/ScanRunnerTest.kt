package io.github.zahakj.tandem.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library scan runner (ScanRunner.kt): one scan at a time, never stopped or lost with the screen that asked. */
class ScanRunnerTest {

    private suspend fun idle(runner: ScanRunner) = withTimeout(5_000) { while (runner.running) yield() }

    /**
     * The cold start with swapped screens: the top screen's library starts the scan, the bottom screen's asks while
     * it runs, then the top screen's composition (and the coroutine waiting on the scan) goes away. The scan must
     * still finish and the bottom screen's pass still run, or the bottom screen is left with no library.
     */
    @Test fun aCancelledCallerNeitherStopsTheScanNorDropsAQueuedPass() = runBlocking<Unit> {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val firstPass = CompletableDeferred<Unit>()
        var passes = 0
        var loaded = false
        val runner = ScanRunner(scope) { _, _ ->
            passes++
            if (passes == 1) firstPass.await()
            loaded = true
        }
        val topScreen = launch { runner.request(announce = false, onlyIfChanged = false)?.join() }
        yield()
        assertTrue(runner.running)
        // The bottom screen's library asks while the first scan runs: queued, nothing new started.
        assertNull(runner.request(announce = false, onlyIfChanged = false))
        topScreen.cancel()
        firstPass.complete(Unit)
        idle(runner)
        assertTrue(loaded)
        assertEquals(2, passes)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun requestsWhileScanningBecomeOneFullAnnouncedPass() = runBlocking<Unit> {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val gate = CompletableDeferred<Unit>()
        val seen = mutableListOf<Pair<Boolean, Boolean>>()
        val runner = ScanRunner(scope) { announce, onlyIfChanged ->
            seen += announce to onlyIfChanged
            if (seen.size == 1) gate.await()
        }
        assertNotNull(runner.request(announce = false, onlyIfChanged = true))
        yield()
        assertNull(runner.request(announce = false, onlyIfChanged = true))
        assertNull(runner.request(announce = true, onlyIfChanged = true))
        gate.complete(Unit)
        idle(runner)
        // The first pass as asked; the queued ones folded into one full pass that announces itself.
        assertEquals(listOf(false to true, true to false), seen)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun aFailedScanIsReportedAndTheNextRequestStartsAgain() = runBlocking<Unit> {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val states = mutableListOf<Boolean>()
        val errors = mutableListOf<Throwable>()
        var calls = 0
        val runner = ScanRunner(scope, onScanning = { states += it }, onError = { errors += it }) { _, _ ->
            calls++
            if (calls == 1) error("disk went away")
        }
        runner.request(announce = false, onlyIfChanged = false)?.join()
        assertFalse(runner.running)
        assertEquals(1, errors.size)
        runner.request(announce = false, onlyIfChanged = false)?.join()
        assertEquals(2, calls)
        assertEquals(listOf(true, false, true, false), states)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
