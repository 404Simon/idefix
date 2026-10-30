package dev.idefix.app.ui

/** Window-size breakpoints used by the app shell, kept pure for testing. */
object AdaptiveLayoutPolicy {
    const val TWO_PANE_MIN_WIDTH_DP = 600f
    const val RAIL_MIN_WIDTH_DP = 840f

    fun useTwoPane(widthDp: Float): Boolean = widthDp >= TWO_PANE_MIN_WIDTH_DP

    fun useNavigationRail(widthDp: Float): Boolean = widthDp >= RAIL_MIN_WIDTH_DP
}
