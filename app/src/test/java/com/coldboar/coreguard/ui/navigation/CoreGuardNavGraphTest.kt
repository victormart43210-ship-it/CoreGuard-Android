package com.coldboar.coreguard.ui.navigation

import com.coldboar.coreguard.quilla.QuillaActionSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreGuardNavGraphTest {

    @Test
    fun `all routes are unique and non-blank`() {
        val routes = CoreGuardNavGraph.allRoutes
        assertTrue(routes.isNotEmpty())
        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.all { it.isNotBlank() && !it.contains(' ') })
    }

    @Test
    fun `bottom tabs are five primary destinations without secondary screens`() {
        assertEquals(
            listOf("home", "scanner", "shield", "compliance", "settings"),
            CoreGuardNavGraph.bottomTabRoutes
        )
        CoreGuardNavGraph.bottomTabRoutes.forEach { tab ->
            assertTrue(tab in CoreGuardNavGraph.allRoutes)
            assertFalse(CoreGuardNavGraph.routesWithoutBottomBar.contains(tab))
        }
    }

    @Test
    fun `start destination is home and shows bottom bar`() {
        assertEquals(CoreGuardRoute.Home.route, CoreGuardNavGraph.startDestination)
        assertTrue(CoreGuardNavGraph.showsBottomBar(CoreGuardRoute.Home.route))
        assertFalse(CoreGuardNavGraph.showsBottomBar(CoreGuardRoute.Tools.route))
        assertFalse(CoreGuardNavGraph.showsBottomBar(null))
    }

    @Test
    fun `quilla navigation actions map to registered routes`() {
        assertEquals(
            CoreGuardRoute.Scanner.route,
            CoreGuardNavGraph.routeForQuillaAction(QuillaActionSuggestion.RUN_SCAN)
        )
        assertEquals(
            CoreGuardRoute.Shield.route,
            CoreGuardNavGraph.routeForQuillaAction(QuillaActionSuggestion.OPEN_SHIELD)
        )
        assertEquals(
            CoreGuardRoute.Timeline.route,
            CoreGuardNavGraph.routeForQuillaAction(QuillaActionSuggestion.OPEN_TIMELINE)
        )
        assertNull(CoreGuardNavGraph.routeForQuillaAction(QuillaActionSuggestion.SYNC_INTEL))
        assertNull(CoreGuardNavGraph.routeForQuillaAction("unknown_action"))
    }

    @Test
    fun `every quilla navigation route is on the nav graph`() {
        listOf(
            QuillaActionSuggestion.RUN_SCAN,
            QuillaActionSuggestion.OPEN_SHIELD,
            QuillaActionSuggestion.OPEN_TIMELINE
        ).forEach { id ->
            val route = CoreGuardNavGraph.routeForQuillaAction(id)
            assertTrue("$id → $route missing from graph", route in CoreGuardNavGraph.allRoutes)
        }
    }
}
