package dev.idefix.app.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncCoordinatorTest {

    @Test
    fun countsNestedWork() = runTest {
        val coordinator = SyncCoordinator()

        coordinator.track {
            assertEquals(1, coordinator.activeCount.value)
            coordinator.track {
                assertEquals(2, coordinator.activeCount.value)
            }
            assertEquals(1, coordinator.activeCount.value)
        }

        assertEquals(0, coordinator.activeCount.value)
    }

    @Test
    fun decrementsWhenWorkFails() = runTest {
        val coordinator = SyncCoordinator()

        try {
            coordinator.track { error("boom") }
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(0, coordinator.activeCount.value)
    }
}
