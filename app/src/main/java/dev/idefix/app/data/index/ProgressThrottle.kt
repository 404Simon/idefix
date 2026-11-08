package dev.idefix.app.data.index

/**
 * Rate-limits progress updates so a fast scan or read loop cannot flood the
 * index [kotlinx.coroutines.flow.StateFlow]. The first call emits; later calls
 * emit only once [intervalMs] has elapsed on the monotonic [nowMs] clock.
 *
 * Not thread-safe: a run uses one instance from its single indexing coroutine.
 */
internal class ProgressThrottle(
    private val intervalMs: Long = 100,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var lastEmitAt: Long? = null

    fun shouldEmit(): Boolean {
        val now = nowMs()
        val last = lastEmitAt
        if (last != null && now - last < intervalMs) return false
        lastEmitAt = now
        return true
    }
}
