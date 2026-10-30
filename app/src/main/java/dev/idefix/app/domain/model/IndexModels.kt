package dev.idefix.app.domain.model

enum class IndexPhase {
    SCANNING,
    READING,
    FINALISING,
    CANCELLED,
}

sealed interface IndexState {
    data object Idle : IndexState

    data class Running(
        val phase: IndexPhase,
        val discovered: Int = 0,
        val processed: Int = 0,
        val indexed: Int = 0,
        val failures: Int = 0,
        val currentPath: String? = null,
    ) : IndexState

    data class Ready(
        val fileCount: Int,
        val lastIndexedAt: Long,
        val failures: Int = 0,
        val message: String? = null,
    ) : IndexState

    data class Failed(val message: String) : IndexState

    data object Cancelled : IndexState

    val isRunning: Boolean get() = this is Running
}

data class IndexSummary(
    val fileCount: Int,
    val lastIndexedAt: Long?,
    val failedCount: Int,
)

data class IndexResult(
    val indexed: Int,
    val removed: Int,
    val failures: List<String>,
    val cancelled: Boolean = false,
)
