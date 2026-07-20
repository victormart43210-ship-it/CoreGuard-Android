package sim

import (
	"fmt"
	"strings"
	"sync"
)

// NodeState describes the current defense posture of a lab node.
type NodeState string

const (
	Healthy     NodeState = "Healthy"
	Compromised NodeState = "Compromised"
	Defended    NodeState = "Defended"
	Isolated    NodeState = "Isolated"
)

// NodeStatus is the JSON-friendly status view of one node.
type NodeStatus struct {
	ID    string    `json:"id"`
	State NodeState `json:"state"`
}

// Metrics summarizes current node states and cumulative attack progress.
type Metrics struct {
	CompromisedCount int `json:"compromisedCount"`
	DefendedCount    int `json:"defendedCount"`
	HealthyCount     int `json:"healthyCount"`
	StepsTaken       int `json:"stepsTaken"`
}

// StatusReport is the complete current lab status.
type StatusReport struct {
	Nodes   []NodeStatus `json:"nodes"`
	Metrics Metrics      `json:"metrics"`
	Events  []Event      `json:"events"`
}

// TopologyReport describes the fixed lab graph and optional MST guidance.
type TopologyReport struct {
	Nodes    []string `json:"nodes"`
	Edges    []Edge   `json:"edges"`
	MSTEdges []Edge   `json:"mstEdges,omitempty"`
}

type snapshot struct {
	states     map[string]NodeState
	stepsTaken int
}

// Engine owns an in-memory lab simulation state for one process.
type Engine struct {
	mu         sync.Mutex
	graph      Graph
	states     map[string]NodeState
	snapshots  []snapshot
	events     []Event
	stepsTaken int
}

// NewEngine constructs a lab engine with every node initially healthy.
func NewEngine() *Engine {
	graph := DefaultGraph()
	states := make(map[string]NodeState, nodeCount)
	for _, node := range graph.Nodes() {
		states[node] = Healthy
	}
	return &Engine{
		graph:  graph,
		states: states,
	}
}

// Graph returns a copy of the fixed topology.
func (e *Engine) Graph() Graph {
	return e.graph
}

// Status returns a snapshot of current lab state, metrics, and events.
func (e *Engine) Status() StatusReport {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.statusLocked()
}

// Topology returns the fixed graph and optional Prim MST hardening guidance.
func (e *Engine) Topology(includeMST bool) (TopologyReport, error) {
	report := TopologyReport{
		Nodes: e.graph.Nodes(),
		Edges: e.graph.Edges(),
	}
	if includeMST {
		mst, err := e.graph.PrimMST("N0")
		if err != nil {
			return TopologyReport{}, err
		}
		report.MSTEdges = mst
	}
	return report, nil
}

// Rollback restores the latest mutating-operation snapshot and keeps events append-only.
func (e *Engine) Rollback() (StatusReport, error) {
	e.mu.Lock()
	defer e.mu.Unlock()

	if len(e.snapshots) == 0 {
		return StatusReport{}, fmt.Errorf("no snapshot available for rollback")
	}

	last := e.snapshots[len(e.snapshots)-1]
	e.snapshots = e.snapshots[:len(e.snapshots)-1]
	e.states = copyStates(last.states)
	e.stepsTaken = last.stepsTaken
	e.events = append(e.events, newEvent("rollback", "restored previous lab snapshot"))

	return e.statusLocked(), nil
}

func (e *Engine) pushSnapshotLocked() {
	e.snapshots = append(e.snapshots, snapshot{
		states:     copyStates(e.states),
		stepsTaken: e.stepsTaken,
	})
}

func (e *Engine) statusLocked() StatusReport {
	nodes := make([]NodeStatus, 0, nodeCount)
	for _, node := range e.graph.Nodes() {
		nodes = append(nodes, NodeStatus{
			ID:    node,
			State: e.states[node],
		})
	}
	return StatusReport{
		Nodes:   nodes,
		Metrics: e.metricsLocked(),
		Events:  copyEvents(e.events),
	}
}

func (e *Engine) metricsLocked() Metrics {
	metrics := Metrics{StepsTaken: e.stepsTaken}
	for _, state := range e.states {
		switch state {
		case Compromised:
			metrics.CompromisedCount++
		case Defended:
			metrics.DefendedCount++
		case Healthy:
			metrics.HealthyCount++
		}
	}
	return metrics
}

func normalizeNode(node string) string {
	return strings.ToUpper(strings.TrimSpace(node))
}

func copyStates(states map[string]NodeState) map[string]NodeState {
	copied := make(map[string]NodeState, len(states))
	for node, state := range states {
		copied[node] = state
	}
	return copied
}

func copyEvents(events []Event) []Event {
	if len(events) == 0 {
		return []Event{}
	}
	return append([]Event(nil), events...)
}

func copyStrings(values []string) []string {
	if len(values) == 0 {
		return []string{}
	}
	return append([]string(nil), values...)
}
