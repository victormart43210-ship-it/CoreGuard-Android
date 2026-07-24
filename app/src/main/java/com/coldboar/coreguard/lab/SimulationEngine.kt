package com.coldboar.coreguard.lab

/**
 * In-memory Network Defense Lab engine.
 * Behavior mirrors `cli/internal/sim` (prototype teaching tool, not real network defense).
 */
class SimulationEngine {

    private val states: MutableMap<String, NodeState> =
        LabGraph.nodes.associateWith { NodeState.HEALTHY }.toMutableMap()
    private val events = mutableListOf<LabEvent>()
    private val snapshots = mutableListOf<Snapshot>()
    private var stepsTaken: Int = 0
    private var selectedNode: String = "N0"

    data class Snapshot(
        val states: Map<String, NodeState>,
        val stepsTaken: Int
    )

    fun selectedNode(): String = selectedNode

    fun selectNode(id: String) {
        require(LabGraph.hasNode(id)) { "unknown node $id" }
        selectedNode = id
    }

    fun status(): LabStatus = LabStatus(
        states = states.toMap(),
        metrics = metrics(),
        events = events.toList()
    )

    fun attack(mode: AttackMode, seed: String = selectedNode, steps: Int = 4): List<String> {
        require(steps >= 0) { "steps must be non-negative" }
        require(LabGraph.hasNode(seed)) { "unknown seed $seed" }
        pushSnapshot()
        val infected = when (mode) {
            AttackMode.BFS -> attackBfs(seed, steps)
            AttackMode.DFS -> attackDfs(seed, steps)
        }
        stepsTaken += infected.size
        appendEvent(
            "attack",
            "${mode.name.lowercase()} from $seed infected ${infected.joinToString(prefix = "[", postfix = "]")}"
        )
        return infected
    }

    fun defend(mode: DefenseMode, target: String = selectedNode): List<String> {
        require(LabGraph.hasNode(target)) { "unknown target $target" }
        pushSnapshot()
        val defended = mutableListOf(target)
        if (mode == DefenseMode.CASCADE) {
            defended.addAll(LabGraph.neighbors(target))
        }
        for (node in defended) {
            if (states[node] != NodeState.ISOLATED) {
                states[node] = NodeState.DEFENDED
            }
        }
        appendEvent(
            "defend",
            "${mode.name.lowercase()} defense applied to ${defended.joinToString(prefix = "[", postfix = "]")}"
        )
        return defended
    }

    fun rollback(): Boolean {
        if (snapshots.isEmpty()) return false
        val last = snapshots.removeAt(snapshots.lastIndex)
        states.clear()
        states.putAll(last.states)
        stepsTaken = last.stepsTaken
        appendEvent("rollback", "restored previous lab snapshot")
        return true
    }

    fun reset() {
        pushSnapshot()
        for (node in LabGraph.nodes) {
            states[node] = NodeState.HEALTHY
        }
        stepsTaken = 0
        appendEvent("reset", "all nodes restored to healthy")
    }

    fun mstEdges(): List<LabEdge> = LabGraph.primMst("N0")

    private fun metrics(): LabMetrics {
        var compromised = 0
        var defended = 0
        var healthy = 0
        for (state in states.values) {
            when (state) {
                NodeState.COMPROMISED -> compromised++
                NodeState.DEFENDED -> defended++
                NodeState.HEALTHY -> healthy++
                NodeState.ISOLATED -> Unit
            }
        }
        return LabMetrics(compromised, defended, healthy, stepsTaken)
    }

    private fun pushSnapshot() {
        snapshots.add(Snapshot(states.toMap(), stepsTaken))
    }

    private fun appendEvent(kind: String, detail: String) {
        events.add(LabEvent(System.currentTimeMillis(), kind, detail))
    }

    private fun traversable(node: String): Boolean {
        val state = states[node] ?: return false
        return state != NodeState.DEFENDED && state != NodeState.ISOLATED
    }

    private fun attackBfs(seed: String, steps: Int): List<String> {
        if (steps == 0) return emptyList()
        val visited = mutableSetOf(seed)
        val queue = ArrayDeque<String>().apply { add(seed) }
        val infected = mutableListOf<String>()
        while (queue.isNotEmpty() && infected.size < steps) {
            val node = queue.removeFirst()
            if (!traversable(node)) continue
            if (states[node] == NodeState.HEALTHY) {
                states[node] = NodeState.COMPROMISED
                infected.add(node)
                if (infected.size == steps) break
            }
            for (neighbor in LabGraph.neighbors(node)) {
                if (visited.add(neighbor)) queue.add(neighbor)
            }
        }
        return infected
    }

    private fun attackDfs(seed: String, steps: Int): List<String> {
        if (steps == 0) return emptyList()
        val visited = mutableSetOf<String>()
        val infected = mutableListOf<String>()
        fun walk(node: String) {
            if (infected.size >= steps || node in visited) return
            visited.add(node)
            if (!traversable(node)) return
            if (states[node] == NodeState.HEALTHY) {
                states[node] = NodeState.COMPROMISED
                infected.add(node)
                if (infected.size >= steps) return
            }
            for (neighbor in LabGraph.neighbors(node)) {
                walk(neighbor)
                if (infected.size >= steps) return
            }
        }
        walk(seed)
        return infected
    }
}
