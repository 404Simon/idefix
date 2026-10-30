package dev.idefix.app.data.index

/**
 * Pure helper for the reconciliation pass. A file that disappeared and a newly
 * discovered file with the same content fingerprint are treated as a rename,
 * provided the fingerprint is unambiguous.
 */
object RenameMatcher {

    data class Candidate(
        val id: Long,
        val relativePath: String,
        val documentUri: String,
        val fingerprint: String,
        val size: Long?,
    )

    data class Rename(
        val oldId: Long,
        val newId: Long,
        val relativePath: String,
        val documentUri: String,
    )

    fun detectRenames(stale: List<Candidate>, fresh: List<Candidate>): List<Rename> {
        if (stale.isEmpty() || fresh.isEmpty()) return emptyList()
        val staleByFingerprint = stale.filter { it.fingerprint.isNotBlank() }
            .groupBy { it.fingerprint to it.size }
            .filterValues { it.size == 1 }
        val freshByFingerprint = fresh.filter { it.fingerprint.isNotBlank() }
            .groupBy { it.fingerprint to it.size }
            .filterValues { it.size == 1 }
        val result = ArrayList<Rename>()
        for ((key, staleList) in staleByFingerprint) {
            val freshList = freshByFingerprint[key] ?: continue
            val old = staleList.single()
            val new = freshList.single()
            if (old.id == new.id) continue
            result += Rename(
                oldId = old.id,
                newId = new.id,
                relativePath = new.relativePath,
                documentUri = new.documentUri,
            )
        }
        return result
    }
}
