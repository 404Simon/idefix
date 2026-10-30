package dev.idefix.app.domain.editor

import dev.idefix.app.util.Hashing

sealed interface SaveCheck {
    data object Clean : SaveCheck
    data class ExternalChange(val currentContent: String) : SaveCheck
}

object SaveGuard {
    /**
     * Compares the provider's current text with the baseline captured when the
     * file was opened. An external modification is never overwritten silently.
     */
    fun check(baselineFingerprint: String, currentContent: String): SaveCheck {
        val currentFingerprint = Hashing.sha256(currentContent)
        return if (currentFingerprint == baselineFingerprint) {
            SaveCheck.Clean
        } else {
            SaveCheck.ExternalChange(currentContent)
        }
    }
}
