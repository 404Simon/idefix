package dev.idefix.app.domain.model

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    MATCHA;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MATCHA
    }
}
