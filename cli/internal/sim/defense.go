package sim

import "fmt"

// DefenseMode selects how broadly a defense operation is applied.
type DefenseMode string

const (
	DefenseDirect  DefenseMode = "direct"
	DefenseCascade DefenseMode = "cascade"
)

// DefenseResult describes one defense operation.
type DefenseResult struct {
	Mode     DefenseMode `json:"mode"`
	Target   string      `json:"target"`
	Defended []string    `json:"defended"`
	Metrics  Metrics     `json:"metrics"`
	Events   []Event     `json:"events"`
}

// Defend marks a target, or a target and all neighbors, as defended.
func (e *Engine) Defend(mode DefenseMode, target string) (DefenseResult, error) {
	target = normalizeNode(target)
	if mode != DefenseDirect && mode != DefenseCascade {
		return DefenseResult{}, fmt.Errorf("unsupported defense mode %q", mode)
	}
	if !e.graph.HasNode(target) {
		return DefenseResult{}, fmt.Errorf("unknown target node %q", target)
	}

	e.mu.Lock()
	defer e.mu.Unlock()

	e.pushSnapshotLocked()
	defended := []string{target}
	if mode == DefenseCascade {
		defended = append(defended, e.graph.adjacency[target]...)
	}
	for _, node := range defended {
		if e.states[node] != Isolated {
			e.states[node] = Defended
		}
	}
	e.events = append(e.events, newEvent("defend", fmt.Sprintf("%s defense applied to %s", mode, formatNodeList(defended))))

	return DefenseResult{
		Mode:     mode,
		Target:   target,
		Defended: copyStrings(defended),
		Metrics:  e.metricsLocked(),
		Events:   copyEvents(e.events),
	}, nil
}
