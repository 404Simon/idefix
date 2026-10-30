package dev.idefix.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutPolicyTest {

    @Test
    fun coverWidthUsesSinglePaneAndBottomBar() {
        val width = 420f
        assertFalse(AdaptiveLayoutPolicy.useTwoPane(width))
        assertFalse(AdaptiveLayoutPolicy.useNavigationRail(width))
    }

    @Test
    fun foldableInnerWidthUsesTwoPanes() {
        val width = 700f
        assertTrue(AdaptiveLayoutPolicy.useTwoPane(width))
        assertFalse(AdaptiveLayoutPolicy.useNavigationRail(width))
    }

    @Test
    fun tabletWidthAddsNavigationRail() {
        val width = 900f
        assertTrue(AdaptiveLayoutPolicy.useTwoPane(width))
        assertTrue(AdaptiveLayoutPolicy.useNavigationRail(width))
    }

    @Test
    fun breakpointsAreInclusive() {
        assertTrue(AdaptiveLayoutPolicy.useTwoPane(600f))
        assertTrue(AdaptiveLayoutPolicy.useNavigationRail(840f))
        assertFalse(AdaptiveLayoutPolicy.useTwoPane(599.9f))
        assertFalse(AdaptiveLayoutPolicy.useNavigationRail(839.9f))
    }
}
