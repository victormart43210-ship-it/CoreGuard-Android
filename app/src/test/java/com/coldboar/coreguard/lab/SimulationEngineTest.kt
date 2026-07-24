package com.coldboar.coreguard.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationEngineTest {

    @Test
    fun `default graph has 16 nodes and ring plus chords`() {
        assertEquals(16, LabGraph.nodes.size)
        assertEquals(24, LabGraph.edges.size) // 16 ring + 8 chords
        assertTrue(LabGraph.hasNode("N0"))
        assertTrue(LabGraph.hasNode("N15"))
    }

    @Test
    fun `bfs attack infects up to steps from seed`() {
        val engine = SimulationEngine()
        val infected = engine.attack(AttackMode.BFS, seed = "N3", steps = 4)
        assertEquals(4, infected.size)
        assertEquals(NodeState.COMPROMISED, engine.status().states["N3"])
        assertEquals(4, engine.status().metrics.compromisedCount)
        assertEquals(12, engine.status().metrics.healthyCount)
    }

    @Test
    fun `defended nodes block attack traversal`() {
        val engine = SimulationEngine()
        engine.defend(DefenseMode.DIRECT, target = "N3")
        val infected = engine.attack(AttackMode.BFS, seed = "N3", steps = 4)
        assertTrue(infected.none { it == "N3" })
        assertEquals(NodeState.DEFENDED, engine.status().states["N3"])
    }

    @Test
    fun `cascade defense covers neighbors`() {
        val engine = SimulationEngine()
        val defended = engine.defend(DefenseMode.CASCADE, target = "N0")
        assertTrue(defended.contains("N0"))
        assertTrue(defended.containsAll(LabGraph.neighbors("N0")))
    }

    @Test
    fun `rollback restores prior snapshot`() {
        val engine = SimulationEngine()
        engine.attack(AttackMode.BFS, seed = "N1", steps = 2)
        assertEquals(2, engine.status().metrics.compromisedCount)
        assertTrue(engine.rollback())
        assertEquals(0, engine.status().metrics.compromisedCount)
        assertEquals(16, engine.status().metrics.healthyCount)
    }

    @Test
    fun `rollback fails when no snapshot`() {
        assertFalse(SimulationEngine().rollback())
    }

    @Test
    fun `prim mst spans all nodes`() {
        val mst = LabGraph.primMst("N0")
        assertEquals(15, mst.size)
    }

    @Test
    fun `protanopia palette differs from standard for compromised`() {
        val std = LabPalette.forType(ColorVisionType.STANDARD)
        val pro = LabPalette.forType(ColorVisionType.PROTANOPIA)
        assertTrue(std.compromised != pro.compromised)
        assertEquals(NodeShape.SQUARE, std.shapeFor(NodeState.COMPROMISED))
        assertEquals(NodeShape.DIAMOND, pro.shapeFor(NodeState.DEFENDED))
    }
}
