package com.coldboar.coreguard.lab

/**
 * Fixed 16-node Network Defense Lab topology (ring + cross chords).
 * Must stay aligned with `cli/internal/sim/graph.go`.
 */
data class LabEdge(
    val from: String,
    val to: String,
    val weight: Int = 1
)

enum class NodeState {
    HEALTHY,
    COMPROMISED,
    DEFENDED,
    ISOLATED
}

enum class AttackMode { BFS, DFS }

enum class DefenseMode { DIRECT, CASCADE }

data class LabEvent(
    val tsEpochMs: Long,
    val kind: String,
    val detail: String
)

data class LabMetrics(
    val compromisedCount: Int,
    val defendedCount: Int,
    val healthyCount: Int,
    val stepsTaken: Int
)

data class LabStatus(
    val states: Map<String, NodeState>,
    val metrics: LabMetrics,
    val events: List<LabEvent>
)

object LabGraph {
    const val NODE_COUNT = 16

    val nodes: List<String> = (0 until NODE_COUNT).map { "N$it" }

    val edges: List<LabEdge> = buildList {
        for (i in 0 until NODE_COUNT) {
            add(LabEdge("N$i", "N${(i + 1) % NODE_COUNT}", weight = 1))
        }
        for (i in 0 until NODE_COUNT / 2) {
            add(LabEdge("N$i", "N${i + 8}", weight = 2))
        }
    }

    private val adjacency: Map<String, List<String>> = buildMap {
        val buckets = nodes.associateWith { mutableListOf<String>() }
        for (edge in edges) {
            buckets.getValue(edge.from).add(edge.to)
            buckets.getValue(edge.to).add(edge.from)
        }
        for ((node, neighbors) in buckets) {
            put(node, neighbors.sortedBy { it.removePrefix("N").toInt() })
        }
    }

    fun hasNode(id: String): Boolean = id in adjacency

    fun neighbors(id: String): List<String> = adjacency[id].orEmpty()

    fun nodeIndex(id: String): Int = id.removePrefix("N").toInt()

    /** Prim MST hardening guidance from [start], matching Go `PrimMST`. */
    fun primMst(start: String = "N0"): List<LabEdge> {
        require(hasNode(start)) { "unknown MST start $start" }
        val visited = mutableSetOf(start)
        val tree = mutableListOf<LabEdge>()
        while (visited.size < nodes.size) {
            val candidate = bestFrontierEdge(visited) ?: error("topology disconnected")
            tree.add(candidate)
            visited.add(candidate.to)
        }
        return tree
    }

    private fun bestFrontierEdge(visited: Set<String>): LabEdge? {
        var best: LabEdge? = null
        for (edge in edges) {
            val fromV = edge.from in visited
            val toV = edge.to in visited
            if (fromV == toV) continue
            val candidate = if (toV) LabEdge(edge.to, edge.from, edge.weight) else edge
            if (best == null || lessEdge(candidate, best)) best = candidate
        }
        return best
    }

    private fun lessEdge(left: LabEdge, right: LabEdge): Boolean {
        if (left.weight != right.weight) return left.weight < right.weight
        if (nodeIndex(left.from) != nodeIndex(right.from)) {
            return nodeIndex(left.from) < nodeIndex(right.from)
        }
        return nodeIndex(left.to) < nodeIndex(right.to)
    }
}
