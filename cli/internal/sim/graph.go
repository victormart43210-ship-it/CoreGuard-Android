package sim

import (
	"fmt"
	"sort"
)

const (
	nodeCount = 16
)

// Edge is an undirected connection in the lab topology.
type Edge struct {
	From   string `json:"from"`
	To     string `json:"to"`
	Weight int    `json:"weight"`
}

// Graph is the fixed CoreGuard Network Defense Lab topology.
type Graph struct {
	nodes     []string
	edges     []Edge
	adjacency map[string][]string
	index     map[string]int
}

// DefaultGraph returns a connected 16-node topology: a ring plus cross-ring chords.
func DefaultGraph() Graph {
	nodes := make([]string, 0, nodeCount)
	index := make(map[string]int, nodeCount)
	for i := 0; i < nodeCount; i++ {
		node := fmt.Sprintf("N%d", i)
		nodes = append(nodes, node)
		index[node] = i
	}

	edges := make([]Edge, 0, 24)
	for i := 0; i < nodeCount; i++ {
		edges = append(edges, Edge{
			From:   fmt.Sprintf("N%d", i),
			To:     fmt.Sprintf("N%d", (i+1)%nodeCount),
			Weight: 1,
		})
	}
	for i := 0; i < nodeCount/2; i++ {
		edges = append(edges, Edge{
			From:   fmt.Sprintf("N%d", i),
			To:     fmt.Sprintf("N%d", i+8),
			Weight: 2,
		})
	}

	adjacency := make(map[string][]string, nodeCount)
	for _, edge := range edges {
		adjacency[edge.From] = append(adjacency[edge.From], edge.To)
		adjacency[edge.To] = append(adjacency[edge.To], edge.From)
	}
	for node := range adjacency {
		sort.Slice(adjacency[node], func(i, j int) bool {
			return index[adjacency[node][i]] < index[adjacency[node][j]]
		})
	}

	return Graph{
		nodes:     nodes,
		edges:     append([]Edge(nil), edges...),
		adjacency: adjacency,
		index:     index,
	}
}

// Nodes returns all node IDs in deterministic order.
func (g Graph) Nodes() []string {
	return append([]string(nil), g.nodes...)
}

// Edges returns all topology edges in deterministic order.
func (g Graph) Edges() []Edge {
	return append([]Edge(nil), g.edges...)
}

// Neighbors returns a node's adjacent nodes sorted by node number.
func (g Graph) Neighbors(node string) ([]string, error) {
	if !g.HasNode(node) {
		return nil, fmt.Errorf("unknown node %q", node)
	}
	return append([]string(nil), g.adjacency[node]...), nil
}

// HasNode reports whether node exists in the fixed lab topology.
func (g Graph) HasNode(node string) bool {
	_, ok := g.index[node]
	return ok
}

func (g Graph) nodeIndex(node string) int {
	return g.index[node]
}
