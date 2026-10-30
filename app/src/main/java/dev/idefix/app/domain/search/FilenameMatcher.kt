package dev.idefix.app.domain.search

import dev.idefix.app.util.MimeTypes
import kotlin.math.abs
import kotlin.math.max

/**
 * Small deterministic fuzzy matcher for file names. It only runs over the index
 * of names, never over document contents.
 */
object FilenameMatcher {

    const val MIN_SCORE = 120.0

    fun score(query: String, filename: String, relativePath: String): Double {
        val rawQuery = query.trim()
        if (rawQuery.isEmpty()) return 0.0
        val q = rawQuery.lowercase()
        val qBase = MimeTypes.displayNameWithoutExtension(q)
        val name = filename.lowercase()
        val base = MimeTypes.displayNameWithoutExtension(name)
        val path = relativePath.lowercase()

        var best = directScore(q, qBase, name, base, path)
        if (best < 500.0) {
            val tokens = rawQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size > 1) {
                var tokenScore = Double.MAX_VALUE
                for (token in tokens) {
                    val t = token.lowercase()
                    val tBase = MimeTypes.displayNameWithoutExtension(t)
                    val score = directScore(t, tBase, name, base, path)
                    if (score <= 0.0) {
                        tokenScore = 0.0
                        break
                    }
                    tokenScore = minOf(tokenScore, score)
                }
                if (tokenScore != Double.MAX_VALUE && tokenScore > 0.0) {
                    best = max(best, tokenScore - 40.0)
                }
            }
        }
        return best
    }

    private fun directScore(
        query: String,
        queryBase: String,
        name: String,
        base: String,
        path: String,
    ): Double {
        if (query.isEmpty()) return 0.0
        return when {
            name == query -> 1000.0
            base == queryBase -> 980.0
            name.startsWith(query) -> 930.0 - (name.length - query.length).coerceAtLeast(0) * 0.5
            base.startsWith(queryBase) -> 910.0 - (base.length - queryBase.length).coerceAtLeast(0) * 0.5
            wordPrefix(base, queryBase) -> 860.0
            base.contains(queryBase) -> 800.0 - (base.indexOf(queryBase)).coerceAtLeast(0)
            name.contains(query) -> 780.0 - name.indexOf(query).coerceAtLeast(0)
            path.contains(query) -> 560.0 - path.indexOf(query).coerceAtLeast(0) * 0.2
            isSubsequence(queryBase, base) -> 480.0 - (base.length - queryBase.length).coerceAtLeast(0) * 2.0
            else -> fuzzyDistanceScore(queryBase, base)
        }
    }

    private fun wordPrefix(base: String, query: String): Boolean {
        if (query.isEmpty()) return false
        var index = base.indexOf(query)
        while (index >= 0) {
            if (index == 0 || !base[index - 1].isLetterOrDigit()) return true
            index = base.indexOf(query, index + 1)
        }
        return false
    }

    private fun fuzzyDistanceScore(query: String, base: String): Double {
        if (query.isEmpty() || base.isEmpty()) return 0.0
        val allowed = max(1, query.length / 3)
        if (abs(query.length - base.length) > allowed * 2) return 0.0
        val tokens = base.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        var bestDistance = Int.MAX_VALUE
        for (token in tokens) {
            bestDistance = minOf(bestDistance, levenshtein(query, token))
        }
        bestDistance = minOf(bestDistance, levenshtein(query, base))
        return if (bestDistance <= allowed) 400.0 - bestDistance * 40.0 else 0.0
    }

    private fun isSubsequence(query: String, value: String): Boolean {
        if (query.isEmpty()) return true
        var qi = 0
        for (ch in value) {
            if (qi < query.length && ch == query[qi]) qi++
            if (qi == query.length) return true
        }
        return false
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
