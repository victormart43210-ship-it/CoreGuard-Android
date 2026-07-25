package sim

import "fmt"

// PrimMST returns deterministic Prim-style hardening guidance from start.
func (g Graph) PrimMST(start string) ([]Edge, error) {
	start = normalizeNode(start)
	if !g.HasNode(start) {
		return nil, fmt.Errorf("unknown MST start node %q", start)
	}

	visited := map[string]bool{start: true}
	tree := make([]Edge, 0, len(g.nodes)-1)

	for len(visited) < len(g.nodes) {
		candidate, ok := g.bestFrontierEdge(visited)
		if !ok {
			return nil, fmt.Errorf("topology is disconnected")
		}
		tree = append(tree, candidate)
		visited[candidate.To] = true
	}

	return tree, nil
}

func (g Graph) bestFrontierEdge(visited map[string]bool) (Edge, bool) {
	var best Edge
	found := false
	for _, edge := range g.edges {
		forwardVisited := visited[edge.From]
		reverseVisited := visited[edge.To]
		if forwardVisited == reverseVisited {
			continue
		}
		candidate := edge
		if reverseVisited {
			candidate = Edge{From: edge.To, To: edge.From, Weight: edge.Weight}
		}
		if !found || g.lessEdge(candidate, best) {
			best = candidate
			found = true
		}
	}
	return best, found
}

func (g Graph) lessEdge(left, right Edge) bool {
	if left.Weight != right.Weight {
		return left.Weight < right.Weight
	}
	if g.nodeIndex(left.From) != g.nodeIndex(right.From) {
		return g.nodeIndex(left.From) < g.nodeIndex(right.From)
	}
	return g.nodeIndex(left.To) < g.nodeIndex(right.To)
}
