package sim

import (
	"fmt"
	"strings"
)

// AttackMode selects the graph traversal strategy for attack simulation.
type AttackMode string

const (
	AttackBFS AttackMode = "bfs"
	AttackDFS AttackMode = "dfs"
)

// AttackResult describes one attack operation.
type AttackResult struct {
	Mode           AttackMode `json:"mode"`
	Seed           string     `json:"seed"`
	RequestedSteps int        `json:"requestedSteps"`
	Infected       []string   `json:"infected"`
	StepsTaken     int        `json:"stepsTaken"`
	Metrics        Metrics    `json:"metrics"`
	Events         []Event    `json:"events"`
}

// Attack compromises healthy nodes along BFS or DFS traversal order.
func (e *Engine) Attack(mode AttackMode, seed string, steps int) (AttackResult, error) {
	seed = normalizeNode(seed)
	if steps < 0 {
		return AttackResult{}, fmt.Errorf("steps must be non-negative")
	}
	if mode != AttackBFS && mode != AttackDFS {
		return AttackResult{}, fmt.Errorf("unsupported attack mode %q", mode)
	}
	if !e.graph.HasNode(seed) {
		return AttackResult{}, fmt.Errorf("unknown seed node %q", seed)
	}

	e.mu.Lock()
	defer e.mu.Unlock()

	e.pushSnapshotLocked()
	infected := e.runAttackLocked(mode, seed, steps)
	e.stepsTaken += len(infected)
	e.events = append(e.events, newEvent("attack", fmt.Sprintf("%s from %s infected %s", mode, seed, formatNodeList(infected))))

	return AttackResult{
		Mode:           mode,
		Seed:           seed,
		RequestedSteps: steps,
		Infected:       copyStrings(infected),
		StepsTaken:     len(infected),
		Metrics:        e.metricsLocked(),
		Events:         copyEvents(e.events),
	}, nil
}

func (e *Engine) runAttackLocked(mode AttackMode, seed string, steps int) []string {
	if steps == 0 {
		return nil
	}
	switch mode {
	case AttackBFS:
		return e.attackBFSLocked(seed, steps)
	case AttackDFS:
		return e.attackDFSLocked(seed, steps)
	default:
		return nil
	}
}

func (e *Engine) attackBFSLocked(seed string, steps int) []string {
	visited := map[string]bool{seed: true}
	queue := []string{seed}
	infected := make([]string, 0, steps)

	for len(queue) > 0 && len(infected) < steps {
		node := queue[0]
		queue = queue[1:]
		if !e.traversableLocked(node) {
			continue
		}
		if e.states[node] == Healthy {
			e.states[node] = Compromised
			infected = append(infected, node)
			if len(infected) == steps {
				break
			}
		}
		for _, neighbor := range e.graph.adjacency[node] {
			if !visited[neighbor] {
				visited[neighbor] = true
				queue = append(queue, neighbor)
			}
		}
	}

	return infected
}

func (e *Engine) attackDFSLocked(seed string, steps int) []string {
	visited := make(map[string]bool, nodeCount)
	infected := make([]string, 0, steps)

	var walk func(string)
	walk = func(node string) {
		if len(infected) >= steps || visited[node] {
			return
		}
		visited[node] = true
		if !e.traversableLocked(node) {
			return
		}
		if e.states[node] == Healthy {
			e.states[node] = Compromised
			infected = append(infected, node)
			if len(infected) >= steps {
				return
			}
		}
		for _, neighbor := range e.graph.adjacency[node] {
			walk(neighbor)
			if len(infected) >= steps {
				return
			}
		}
	}

	walk(seed)
	return infected
}

func (e *Engine) traversableLocked(node string) bool {
	return e.states[node] != Defended && e.states[node] != Isolated
}

func formatNodeList(nodes []string) string {
	if len(nodes) == 0 {
		return "[]"
	}
	return "[" + strings.Join(nodes, ", ") + "]"
}
