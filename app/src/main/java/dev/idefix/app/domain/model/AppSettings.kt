package dev.idefix.app.domain.model

data class AppSettings(
    val vaultUri: String? = null,
    val vaultName: String? = null,
    val lectureUri: String? = null,
    val lectureName: String? = null,
    val themeMode: ThemeMode = ThemeMode.MATCHA,
    val includeHidden: Boolean = false,
    val caseSensitiveSearch: Boolean = false,
    val dynamicColor: Boolean = true,
    val lastSearchMode: SearchMode = SearchMode.FILENAME,
    val recentLimit: Int = 50,
) {
    val hasVault: Boolean get() = !vaultUri.isNullOrBlank()
    val hasLecture: Boolean get() = !lectureUri.isNullOrBlank()
}
