package dev.idefix.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central place to report background file/index work. The counter is shared by
 * the whole app so every screen can show the same sync state.
 */
class SyncCoordinator {

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    suspend fun <T> track(block: suspend () -> T): T {
        _activeCount.update { it + 1 }
        return try {
            block()
        } finally {
            _activeCount.update { (it - 1).coerceAtLeast(0) }
        }
    }
}
