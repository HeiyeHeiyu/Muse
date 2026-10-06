package io.zer0.muse.schedule

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes foreground-service shutdown idempotent across user stop, timeout and empty-state races.
 */
internal class ForegroundServiceStopGate {
    private val requested = AtomicBoolean(false)

    fun tryRequest(): Boolean = requested.compareAndSet(false, true)

    fun reset() {
        requested.set(false)
    }
}
